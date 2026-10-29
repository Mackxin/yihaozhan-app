package xyz.yihaozhan.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.MainVm
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.data.Repo

/**
 * 设置页 —— 主题、字号、启动页、自动更新、缓存、关于。
 *
 * 每一项都是真接上了的：改主题当场换配色，改字号文章正文立刻变，
 * 启动页下次打开生效，清缓存真的删文件。没有摆设开关。
 */
@Composable
fun SettingsScreen(prefs: Prefs, vm: MainVm, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ui = vm.ui
    val data = ui.data
    val ctx = LocalContext.current
    var cacheSize by remember(data, ui.lastSync) { mutableStateOf(Repo.cacheSize(ctx)) }
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "1.1"
    }

    Column(Modifier.fillMaxSize()) {
        /* 顶栏：返回 + 居中标题 */
        Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
            Text(
                "设置",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.01).em,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            /* ---------------- 外观 ---------------- */
            item { GroupTitle("外观") }
            item {
                SettingCard {
                    Text("主题", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf("跟随系统", "浅色", "深色"),
                        selected = when (prefs.theme) {
                            "light" -> 1
                            "dark" -> 2
                            else -> 0
                        }
                    ) { i ->
                        prefs.updateTheme(if (i == 1) "light" else if (i == 2) "dark" else "auto")
                    }
                }
            }

            /* ---------------- 阅读 ---------------- */
            item { GroupTitle("阅读") }
            item {
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("正文字号", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            prefs.fontSizeName, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Segmented(options = listOf("小", "标准", "大"), selected = prefs.fontSize) {
                        prefs.updateFontSize(it)
                    }
                    Spacer(Modifier.height(12.dp))
                    /* 现成的一句话当预览，不用跳去文章里试 */
                    Text(
                        "文章正文会长这样：壹号为栈，随处可归。",
                        fontSize = (14 * prefs.fontScale).sp,
                        lineHeight = (24 * prefs.fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /* ---------------- 启动 ---------------- */
            item { GroupTitle("启动") }
            item {
                SettingCard {
                    Text("打开 App 停在", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "下次打开生效",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf("首页", "分享", "文章", "导航", "关于"),
                        selected = prefs.startTab,
                        compact = true
                    ) { prefs.updateStartTab(it) }
                }
            }

            /* ---------------- 内容更新 ---------------- */
            item { GroupTitle("内容更新") }
            item {
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("打开时自动拉最新", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "关掉之后只在手动点「立即同步」时联网",
                                fontSize = 11.5.sp, lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = prefs.autoSync, onCheckedChange = { prefs.updateAutoSync(it) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.refresh() }, enabled = !ui.refreshing) {
                            if (ui.refreshing) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (ui.refreshing) "正在拉最新…" else "立即同步", fontSize = 13.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (ui.offline) "离线" else "在线",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "上次同步 " + syncText(ui.lastSync) +
                            (data?.let { " · 数据版本 " + it.build } ?: ""),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /* ---------------- 存储 ---------------- */
            item { GroupTitle("存储") }
            item {
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("离线缓存", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "整站内容压在手机里，占 " + sizeText(cacheSize),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            val ok = Repo.clearCache(ctx)
                            cacheSize = Repo.cacheSize(ctx)
                            Toast.makeText(
                                ctx,
                                if (ok) "已清除，下次打开需要联网一次" else "清除失败，稍后再试",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) { Text("清除", fontSize = 13.sp) }
                    }
                }
            }

            /* ---------------- 关于 ---------------- */
            item { GroupTitle("关于") }
            item {
                SettingCard {
                    InfoRow("版本", "壹号栈 $version")
                    InfoRow(
                        "数据来源",
                        "mackxin.github.io/xingyu",
                        onClick = { onOpen("https://mackxin.github.io/xingyu/") }
                    )
                    InfoRow(
                        "开源仓库",
                        "github.com/Mackxin/yihaozhan-app",
                        onClick = { onOpen("https://github.com/Mackxin/yihaozhan-app") }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "内容每次打开自动更新，离线也能看。这里只读不写，改内容在站点后台。",
                        fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ------------------------------ 零件 ------------------------------ */

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.18.em,
        color = LocalXyPal.current.mute,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(XyR.md.dp))
            .background(LocalXyPal.current.glass)
            .padding(horizontal = 15.dp, vertical = 15.dp)
    ) { content() }
}

/** 分段选择：一排等宽胶囊，选中的填主色 */
@Composable
private fun Segmented(
    options: List<String>,
    selected: Int,
    compact: Boolean = false,
    onPick: (Int) -> Unit
) {
    val pal = LocalXyPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glass)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(99.dp))
                    .then(if (on) Modifier.background(pal.grad()) else Modifier)
                    .clickable { onPick(i) }
                    .padding(vertical = if (compact) 7.dp else 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (on && !compact) {
                    Icon(
                        Icons.Rounded.Check, null,
                        tint = XY_INK,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    label,
                    fontSize = if (compact) 11.5.sp else 12.5.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    color = if (on) XY_INK else pal.dim
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, fontSize = 12.5.sp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.ArrowOutward, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024f / 1024f)
}

private fun syncText(ts: Long): String {
    if (ts <= 0L) return "还没同步过"
    return java.text.SimpleDateFormat("M 月 d 日 HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(ts))
}
