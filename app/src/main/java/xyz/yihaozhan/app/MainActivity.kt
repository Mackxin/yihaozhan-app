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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.yihaozhan.app.data.Post
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.ui.AboutScreen
import xyz.yihaozhan.app.ui.FeedScreen
import xyz.yihaozhan.app.ui.HomeScreen
import xyz.yihaozhan.app.ui.LinksScreen
import xyz.yihaozhan.app.ui.PostDetailScreen
import xyz.yihaozhan.app.ui.PostsScreen
import xyz.yihaozhan.app.ui.SettingsScreen
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
                val vm: MainVm = viewModel()
                AppRoot(vm, prefs)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("首页", Icons.Rounded.Home),
    Tab("分享", Icons.Rounded.Bolt),
    Tab("文章", Icons.AutoMirrored.Rounded.MenuBook),
    Tab("导航", Icons.Rounded.GridView),
    Tab("关于", Icons.Rounded.Info)
)

@Composable
private fun AppRoot(vm: MainVm, prefs: Prefs) {
    val ui = vm.ui
    var tab by rememberSaveable { mutableIntStateOf(prefs.startTab.coerceIn(0, TABS.size - 1)) }
    var openPost by remember { mutableStateOf<Post?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val open = rememberOpener()
    val goTab: (Int) -> Unit = { i ->
        openPost = null
        showSettings = false
        tab = i
    }

    /* 返回键：设置/阅读先退一层，不然直接退出 App */
    BackHandler(enabled = openPost != null || showSettings) {
        if (showSettings) showSettings = false else openPost = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            /* 设置页和阅读页不显示底栏 */
            if (openPost == null && !showSettings) {
                BottomTabs(tab) { tab = it }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val data = ui.data
            when {
                data == null && ui.booting -> Booting()
                data == null -> Failed(ui.error ?: "加载失败") { vm.refresh() }
                showSettings -> SettingsScreen(prefs, vm, { showSettings = false }, open)
                openPost != null -> PostDetailScreen(
                    openPost!!, { openPost = null }, open, prefs.fontScale
                )
                else -> when (tab) {
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

/**
 * 底部标签栏：自己拼的，没用 NavigationBar。
 * NavigationBar 固定 80dp 高 + 顶部留白，五个 tab 显得特别占地方；这里压到 54dp。
 */
@Composable
private fun BottomTabs(tab: Int, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surface)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(scheme.outline.copy(alpha = 0.45f))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEachIndexed { i, t ->
                val on = tab == i
                val tint = if (on) scheme.primary else scheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPick(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(t.icon, null, Modifier.size(19.dp), tint = tint)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        t.label,
                        fontSize = 10.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 14.dp)
        )
        Button(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
            Text("重试", modifier = Modifier.padding(start = 6.dp))
        }
    }
}
