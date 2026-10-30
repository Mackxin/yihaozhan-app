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

    /** 启动停在哪一页（对应 tab 下标，0 首页 / 1 分享 / 2 文章 / 3 导航 / 4 设置 / 5 收藏） */
    var startTab by mutableStateOf(sp.getInt("startTab", 0).coerceIn(0, LAST_TAB))
        private set

    /** 底栏显示图标？默认关 —— 只留文字，药丸更矮更轻 */
    var dockIcons by mutableStateOf(sp.getBoolean("dockIcons", false))
        private set

    /** 底栏按钮顺序，逗号分隔的 tab 下标 */
    var tabOrder by mutableStateOf(sp.getString("tabOrder", DEFAULT_ORDER) ?: DEFAULT_ORDER)
        private set

    /** 被隐藏的 tab 下标，逗号分隔 */
    var tabHidden by mutableStateOf(sp.getString("tabHidden", "") ?: "")
        private set

    /** 链接在 App 内预览？关掉就直接调浏览器（要登录、要支付的站点更适合） */
    var linkInApp by mutableStateOf(sp.getBoolean("linkInApp", true))
        private set

    /** 启动时自动问一句有没有新版 */
    var autoCheckUpdate by mutableStateOf(sp.getBoolean("autoCheckUpdate", true))
        private set

    /** 只在 Wi-Fi 下下安装包（一个包 1.2 MB，流量党会骂） */
    var updateWifiOnly by mutableStateOf(sp.getBoolean("updateWifiOnly", true))
        private set

    /** 导航页一进来就全部折起来，只看到分类目录 */
    var navCollapsed by mutableStateOf(sp.getBoolean("navCollapsed", false))
        private set

    /** 记住每篇文章读到哪里，下次点进去接着读 */
    var rememberPos by mutableStateOf(sp.getBoolean("rememberPos", true))
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
        startTab = v.coerceIn(0, LAST_TAB)
        sp.edit().putInt("startTab", startTab).apply()
    }

    fun updateDockIcons(v: Boolean) {
        dockIcons = v
        sp.edit().putBoolean("dockIcons", v).apply()
    }

    fun updateLinkInApp(v: Boolean) {
        linkInApp = v
        sp.edit().putBoolean("linkInApp", v).apply()
    }

    fun updateAutoCheckUpdate(v: Boolean) {
        autoCheckUpdate = v
        sp.edit().putBoolean("autoCheckUpdate", v).apply()
    }

    fun updateUpdateWifiOnly(v: Boolean) {
        updateWifiOnly = v
        sp.edit().putBoolean("updateWifiOnly", v).apply()
    }

    fun updateNavCollapsed(v: Boolean) {
        navCollapsed = v
        sp.edit().putBoolean("navCollapsed", v).apply()
    }

    fun updateRememberPos(v: Boolean) {
        rememberPos = v
        sp.edit().putBoolean("rememberPos", v).apply()
    }

    /* ---------------- 底栏排序 / 显隐 ---------------- */

    /** 全部 tab 的顺序（包含被隐藏的） */
    val tabsInOrder: List<Int>
        get() {
            val parsed = tabOrder.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0..LAST_TAB }
                .distinct()
                .toMutableList()
            /* 以后加了新 tab 而老设置里没记，补在末尾，不至于凭空消失。
               v1.8 加「收藏」时就靠这一句：老用户存的 0,1,2,3,4 自动补上 5 */
            for (i in 0..LAST_TAB) if (i !in parsed) parsed.add(i)
            return parsed
        }

    val hiddenSet: Set<Int>
        get() = tabHidden.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..LAST_TAB }
            .toSet()

    /** 真正显示在底栏上的 tab（顺序已排好、隐藏的已剔除，至少留一个） */
    val visibleTabs: List<Int>
        get() {
            val list = tabsInOrder.filter { it !in hiddenSet }
            return list.ifEmpty { listOf(tabsInOrder.first()) }
        }

    /** 上下移动一位（dir = -1 上移 / +1 下移） */
    fun moveTab(id: Int, dir: Int) {
        val list = tabsInOrder.toMutableList()
        val from = list.indexOf(id)
        val to = from + dir
        if (from < 0 || to < 0 || to >= list.size) return
        val tmp = list[from]
        list[from] = list[to]
        list[to] = tmp
        tabOrder = list.joinToString(",")
        sp.edit().putString("tabOrder", tabOrder).apply()
    }

    /** 显示 / 隐藏某个 tab。只剩一个时不许再隐藏，否则底栏就空了 */
    fun setTabHidden(id: Int, hidden: Boolean) {
        val set = hiddenSet.toMutableSet()
        if (hidden) {
            if (visibleTabs.size <= 1) return
            set.add(id)
        } else {
            set.remove(id)
        }
        tabHidden = set.joinToString(",")
        sp.edit().putString("tabHidden", tabHidden).apply()
    }

    /** 当前能不能隐藏它（设置页禁用开关用） */
    fun canHide(id: Int): Boolean = id in hiddenSet || visibleTabs.size > 1

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
        /** 最大 tab 下标。加新页签时只改这里 + MainActivity 的 TABS */
        const val LAST_TAB = 5

        private const val DEFAULT_ORDER = "0,1,2,3,4,5"

        /* 全 App 一份：界面要读写同一份状态，ViewModel 也得能读到「自动更新」这个开关 */
        @Volatile
        private var inst: Prefs? = null

        fun of(ctx: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext).also { inst = it }
        }
    }
}
