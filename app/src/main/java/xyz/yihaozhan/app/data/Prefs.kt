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

    /**
     * 自定义数据源（自己的镜像 / 国内虚拟主机）。
     *
     * 空串 = 只用 App 内置那几个源（GitHub Pages → raw → jsDelivr）。
     * 填了就让**它排第一**，成功就不再问 GitHub —— 国内直连 Pages 常常要等十几秒。
     * 存的是完整文件地址；用户只填域名或目录时由 [updateDataMirror] 补成 `…/api/app.json`。
     */
    var dataMirror by mutableStateOf(sp.getString("dataMirror", "").orEmpty())
        private set

    fun updateDataMirror(v: String) {
        var s = v.trim()
        if (s.isNotEmpty()) {
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            s = s.trimEnd('/')
            /* 只填了域名/目录的，补成文件路径；已经指到 .json 的原样用 */
            if (!s.endsWith(".json")) s = "$s/api/app.json"
        }
        dataMirror = s
        sp.edit().putString("dataMirror", s).apply()
    }

    /**
     * 默认先问哪个源：`false` = GitHub 内置（正式源），`true` = 先问我自己的镜像。
     *
     * 为什么不直接「填了地址就用」：地址得留着 —— 平时走 GitHub、偶尔切镜像（或者反过来）都是常见用法，
     * 一填就被迫生效、一关就得重填，太别扭。所以地址和「用不用」分开存。
     * 老用户（存过 dataMirror 的）默认按「用镜像」，跟 2.7.1 的行为保持一致，不因为升级变哑。
     */
    var preferMirror by mutableStateOf(
        sp.getBoolean("preferMirror", sp.getString("dataMirror", "").orEmpty().isNotEmpty())
    )
        private set

    fun updatePreferMirror(on: Boolean) {
        preferMirror = on
        sp.edit().putBoolean("preferMirror", on).apply()
    }

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
    /* 2.7.8：默认改成「进来先折叠」。分类换成三列网格之后，折叠态本身就是一张目录 ——
       五十个分类一屏能扫完；全展开反而会变成「格子 → 标签云 → 格子」交替，看不清谁是谁。
       只影响没点过这个开关的人（点过的话盘里已经存了自己的选择，不动它）。 */
    var navCollapsed by mutableStateOf(sp.getBoolean("navCollapsed", true))
        private set

    /** 记住每篇文章读到哪里，下次点进去接着读 */
    var rememberPos by mutableStateOf(sp.getBoolean("rememberPos", true))
        private set

    /**
     * 阅读页要不要留着底部 Tab 栏？
     *
     * 默认关 —— 2.6.8 及以前阅读页一律整屏（底栏让位给正文）。
     * 2.6.9 起做成开关：有人喜欢整屏沉浸，有人更想随手切页签。
     */
    var dockInReader by mutableStateOf(sp.getBoolean("dockInReader", false))
        private set

    /* ---------------- 阅读：行距 / 字体（跟站点 read 那份配置对齐） ---------------- */

    /** 行距档位：0 紧凑 / 1 标准 / 2 宽松（对应倍率 1.7 / 1.95 / 2.2，跟网页端同一套） */
    var readLine by mutableStateOf(sp.getInt("readLine", 1).coerceIn(0, 2))
        private set

    /** 正文字体：sans 无衬线 / serif 衬线（网页端 read.font 就这两个值） */
    var readFont by mutableStateOf(sp.getString("readFont", "sans") ?: "sans")
        private set

    /* ---------------- 首页板块 ---------------- */

    /**
     * 首页要显示哪些块、按什么顺序 —— 逗号分隔的 key。
     * 空 = 从来没调过，用 [HOME_BLOCKS] 的默认顺序（老用户升级上来就是原样）。
     */
    var homeBlocks by mutableStateOf(sp.getString("homeBlocks", "") ?: "")
        private set

    /** 卡片密度：0 紧凑（一屏多几行）/ 1 标准 */
    var cardDensity by mutableStateOf(sp.getInt("cardDensity", 1).coerceIn(0, 1))
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

    fun updateDockInReader(v: Boolean) {
        dockInReader = v
        sp.edit().putBoolean("dockInReader", v).apply()
    }

    fun updateReadLine(v: Int) {
        readLine = v.coerceIn(0, 2)
        sp.edit().putInt("readLine", readLine).apply()
    }

    fun updateReadFont(v: String) {
        readFont = if (v == "serif") "serif" else "sans"
        sp.edit().putString("readFont", readFont).apply()
    }

    fun updateCardDensity(v: Int) {
        cardDensity = v.coerceIn(0, 1)
        sp.edit().putInt("cardDensity", cardDensity).apply()
    }

    /** 首页板块：改显示 / 改顺序都写这一份 */
    fun updateHomeBlocks(v: List<String>) {
        homeBlocks = v.joinToString(",")
        sp.edit().putString("homeBlocks", homeBlocks).apply()
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

    /** 一次性写回整份顺序。拖动结束时用：拖动过程中只在内存里挪，松手才落盘 */
    fun setTabOrder(list: List<Int>) {
        tabOrder = list.joinToString(",")
        sp.edit().putString("tabOrder", tabOrder).apply()
    }

    /** 显示 / 隐藏某个 tab。只剩一个时不许再隐藏，否则底栏就空了 */
    fun setTabHidden(id: Int, hidden: Boolean) {
        /* 「设置」永远不许藏：它是唯一能把这些开关改回来的地方，
           一旦藏了，除了重装没有第二条路（v2.4 修的就是这个死胡同） */
        if (hidden && id == SETTINGS_TAB) return
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

    /** 当前能不能隐藏它（设置页禁用开关用）。设置永远是常驻项 */
    fun canHide(id: Int): Boolean =
        id != SETTINGS_TAB && (id in hiddenSet || visibleTabs.size > 1)

    init {
        /* v2.3 及以前允许把「设置」也藏起来 —— 藏完就再也进不去设置。
           这里一次性把 4 从隐藏集合里摘掉，老配置自动修好，不用重装。 */
        if (SETTINGS_TAB in hiddenSet) {
            tabHidden = (hiddenSet - SETTINGS_TAB).joinToString(",")
            sp.edit().putString("tabHidden", tabHidden).apply()
        }
    }

    /** 字号档位换算成倍率，交给 Markdown 渲染用 */
    val fontScale: Float
        get() = when (fontSize) {
            0 -> 0.92f
            2 -> 1.14f
            else -> 1f
        }

    /** 行距倍率：交给文章正文排版用 */
    val lineHeightFactor: Float
        get() = when (readLine) {
            0 -> 1.7f
            2 -> 2.2f
            else -> 1.95f
        }

    val lineName: String get() = when (readLine) { 0 -> "紧凑"; 2 -> "宽松"; else -> "标准" }
    val fontName: String get() = if (readFont == "serif") "衬线" else "无衬线"
    val densityName: String get() = if (cardDensity == 0) "紧凑" else "标准"

    /**
     * 真正要画的首页板块：**只包含开着的**，顺序照用户拖过的来。
     *
     * 存的是「开着的清单」，空串表示从来没动过（= 全部打开，用默认顺序）。
     * 早先写成「缺的补到末尾」是错的：那样关掉的板块会被当成缺项补回来，等于关不掉。
     */
    val homeBlockList: List<String>
        get() {
            val keys = HOME_BLOCKS.map { it.first }
            val saved = homeBlocks.split(',').map { it.trim() }.filter { it in keys }.distinct()
            return if (saved.isEmpty()) keys else saved
        }

    fun toggleHomeBlock(key: String) {
        val cur = homeBlockList
        /* 关掉就是移出列表；再打开接到末尾，用户可以在下面那个「顺序」里往上挪 */
        updateHomeBlocks(if (cur.contains(key)) cur.filter { it != key } else cur + key)
    }

    fun moveHomeBlock(key: String, dir: Int) {
        val list = homeBlockList.toMutableList()
        val from = list.indexOf(key)
        val to = from + dir
        if (from < 0 || to < 0 || to >= list.size) return
        val tmp = list[from]; list[from] = list[to]; list[to] = tmp
        updateHomeBlocks(list)
    }

    /** 设置页显示的档位名 */
    val fontSizeName: String
        get() = when (fontSize) {
            0 -> "小"
            2 -> "大"
            else -> "标准"
        }

    /* ------------------------------ 备份用 ------------------------------ */

    /**
     * 把整份设置原样吐出来给备份用。
     * 保留类型很重要 —— 恢复时若把 Int 写成 String，`sp.getInt` 会直接抛
     * ClassCastException，一开机就崩。
     */
    fun exportMap(): Map<String, Any?> = sp.all

    /**
     * 恢复设置。只认 Boolean / Int / String，其余（集合之类）跳过。
     *
     * 注意：这里写进 SP 之后，内存里那些 mutableStateOf 不会自动跟着变 ——
     * 设置页得重开 App 才显示新值。调用方要提示用户这一点。
     */
    fun importMap(m: Map<String, Any?>) {
        if (m.isEmpty()) return
        val e = sp.edit()
        m.forEach { (k, v) ->
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Number -> e.putInt(k, v.toInt())
                is String -> e.putString(k, v)
            }
        }
        e.apply()
    }

    companion object {
        /** 最大 tab 下标。加新页签时只改这里 + MainActivity 的 TABS。
            2.6 把「足迹」从页签挪成内置页，最大值回到 5 ——
            老设置里存的 6 会被 `filter { it in 0..LAST_TAB }` 自然丢掉，不用另写迁移 */
        const val LAST_TAB = 5

        /** 「设置」页签的下标 —— 常驻，不许隐藏 */
        const val SETTINGS_TAB = 4

        private const val DEFAULT_ORDER = "0,1,2,3,4,5"

        /** 首页板块：key to 名字。顺序就是默认顺序；设置页「首页板块」直接照这个画 */
        val HOME_BLOCKS: List<Pair<String, String>> = listOf(
            "hero" to "大标题与统计",
            "quick" to "两个快捷入口",
            "today" to "今日三站",
            "featured" to "今日精选",
            "recent" to "最近分享",
            "latest" to "最新文章",
            "about" to "关于这一栈",
            "sponsor" to "赞助",
            "web" to "在浏览器里打开"
        )

        /* 全 App 一份：界面要读写同一份状态，ViewModel 也得能读到「自动更新」这个开关 */
        @Volatile
        private var inst: Prefs? = null

        fun of(ctx: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext).also { inst = it }
        }
    }
}
