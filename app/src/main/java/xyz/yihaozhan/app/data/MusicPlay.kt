package xyz.yihaozhan.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.yihaozhan.app.MusicService
import kotlin.random.Random

/**
 * 全 App 一份的播放器。
 *
 * 为什么是单例而不是放在页面里：音乐页只是「控制面板」，用户切到别的页签、
 * 甚至退回桌面，声音都得继续。播放器活在进程里，页面来去自由。
 *
 * 线程：所有公开方法都当主线程调用（UI 点击 / 通知按钮 / 广播），
 * 内部只碰 [MediaPlayer]，它自己也是主线程模型。
 */
object MusicPlay {

    private var mp: MediaPlayer? = null
    private var app: Context? = null

    /** 当前这首 */
    var cur: Track? by mutableStateOf(null)
        private set

    var playing by mutableStateOf(false)
        private set

    var posMs by mutableStateOf(0L)
        private set

    var durMs by mutableStateOf(0L)
        private set

    /** 播放模式：0 顺序循环 / 1 单曲循环 / 2 随机 */
    var mode by mutableStateOf(0)
        private set

    /** 当前播放队列（就是曲库那份，点某首时一起传进来） */
    var queue: List<Track> by mutableStateOf(emptyList())
        private set

    var index by mutableStateOf(-1)
        private set

    val modeName: String
        get() = when (mode) {
            1 -> "单曲循环"
            2 -> "随机播放"
            else -> "顺序播放"
        }

    /** 睡眠定时：还剩几分钟（0 = 没设） */
    var sleepMin by mutableStateOf(0)
        private set

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var sleepTask: Runnable? = null

    /**
     * 定时暂停。到点只是 `pause()`，不销毁播放器 —— 第二天点一下播放还能接着听，
     * 也不用重新准备音频（准备一次要读盘 + 解析，比暂停重启慢得多）。
     */
    fun sleepAfter(min: Int) {
        cancelSleep()
        if (min <= 0) return
        sleepMin = min
        val r = Runnable {
            sleepTask = null
            sleepMin = 0
            runCatching { mp?.pause() }
            playing = false
            push()
        }
        sleepTask = r
        main.postDelayed(r, min * 60_000L)
    }

    fun cancelSleep() {
        sleepTask?.let { main.removeCallbacks(it) }
        sleepTask = null
        sleepMin = 0
    }

    fun attach(ctx: Context) {
        if (app == null) {
            app = ctx.applicationContext
            runCatching { MusicService.ensureChannel(ctx.applicationContext) }
        }
    }

    /* -------------------------------- 控制 -------------------------------- */

    /** 换一整批歌，并从第 start 首开始（点列表某一行就走这里） */
    fun setQueue(list: List<Track>, start: Int, autoplay: Boolean = true) {
        queue = list
        if (list.isEmpty()) {
            stop()
            return
        }
        startAt(start.coerceIn(0, list.lastIndex), autoplay)
    }

    fun playAt(i: Int) {
        if (i in queue.indices) startAt(i, true)
    }

    fun toggle() {
        val p = mp
        if (p == null) {
            /* 还没放过：把队列第一首（或上次停的那首）拉起来 */
            if (queue.isNotEmpty()) startAt(if (index in queue.indices) index else 0, true)
            return
        }
        runCatching { if (p.isPlaying) p.pause() else p.start() }
        playing = runCatching { mp?.isPlaying == true }.getOrDefault(false)
        push()
    }

    /** 下一首。auto = 上一首自然播完（不是用户主动点） */
    fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        /* 单曲循环：播完了也原地重来；用户主动点「下一首」还是要换 */
        if (auto && mode == 1) {
            startAt(index, true)
            return
        }
        val i = when {
            mode == 2 && queue.size > 1 -> {
                var r = index
                while (r == index) r = Random.nextInt(queue.size)
                r
            }
            index >= queue.lastIndex -> 0
            else -> index + 1
        }
        startAt(i, true)
    }

    /** 上一首。播过 3 秒就别换歌，回到这首歌开头 —— 跟主流播放器一个手感 */
    fun prev() {
        if (queue.isEmpty()) return
        if (posMs > 3000) {
            seekTo(0)
            return
        }
        val i = if (index <= 0) queue.lastIndex else index - 1
        startAt(coerce(i), true)
    }

    private fun coerce(i: Int): Int = if (queue.isEmpty()) 0 else i.coerceIn(0, queue.lastIndex)

    fun seekTo(ms: Long) {
        val p = mp ?: return
        val to = ms.coerceIn(0, durMs.coerceAtLeast(0))
        runCatching { p.seekTo(to.toInt()) }
        posMs = to
    }

    fun cycleMode() {
        mode = (mode + 1) % 3
    }

    fun stop() {
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        mp = null
        playing = false
        posMs = 0
        cur = null
        index = -1
        cancelSleep()
        MusicService.shutdown(app)
    }

    /** 让播放位置跟上真实进度。UI 每 400ms 调一次（没在播就不用调） */
    fun syncPos() {
        val p = mp ?: return
        runCatching {
            if (p.isPlaying) {
                posMs = p.currentPosition.toLong()
                durMs = p.duration.toLong()
            }
        }
    }

    /* -------------------------------- 内核 -------------------------------- */

    private fun startAt(i: Int, autoplay: Boolean) {
        val t = queue.getOrNull(i) ?: return
        val ctx = app ?: return
        index = i
        cur = t
        posMs = 0
        durMs = t.durMs

        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        mp = null

        val p = MediaPlayer()
        mp = p
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        runCatching {
            p.setDataSource(MusicLib.of(ctx).audioFile(t).absolutePath)
        }.onFailure {
            /* 文件没了（清过缓存 / 手动删）：把这条从队列里剔掉，别一直卡着 */
            playing = false
            return
        }
        p.setOnPreparedListener {
            durMs = runCatching { it.duration.toLong() }.getOrDefault(t.durMs)
            if (autoplay) {
                runCatching { it.start() }
                playing = true
            } else {
                playing = false
            }
            push()
        }
        p.setOnCompletionListener {
            playing = false
            next(auto = true)
        }
        p.setOnErrorListener { _, _, _ ->
            playing = false
            push()
            true
        }
        runCatching { p.prepareAsync() }
        push()
    }

    /** 起前台服务 + 刷新通知 */
    private fun push() = MusicService.refresh(app)
}
