package xyz.yihaozhan.app

import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import xyz.yihaozhan.app.data.Post
import xyz.yihaozhan.app.data.Local
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.data.UpdateRepo
import xyz.yihaozhan.app.ui.AuroraBackground
import xyz.yihaozhan.app.ui.FeedScreen
import xyz.yihaozhan.app.ui.HomeScreen
import xyz.yihaozhan.app.ui.LinksScreen
import xyz.yihaozhan.app.ui.LocalXyPal
import xyz.yihaozhan.app.ui.PostDetailScreen
import xyz.yihaozhan.app.ui.PullRefresh
import xyz.yihaozhan.app.ui.UpdateDialog
import xyz.yihaozhan.app.ui.PostsScreen
import xyz.yihaozhan.app.ui.SavedScreen
import xyz.yihaozhan.app.ui.SettingsScreen
import xyz.yihaozhan.app.ui.WebScreen
import xyz.yihaozhan.app.ui.YiTheme
import xyz.yihaozhan.app.ui.rememberOpener

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs.of(this)
        setContent {
            /* 主题跟着设置走：跟随系统 / 浅色 / 深色 */
            val dark = when (prefs.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            YiTheme(dark = dark) {
                /* 极光背景铺在最底下，整页（含状态栏、底栏后面）都是那三团光 */
                AuroraBackground {
                    val vm: MainVm = viewModel()
                    AppRoot(vm, prefs, dark)
                }
            }
        }
    }
}

private data class Tab(val id: Int, val label: String, val icon: ImageVector)

/* id 就是「底栏排序 / 显隐」设置里存的那个数字，别改 */
private val TABS = listOf(
    Tab(0, "首页", Icons.Rounded.Home),
    Tab(1, "分享", Icons.Rounded.Bolt),
    Tab(2, "文章", Icons.AutoMirrored.Rounded.MenuBook),
    Tab(3, "导航", Icons.Rounded.GridView),
    Tab(4, "设置", Icons.Rounded.Settings),
    /* 1.8 新加的一页：收藏 + 稍后读。id 5，老设置里没有会自动补到末尾 */
    Tab(5, "收藏", Icons.Rounded.StarBorder)
)

fun tabLabel(id: Int): String = TABS.firstOrNull { it.id == id }?.label ?: "?"

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppRoot(vm: MainVm, prefs: Prefs, dark: Boolean) {
    val context = LocalContext.current
    /* 收藏 / 稍后读 / 最近打开 / 读到哪：本机数据，一份单例 */
    val local = remember { Local.of(context) }
    val ui = vm.ui
    /* 只在设置里改过顺序/显隐时才重算：visibleTabs 是 getter，
       不 remember 的话每次重组都新造一个 List，底栏和无障碍树跟着白刷一遍。 */
    val visible = remember(prefs.tabOrder, prefs.tabHidden) { prefs.visibleTabs }
    var openPost by remember { mutableStateOf<Post?>(null) }
    var webUrl by rememberSaveable { mutableStateOf<String?>(null) }

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    /* 站外链接：默认 App 内预览；设置里关掉「链接在 App 内打开」就直接丢给浏览器 */
    val external = rememberOpener()
    val open: (String) -> Unit = { u ->
        if (u.isNotBlank()) {
            if (prefs.linkInApp) webUrl = u else external(u)
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

    val goTab: (Int) -> Unit = { id ->
        val i = visible.indexOf(id)
        if (i >= 0) {
            openPost = null
            webUrl = null
            goPage(i)
        }
    }

    /* 返回键：先退网页预览，再退阅读页，退完才退出 App */
    BackHandler(enabled = webUrl != null || openPost != null) {
        when {
            webUrl != null -> webUrl = null
            else -> openPost = null
        }
    }

    Scaffold(
        /* 透明：底色交给下面那层极光，不然会把光晕盖住 */
        containerColor = Color.Transparent,
        bottomBar = {
            if (openPost == null && webUrl == null) {
                BottomDock(
                    position = dockPos,
                    current = curId,
                    tabs = visible,
                    showIcons = prefs.dockIcons
                ) { id ->
                    val i = visible.indexOf(id)
                    if (i >= 0) goPage(i)
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
            val data = ui.data
            when {
                data == null && ui.booting -> Booting()
                data == null -> Failed(ui.error ?: "加载失败") { vm.refresh() }
                webUrl != null -> WebScreen(webUrl!!, { webUrl = null }, external, dark)
                openPost != null -> PostDetailScreen(
                    openPost!!, { openPost = null }, open, prefs.fontScale,
                    local = local, rememberPos = prefs.rememberPos
                )
                /* 下拉刷新只包在内容页上：网页预览 / 阅读页下拉没意义 */
                else -> PullRefresh(
                    refreshing = ui.refreshing,
                    onRefresh = { vm.refresh() }
                ) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        /* 预取相邻一页：滑到一半才开始搭下一页会顿一下 */
                        beyondBoundsPageCount = 1,
                        key = { visible[it] }
                    ) { page ->
                        when (visible[page]) {
                            0 -> HomeScreen(
                                data = data,
                                offline = ui.offline,
                                onSettings = { goTab(4) },
                                onOpen = open,
                                onGoTab = goTab,
                                onOpenPost = { openPost = it }
                            )
                            1 -> FeedScreen(data, open) { goTab(4) }
                            2 -> PostsScreen(data, { openPost = it }) { goTab(4) }
                            3 -> LinksScreen(
                                data, open, { goTab(4) }, prefs.navCollapsed, local
                            )
                            5 -> SavedScreen(
                                data.posts, local,
                                onOpenPost = { openPost = it },
                                onSettings = { goTab(4) }
                            )
                            else -> SettingsScreen(prefs, vm, open, local)
                        }
                    }
                }
            }
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

/**
 * 底部标签栏：照站点手机端的 .dock —— 一颗**悬浮药丸**，不是通到底的横栏。
 *
 * 图标默认不显示（设置里能开）：只留文字时药丸只有 ~44dp 高，几乎不抢内容；
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
    val itemW = if (showIcons) 58.dp else 64.dp
    val gap = 1.dp
    val density = LocalDensity.current
    /* 胶囊高度取整行的实测高度：图标开关会让行变高，写死数字迟早对不上 */
    var rowH by remember { mutableStateOf(0) }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp, top = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(pal.bgSoft.copy(alpha = 0.94f))
                .padding(5.dp)
        ) {
            if (rowH > 0) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (itemW + gap) * position)
                        .width(itemW)
                        .height(with(density) { rowH.toDp() })
                        .background(pal.glassHi, RoundedCornerShape(99.dp))
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
                            .padding(vertical = if (showIcons) 6.dp else 9.dp),
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
