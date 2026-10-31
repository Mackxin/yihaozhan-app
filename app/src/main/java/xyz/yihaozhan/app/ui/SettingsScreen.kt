package xyz.yihaozhan.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import xyz.yihaozhan.app.MainVm
import xyz.yihaozhan.app.tabLabel
import xyz.yihaozhan.app.data.Local
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.data.Repo
import xyz.yihaozhan.app.data.UpdateRepo
import kotlin.math.roundToInt

/**
 * 设置页 —— 外观、阅读、导航、底栏、内容更新、App 更新、存储、关于。
 *
 * 它是**底栏上的一个页面**（1.7 起），不是弹出来的浮层：能左右滑过来，也能被记住成启动页。
 * 原来那个独立的「关于」页整块并到最下面一组，省掉一个 tab。
 *
 * 每一项都是真接上了的：改主题当场换配色，改字号文章正文立刻变，
 * 启动页下次打开生效，清缓存真的删文件。没有摆设开关。
 */
@Composable
fun SettingsScreen(prefs: Prefs, vm: MainVm, onOpen: (String) -> Unit, local: Local) {
    val ui = vm.ui
    val data = ui.data
    val ctx = LocalContext.current
    var cacheSize by remember(data, ui.lastSync) { mutableStateOf(Repo.cacheSize(ctx)) }
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "1.2"
    }

    /* 装更新：系统没给「安装未知应用」权限就先跳去开关页，别让用户对着「没反应」发呆 */
    val installUpdate: () -> Unit = {
        val apk = vm.upd.apk
        if (apk != null) {
            if (UpdateRepo.canInstall(ctx)) UpdateRepo.install(ctx, apk)
            else UpdateRepo.openInstallSetting(ctx)
        }
    }

    /* 页头整块当列表第一个 item，往下滑跟着走，不钉在顶上（跟分享/文章/导航一致） */
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("head") {
            PageHeader(
                "设置",
                "外观、阅读、底栏、更新都在这里。",
                eyebrow = "SETTINGS · 设置",
                inset = 0.dp
            )
        }
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
                        Text(
                            "正文字号", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
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
                    Spacer(Modifier.height(12.dp))
                    HairLine()
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        title = "记住读到哪里",
                        desc = "每篇各记各的，下次点进去接着读（关掉就每次都从开头）",
                        checked = prefs.rememberPos
                    ) { prefs.updateRememberPos(it) }
                }
            }

            /* ---------------- 导航 ---------------- */
            item { GroupTitle("导航") }
            item {
                SettingCard {
                    SwitchRow(
                        title = "进来先折叠",
                        desc = "只看到分类目录，点哪个展开哪个（默认全部展开）",
                        checked = prefs.navCollapsed
                    ) { prefs.updateNavCollapsed(it) }
                    Spacer(Modifier.height(8.dp))
                    HairLine()
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "链接在 App 内打开",
                        desc = "关掉就直接跳浏览器（要登录的站点建议开着）",
                        checked = prefs.linkInApp
                    ) { prefs.updateLinkInApp(it) }
                }
            }

            /* ---------------- 底部标签栏 ---------------- */
            item { GroupTitle("底部标签栏") }
            item {
                SettingCard {
                    SwitchRow(
                        title = "显示图标",
                        desc = "默认关：只留文字，底栏更矮",
                        checked = prefs.dockIcons
                    ) { prefs.updateDockIcons(it) }
                    Spacer(Modifier.height(10.dp))
                    HairLine()
                    Spacer(Modifier.height(8.dp))
                    Text("排序与显示", fontSize = 12.5.sp, color = LocalXyPal.current.mute)
                    Spacer(Modifier.height(2.dp))
                    TabOrderEditor(prefs)
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
                        options = listOf("首页", "分享", "文章", "导航", "设置", "收藏"),
                        selected = prefs.startTab,
                        compact = true
                    ) { prefs.updateStartTab(it) }
                }
            }

            /* ---------------- 内容更新 ---------------- */
            item { GroupTitle("内容更新") }
            item {
                SettingCard {
                    SwitchRow(
                        title = "打开时自动拉最新",
                        desc = "关掉之后只在手动点「立即同步」时联网",
                        checked = prefs.autoSync
                    ) { prefs.updateAutoSync(it) }
                    Spacer(Modifier.height(6.dp))
                    HairLine()
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

            /* ---------------- App 更新 ---------------- */
            item { GroupTitle("App 更新") }
            item {
                SettingCard {
                    SwitchRow(
                        title = "启动时检查新版",
                        desc = "发现新版会弹一次，点「稍后」这次就不打扰",
                        checked = prefs.autoCheckUpdate
                    ) { prefs.updateAutoCheckUpdate(it) }
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(
                        title = "只在 Wi-Fi 下下载",
                        desc = "一个安装包 1.2 MB，用流量时就先等着",
                        checked = prefs.updateWifiOnly
                    ) { prefs.updateUpdateWifiOnly(it) }
                    Spacer(Modifier.height(6.dp))
                    HairLine()
                    Spacer(Modifier.height(6.dp))
                    UpdateRow(
                        upd = vm.upd,
                        myVersion = vm.myVersion.ifBlank { version },
                        onCheck = { vm.checkUpdate(silent = false) },
                        onDownload = { vm.downloadUpdate() },
                        onInstall = { installUpdate() }
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

            /* ---------------- 本机数据 ---------------- */
            item { GroupTitle("本机数据") }
            item {
                SettingCard {
                    Text(
                        "只存在这台手机里，不上传、不同步。",
                        fontSize = 11.5.sp, color = LocalXyPal.current.mute
                    )
                    Spacer(Modifier.height(10.dp))
                    InfoRow("收藏 / 稍后读", "${local.fav.size} / ${local.later.size} 篇")
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("阅读记录", fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${local.posCount} 篇记着读到哪了",
                                fontSize = 11.sp, color = LocalXyPal.current.mute
                            )
                        }
                        TextButton(
                            onClick = {
                                local.clearPositions()
                                Toast.makeText(ctx, "已清空阅读记录", Toast.LENGTH_SHORT).show()
                            },
                            enabled = local.posCount > 0
                        ) { Text("清除", fontSize = 13.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("最近打开", fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${local.recents.size} 个站点",
                                fontSize = 11.sp, color = LocalXyPal.current.mute
                            )
                        }
                        TextButton(
                            onClick = {
                                local.clearRecents()
                                Toast.makeText(ctx, "已清空最近打开", Toast.LENGTH_SHORT).show()
                            },
                            enabled = local.recents.isNotEmpty()
                        ) { Text("清除", fontSize = 13.sp) }
                    }
                }
            }

            /* ---------------- 关于（原来的独立页，整块搬进来） ---------------- */
            item { GroupTitle("关于") }
            item {
                SettingCard {
                    if (!data?.site?.desc.isNullOrBlank()) {
                        Text(
                            data!!.site.desc,
                            fontSize = 13.sp, lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!data?.site?.since.isNullOrBlank()) {
                        Text(
                            data!!.site.since,
                            fontSize = 11.5.sp, color = LocalXyPal.current.mute
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    /* 版本号只在「App 更新」组里说一次，这里不再重复 */
                    InfoRow(
                        "数据来源",
                        "mackxin.github.io/xingyu",
                        onClick = { onOpen(data?.site?.url ?: "https://mackxin.github.io/xingyu/") }
                    )
                    InfoRow(
                        "开源仓库",
                        "github.com/Mackxin/yihaozhan-app",
                        onClick = { onOpen("https://github.com/Mackxin/yihaozhan-app") }
                    )
                    Spacer(Modifier.height(10.dp))
                    CardAction("在浏览器里打开网站") {
                        onOpen(data?.site?.url ?: "https://mackxin.github.io/xingyu/")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "内容每次打开自动更新，离线也能看。这里只读不写，改内容在站点后台。",
                        fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

/** 左边两行字、右边一个开关 —— 设置页里出现最多次的那种行 */
@Composable
private fun SwitchRow(
    title: String,
    desc: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    desc, fontSize = 11.5.sp, lineHeight = 17.sp,
                    color = LocalXyPal.current.mute
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
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

/**
 * 底栏按钮的排序与显隐：**长按一行就能拖**，拖到哪儿哪儿就是它的新位置。
 * 原来那对上下箭头一次只能挪一格，五个 tab 想把最后一个顶到最前要点八下。
 *
 * 手势用 `detectDragGesturesAfterLongPress`：长按超过阈值才接管，
 * 平时按下去不会跟外面 LazyColumn 的滚动打架（它会 consume，父级就收不到）。
 */
@Composable
private fun TabOrderEditor(prefs: Prefs) {
    val pal = LocalXyPal.current
    val rowH = 46.dp
    val step = with(LocalDensity.current) { rowH.toPx() }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragDy by remember { mutableStateOf(0f) }

    Column(Modifier.fillMaxWidth()) {
        prefs.tabsInOrder.forEach { id ->
            val dragging = dragId == id
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .zIndex(if (dragging) 2f else 0f)
                    .offset { IntOffset(0, if (dragging) dragDy.roundToInt() else 0) }
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (dragging) Modifier.background(pal.glassHi) else Modifier)
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragId = id
                                dragDy = 0f
                            },
                            onDragEnd = {
                                dragId = null
                                dragDy = 0f
                            },
                            onDragCancel = {
                                dragId = null
                                dragDy = 0f
                            }
                        ) { change, delta ->
                            change.consume()
                            dragDy += delta.y
                            val cur = prefs.tabsInOrder.indexOf(id)
                            if (dragDy > step * 0.6f && cur < prefs.tabsInOrder.lastIndex) {
                                prefs.moveTab(id, 1)
                                dragDy -= step
                            } else if (dragDy < -step * 0.6f && cur > 0) {
                                prefs.moveTab(id, -1)
                                dragDy += step
                            }
                        }
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("≡", fontSize = 16.sp, color = pal.mute)
                Spacer(Modifier.width(10.dp))
                Text(
                    tabLabel(id),
                    fontSize = 13.5.sp,
                    fontWeight = if (id in prefs.hiddenSet) FontWeight.Normal else FontWeight.Medium,
                    color = if (id in prefs.hiddenSet) pal.mute else pal.text,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = id !in prefs.hiddenSet,
                    enabled = prefs.canHide(id),
                    onCheckedChange = { prefs.setTabHidden(id, !it) }
                )
            }
        }
        Text(
            "长按一行拖动换位置；开关决定它露不露脸，至少留一个",
            fontSize = 11.sp,
            color = pal.mute,
            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
        )
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
        Text(
            label, fontSize = 13.sp, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/** 卡片最下面的那颗宽按钮，比如「在浏览器里打开网站」 */
@Composable
private fun CardAction(text: String, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pal.glassHi)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(
            Icons.Rounded.ArrowOutward, null,
            tint = pal.dim,
            modifier = Modifier.size(15.dp)
        )
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
