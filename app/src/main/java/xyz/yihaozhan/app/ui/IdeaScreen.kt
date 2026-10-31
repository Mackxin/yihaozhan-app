package xyz.yihaozhan.app.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.Idea
import xyz.yihaozhan.app.data.Local
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 灵感随记（原生版，对应站点 `#/idea`）。
 *
 * 跟电脑版对齐后的功能：站内搜索、🎲 随机回顾、标签改名/删除、草稿缓存（切页不丢）、
 * 按条存图、导出/导入 JSON 与 .md、Markdown 正文（走 App 自带的 Markdown.kt）。
 * 数据落在 [Local]（SharedPreferences），不跟着 app.json 走，每次「同步内容」都不会冲掉。
 */
@Composable
fun IdeaScreen(local: Local, onBack: () -> Unit) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(local.ideaDraft) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var askClear by remember { mutableStateOf(false) }
    var showTagManage by remember { mutableStateOf(false) }
    var renamingTag by remember { mutableStateOf<String?>(null) }
    var deletingTag by remember { mutableStateOf<String?>(null) }
    var askExport by remember { mutableStateOf(false) }
    var flashId by remember { mutableStateOf<String?>(null) }

    val all = local.sortedIdeas
    val tags = remember(all) {
        all.flatMap { ideaTags(it.text) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
    }
    val shown = remember(all, tag, q) { ideaFiltered(all, tag, q) }
    val listState = rememberLazyListState()
    val cardBase = 3 + if (tags.isNotEmpty()) 1 else 0

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull() ?: ""
        val added = if (text.isBlank()) -1 else local.importIdeasJson(text)
        Toast.makeText(
            ctx,
            when {
                added < 0 -> "这不是灵感备份（要 JSON）"
                added == 0 -> "都是已有的，没有新增"
                else -> "导入 $added 条"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    /* 随机回顾：从当前结果里抽一条，滑到它并闪一下 */
    fun doRandom() {
        if (shown.isEmpty()) {
            Toast.makeText(ctx, "这里没有可回顾的灵感", Toast.LENGTH_SHORT).show()
            return
        }
        val pick = shown.random()
        val idx = shown.indexOf(pick)
        scope.launch { listState.animateScrollToItem(cardBase + idx) }
        flashId = pick.id
    }

    LaunchedEffect(flashId) {
        if (flashId != null) {
            kotlinx.coroutines.delay(1600)
            flashId = null
        }
    }

    fun exportAndShare(kind: String) {
        if (local.ideas.isEmpty()) {
            Toast.makeText(ctx, "还没有可以导出的灵感", Toast.LENGTH_SHORT).show()
            return
        }
        val text = if (kind == "json") local.exportIdeasJson() else local.exportIdeasMd()
        val name = "灵感随记_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date()) +
                if (kind == "json") ".json" else ".md"
        val file = writeCacheText(ctx, name, text)
        if (file == null) {
            Toast.makeText(ctx, "导出失败，存储不可用", Toast.LENGTH_SHORT).show()
            return
        }
        shareFile(
            ctx, file,
            if (kind == "json") "application/json" else "text/markdown",
            "分享灵感备份"
        )
    }

    Column(Modifier.fillMaxSize()) {
        /* 顶栏：返回 / 标题 / 清空 */
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                "灵感随记",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
            if (all.isNotEmpty()) {
                IconButton(onClick = { askClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, "清空", tint = pal.mute)
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            /* 页头整块跟着列表滚（跟分享/文章/导航一致，不钉在顶上） */
            item("head") { IdeaHead(all) }

            item("input") {
                InputCard(
                    text = draft,
                    onText = {
                        draft = it
                        local.ideaDraft = it
                    },
                    onSave = {
                        if (local.addIdea(draft)) {
                            draft = ""
                            local.ideaDraft = ""
                            keyboard?.hide()
                        }
                    }
                )
            }

            /* 工具条：搜索 + 随机回顾 + 导出/导入 */
            item("toolbar") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(pal.glassHi)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = pal.mute, modifier = Modifier.size(18.dp))
                        BasicTextField(
                            value = q,
                            onValueChange = { q = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = pal.text),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            decorationBox = { inner ->
                                if (q.isEmpty()) {
                                    Text("搜正文 / 标签 / 日期", fontSize = 13.sp, color = pal.mute)
                                }
                                inner()
                            }
                        )
                        if (q.isNotEmpty()) {
                            IconButton(onClick = { q = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Rounded.Close, "清掉搜索", tint = pal.mute, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        LinkBtn(Icons.Rounded.Shuffle, "随机回顾") { doRandom() }
                        Spacer(Modifier.weight(1f))
                        LinkBtn(null, "导出") { askExport = true }
                        Spacer(Modifier.width(6.dp))
                        LinkBtn(Icons.Rounded.Upload, "导入") { importLauncher.launch("application/json") }
                    }
                }
            }

            if (tags.isNotEmpty()) {
                item("tags") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeadPill("全部 ${all.size}", tag.isBlank()) { tag = "" }
                        tags.forEach { (name, n) ->
                            HeadPill("#$name $n", tag == name) { tag = name }
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showTagManage = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("⚙ 标签", fontSize = 12.5.sp, color = pal.dim)
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item("empty") {
                    if (all.isEmpty()) {
                        EmptyState("💡", "还没有随记", "想到什么就写一句，上面那颗「记下来」按下去就有了。")
                    } else if (q.isNotBlank()) {
                        EmptyState("🔍", "没搜到「$q」", "换个词试试，或者把搜索清掉。")
                    } else {
                        EmptyState("🍃", "这个标签下还没有", "换个标签，或者把筛选点回「全部」。")
                    }
                }
            }

            items(shown, key = { it.id }) { one ->
                IdeaCard(
                    idea = one,
                    editing = editingId == one.id,
                    editText = editText,
                    onEditText = { editText = it },
                    onStartEdit = {
                        editingId = one.id
                        editText = one.text
                    },
                    onCancelEdit = { editingId = null },
                    onSaveEdit = { if (local.updateIdea(one.id, editText)) editingId = null },
                    onPin = { local.togglePinIdea(one.id) },
                    onCopy = { shareText(ctx, one.text) },
                    onSaveImage = {
                        val c = IdeaImgColors(
                            bg = pal.bg.toArgb(),
                            text = pal.text.toArgb(),
                            dim = pal.dim.toArgb(),
                            mute = pal.mute.toArgb(),
                            ink = pal.inkA1.toArgb(),
                            a1 = 0x7C9CFF.toInt(),
                            a2 = 0xB47CFF.toInt(),
                            a3 = 0x4FE3D0.toInt()
                        )
                        shareIdeaImage(ctx, one, c)
                    },
                    onDelete = { local.removeIdea(one.id) },
                    flash = flashId == one.id
                )
            }
        }
    }

    /* 导出格式二选一：JSON（能再导回来）/ Markdown（方便贴别处） */
    if (askExport) {
        AlertDialog(
            onDismissRequest = { askExport = false },
            confirmButton = {
                Row {
                    TextButton(onClick = { askExport = false; exportAndShare("json") }) {
                        Text("JSON", color = pal.inkA1)
                    }
                    TextButton(onClick = { askExport = false; exportAndShare("md") }) {
                        Text("Markdown", color = pal.inkA1)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { askExport = false }) { Text("取消", color = pal.mute) }
            },
            title = { Text("导出成哪种格式？", color = pal.text) },
            text = { Text("JSON 可以再导回 App；Markdown 方便贴到别处。", color = pal.dim) }
        )
    }

    /* 清空确认 */
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            confirmButton = {
                TextButton(onClick = { local.clearIdeas(); tag = ""; q = ""; askClear = false }) {
                    Text("全清掉", color = pal.inkA1)
                }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text("算了", color = pal.mute) }
            },
            title = { Text("清空全部随记？", color = pal.text) },
            text = { Text("一共 ${all.size} 条，清了就找不回来了。", color = pal.dim, fontSize = 13.sp) },
            containerColor = pal.bgSoft
        )
    }

    /* 标签管理：列出全部标签，逐个改名 / 删除 */
    if (showTagManage) {
        AlertDialog(
            onDismissRequest = { showTagManage = false },
            confirmButton = {
                TextButton(onClick = { showTagManage = false }) { Text("完成", color = pal.inkA1) }
            },
            title = { Text("管理标签", color = pal.text) },
            text = {
                if (tags.isEmpty()) {
                    Text("还没有标签，写的时候带上 #话题 就行。", color = pal.dim, fontSize = 13.sp)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        tags.forEach { (name, n) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#$name", fontSize = 14.sp, color = pal.text, modifier = Modifier.weight(1f))
                                Text("$n 条", fontSize = 12.sp, color = pal.mute)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "改名", fontSize = 13.sp, color = pal.inkA1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { renamingTag = name; showTagManage = false }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "删除", fontSize = 13.sp, color = pal.mute,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { deletingTag = name; showTagManage = false }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = pal.bgSoft
        )
    }

    /* 标签改名 */
    if (renamingTag != null) {
        var newName by remember(renamingTag) { mutableStateOf(renamingTag!!) }
        AlertDialog(
            onDismissRequest = { renamingTag = null },
            confirmButton = {
                TextButton(onClick = {
                    val t = newName.trim()
                    if (t.isNotBlank() && t != renamingTag) local.renameIdeaTag(renamingTag!!, t)
                    renamingTag = null
                }) { Text("确定", color = pal.inkA1) }
            },
            dismissButton = { TextButton(onClick = { renamingTag = null }) { Text("取消", color = pal.mute) } },
            title = { Text("重命名 #${renamingTag}", color = pal.text) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = pal.text),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            containerColor = pal.bgSoft
        )
    }

    /* 标签删除确认 */
    if (deletingTag != null) {
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            confirmButton = {
                TextButton(onClick = { local.deleteIdeaTag(deletingTag!!); deletingTag = null }) {
                    Text("删除", color = pal.inkA1)
                }
            },
            dismissButton = { TextButton(onClick = { deletingTag = null }) { Text("取消", color = pal.mute) } },
            title = { Text("删除标签 #${deletingTag}", color = pal.text) },
            text = { Text("会把它从所有灵感里去掉（正文里的 #xxx 删掉），不能撤销。", color = pal.dim, fontSize = 13.sp) },
            containerColor = pal.bgSoft
        )
    }
}

/* ------------------------------ 页头 ------------------------------ */

@Composable
private fun IdeaHead(all: List<Idea>) {
    val pal = LocalXyPal.current
    val tagCount = remember(all) { all.flatMap { ideaTags(it.text) }.toSet().size }
    val weekCount = remember(all) {
        val cut = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        all.count { it.ts >= cut }
    }
    val chars = remember(all) { all.sumOf { it.text.count { c -> !c.isWhitespace() } } }
    val (charText, charUnit) = remember(chars) {
        if (chars < 1000) chars.toString() to "字" else fmtChars(chars)
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Eyebrow("Idea · 随记") }
        Spacer(Modifier.height(14.dp))
        Text(
            "灵感随记",
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = pal.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "想到什么就写一句。#标签 会自动归类，支持 Markdown —— 全都存在这台手机里，不上传。",
            fontSize = 15.sp,
            lineHeight = 25.sp,
            color = pal.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        StatRow {
            StatBox("${all.size}", "条灵感", Modifier.weight(1f))
            StatBox("$tagCount", "个标签", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        StatRow {
            StatBox("$weekCount", "本周", Modifier.weight(1f))
            StatBox(charText, charUnit, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/* ------------------------------ 输入卡 ------------------------------ */

@Composable
private fun InputCard(text: String, onText: (String) -> Unit, onSave: () -> Unit) {
    val pal = LocalXyPal.current
    CardBox(padding = PaddingValues(14.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(pal.glassHi)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = pal.text),
                cursorBrush = SolidColor(pal.inkA1),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .onPreviewKeyEvent { ev ->
                        /* 外接键盘：Ctrl/⌘ + Enter 直接记下来 */
                        val nk = ev.nativeKeyEvent
                        if (nk.action == AndroidKeyEvent.ACTION_DOWN &&
                            (nk.isCtrlPressed || nk.isMetaPressed) &&
                            nk.keyCode == AndroidKeyEvent.KEYCODE_ENTER
                        ) {
                            onSave(); true
                        } else false
                    },
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            "想到什么就记一句…\n#标签 会自动归类",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = pal.mute
                        )
                    }
                    inner()
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(pal.grad())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSave() },
            contentAlignment = Alignment.Center
        ) {
            Text("记下来", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = XY_INK)
        }
    }
}

/* ------------------------------ 一张随记 ------------------------------ */

@Composable
private fun IdeaCard(
    idea: Idea,
    editing: Boolean,
    editText: String,
    onEditText: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onSaveImage: () -> Unit,
    onDelete: () -> Unit,
    flash: Boolean = false
) {
    val pal = LocalXyPal.current
    val body = remember(idea.text) { idea.text.replace(TAG_RE, "").trim() }
    val tags = remember(idea.text) { ideaTags(idea.text) }

    CardBox(
        padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        tint = if (flash) pal.inkA1.copy(alpha = 0.12f) else null
    ) {
        if (editing) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.glassHi)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = editText,
                    onValueChange = onEditText,
                    textStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = pal.text),
                    cursorBrush = SolidColor(pal.inkA1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .onPreviewKeyEvent { ev ->
                            val nk = ev.nativeKeyEvent
                            if (nk.action != AndroidKeyEvent.ACTION_DOWN) false
                            else if ((nk.isCtrlPressed || nk.isMetaPressed) &&
                                nk.keyCode == AndroidKeyEvent.KEYCODE_ENTER
                            ) {
                                onSaveEdit(); true
                            } else if (nk.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                                onCancelEdit(); true
                            } else false
                        }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "取消", fontSize = 13.sp, color = pal.mute,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCancelEdit() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    "保存", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.inkA1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSaveEdit() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else {
            Text(
                inlineMd(body),
                fontSize = 14.5.sp,
                lineHeight = 24.sp,
                color = pal.text
            )
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t -> MiniTag("#$t") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (idea.pinned) {
                    Text("📌", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    fmtDay(idea.ts),
                    fontSize = 11.5.sp,
                    color = pal.mute,
                    modifier = Modifier.weight(1f)
                )
                CardAction(Icons.Rounded.PushPin, if (idea.pinned) "取消置顶" else "置顶", idea.pinned, onPin)
                CardAction(Icons.Rounded.Image, "存图", false, onSaveImage)
                CardAction(Icons.Rounded.ContentCopy, "复制", false, onCopy)
                CardAction(Icons.Rounded.Edit, "编辑", false, onStartEdit)
                CardAction(Icons.Rounded.Delete, "删除", false, onDelete)
            }
        }
    }
}

@Composable
private fun LinkBtn(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = pal.dim, modifier = Modifier.size(15.dp))
        if (icon != null) Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 13.sp, color = pal.dim)
    }
}

@Composable
private fun CardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (active) pal.inkA1 else pal.mute, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun MiniTag(text: String) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glassHi)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.5.sp, color = pal.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------ 工具 ------------------------------ */

