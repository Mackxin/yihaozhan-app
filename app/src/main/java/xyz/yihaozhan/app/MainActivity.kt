package xyz.yihaozhan.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import xyz.yihaozhan.app.data.Post
import xyz.yihaozhan.app.data.Local
import xyz.yihaozhan.app.data.MusicPlay
import xyz.yihaozhan.app.data.Notes
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.data.UpdateRepo
import xyz.yihaozhan.app.ui.AboutScreen
import xyz.yihaozhan.app.ui.AuroraBackground
import xyz.yihaozhan.app.ui.ChangelogScreen
import xyz.yihaozhan.app.ui.CheckScreen
import xyz.yihaozhan.app.ui.LocalOpenTag
import xyz.yihaozhan.app.ui.TagScreen
import xyz.yihaozhan.app.ui.FeedScreen
import xyz.yihaozhan.app.ui.ExtractScreen
import xyz.yihaozhan.app.ui.FootprintScreen
import xyz.yihaozhan.app.ui.HelpScreen
import xyz.yihaozhan.app.ui.HomeScreen
import xyz.yihaozhan.app.ui.IdeaScreen
import xyz.yihaozhan.app.ui.LinksScreen
import xyz.yihaozhan.app.ui.LocalXyPal
import xyz.yihaozhan.app.ui.MusicScreen
import xyz.yihaozhan.app.ui.NoteScreen
import xyz.yihaozhan.app.ui.PickItem
import xyz.yihaozhan.app.ui.PickerSheet
import xyz.yihaozhan.app.ui.PostDetailScreen
import xyz.yihaozhan.app.ui.PullRefresh
import xyz.yihaozhan.app.ui.SearchOverlay
import xyz.yihaozhan.app.ui.TopBar
import xyz.yihaozhan.app.ui.TopFilter
import xyz.yihaozhan.app.ui.UpdateDialog
import xyz.yihaozhan.app.ui.PostsScreen
import xyz.yihaozhan.app.ui.SavedScreen
import xyz.yihaozhan.app.ui.SettingsScreen
import xyz.yihaozhan.app.ui.WebScreen
import xyz.yihaozhan.app.ui.YiTheme
import xyz.yihaozhan.app.ui.toggleEnd
import xyz.yihaozhan.app.ui.rememberOpener
import xyz.yihaozhan.app.ui.enterRise
import xyz.yihaozhan.app.ui.orderedPosts

/**
 * 从 Activity 外面递进来的一件事：进来要直接打开哪一页。
 *
 * 为什么需要这一层：分享（`ACTION_SEND`）和长按图标的快捷方式都只到得了 Activity 的
 * intent，而「打开随记页 / 搜索 / 稍后读」这些状态活在 Compose 里面，两边够不着。
 * 于是 Activity 把「要做的事」写进 [MainActivity.hook]，`AppRoot` 里的
 * `LaunchedEffect` 取走一次再置空 —— 不能直接改 Compose 状态，也不能用标志位不清。
 */
data class BootAction(val page: String, val url: String = "")

class MainActivity : ComponentActivity() {

    /** 一次性信使：`AppRoot` 消费完就置空，所以旋转屏幕/重组不会重复跳页 */
    private val hook = mutableStateOf<BootAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        /* 冷启动也要看一眼：可能是「分享到壹号栈」或者快捷方式把 App 拉起来的 */
        takeIntent(intent)
        val prefs = Prefs.of(this)
        setContent {
            /* vm 提到主题外面：站点后台「主题」页选的那套外观得在铺底色之前就知道，
               不然会先画一帧内置配色、再跳成站点配色（闪一下） */
            val vm: MainVm = viewModel()
            /* 主题跟着设置走：跟随系统 / 浅色 / 深色 */
            val dark = when (prefs.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            YiTheme(dark = dark, cfg = vm.ui.data?.theme) {
                /* 极光背景铺在最底下，整页（含状态栏、底栏后面）都是那三团光 */
                AuroraBackground {
                    AppRoot(vm, prefs, dark, hook)
                }
            }
        }
    }

    /**
     * App 已经在后台时，分享进来/点快捷方式走这里（Manifest 里设了 singleTask）。
     * 以前没重写它 —— 那样系统会另起一个实例，收下东西的是新的一份，很不像话。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeIntent(intent)
    }

    /**
     * 认两种 intent：
     *  1. `ACTION_SEND` —— 别的 App「分享到」的一段文字（多半带个网址），存进「待整理」；
     *  2. 带 `page` extra 的 —— 长按图标那三个快捷方式，直接跳到对应页面。
     *
     * 处理完就把标记抹掉（action 置空 / 删 extra）：进程被系统回收后，会拿同一个 Intent
     * 重建 Activity，不抹就会把同一条分享重复收一遍。
     */
    private fun takeIntent(i: Intent?) {
        if (i == null) return

        if (i.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            i.action = null
            setIntent(i)
            if (text.isNotEmpty() && Local.of(this).addInbox(text)) {
                Toast.makeText(this, "已收进「待整理」", Toast.LENGTH_SHORT).show()
                hook.value = BootAction("idea")
            }
            return
        }

        val page = i.getStringExtra("page")
        val url = i.getStringExtra("open_url")
        if (page == null && url == null) return
        i.removeExtra("page")
        i.removeExtra("open_url")
        setIntent(i)
        /* 小组件点某一行带的是网址：直接进 App 内预览那一页，不甩给浏览器 */
        hook.value = if (!url.isNullOrBlank()) BootAction("web", url) else BootAction(page.orEmpty())
    }
}

