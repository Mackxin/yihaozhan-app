package xyz.yihaozhan.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import xyz.yihaozhan.app.data.MusicPlay

/**
 * 让音乐在切出去之后还活着。
 *
 * 为什么必须是个前台服务：Android 8 起后台进程随时会被掐，普通 Service 也不行；
 * 只有前台服务（带一条常驻通知）才算「用户知道我在干活」。
 * 顺带好处是通知栏能直接上一首 / 播放暂停 / 下一首，不用回 App。
 *
 * 播放器本身不在这个 Service 里 —— 它是 [MusicPlay] 那个单例。
 * Service 只干两件事：把进程顶在前台、把控制按钮的点击转成对单例的调用。
 * 这样 UI 和通知操作的是同一个播放器，不会出现「通知栏显示在播、页面显示暂停」。
 */
class MusicService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MusicPlay.attach(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MusicPlay.attach(this)
        when (intent?.action) {
            ACTION_PREV -> MusicPlay.prev()
            ACTION_NEXT -> MusicPlay.next()
            ACTION_TOGGLE -> MusicPlay.toggle()
            ACTION_STOP -> {
                MusicPlay.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        /* 不管是哪条命令，都顺手把通知刷一遍 —— 标题、播放/暂停图标都跟着变 */
        runCatching { startForeground(NOTI_ID, build()) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { stopForeground(true) }
        super.onDestroy()
    }

    private fun build(): Notification {
        val t = MusicPlay.cur
        val title = t?.title?.ifBlank { "未命名" } ?: "本地音乐"
        val sub = when {
            t == null -> "点开挑一首"
            MusicPlay.playing -> t.artist.ifBlank { "正在播放" } + " · " + MusicPlay.modeName
            else -> "已暂停 · " + MusicPlay.modeName
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("page", "music")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(sub)
            .setContentIntent(open)
            .setOngoing(MusicPlay.playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_media_previous, "上一首",
                cmd(ACTION_PREV, 1)
            )
            .addAction(
                if (MusicPlay.playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (MusicPlay.playing) "暂停" else "播放",
                cmd(ACTION_TOGGLE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "下一首", cmd(ACTION_NEXT, 3))

        return b.build()
    }

    private fun cmd(action: String, req: Int): PendingIntent = PendingIntent.getService(
        this, req,
        Intent(this, MusicService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val CHANNEL = "xy_music"
        private const val NOTI_ID = 4201

        const val ACTION_SYNC = "xyz.yihaozhan.app.music.SYNC"
        const val ACTION_PREV = "xyz.yihaozhan.app.music.PREV"
        const val ACTION_NEXT = "xyz.yihaozhan.app.music.NEXT"
        const val ACTION_TOGGLE = "xyz.yihaozhan.app.music.TOGGLE"
        const val ACTION_STOP = "xyz.yihaozhan.app.music.STOP"

        /** 起服务并刷新通知。已经在前台时再调只是更新那一条，不会叠 */
        fun refresh(ctx: Context?) {
            val c = ctx ?: return
            runCatching {
                val i = Intent(c, MusicService::class.java).setAction(ACTION_SYNC)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            }
        }

        /** 彻底收工（清空曲库 / 停播时） */
        fun shutdown(ctx: Context?) {
            val c = ctx ?: return
            runCatching { c.stopService(Intent(c, MusicService::class.java)) }
        }

        /** 通知渠道：只在 8.0+ 存在，建过一次就不用再建 */
        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "本地音乐", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "播放本地音乐时常驻的控制条"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
    }
}