/** 正文里的 `#标签`：井号开头、非空白非井号，最多 20 个字 */
private val TAG_RE = Regex("(?<![^\\s])#[^\\s#]{1,20}")

private fun ideaTags(text: String): List<String> =
    TAG_RE.findAll(text).map { it.value.drop(1) }.distinct().toList()

/** 站内搜索：正文 / 标签 / 显示出来的时间都算，空格分开的多个词要全部命中 */
private fun ideaMatch(it: Idea, q: String): Boolean {
    val s = q.trim().lowercase()
    if (s.isBlank()) return true
    val hay = (it.text + " " + ideaTags(it.text).joinToString(" ") + " " + fmtDay(it.ts)).lowercase()
    return s.split(Regex("\\s+")).all { hay.contains(it) }
}

private fun ideaFiltered(list: List<Idea>, tag: String, q: String): List<Idea> =
    list.filter { (tag.isBlank() || tag == "all" || ideaTags(it.text).contains(tag)) && ideaMatch(it, q) }

private fun fmtDay(ts: Long): String {
    if (ts <= 0L) return ""
    val d = Date(ts)
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()
    cal.time = d
    val sameYear = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val sameDay = sameYear && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> "今天 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(d)
        sameYear -> SimpleDateFormat("M 月 d 日", Locale.CHINA).format(d)
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(d)
    }
}