/**
 * 这条链接是不是站点那个「本地音乐」页。
 *
 * `app.json` 的导航里它是一条完整网址（`https://mackxin.github.io/xingyu/#/music`），
 * 后台自定义入口里也可能写成相对的 `#/music` —— 两种都算，统一认 `#/music` 这一段。
 * 认出来就开内置页，不再塞进 WebView。
 */
private fun isMusicUrl(u: String): Boolean = u.contains("#/music")

/**
 * 这条链接是不是站点那个「随手记」页。
 *
 * 跟 [isMusicUrl] 一个道理：导航里是完整网址（`…/xingyu/#/note`），
 * 后台自定义入口里也可能只写 `#/note`。认出来就开原生页，别塞 WebView。
 */
private fun isNoteUrl(u: String): Boolean = u.contains("#/note")

private data class Tab(val id: Int, val label: String, val icon: ImageVector)/* id 就是「底栏排序 / 显隐」设置里存的那个数字，别改 */
private val TABS = listOf(
    Tab(0, "首页", Icons.Rounded.Home),
    Tab(1, "分享", Icons.Rounded.Bolt),
    Tab(2, "文章", Icons.AutoMirrored.Rounded.MenuBook),
    Tab(3, "导航", Icons.Rounded.GridView),
    Tab(4, "设置", Icons.Rounded.Settings),
    /* 1.8 新加的一页：收藏 + 稍后读。id 5，老设置里没有会自动补到末尾。
       2.5 曾把「足迹」塞成 id 6 的底栏页签，2.6 又挪回顶栏自定义入口了 ——
       它是内置页（跟灵感随记、关于一样整屏推开），不是页签。id 6 空着不复用。 */
    Tab(5, "收藏", Icons.Rounded.StarBorder)
)

fun tabLabel(id: Int): String = TABS.firstOrNull { it.id == id }?.label ?: "?"

