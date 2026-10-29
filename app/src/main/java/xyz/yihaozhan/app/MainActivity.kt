package xyz.yihaozhan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.yihaozhan.app.data.Post
import xyz.yihaozhan.app.ui.AboutScreen
import xyz.yihaozhan.app.ui.FeedScreen
import xyz.yihaozhan.app.ui.LinksScreen
import xyz.yihaozhan.app.ui.PostDetailScreen
import xyz.yihaozhan.app.ui.PostsScreen
import xyz.yihaozhan.app.ui.YiTheme
import xyz.yihaozhan.app.ui.rememberOpener

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YiTheme {
                val vm: MainVm = viewModel()
                AppRoot(vm)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("分享", Icons.Rounded.Bolt),
    Tab("文章", Icons.AutoMirrored.Rounded.MenuBook),
    Tab("导航", Icons.Rounded.GridView),
    Tab("关于", Icons.Rounded.Info)
)

@Composable
private fun AppRoot(vm: MainVm) {
    val ui = vm.ui
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPost by remember { mutableStateOf<Post?>(null) }
    val open = rememberOpener()

    /* 阅读文章时按返回键先回到列表，而不是直接退出 App */
    BackHandler(enabled = openPost != null) { openPost = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (openPost == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, null, Modifier.size(21.dp)) },
                            label = { Text(t.label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val data = ui.data
            when {
                data == null && ui.booting -> Booting()
                data == null -> Failed(ui.error ?: "加载失败") { vm.refresh() }
                openPost != null -> PostDetailScreen(openPost!!, { openPost = null }, open)
                else -> when (tab) {
                    0 -> FeedScreen(data, open)
                    1 -> PostsScreen(data) { openPost = it }
                    2 -> LinksScreen(data, open)
                    else -> AboutScreen(vm, open)
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
