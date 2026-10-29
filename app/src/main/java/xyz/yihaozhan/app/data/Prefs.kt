package xyz.yihaozhan.app.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 用户设置，存 SharedPreferences。
 *
 * 用 mutableStateOf 兜着而不是每次现读：改了设置界面要立刻跟着变，
 * 读文件是异步又不通知的，那样切个主题得等下次重进。
 */
@Stable
class Prefs private constructor(ctx: Context) {

    private val sp = ctx.applicationContext
        .getSharedPreferences("yihaozhan.prefs", Context.MODE_PRIVATE)

    /** 主题：auto 跟随系统 / light 浅色 / dark 深色 */
    var theme by mutableStateOf(sp.getString("theme", "auto") ?: "auto")
        private set

    /** 正文字号档位：0 小 / 1 标准 / 2 大 */
    var fontSize by mutableStateOf(sp.getInt("fontSize", 1).coerceIn(0, 2))
        private set

    /** 打开时自动拉最新内容 */
    var autoSync by mutableStateOf(sp.getBoolean("autoSync", true))
        private set

    /** 启动停在哪一页（对应底部 tab 下标） */
    var startTab by mutableStateOf(sp.getInt("startTab", 0).coerceIn(0, 4))
        private set

    fun updateTheme(v: String) {
        theme = v
        sp.edit().putString("theme", v).apply()
    }

    fun updateFontSize(v: Int) {
        fontSize = v.coerceIn(0, 2)
        sp.edit().putInt("fontSize", fontSize).apply()
    }

    fun updateAutoSync(v: Boolean) {
        autoSync = v
        sp.edit().putBoolean("autoSync", v).apply()
    }

    fun updateStartTab(v: Int) {
        startTab = v.coerceIn(0, 4)
        sp.edit().putInt("startTab", startTab).apply()
    }

    /** 字号档位换算成倍率，交给 Markdown 渲染用 */
    val fontScale: Float
        get() = when (fontSize) {
            0 -> 0.92f
            2 -> 1.14f
            else -> 1f
        }

    /** 设置页显示的档位名 */
    val fontSizeName: String
        get() = when (fontSize) {
            0 -> "小"
            2 -> "大"
            else -> "标准"
        }

    companion object {
        /* 全 App 一份：界面要读写同一份状态，ViewModel 也得能读到「自动更新」这个开关 */
        @Volatile
        private var inst: Prefs? = null

        fun of(ctx: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext).also { inst = it }
        }
    }
}