/* 底部那个筛选面板打开的是哪一份清单 */
private const val PICK_NONE = 0
private const val PICK_CAT = 1
private const val PICK_FEED_TAG = 2
private const val PICK_POST_TAG = 3

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppRoot(vm: MainVm, prefs: Prefs, dark: Boolean, hook: MutableState<BootAction?>) {
    val context = LocalContext.current
    /* 收藏 / 稍后读 / 最近打开 / 读到哪：本机数据，一份单例 */
    val local = remember { Local.of(context) }
    /* 播放器也在这儿挂一次：万一从通知栏回来时页面还没搭起来，服务那边也有上下文 */
    LaunchedEffect(Unit) { MusicPlay.attach(context) }
    val ui = vm.ui
    val data = ui.data
    /* 只在设置里改过顺序/显隐时才重算：visibleTabs 是 getter，
       不 remember 的话每次重组都新造一个 List，底栏和无障碍树跟着白刷一遍。 */
    val visible = remember(prefs.tabOrder, prefs.tabHidden) { prefs.visibleTabs }
    var openPost by remember { mutableStateOf<Post?>(null) }
    var webUrl by rememberSaveable { mutableStateOf<String?>(null) }
    /* 灵感随记：原生整屏（不再拿 WebView 打开站点那页） */
    var ideaOpen by rememberSaveable { mutableStateOf(false) }
    /* 关于这一页：站点的 #/about 现在有原生版了，正文从 app.json 的 about 段来 */
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    /* 足迹（站点 #/insight）：内置页，从顶栏自定义入口那条「👣 足迹」进。
       2.6 起不再占底栏页签 —— 底栏只放四个主页面 + 收藏 + 设置。 */
    var footOpen by rememberSaveable { mutableStateOf(false) }
    /* 设置里那两份说明书（2.6.5 加的）：都是整屏内置页，跟灵感随记/关于/足迹一个待遇 ——
       进去之后底栏还在、返回键只退这一页。 */
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var logOpen by rememberSaveable { mutableStateOf(false) }
    /* 标签页：点任何一个 #标签 打开它，空串 = 没开 */
    var tagOpen by rememberSaveable { mutableStateOf("") }
    /* 摘录本（2.6.7）：阅读页长按存下来的段落，从随记页工具条或设置里进 */
    var exOpen by rememberSaveable { mutableStateOf(false) }
    /* 站点巡检（2.6.7）：设置里进去抽查站点通不通 */
    var checkOpen by rememberSaveable { mutableStateOf(false) }
    /* 本地音乐（2.6.9）：从导航页「万物归一 → 本地音乐」进来。
       以前这条链接指向网页版 #/music（WebView 里跑），现在换成本内置页。 */
    var musicOpen by rememberSaveable { mutableStateOf(false) }
    /* 随手记（2.7.7）：以前也是拿 WebView 打开站点 #/note，现在原生了。
       数据在 Notes 里（字段跟网页版同形），第一次进来会把网页版攒的搬过来。 */
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    val notes = remember { Notes.of(context) }
    LaunchedEffect(Unit) { notes.migrateFromWeb(context) }

    /*
     * 回到前台时对一次内容。
     *
     * 为什么非加不可：ViewModel 活着的时候 boot() 只跑一次 —— 早上打开过、晚上切回来，
     * 看到的还是早上那份数据（除非自己往下拉）。用户对「App 不更新」的抱怨多半来自这里。
     * 节流放在 vm 里（60 秒），锁屏解锁、切出去回个消息不会每次发请求。
     */
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.onForeground()
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    /* 手动刷新（下拉）之后的一句话反馈：转完两秒什么都没变，会被当成没反应。
       2.7.8 起不再用 Toast —— 它弹在屏幕最下面，正好被底栏压住半截；改成顶部滑下来的小胶囊。 */
    var tip by remember { mutableStateOf("") }
    /* 下拉进度（0~1），顶栏那颗小圈靠它画弧 —— 页面里不再画指示器了 */
    var pullProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ui.note) {
        ui.note?.let { n ->
            tip = n
            vm.clearNote()
        }
    }
    /* 收回倒计时单独一条。
       ⚠️ 千万别把 delay 写进上面那条：`clearNote()` 会把 note 变回 null，
       `LaunchedEffect(ui.note)` 立刻被取消重启，倒计时跟着一起取消 ——
       胶囊就再也收不回去了（2.7.8 自己踩过这个坑）。 */
    LaunchedEffect(tip) {
        if (tip.isNotBlank()) {
            kotlinx.coroutines.delay(1800)
            tip = ""
        }
    }

    /* 每个页签各有一条滚动状态，统统提到这儿持有。
       为什么：顶栏那颗「壹号栈」要能在**任意页签**上做「到底 / 回顶」（小宝 2.6.4 的要求：
       「不再返回首页，只要点击就是滑动到页面底部和返回顶部」），所以它必须在点击那一刻
       拿到「当前这一页」的 state —— 页面自己 remember 的话外层根本摸不到。
       顺带好处：翻页回来滚动位置还在（state 是外层的，不随页面组合销毁）。 */
    val homeList = rememberLazyListState()
    val feedList = rememberLazyListState()
    val postList = rememberLazyListState()
    val navList = rememberLazyListState()
    val savedList = rememberLazyListState()
    val setList = rememberLazyListState()
    val listForTab = remember(homeList, feedList, postList, navList, savedList, setList) {
        mapOf(0 to homeList, 1 to feedList, 2 to postList, 3 to navList, 4 to setList, 5 to savedList)
    }

    /* 顶栏那套：搜索词是全局的（导航/分享/文章都拿它筛），三个筛选各自独立 */
    var query by rememberSaveable { mutableStateOf("") }
    var feedTag by rememberSaveable { mutableStateOf("") }
    var postTag by rememberSaveable { mutableStateOf("") }
    var navCat by rememberSaveable { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(PICK_NONE) }
    /* 顶栏自定义入口点 #/later 时，要「进去就停在稍后读那一段」。
       一次性 hint：SavedScreen 用掉就回调清掉，不然以后每次重组都会被拽回去 */
    var savedSegHint by remember { mutableStateOf<Int?>(null) }

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    /* 站外链接：默认 App 内预览；设置里关掉「链接在 App 内打开」就直接丢给浏览器 */
    val external = rememberOpener()
    /* 「这条链接在 App 里打开时要不要留底栏」——后台可以逐条关（默认开）。
       做成 url -> dock 的查找表，open() 里就近查一次就行，
       不用把这个开关一层层穿到导航页、首页卡片那些调用点上。 */
    val dockByUrl = remember(data) {
        val m = HashMap<String, Boolean>()
        data?.cats?.forEach { c -> c.links.forEach { l -> m[l.url] = l.dock } }
        m
    }
    var webDock by rememberSaveable { mutableStateOf(true) }
    val open: (String) -> Unit = { u ->
        if (u.isNotBlank()) {
            /* 导航页那条「本地音乐」在 app.json 里是完整网址（…/#/music）。
               认出来就开内置页，别再塞进 WebView —— 那正是「怎么不是内置的」那个问题。 */
            /* 导航页「万物归一」里那两条 App 专属入口（摘录本 / 站点巡检）用 app:// 前缀：
               它们不是网址、站点上也没有对应页面，只能在这儿认领成内置页。
               故意用 app:// 而不是 #/xxx —— 万一哪天被复制出去，也不会把站点打开一个 404。 */
            if (u.startsWith("app://")) {
                when (u.removePrefix("app://").trimEnd('/')) {
                    "extract" -> exOpen = true
                    "check" -> checkOpen = true
                    "note" -> noteOpen = true
                }
            } else if (isMusicUrl(u)) {
                musicOpen = true
            } else if (isNoteUrl(u)) {
                noteOpen = true
            } else if (prefs.linkInApp) {
                /* 后台把这条链接的底栏关掉了就跟着关；查不到（比如首页推荐位、巡检结果里的
                   裸网址）一律按默认开着 */
                webDock = dockByUrl[u] ?: dockByUrl[u.trimEnd('/')] ?: true
                webUrl = u
            } else {
                external(u)
            }
        }
    }

    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(
        initialPage = visible.indexOf(prefs.startTab).coerceAtLeast(0),
        pageCount = { visible.size }
    )
    val curId = visible.getOrElse(pager.currentPage) { visible.first() }
    /* 连续位置（当前页 + 拖到一半的偏移）：底栏那颗高亮胶囊靠它跟着手指平滑走。
       翻页翻过去的瞬间 currentPage 加一、offsetFraction 正好变成 -0.5，两边接得上，不会跳。 */
    val dockPos = pager.currentPage + pager.currentPageOffsetFraction

    /* 底栏被改过（隐藏/换序）之后页数会变，把位置夹回范围内 */
    LaunchedEffect(prefs.tabOrder, prefs.tabHidden) {
        if (pager.currentPage > visible.lastIndex) pager.scrollToPage(visible.lastIndex)
    }

    /* 换页就把输入法收掉：搜索框已经不在了，键盘还杵着挡半屏最烦 */
    LaunchedEffect(curId) {
        focus.clearFocus()
        keyboard?.hide()
    }

    /* 隔了一页以上就瞬移，不 animate —— 动画会把中间那几页拖过去一遍，
       肉眼看就是「闪一下」；相邻页才用滑的，才自然。 */
    val goPage: (Int) -> Unit = { i ->
        if (i in visible.indices && i != pager.currentPage) {
            scope.launch {
                if (kotlin.math.abs(i - pager.currentPage) > 1) pager.scrollToPage(i)
                else pager.animateScrollToPage(i)
            }
        }
    }

    /* keepQuery：从搜索结果「看全部 N 个」跳过去时要留着关键词（那一页正好用它筛），
       而直接点底栏就是重新开始，关键词清掉。 */
    /* 目标页被藏在设置里时不能「点了毫无反应」（顶栏那颗 🕐 稍后读最容易撞上）：
       先把它放回底栏，等重组把页数改好，再由下面那个 effect 翻过去 */
    var pendingTab by remember { mutableStateOf<Int?>(null) }
    val jump: (Int, Boolean) -> Unit = { id, keepQuery ->
        openPost = null
        webUrl = null
        ideaOpen = false
        aboutOpen = false
        footOpen = false
        helpOpen = false
        logOpen = false
        tagOpen = ""
        exOpen = false
        checkOpen = false
        musicOpen = false
        if (!keepQuery) query = ""
        picker = PICK_NONE
        val i = visible.indexOf(id)
        if (i >= 0) {
            goPage(i)
        } else {
            prefs.setTabHidden(id, false)
            pendingTab = id
            Toast.makeText(
                context, "已把「" + tabLabel(id) + "」放回底栏", Toast.LENGTH_SHORT
            ).show()
        }
    }

    /* 底栏放回来之后再翻页：pager 的页数读的是 visible，
       直接 scrollToPage 一个新区间外的下标会被夹回原位 */
    LaunchedEffect(pendingTab, visible) {
        val id = pendingTab ?: return@LaunchedEffect
        val i = visible.indexOf(id)
        if (i >= 0) {
            pendingTab = null
            goPage(i)
        }
    }

    val goTab: (Int) -> Unit = { id -> jump(id, false) }

    /* Activity 递进来的一件事（外部分享 / 长按快捷方式）：取走一次就置空。
       置空这一步不能省 —— 留着一个非空值的话，以后每次重组都会再跳一遍那一页。 */
    val boot by hook
    LaunchedEffect(boot) {
        val b = boot ?: return@LaunchedEffect
        hook.value = null
        when (b.page) {
            /* 分享进来的东西存在「待整理」里，人先落到随记页把它收拾掉；
               顺手把阅读页/网页预览关掉，不然底栏按 openPost/webUrl 判显隐会一起消失 */
            "idea" -> {
                openPost = null
                webUrl = null
                ideaOpen = true
            }
            "search" -> {
                openPost = null
                webUrl = null
                searchOpen = true
            }
            "later" -> {
                savedSegHint = 1
                jump(5, false)
            }
            "post" -> jump(2, false)
            "nav" -> jump(3, false)
            "saved" -> {
                savedSegHint = 0
                jump(5, false)
            }
            /* 桌面小组件点了某一站：进 App 内的网页预览，不跳去浏览器 */
            "web" -> {
                openPost = null
                webUrl = b.url
            }
            "home" -> jump(0, false)
            /* 通知栏点回来的（Intent extra page=music）：直接落到本地音乐 */
            "music" -> {
                openPost = null
                webUrl = null
                musicOpen = true
            }
        }
    }

    /* 返回键：先退网页预览，再退阅读页 / 内置页，退完才退出 App。
       2.6 修：灵感随记、足迹、关于这三个内置页以前没进这个判断，
       按返回直接把 App 干掉（用户报的就是灵感随记那个）。 */
    BackHandler(
        enabled = webUrl != null || openPost != null || ideaOpen || aboutOpen || footOpen ||
            helpOpen || logOpen || tagOpen.isNotBlank() || exOpen || checkOpen || musicOpen ||
            noteOpen
    ) {
        when {
            webUrl != null -> webUrl = null
            ideaOpen -> ideaOpen = false
            aboutOpen -> aboutOpen = false
            footOpen -> footOpen = false
            helpOpen -> helpOpen = false
            logOpen -> logOpen = false
            tagOpen.isNotBlank() -> tagOpen = ""
            exOpen -> exOpen = false
            checkOpen -> checkOpen = false
            /* 音乐页退出去**不**停播 —— 它是个控制面板，不是播放开关 */
            musicOpen -> musicOpen = false
            noteOpen -> noteOpen = false
            else -> openPost = null
        }
    }

    /* 顶栏那颗胶囊：导航页换分类、分享/文章页换标签 */
    val catName = remember(navCat, data) {
        data?.cats?.firstOrNull { it.id == navCat }?.name.orEmpty()
    }
    val filter = when (curId) {
        3 -> TopFilter("🗂", catName.ifBlank { "全部分类" }) { picker = PICK_CAT }
        1 -> TopFilter("🏷", feedTag.ifBlank { "全部标签" }) { picker = PICK_FEED_TAG }
        2 -> TopFilter("🏷", postTag.ifBlank { "全部标签" }) { picker = PICK_POST_TAG }
        else -> null
    }

    /* 标签胶囊散在分享卡 / 文章卡 / 阅读页三处，中间隔着好几层，
       与其一路传回调，不如在这儿提供一次「点标签开哪一页」。*/
    /* 标签胶囊散在分享卡 / 文章卡 / 阅读页三处，中间隔着好几层，
       与其一路传回调，不如在这儿提供一次「点标签开哪一页」。 */
    /* 整屏浮层（搜索结果 / 分类标签面板）要盖住顶栏和底栏，
       所以跟 Scaffold 平级放在这个 Box 里，而不是塞进 Scaffold 的内容区。
       以前它们是 Dialog（另开一个窗口）—— 那个窗口只铺到状态栏下沿，
       浅色主题下会在状态栏那条露出一道压暗的灰带，状态栏图标也会变成白的看不清。 */
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalOpenTag provides { t -> tagOpen = t }) {
            Scaffold(
                /* 透明：底色交给下面那层极光，不然会把光晕盖住 */
                containerColor = Color.Transparent,
                bottomBar = {
                    /* 底栏在「站外网页预览」时让位 —— 但后台给那条链接开了「显示底栏」就留着
                       （2.7.6 新增：逐条可控，默认开着）。
                       阅读页看设置：`dockInReader` 打开就留着（2.6.9 新增的开关），
                       关着就是原样整屏看正文。
                       内置页（灵感随记 / 关于 / 足迹 / 手册 / 版本更新 / 本地音乐）一律留着底栏 ——
                       用户明确要求：「之后要是还有内置页面都要显示 tab 栏出来」。 */
                    if ((webUrl == null || webDock) && (openPost == null || prefs.dockInReader)) {
                        BottomDock(
                            position = dockPos,
                            current = curId,
                            tabs = visible,
                            showIcons = prefs.dockIcons
                        ) { id ->
                            val i = visible.indexOf(id)
                            if (i >= 0) jump(id, false)
                        }
                    }
                }
            ) { pad ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(pad)
                        /* 点空白处就收键盘：这里能收到 tap 只说明「没人认领这次点击」——
                           输入框、卡片这些自己会消费按下事件，所以不会误伤 */
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focus.clearFocus()
                                keyboard?.hide()
                            })
                        }
                ) {
                    when {
                        data == null && ui.booting -> Booting()
                        data == null -> Failed(ui.error ?: "加载失败") { vm.refresh() }
                        webUrl != null -> Box(Modifier.fillMaxSize().enterRise()) {
                            WebScreen(webUrl!!, { webUrl = null }, external, dark)
                        }
                        ideaOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            IdeaScreen(
                                local, open,
                                onExtracts = { exOpen = true }
                            ) { ideaOpen = false }
                        }
                        aboutOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            AboutScreen(data, { aboutOpen = false }, open)
                        }
                        footOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            FootprintScreen(data, local, open) { footOpen = false }
                        }
                        helpOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            HelpScreen(data) { helpOpen = false }
                        }
                        logOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            ChangelogScreen(data) { logOpen = false }
                        }
                        tagOpen.isNotBlank() -> Box(Modifier.fillMaxSize().enterRise()) {
                            TagScreen(
                                data = data,
                                tag = tagOpen,
                                onBack = { tagOpen = "" },
                                onOpenUrl = { u -> open(u) },
                                onOpenPost = { p -> openPost = p }
                            )
                        }
                        exOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            ExtractScreen(
                                local = local,
                                posts = data.posts,
                                onOpenPost = { p ->
                                    exOpen = false
                                    openPost = p
                                },
                                onBack = { exOpen = false }
                            )
                        }
                        checkOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            CheckScreen(
                                data = data,
                                local = local,
                                onOpen = { u -> open(u) },
                                onBack = { checkOpen = false }
                            )
                        }
                        musicOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            MusicScreen { musicOpen = false }
                        }
                        noteOpen -> Box(Modifier.fillMaxSize().enterRise()) {
                            NoteScreen(notes = notes) { noteOpen = false }
                        }
                        openPost != null -> {
                            /* 阅读页也走 pager：左右滑动就是上一篇 / 下一篇，跟站点 POSTS 邻页一个体验。
                               顺序跟文章列表共用 orderedPosts()，滑到的下一篇一定是列表里挨着的那篇。 */
                            val reader = remember(data) { orderedPosts(data.posts) }
                            val idx = reader.indexOfFirst { it.id == openPost!!.id }
                            if (idx < 0) {
                                /* 兜底：这篇不在列表里（比如刚被删掉）就单篇渲染，不然 pager 建不起来 */
                                Box(Modifier.fillMaxSize().enterRise()) {
                                    PostDetailScreen(
                                        openPost!!, { openPost = null }, open, prefs.fontScale,
                                        prefs.lineHeightFactor / 1.95f, prefs.readFont == "serif",
                                        local = local, rememberPos = prefs.rememberPos
                                    )
                                }
                            } else {
                                val postPager = rememberPagerState(initialPage = idx) { reader.size }
                                /* 手指滑到哪一篇，openPost 就跟着变 —— 返回键、标题栏都读它 */
                                LaunchedEffect(postPager.currentPage) {
                                    val p = reader.getOrNull(postPager.currentPage)
                                    if (p != null && p.id != openPost?.id) openPost = p
                                }
                                Box(Modifier.fillMaxSize().enterRise()) {
                                    HorizontalPager(
                                        state = postPager,
                                        modifier = Modifier.fillMaxSize(),
                                        beyondBoundsPageCount = 1,
                                        key = { i -> reader.getOrElse(i) { Post() }.id }
                                    ) { page ->
                                        val p = reader.getOrNull(page) ?: return@HorizontalPager
                                        PostDetailScreen(
                                            post = p,
                                            onBack = { openPost = null },
                                            onOpen = open,
                                            fontScale = prefs.fontScale,
                                            lineFactor = prefs.lineHeightFactor / 1.95f,
                                            serif = prefs.readFont == "serif",
                                            local = local,
                                            rememberPos = prefs.rememberPos,
                                            /* 列表是新的在前：下一格更早，上一格更新 */
                                            older = reader.getOrNull(page + 1),
                                            newer = reader.getOrNull(page - 1),
                                            onJump = { t ->
                                                val j = reader.indexOfFirst { it.id == t.id }
                                                if (j >= 0) scope.launch { postPager.animateScrollToPage(j) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        else -> Column(Modifier.fillMaxSize()) {
                            /* 顶栏在所有页签之上，翻页它不动 —— 跟站点那个 sticky header 一样 */
                            TopBar(
                                siteName = data.site.name.ifBlank { "壹号栈" },
                                dark = dark,
                                searchText = query,
                                /* 自定义入口跟网页端同一份：站点后台「自定义链接」配几条这儿就有几颗 */
                                links = data.links,
                                /* 品牌（左上角「壹号栈」）：任何页签上都是「到底 / 回顶」，不再回首页。
                                   滚的是当前页那条列表 —— 换成别的页就是滚那一页。 */
                                onBrandTap = { listForTab[curId]?.let { toggleEnd(scope, it) } },
                                onLink = { l ->
                                    /* 站内路由优先跳原生页，剩下的一律交给内置浏览器。
                                       站点后台「自定义链接」里常见的就是 #/later、#/about、#/tag/设计 这些，
                                       以前只认五个，别的全甩给 WebView —— 明明是同一个站点却像换了个 App。 */
                                    val raw = l.url.substringAfter("#/", "").substringBefore('?').trimEnd('/')
                                    when {
                                        raw.startsWith("idea") -> ideaOpen = true
                                        raw.startsWith("nav") -> goTab(3)
                                        raw.startsWith("share") -> goTab(1)
                                        raw.startsWith("post") -> goTab(2)
                                        raw.startsWith("home") || raw.isEmpty() -> goTab(0)
                                        /* 收藏页三段的定位：#/later 落在「稍后读」，#/saved|#/fav 落在「收藏」 */
                                        raw.startsWith("later") -> { savedSegHint = 1; jump(5, false) }
                                        raw.startsWith("saved") || raw.startsWith("fav") -> {
                                            savedSegHint = 0; jump(5, false)
                                        }
                                        raw.startsWith("about") -> aboutOpen = true
                                        /* 本地音乐：站点的 #/music 在 App 里是内置页 */
                                        raw.startsWith("music") -> musicOpen = true
                                        /* 随手记：站点的 #/note 也是内置页了（2.7.7） */
                                        raw.startsWith("note") -> noteOpen = true
                                        /* 数据洞察 = 足迹页。2.6 起是内置整屏页（不再占底栏页签），
                                           跟网页端顶栏那条「👣 足迹」一个去处 */
                                        raw.startsWith("insight") -> footOpen = true
                                        raw.startsWith("tag/") -> {
                                            val t = runCatching {
                                                java.net.URLDecoder.decode(
                                                    raw.removePrefix("tag/"), "UTF-8"
                                                )
                                            }.getOrDefault(raw.removePrefix("tag/"))
                                            /* 标签页在站点里是「分享 + 文章」一起筛，App 分两页 —— 
                                               哪边有这个标签就去哪边，两边都有就先去分享 */
                                            when {
                                                t.isBlank() -> goTab(1)
                                                data.feed.any { it.tags.contains(t) } -> {
                                                    feedTag = t; jump(1, true)
                                                }
                                                data.posts.any { it.tags.contains(t) } -> {
                                                    postTag = t; jump(2, true)
                                                }
                                                else -> open(
                                                    data.site.url.trimEnd('/') + "/#/tag/" + t
                                                )
                                            }
                                        }
                                        else -> open(
                                            if (l.url.startsWith("#")) {
                                                data.site.url.trimEnd('/') + "/" + l.url
                                            } else l.url
                                        )
                                    }
                                },
                                onSaved = { goTab(5) },
                                onTheme = { prefs.updateTheme(if (dark) "light" else "dark") },
                                onOpenSearch = { searchOpen = true },
                                filter = filter,
                                /* 下拉刷新的状态画在搜索框右端（2.8.1） */
                                pullProgress = pullProgress,
                                refreshing = ui.refreshing,
                                refreshTip = tip,
                                /* 顶栏各块显隐：站点后台「站点信息 → 顶栏显示」说了算 */
                                top = data.top
                            )
                            /* 下拉刷新只包在内容页上：网页预览 / 阅读页下拉没意义 */
                            PullRefresh(
                                refreshing = ui.refreshing,
                                onRefresh = { vm.refresh() },
                                modifier = Modifier.weight(1f),
                                /* 设置页是一堆开关，点一下立刻生效，下拉刷新对它没意义，
                                   白转一个圈反而让人以为「要点刷新才存」 */
                                enabled = visible.getOrElse(pager.currentPage) { 0 } != 4,
                                onPull = { pullProgress = it }
                            ) {
                                HorizontalPager(
                                    state = pager,
                                    modifier = Modifier.fillMaxSize(),
                                    /* 预取相邻一页：滑到一半才开始搭下一页会顿一下 */
                                    beyondBoundsPageCount = 1,
                                    /* 底栏藏/换序之后 visible 会变短，而 pager 在换页数的那一帧
                                       仍可能拿旧下标来问 key —— 直接下标会 IndexOutOfBounds（就是
                                       「设置里关掉一个标签就闪退」那个崩溃）。getOrElse 兜住 */
                                    key = { i -> visible.getOrElse(i) { -1 } }
                                ) { page ->
                                    when (visible.getOrElse(page) { -1 }) {
                                        0 -> Box(Modifier.fillMaxSize().enterRise()) {
                                            HomeScreen(
                                                blocks = prefs.homeBlockList,
                                                data = data,
                                                offline = ui.offline,
                                                onOpen = open,
                                                onGoTab = goTab,
                                                onOpenPost = { openPost = it },
                                                onOpenAbout = { aboutOpen = true },
                                                listState = homeList
                                            )
                                        }
                                        1 -> Box(Modifier.fillMaxSize().enterRise()) {
                                            FeedScreen(
                                                data, query, feedTag, open,
                                                /* 点标签不再原地筛，而是打开「这个标签的全部内容」那一页 */
                                                onPickTag = { tagOpen = it },
                                                listState = feedList
                                            )
                                        }
                                        2 -> Box(Modifier.fillMaxSize().enterRise()) {
                                            PostsScreen(data, query, postTag, { openPost = it }, postList)
                                        }
                                        3 -> Box(Modifier.fillMaxSize().enterRise()) {
                                            LinksScreen(
                                                data, query, navCat, open, prefs.navCollapsed, local,
                                                navList
                                            )
                                        }
                                        5 -> Box(Modifier.fillMaxSize().enterRise()) {
                                            SavedScreen(
                                                data, local,
                                                onOpenPost = { openPost = it },
                                                onOpenUrl = { u -> open(u) },
                                                segHint = savedSegHint,
                                                onSegHint = { savedSegHint = null },
                                                listState = savedList
                                            )
                                        }
                                        else -> Box(Modifier.fillMaxSize().enterRise()) {
                                            SettingsScreen(
                                                prefs, vm, open, local, setList,
                                                onHelp = { helpOpen = true },
                                                onLog = { logOpen = true },
                                                onExtracts = { exOpen = true },
                                                onCheck = { checkOpen = true }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /* 全站搜索：整屏铺开，键盘起来也够放结果 */
        if (searchOpen && data != null) {
            SearchOverlay(
                data = data,
                query = query,
                onQuery = { query = it },
                onClose = {
                    /* 取消时把关键词一起清掉：否则退回去的列表还被这个词筛着，
                       页面上又没有清除入口，只剩一个「找到 0 个」的空态看着像丢数据 */
                    query = ""
                    searchOpen = false
                    focus.clearFocus()
                    keyboard?.hide()
                },
                onOpenUrl = { u -> query = ""; searchOpen = false; open(u) },
                onOpenPost = { p -> searchOpen = false; openPost = p },
                onCategory = { c ->
                    /* 从搜索里点分类：要的是「这一类的全部」，关键词清掉 —— 两个字叠着筛只剩几条，反而像没数据 */
                    query = ""
                    navCat = c.id
                    searchOpen = false
                    jump(3, true)
                },
                onTab = { id ->
                    /* 「看全部 N 个」那串数字是按全站算的，跳过去必须把该页自己的分类/标签筛清掉 ——
                       两个筛叠着只剩几条，跟刚才看到的 N 对不上，看着像丢了数据 */
                    when (id) {
                        1 -> feedTag = ""
                        2 -> postTag = ""
                        3 -> navCat = ""
                    }
                    searchOpen = false
                    jump(id, true)
                }
            )
        }

        /* 底部筛选面板：分类 / 标签，三处共用 */
        if (picker != PICK_NONE && data != null) {
            val (title, allLabel, items, current, set) = when (picker) {
                PICK_CAT -> Quint(
                    "全部分类", "全部分类",
                    /* emoji 跟「分类图标」那个开关走 —— 网页端面板里关掉就是纯名字，
                       App 这儿以前无条件带 emoji，两边对不上 */
                    data.cats.map {
                        PickItem(it.id, if (data.nav.catIcon) it.icon else "", it.name, it.links.size)
                    },
                    navCat, { v: String -> navCat = v }
                )
                PICK_FEED_TAG -> Quint(
                    "按标签筛选", "全部标签",
                    data.feed.flatMap { it.tags }.groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }
                        .map { PickItem(it.key, "", it.key, it.value) },
                    feedTag, { v: String -> feedTag = v }
                )
                else -> Quint(
                    "按标签筛选", "全部标签",
                    data.posts.flatMap { it.tags }.groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }
                        .map { PickItem(it.key, "", it.key, it.value) },
                    postTag, { v: String -> postTag = v }
                )
            }
            PickerSheet(
                title = title,
                allLabel = allLabel,
                items = items,
                current = current,
                /* 选完就把面板收掉 —— 站点那边点分类 / 标签也是「选中即关」，
                   App 以前只改了筛选、面板还支在那儿，得再点一次「完成」才看得到结果 */
                onPick = { v -> set(v); picker = PICK_NONE },
                onClose = { picker = PICK_NONE },
                /* 分类后面的站点数跟站点后台那个开关走；标签数照旧一直显示 */
                showCount = if (picker == PICK_CAT) data.nav.count else true
            )
        }
    }

    /* 发现新版就弹一次；点「稍后」会把 latest 清掉，这次打开不再打扰（下次启动还会问） */
    if (vm.upd.latest != null) {
        UpdateDialog(
            upd = vm.upd,
            myVersion = vm.myVersion,
            onLater = { vm.dismissUpdate() },
            onDownload = { vm.downloadUpdate() },
            onInstall = {
                val apk = vm.upd.apk
                if (apk != null) {
                    if (UpdateRepo.canInstall(context)) UpdateRepo.install(context, apk)
                    else UpdateRepo.openInstallSetting(context)
                }
            }
        )
    }
}

/** 面板参数凑成一包，省得在 when 里写四份重复的具名参数 */
private data class Quint(
    val title: String,
    val allLabel: String,
    val items: List<PickItem>,
    val current: String,
    val set: (String) -> Unit
)

/**
 * 底部标签栏：照站点手机端的 .dock —— 一颗**悬浮药丸**，不是通到底的横栏。
 *
 * 图标默认不显示（设置里能开）：只留文字时药丸只有 ~39dp、连外边距整条 ~51dp，
 * 几乎不抢内容（2.6.4 又收了一档：行内留白 9dp→6dp、外边距 10/6dp→8/4dp）；
 * 想一眼认出来再去设置里打开图标。按钮的先后顺序和显示隐藏也在设置里改。
 *
 * 高亮是一颗**会滑的胶囊**，位置取 pager 的连续位置（`position`），
 * 所以左右翻页时它跟内容一起走，而不是到页了才「啪」地跳过去。
 */
@Composable
private fun BottomDock(
    position: Float,
    current: Int,
    tabs: List<Int>,
    showIcons: Boolean,
    onPick: (Int) -> Unit
) {
    val pal = LocalXyPal.current
    val gap = 1.dp
    val density = LocalDensity.current
    /* 按屏宽和 tab 数自适应，别写死 64dp —— 一开到「收藏」变六项，固定宽度会超出屏幕、
       最后一个被裁掉。留出左右各 12dp 外边距 + 药丸内边距 5dp*2，再减去项间距后平均分。 */
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val count = tabs.size.coerceAtLeast(1)
    val maxItemW = if (showIcons) 58.dp else 64.dp
    val avail = screenW - 24.dp - 10.dp - gap * (count - 1)
    val itemW = (avail / count.toFloat()).coerceAtMost(maxItemW).coerceAtLeast(38.dp)
    /* 胶囊高度取整行的实测高度：图标开关会让行变高，写死数字迟早对不上 */
    var rowH by remember { mutableStateOf(0) }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(pal.bgSoft.copy(alpha = 0.94f))
                .padding(5.dp)
        ) {
            if (rowH > 0) {
                /* 高亮块有两种样式，按「底栏显不显示图标」分开（小宝 2026-11-01 定的）：
                   · 带图标的这一版 —— 圆角按位置来：贴着药丸两端的格子才跟着药丸的弧走，
                     第一项只圆**左边**两角、最后一项只圆**右边**两角、中间一律直角；
                   · 纯文字的那一版 —— 保持原来的整颗胶囊（四项都是 99dp 全圆角），
                     字少、每格窄，胶囊看着更整。
                   两种情况都跟着 pager 的连续位置滑，只是形状不同。 */
                val at = position.roundToInt().coerceIn(0, count - 1)
                val hlShape = if (!showIcons) {
                    RoundedCornerShape(99.dp)
                } else when {
                    count <= 1 -> RoundedCornerShape(99.dp)
                    at == 0 -> RoundedCornerShape(topStart = 99.dp, bottomStart = 99.dp)
                    at == count - 1 -> RoundedCornerShape(topEnd = 99.dp, bottomEnd = 99.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (itemW + gap) * position)
                        .width(itemW)
                        .height(with(density) { rowH.toDp() })
                        .background(pal.glassHi, hlShape)
                )
            }
            Row(
                Modifier.onSizeChanged { rowH = it.height },
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { id ->
                    val t = TABS.firstOrNull { it.id == id } ?: return@forEach
                    val on = current == id
                    Column(
                        Modifier
                            /* 固定宽度：不然选中项字重变粗会让整颗药丸左右微跳 */
                            .width(itemW)
                            .clip(RoundedCornerShape(99.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onPick(id) }
                            /* 上下留白两种模式统一 6dp：2.6.4 之前纯文字版是 9dp，
                               小宝说「图标不显示时 tab 栏可以再矮一点」→ 收掉 3dp，
                               整条底栏从 ~61dp 降到 ~50dp */
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showIcons) {
                            Icon(
                                t.icon, null,
                                Modifier.size(19.dp),
                                tint = if (on) pal.inkA1 else pal.mute
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            t.label,
                            fontSize = if (showIcons) 9.5.sp else 12.5.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) pal.inkA1 else pal.mute
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Booting() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
        Text(
            "正在同步内容…",
            fontSize = 13.sp,
            color = LocalXyPal.current.dim,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun Failed(msg: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📡", fontSize = 34.sp)
        Text(
            msg,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = LocalXyPal.current.dim,
            modifier = Modifier.padding(vertical = 14.dp)
        )
        Button(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
            Text("重试", modifier = Modifier.padding(start = 6.dp))
        }
    }
}
