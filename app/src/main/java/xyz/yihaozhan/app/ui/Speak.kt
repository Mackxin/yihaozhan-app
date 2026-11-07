package xyz.yihaozhan.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 文章朗读 —— 直接用系统的文字转语音（TextToSpeech），不引第三方、不联网。
 *
 * 为什么不用 MediaPlayer + 在线合成：那要联网、要密钥、要缓存一堆 mp3；
 * 系统 TTS 在 Android 上本来就带着（多数机型是高德/讯飞那套），离线也能念。
 *
 * 两个必须处理的坑：
 *  ① `TextToSpeech` 的初始化回调可能在字段赋值之前就回来 —— 所以语言设置在
 *    **第一次 speak() 时**才做，不在 init 里摸自己；
 *  ② 单次 `speak()` 的文本上限是 4000 字（`getMaxSpeechInputLength`），
 *    长文章要切成几段、用 `QUEUE_ADD` 排进同一队列，顺序才不会乱。
 */
@Stable
class Speaker(private val ctx: Context) {

    private var tts: TextToSpeech? = null

    /** 初始化成功、且这台机器上有中文语音 */
    private var ok = false
    private var langSet = false

    /** 正在念（按钮据此在「朗读 / 停止」之间切换） */
    var speaking by mutableStateOf(false)
        private set

    /** 语速倍率（0.8 / 1.0 / 1.25 / 1.5），改完对**下一次** speak 生效 */
    var rate by mutableStateOf(1.0f)
        private set

    /** 定时停止：还剩几分钟（0 = 没设）。到点自动停并清零 */
    var sleepMin by mutableStateOf(0)
        private set

    private var sleepTask: Runnable? = null

    private val main = Handler(Looper.getMainLooper())
    private var lastUtterance = ""

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ok = status == TextToSpeech.SUCCESS
        }
    }

    /** 真正开始念；`text` 是已经拼好的纯文本 */
    fun speak(text: String) {
        val t = tts
        if (t == null || !ok) {
            Toast.makeText(ctx, "这台机器没有可用的朗读引擎", Toast.LENGTH_SHORT).show()
            return
        }
        if (!langSet) {
            langSet = true
            val r = runCatching { t.setLanguage(Locale.CHINA) }.getOrDefault(-1)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                ok = false
                Toast.makeText(ctx, "系统里没装中文语音包，去「设置 → 无障碍 → 文字转语音」装一个", Toast.LENGTH_LONG)
                    .show()
                return
            }
            runCatching { t.setSpeechRate(rate) }
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    /* 只有最后一段念完才算「停了」——中间几段结束时按钮不能弹回去 */
                    if (utteranceId == lastUtterance) {
                        main.post { speaking = false }
                    }
                }
                @Deprecated("老接口也要实现")
                override fun onError(utteranceId: String?) {
                    main.post { speaking = false }
                }
            })
        }

        val body = text.trim()
        if (body.isEmpty()) return
        /* 换一篇再念：只掐声音，**别撤定时器** —— 否则先设「30 分钟后停」再点朗读，
           定时器当场就被清了，等于没设（这个顺序差异很容易漏） */
        stopSpeak()
        val parts = splitForTts(body)
        parts.forEachIndexed { i, p ->
            val id = "xy-" + System.currentTimeMillis() + "-" + i
            if (i == parts.lastIndex) lastUtterance = id
            t.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        speaking = parts.isNotEmpty()
    }

    /** 改语速。TTS 引擎没起来时先记着，等第一次 speak 再一起设 */
    fun updateRate(v: Float) {
        rate = v.coerceIn(0.5f, 2f)
        if (ok) runCatching { tts?.setSpeechRate(rate) }
    }

    /** 定时停止：min 分钟后自动停（0 = 取消）。睡前听长文用 */
    fun sleepAfter(min: Int) {
        cancelSleep()
        if (min <= 0) return
        sleepMin = min
        val r = Runnable {
            sleepTask = null
            sleepMin = 0
            stop()
        }
        sleepTask = r
        main.postDelayed(r, min * 60_000L)
    }

    private fun cancelSleep() {
        sleepTask?.let { main.removeCallbacks(it) }
        sleepTask = null
        sleepMin = 0
    }

    fun stop() {
        stopSpeak()
        /* 手动停也把定时撤掉，不然过一会儿又「自动停」一次，看着像鬼 */
        cancelSleep()
    }

    /** 只掐掉这一次朗读，不动定时器 —— speak() 里换段落时用它 */
    private fun stopSpeak() {
        runCatching { tts?.stop() }
        speaking = false
    }

    fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ok = false
    }
}

/**
 * 切成能念的几段：优先在句号/问号/换行处断开，一段最多 700 字
 * （离 4000 的上限很远，宁可多排几次队，也不要中途被系统截断）。
 */
private fun splitForTts(text: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    val stops = charArrayOf('。', '！', '？', '；', '\n', '.', '!', '?')
    for (ch in text) {
        sb.append(ch)
        if (sb.length >= 700 || (ch in stops && sb.length >= 200)) {
            val one = sb.toString().trim()
            if (one.isNotEmpty()) out += one
            sb.setLength(0)
        }
    }
    val tail = sb.toString().trim()
    if (tail.isNotEmpty()) out += tail
    return out
}

/** 页面里拿一个朗读器：页面销毁时自动 shutdown（不关掉会一直占着音频焦点） */
@Composable
fun rememberSpeaker(): Speaker {
    val ctx = LocalContext.current
    val sp = remember { Speaker(ctx) }
    DisposableEffect(sp) { onDispose { sp.release() } }
    return sp
}
