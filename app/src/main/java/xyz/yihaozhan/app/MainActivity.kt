package xyz.yihaozhan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import xyz.yihaozhan.app.data.Post
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.ui.AboutScreen
import xyz.yihaozhan.app.ui.AuroraBackground
import xyz.yihaozhan.app.ui.FeedScreen
import xyz.yihaozhan.app.ui.HomeScreen
import xyz.yihaozhan.app.ui.LinksScreen
import xyz.yihaozhan.app.ui.LocalXyPal
import xyz.yihaozhan.app.ui.PostDetailScreen
import xyz.yihaozhan.app.ui.PostsScreen
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
                    AppRoot(vm, prefs)
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
    Tab(4, "关于", Icons.Rounded.Info)
)

fun tabLabel(id: Int): String = TABS.firstOrNull { it.id == id }?.label ?: "?"

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppRoot(vm: MainVm, prefs: Prefs) {
    val ui = vm.ui
    /* 只在设置里改过顺序/显隐时才重算：visibleTabs 是 getter，
       不 remember 的话每次重组都新造一个 List，底栏和无障碍树跟着白刷一遍。 */
    val visible = remember(prefs.tabOrder, prefs.tabHidden) { prefs.visibleTabs }
    var openPost by remember { mutableStateOf<Post?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var webUrl by rememberSaveable { mutableStateOf<String?>(null) }

    /* 站外链接一律在 App 内预览；真要浏览器（登录、支付）由预览页那颗按钮转出去 */
    val external = rememberOpener()
    val open: (String) -> Unit = { u -> if (u.isNotBlank()) webUrl = u }

    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(
        initialPage = visible.indexOf(prefs.startTab).coerceAtLeast(0),
        pageCount = { visible.size }
    )
    val curId = visible.getOrElse(pager.currentPage) { visible.first() }

    /* 底栏被改过（隐藏/换序）之后页数会变，把位置夹回范围内 */
    LaunchedEffect(prefs.tabOrder, prefs.tabHidden) {
        if (pager.currentPage > visible.lastIndex) pager.scrollToPage(visible.lastIndex)
    }

    val goTab: (Int) -> Unit = { id ->
        val i = visible.indexOf(id)
        if (i >= 0) {
            openPost = null
            showSettings = false
            webUrl = null
            scope.launch { pager.animateScrollToPage(i) }
        }
    }

    /* 返回键：网页 → 设置 → 阅读，一层层退，退完才退出 App */
    BackHandler(enabled = webUrl != null || showSettings || openPost != null) {
        when {
            webUrl != null -> webUrl = null
            showSettings -> showSettings = false
            else -> openPost = null
        }
    }

    Scaffold(
        /* 透明：底色交给下面那层极光，不然会把光晕盖住 */
        containerColor = Color.Transparent,
        bottomBar = {
            if (openPost == null && !showSettings && webUrl == null) {
                BottomDock(
                    current = curId,
                    tabs = visible,
                    showIcons = prefs.dockIcons
                ) { id ->
                    val i = visible.indexOf(id)
                    if (i >= 0) scope.launch { pager.animateScrollToPage(i) }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val data = ui.data
            when {
                data == null && ui.booting -> Booting()
                data == null -> Failed(ui.error ?: "加载失败") { vm.refresh() }
                webUrl != null -> WebScreen(webUrl!!, { webUrl = null }, external)
                showSettings -> SettingsScreen(prefs, vm, { showSettings = false }, open)
                openPost != null -> PostDetailScreen(
                    openPost!!, { openPost = null }, open, prefs.fontScale
                )
                else -> HorizontalPager(
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
                            onSettings = { showSettings = true },
                            onOpen = open,
                            onGoTab = goTab,
                            onOpenPost = { openPost = it }
                        )
                        1 -> FeedScreen(data, open) { showSettings = true }
                        2 -> PostsScreen(data, { openPost = it }) { showSettings = true }
                        3 -> LinksScreen(data, open) { showSettings = true }
                        else -> AboutScreen(vm, open) { showSettings = true }
                    }
                }
            }
        }
    }
}

/**
 * 底部标签栏：照站点手机端的 .dock —— 一颗**悬浮药丸**，不是通到底的横栏。
 *
 * 图标默认不显示（设置里能开）：只留文字时药丸只有 ~44dp 高，几乎不抢内容；
 * 想一眼认出来再去设置里打开图标。按钮的先后顺序和显示隐藏也在设置里改。
 */
@Composable
private fun BottomDock(
    current: Int,
    tabs: List<Int>,
    showIcons: Boolean,
    onPick: (Int) -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp, top = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(pal.bgSoft.copy(alpha = 0.94f))
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { id ->
                val t = TABS.firstOrNull { it.id == id } ?: return@forEach
                val on = current == id
                Column(
                    Modifier
                        /* 固定宽度：不然选中项字重变粗会让整颗药丸左右微跳 */
                        .width(if (showIcons) 58.dp else 64.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .then(if (on) Modifier.background(pal.glassHi) else Modifier)
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
