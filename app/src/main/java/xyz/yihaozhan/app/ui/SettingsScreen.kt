package xyz.yihaozhan.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.font.FontFamily
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
fun SettingsScreen(
    prefs: Prefs,
    vm: MainVm,
    onOpen: (String) -> Unit,
    local: Local,
    /* 顶栏那颗「壹号栈」在这一页也要能做「到底 / 回顶」 */
    listState: LazyListState = rememberLazyListState(),
    /* 两份说明书：使用手册 / 版本更新。都是整屏内置页，由 MainActivity 管开关 */
    onHelp: () -> Unit = {},
    onLog: () -> Unit = {},
    /* 摘录本（2.6.7）：阅读页长按存下来的段落 */
    onExtracts: () -> Unit = {},
    /* 站点巡检（2.6.7）：抽查站点还通不通 */
    onCheck: () -> Unit = {}
) {
    val ui = vm.ui
    val data = ui.data
    val ctx = LocalContext.current
    var cacheSize by remember(data, ui.lastSync) { mutableStateOf(Repo.cacheSize(ctx)) }
    /* 自定义数据源：改之前把当前值抄进 draft，取消就丢掉 */
    var editSrc by remember { mutableStateOf(false) }
    var srcDraft by remember { mutableStateOf("") }
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "1.2"
    }

    /* ---- 备份 / 恢复：走系统文件选择器（SAF），一个存储权限都不用申请 ----
       导出自己挑存哪（下载目录、网盘都行），导入自己挑那份 JSON。 */
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val json = withContext(Dispatchers.IO) { local.exportAllJson(prefs) }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(
                ctx,
                if (ok) "备份已导出（" + (json.length / 1024) + " KB）" else "导出失败，换个位置再试试",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            val n = if (text == null) -1
            else withContext(Dispatchers.IO) {
                runCatching { local.importAllJson(text, prefs) }.getOrDefault(-1)
            }
            Toast.makeText(
                ctx,
                when {
                    n < 0 -> "这个文件读不了，确认是壹号栈导出的备份"
                    n == 0 -> "没有可恢复的新内容"
                    else -> "已恢复 $n 项 · 设置项重开 App 后生效"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /* 装更新：系统没给「安装未知应用」权限就先跳去开关页，别让用户对着「没反应」发呆 */
    val installUpdate: () -> Unit = {
        val apk = vm.upd.apk
        if (apk != null) {
            if (UpdateRepo.canInstall(ctx)) UpdateRepo.install(ctx, apk)
            else UpdateRepo.openInstallSetting(ctx)
        }
    }

    /* 数据源编辑框：填自己的镜像（国内虚拟主机），留空就回内置的 GitHub。
       放在 LazyColumn 外面 —— 塞进 item 里的话，列表一滚这一项被回收，框就跟着没了。 */
    if (editSrc) {
        AlertDialog(
            onDismissRequest = { editSrc = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("数据源", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "留空 = 只用内置的 GitHub 源。\n" +
                            "填了自己的镜像（比如 https://你的域名），App 会先问它，成功后就不再等 GitHub —— " +
                            "国内直连 GitHub 常常要十几秒。\n" +
                            "只填域名也行，会自动补成 …/api/app.json。",
                        fontSize = 12.5.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = srcDraft,
                        onValueChange = { srcDraft = it.trim() },
                        singleLine = true,
                        placeholder = { Text("https://你的域名", fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.updateDataMirror(srcDraft)
                    editSrc = false
                    vm.refresh()
                }) { Text("保存并立即同步") }
            },
            dismissButton = {
                TextButton(onClick = { editSrc = false }) { Text("取消") }
            }
        )
    }

    /* 页头整块当列表第一个 item，往下滑跟着走，不钉在顶上（跟分享/文章/导航一致） */
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "行距", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            prefs.lineName, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Segmented(options = listOf("紧凑", "标准", "宽松"), selected = prefs.readLine) {
                        prefs.updateReadLine(it)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "正文字体", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            prefs.fontName, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf("无衬线", "衬线"),
                        selected = if (prefs.readFont == "serif") 1 else 0
                    ) { i -> prefs.updateReadFont(if (i == 1) "serif" else "sans") }
                    Spacer(Modifier.height(12.dp))
                    /* 现成的一句话当预览：字号、行距、字体改了这里立刻能看出来 */
                    val pSize = (14 * prefs.fontScale).sp
                    Text(
                        "文章正文会长这样：壹号为栈，随处可归。",
                        fontSize = pSize,
                        lineHeight = (14 * prefs.fontScale * prefs.lineHeightFactor).sp,
                        fontFamily = if (prefs.readFont == "serif") FontFamily.Serif else FontFamily.SansSerif,
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
                    Spacer(Modifier.height(12.dp))
                    HairLine()
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        title = "阅读页显示底栏",
                        desc = "打开后进文章也留着底部 Tab 栏，方便随手切页签；关掉就是整屏看正文",
                        checked = prefs.dockInReader
                    ) { prefs.updateDockInReader(it) }
                }
            }

            /* ---------------- 列表 ---------------- */
            item { GroupTitle("列表") }
            item {
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "卡片密度", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            prefs.densityName, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "紧凑会把导航和分享的卡片压薄一点，一屏多看好几条。",
                        fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Segmented(options = listOf("紧凑", "标准"), selected = prefs.cardDensity) {
                        prefs.updateCardDensity(it)
                    }
                }
            }

            /* ---------------- 首页板块 ---------------- */
            item { GroupTitle("首页板块") }
            item {
                SettingCard {
                    Text(
                        "首页从上到下按这个顺序排，关掉的就整块不出现。",
                        fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    val keys = prefs.homeBlockList
                    /* 全部板块都列出来（关掉的也在），开关状态就看它在不在列表里 */
                    Prefs.HOME_BLOCKS.forEach { (key, label) ->
                        val shown = keys.contains(key)
                        val idx = keys.indexOf(key)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    label, fontSize = 13.5.sp,
                                    fontWeight = if (shown) FontWeight.Medium else FontWeight.Normal,
                                    color = if (shown) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            /* 上下挪顺序：只对显示中的项有意义 */
                            if (shown) {
                                Text(
                                    "↑", fontSize = 15.sp,
                                    color = if (idx > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = idx > 0) { prefs.moveHomeBlock(key, -1) }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                                Text(
                                    "↓", fontSize = 15.sp,
                                    color = if (idx in 0 until keys.size - 1)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = idx in 0 until keys.size - 1) {
                                            prefs.moveHomeBlock(key, 1)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Switch(
                                checked = shown,
                                onCheckedChange = { prefs.toggleHomeBlock(key) }
                            )
                        }
                    }
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
                            (data?.let { " · 数据版本 " + it.build } ?: "") +
                            (if (ui.source.isNotBlank()) " · 来自 " + ui.source else ""),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    HairLine()
                    Spacer(Modifier.height(6.dp))
                    /* 数据源：内置是 GitHub Pages；国内直连慢的话，填自己的镜像让它排第一 */
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                srcDraft = prefs.dataMirror
                                editSrc = true
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("数据源", fontSize = 14.sp)
                            Text(
                                if (prefs.dataMirror.isBlank())
                                    "GitHub（内置，不用管）"
                                else "自定义镜像：" + prefs.dataMirror,
                                fontSize = 11.5.sp,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "修改",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                            Text("收藏的站点", fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${local.favLinks.size} 个（导航页长按站点加）",
                                fontSize = 11.sp, color = LocalXyPal.current.mute
                            )
                        }
                        TextButton(
                            onClick = {
                                local.clearFavLinks()
                                Toast.makeText(ctx, "已清空站点收藏", Toast.LENGTH_SHORT).show()
                            },
                            enabled = local.favLinks.isNotEmpty()
                        ) { Text("清除", fontSize = 13.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("失效标记", fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${local.dead.size} 个站点标着打不开",
                                fontSize = 11.sp, color = LocalXyPal.current.mute
                            )
                        }
                        TextButton(
                            onClick = {
                                local.clearDead()
                                Toast.makeText(ctx, "已全部恢复正常", Toast.LENGTH_SHORT).show()
                            },
                            enabled = local.dead.isNotEmpty()
                        ) { Text("恢复", fontSize = 13.sp) }
                    }
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
                            Text("最常去 / 每日计数", fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${local.visits.size} 个站点有次数 · ${local.daily.size} 天有记录",
                                fontSize = 11.sp, color = LocalXyPal.current.mute
                            )
                        }
                        TextButton(
                            onClick = {
                                local.clearVisits()
                                local.clearDaily()
                                Toast.makeText(ctx, "足迹统计已清空", Toast.LENGTH_SHORT).show()
                            },
                            enabled = local.visits.isNotEmpty() || local.daily.isNotEmpty()
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

            /* ---------------- 我的内容 ----------------
               摘录本：阅读页长按任意一段存下来的。放在备份上面 ——
               同属「我自己攒的东西」，攒多了才轮到下面那组「备份/恢复」。 */
            item { GroupTitle("我的内容") }
            item {
                SettingCard {
                    InfoRow(
                        "摘录本",
                        local.extracts.size.toString() + " 段 · 看/删/转随记 →",
                        onClick = onExtracts
                    )
                    InfoRow(
                        "站点巡检",
                        "抽查打不开的站 →",
                        onClick = onCheck
                    )
                }
            }

            /* ---------------- 备份与恢复 ----------------
               这一组是「换手机」唯一的保险：上面那些数据一个都没有账号、不上云。 */
            item { GroupTitle("备份与恢复") }
            item {
                SettingCard {
                    Text(
                        "收藏、稍后读、灵感随记、摘录、待整理、足迹、阅读进度和设置，打成一份 JSON。" +
                            "换手机或重装之前导一份出去，丢网盘就行。",
                        fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = LocalXyPal.current.mute
                    )
                    Spacer(Modifier.height(8.dp))
                    InfoRow("导出备份", "挑个地方存 →", onClick = {
                        exportLauncher.launch(
                            "yihaozhan-backup-" + Local.dayKey(System.currentTimeMillis()) + ".json"
                        )
                    })
                    InfoRow("从备份恢复", "合并进去，不覆盖现有 →", onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    })
                }
            }

            /* ---------------- 帮助（使用手册 / 版本更新） ----------------
               两份都是整屏内置页，内容来自站点仓库的 data/help.md 与 data/changelog.json，
               所以这里不写正文，只放入口 —— 改文案不用发版。
               数据里没有就直接不画这一组，不留一个点进去空白的按钮。 */
            val hasHelp = !data?.help.isNullOrBlank()
            val logs = data?.changelog.orEmpty()
            if (hasHelp || logs.isNotEmpty()) {
                item { GroupTitle("帮助") }
                item {
                    SettingCard {
                        if (hasHelp) {
                            InfoRow("使用手册", "怎么用 →", onClick = onHelp)
                        }
                        if (logs.isNotEmpty()) {
                            InfoRow(
                                "版本更新",
                                "共 " + logs.size + " 版 · 最新 " + (logs.firstOrNull()?.version ?: ""),
                                onClick = onLog
                            )
                        }
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
    val pal = LocalXyPal.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(pal.rMd.dp))
            .background(pal.glass)
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
                if (!prefs.canHide(id)) {
                    /* 「设置」是回后台的唯一入口，藏了就再也改不回来 —— 干脆标成常驻 */
                    Text(
                        if (id == Prefs.SETTINGS_TAB) "常驻" else "至少留一个",
                        fontSize = 10.5.sp,
                        color = pal.mute,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Switch(
                    checked = id !in prefs.hiddenSet,
                    enabled = prefs.canHide(id),
                    onCheckedChange = { prefs.setTabHidden(id, !it) }
                )
            }
        }
        Text(
            "长按一行拖动换位置；开关决定它露不露脸。「设置」常驻（藏了就再也进不来），其余至少留一个",
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
