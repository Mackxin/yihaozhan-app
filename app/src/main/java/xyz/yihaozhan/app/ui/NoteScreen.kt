package xyz.yihaozhan.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.yihaozhan.app.data.Note
import xyz.yihaozhan.app.data.Notes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 随手记（原生版，对应站点 `#/note`）。
 *
 * 2.7.7 起不再拿 WebView 打开站点那一页 —— 走原生之后没有网页顶栏、没有滚动条、
 * 字体跟全站一致，返回键只退这一页（再按才退 App），跟「本地音乐」「摘录本」一个待遇。
 *
 * 数据在 [Notes]（SharedPreferences），字段跟网页版同形，网页导出/App 导出可以互换。
 * 网页版时代攒下的笔记由 `Notes.migrateFromWeb()` 搬一次，用户不会看到空列表。
 *
 * 三种形态，同一个 Composable 里切：
 *  ① 列表：搜索 + 标签筛选 + 置顶在前；② 编辑器：Markdown 编辑 / 预览；③ 回收站：恢复或彻底删。
 */
@Composable
fun NoteScreen(notes: Notes, onBack: () -> Unit) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current

    /* null = 在列表上；非 null = 正在编这一条 */
    var editingId by remember { mutableStateOf<String?>(null) }
    /* 0 = 笔记，1 = 回收站 */
    var seg by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var tagF by remember { mutableStateOf("") }
    var askClearTrash by remember { mutableStateOf(false) }
    var askBackup by remember { mutableStateOf(false) }

    val active = notes.active
    val trash = notes.trash
    val allTags = notes.allTags

    val shown = remember(active, q, tagF) {
        val kw = q.trim().lowercase()
        active.filter { n ->
            (tagF.isBlank() || n.tags.contains(tagF)) &&
                (kw.isBlank() || (n.title + "\n" + n.content + "\n" + n.tags.joinToString(" "))
                    .lowercase().contains(kw))
        }
    }

    /* ---- 备份：走系统文件选择器，一个权限都不用申请 ---- */
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val json = notes.exportJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(
                ctx,
                if (ok) "已导出 ${notes.notes.size} 条（${json.length / 1024} KB）" else "导出失败，换个位置再试试",
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
            val n = if (text == null) -1 else withContext(Dispatchers.IO) { notes.importJson(text) }
            Toast.makeText(
                ctx,
                when {
                    n < 0 -> "这个文件读不了，确认是随手记导出的 JSON"
                    n == 0 -> "都是已有的，没有新增"
                    else -> "导入 $n 条"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /* 编辑器叠在列表上：进编辑器时列表不重组（state 都留着，退出回到原来的滚动位置） */
    val id = editingId
    if (id != null && notes.byId(id) != null) {
        NoteEditor(notes = notes, noteId = id, onClose = { editingId = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        /* 顶栏：返回 / 标题 / 新建 / 更多 */
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                "随手记",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { editingId = notes.create().id }) {
                Icon(Icons.Rounded.Add, "新写一条", tint = pal.text)
            }
            IconButton(onClick = { askBackup = true }) {
                Icon(Icons.Rounded.MoreVert, "更多", tint = pal.mute)
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("head") { NoteHead(notes) }

            /* 笔记 / 回收站 */
            item("seg") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeadPill("笔记 ${active.size}", seg == 0) { seg = 0 }
                        HeadPill(
                            if (trash.isEmpty()) "回收站" else "回收站 ${trash.size}",
                            seg == 1
                        ) { seg = 1 }
                    }
                    /* 「清空回收站」不能塞进上面那条滚动 Row（滚动容器里用 weight 会算不出宽度），
                       单独摆一行、居中，反而更好点 */
                    if (seg == 1 && trash.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), Alignment.Center) {
                            Text(
                                "清空回收站",
                                fontSize = 12.5.sp,
                                color = pal.mute,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { askClearTrash = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            if (seg == 0) {
                item("search") {
                    SearchField(
                        value = q,
                        onValueChange = { q = it },
                        placeholder = "搜标题 / 正文 / 标签"
                    )
                }

                if (allTags.isNotEmpty()) {
                    item("tags") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TagChip("全部", onClick = { tagF = "" }, active = tagF.isBlank())
                            allTags.forEach { t ->
                                TagChip(
                                    text = "#$t",
                                    onClick = { tagF = if (tagF == t) "" else t },
                                    active = tagF == t
                                )
                            }
                        }
                    }
                }

                if (shown.isEmpty()) {
                    item("empty") {
                        if (active.isEmpty()) {
                            EmptyState("📝", "还没有随手记", "右上角那颗 ＋ 按一下就开写。支持 Markdown，全在本机，不上传。")
                        } else if (q.isNotBlank()) {
                            EmptyState("🔍", "没搜到「$q」", "换个词试试，或者把搜索清掉。")
                        } else {
                            EmptyState("🍃", "这个标签下还没有", "换个标签，或者把筛选点回「全部」。")
                        }
                    }
                }

                items(shown, key = { it.id }) { one ->
                    NoteCard(
                        note = one,
                        onOpen = { editingId = one.id },
                        onPin = { notes.togglePin(one.id) },
                        onCopy = {
                            copyText(ctx, one.content)
                            Toast.makeText(ctx, "正文已复制", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            if (one.content.isBlank()) {
                                Toast.makeText(ctx, "这条是空的", Toast.LENGTH_SHORT).show()
                            } else {
                                shareText(ctx, one.content)
                            }
                        },
                        onDelete = {
                            notes.toTrash(one.id)
                            Toast.makeText(ctx, "已移进回收站", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else {
                if (trash.isEmpty()) {
                    item("emptyTrash") {
                        EmptyState("🗑", "回收站是空的", "删掉的笔记会先来这儿，随时能捞回来。")
                    }
                }
                items(trash, key = { it.id }) { one ->
                    TrashCard(
                        note = one,
                        onRestore = {
                            notes.restore(one.id)
                            Toast.makeText(ctx, "已恢复", Toast.LENGTH_SHORT).show()
                        },
                        onPurge = { notes.purge(one.id) }
                    )
                }
            }
        }
    }

    if (askClearTrash) {
        AlertDialog(
            onDismissRequest = { askClearTrash = false },
            confirmButton = {
                TextButton(onClick = { notes.clearTrash(); askClearTrash = false }) {
                    Text("全清掉", color = pal.inkA1)
                }
            },
            dismissButton = {
                TextButton(onClick = { askClearTrash = false }) { Text("算了", color = pal.mute) }
            },
            title = { Text("清空回收站？", color = pal.text) },
            text = {
                Text(
                    "一共 ${trash.size} 条，清了就真没了。",
                    color = pal.dim,
                    fontSize = 13.sp
                )
            },
            containerColor = pal.bgSoft
        )
    }

    if (askBackup) {
        AlertDialog(
            onDismissRequest = { askBackup = false },
            confirmButton = {
                TextButton(onClick = {
                    askBackup = false
                    val name = "随手记_" +
                        SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date()) + ".json"
                    exportLauncher.launch(name)
                }) { Text("导出备份", color = pal.inkA1) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askBackup = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("导入备份", color = pal.mute) }
            },
            title = { Text("备份", color = pal.text) },
            text = {
                Text(
                    "导出成一份 JSON 存到手机或网盘；导入时按 id 去重，同一条不会重复。\n" +
                        "网页版「随手记」导出的备份也能直接导进来。",
                    color = pal.dim,
                    fontSize = 13.sp
                )
            },
            containerColor = pal.bgSoft
        )
    }
}

/* ------------------------------ 页头 ------------------------------ */

@Composable
private fun NoteHead(notes: Notes) {
    val pal = LocalXyPal.current
    val total = notes.active.size
    val tagCount = remember(notes.notes) { notes.allTags.size }
    val chars = notes.totalChars
    val (charText, charUnit) = remember(chars) {
        if (chars < 1000) chars.toString() to "字" else fmtChars(chars)
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Eyebrow("Note · 随手记") }
        Spacer(Modifier.height(14.dp))
        Text(
            "随手记",
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = pal.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "想到什么就写下来，长一点也没关系。支持 Markdown，可打标签、可置顶 —— 全在这台手机里，不上传。",
            fontSize = 15.sp,
            lineHeight = 25.sp,
            color = pal.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        StatRow {
            StatBox("$total", "条笔记", Modifier.weight(1f))
            StatBox("$tagCount", "个标签", Modifier.weight(1f))
            StatBox("${notes.thisWeek}", "本周", Modifier.weight(1f))
            StatBox(charText, charUnit, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/* ------------------------------ 列表里的一条 ------------------------------ */

@Composable
private fun NoteCard(
    note: Note,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val pal = LocalXyPal.current
    var menu by remember { mutableStateOf(false) }
    val snippet = remember(note.content, note.title) { Notes.snippetOf(note.content, note.title) }

    CardBox(
        padding = PaddingValues(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        onClick = onOpen
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { "未命名笔记" },
                    fontSize = 15.5.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (snippet.isNotBlank() && snippet != note.title) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        snippet,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = pal.dim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (note.pinned) {
                Text("📌", fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp, top = 3.dp))
            }
        }

        if (note.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                note.tags.forEach { t -> MiniTag("#$t") }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Notes.dayText(note.updatedAt),
                fontSize = 11.5.sp,
                color = pal.mute,
                modifier = Modifier.weight(1f)
            )
            CardAction(Icons.Rounded.PushPin, if (note.pinned) "取消置顶" else "置顶", note.pinned, onPin)
            CardAction(Icons.Rounded.MoreVert, "更多", menu) { menu = true }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            confirmButton = {
                TextButton(onClick = { menu = false; onDelete() }) {
                    Text("移到回收站", color = pal.inkA1)
                }
            },
            dismissButton = {
                TextButton(onClick = { menu = false }) { Text("取消", color = pal.mute) }
            },
            title = {
                Text(
                    note.title.ifBlank { "未命名笔记" },
                    color = pal.text,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MenuRow("复制正文") { menu = false; onCopy() }
                    MenuRow("分享出去") { menu = false; onShare() }
                }
            },
            containerColor = pal.bgSoft
        )
    }
}

@Composable
private fun TrashCard(note: Note, onRestore: () -> Unit, onPurge: () -> Unit) {
    val pal = LocalXyPal.current
    var ask by remember { mutableStateOf(false) }
    val snippet = remember(note.content, note.title) { Notes.snippetOf(note.content, note.title) }

    CardBox(padding = PaddingValues(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
        Text(
            note.title.ifBlank { "未命名笔记" },
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (snippet.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                snippet,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                color = pal.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "删于 " + Notes.dayText(note.deletedAt ?: 0L),
                fontSize = 11.5.sp,
                color = pal.mute,
                modifier = Modifier.weight(1f)
            )
            TinyBtn("恢复", strong = true, onClick = onRestore)
            TinyBtn("彻底删掉") { ask = true }
        }
    }

    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            confirmButton = {
                TextButton(onClick = { ask = false; onPurge() }) {
                    Text("删掉", color = pal.inkA1)
                }
            },
            dismissButton = {
                TextButton(onClick = { ask = false }) { Text("算了", color = pal.mute) }
            },
            title = { Text("彻底删掉这条？", color = pal.text) },
            text = { Text("删了就找不回来了。", color = pal.dim, fontSize = 13.sp) },
            containerColor = pal.bgSoft
        )
    }
}

/* ------------------------------ 编辑器 ------------------------------ */

/**
 * 一条笔记的全屏编辑器。
 *
 * 边写边存（停手 700ms 落一次盘），退出时再冲一遍 —— 手机上随时可能被系统杀掉，
 * 只有「点保存才算数」的编辑器丢过东西的人不会再信任第二次。
 * 新写的一条如果什么都没填，退出时自动丢掉，不在列表里留一行「空白笔记」。
 */
@Composable
private fun NoteEditor(notes: Notes, noteId: String, onClose: () -> Unit) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val start = remember(noteId) { notes.byId(noteId) }
    if (start == null) {
        LaunchedEffect(noteId) { onClose() }
        return
    }

    /* 打开时就空的那条：退出时允许自动丢掉 */
    val fresh = remember(noteId) {
        start.content.isBlank() && start.tags.isEmpty() && !start.customTitle
    }

    var body by remember(noteId) { mutableStateOf(start.content) }
    /* 标题留空 = 跟着正文第一行走（跟网页版、跟 [Notes.save] 一个口径） */
    var titleDraft by remember(noteId) { mutableStateOf(if (start.customTitle) start.title else "") }
    var tags by remember(noteId) { mutableStateOf(start.tags) }
    var preview by remember(noteId) { mutableStateOf(start.content.isNotBlank()) }
    var menu by remember { mutableStateOf(false) }
    var askDel by remember { mutableStateOf(false) }
    var tagAsk by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }

    /* 停手 700ms 落一次盘。key 里带上 tags：改标签也要存 */
    LaunchedEffect(body, tags) {
        delay(700)
        notes.save(noteId, body, tags)
    }

    /** 退出前把没落盘的东西冲掉 */
    fun flush() {
        notes.save(noteId, body, tags)
        val t = titleDraft.trim()
        if (t.isBlank()) {
            if (notes.byId(noteId)?.customTitle == true) notes.rename(noteId, "")
        } else {
            notes.rename(noteId, t)
        }
        if (fresh && body.isBlank() && tags.isEmpty() && t.isBlank()) notes.dropIfEmpty(noteId)
    }

    /* 返回键：先关编辑器（存盘），不是直接退整个页面 */
    BackHandler {
        flush()
        onClose()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { keyboard?.hide(); flush(); onClose() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                if (preview) "预览" else "编辑",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                keyboard?.hide()
                preview = !preview
            }) {
                Icon(
                    Icons.Rounded.Visibility,
                    if (preview) "继续写" else "预览",
                    tint = if (preview) pal.inkA1 else pal.mute
                )
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "更多", tint = pal.mute)
            }
        }

        if (preview) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 28.dp)
            ) {
                Text(
                    (if (titleDraft.isBlank()) Notes.autoTitleOf(body) else titleDraft),
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = pal.text
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "改于 " + Notes.dayText(System.currentTimeMillis()) +
                        " · ${Notes.plainChars(body)} 字",
                    fontSize = 11.5.sp,
                    color = pal.mute
                )
                Spacer(Modifier.height(16.dp))
                if (body.isBlank()) {
                    EmptyState("🪶", "还是空的", "点右上角那只眼睛回去写。")
                } else {
                    CardBox {
                        MarkdownView(body)
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                    .padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 28.dp)
            ) {
                /* 标题：留空就自动跟着正文第一行走 */
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(pal.glass)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal.text
                        ),
                        cursorBrush = SolidColor(pal.inkA1),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (titleDraft.isBlank()) {
                                Text(
                                    "标题（留空就取正文第一行）",
                                    fontSize = 15.sp,
                                    color = pal.mute
                                )
                            }
                            inner()
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tags.forEach { t ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(pal.glassHi)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { tags = tags - t }
                                .padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)
                        ) {
                            Text("#$t  ×", fontSize = 12.sp, color = pal.dim, maxLines = 1)
                        }
                    }
                    TinyBtn("＋ 标签") {
                        tagDraft = ""
                        tagAsk = true
                    }
                }

                /* 正文 */
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(pal.glass)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = body,
                        onValueChange = { body = it },
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 26.sp,
                            color = pal.text
                        ),
                        cursorBrush = SolidColor(pal.inkA1),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
                        decorationBox = { inner ->
                            if (body.isBlank()) {
                                Text(
                                    "写点什么…\n\n# 标题\n**加粗**、- 列表、> 引用都认得",
                                    fontSize = 15.sp,
                                    lineHeight = 26.sp,
                                    color = pal.mute
                                )
                            }
                            inner()
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Notes.plainChars(body)} 字 · " +
                            Notes.dayText(notes.byId(noteId)?.updatedAt ?: start.updatedAt) + " 存过",
                        fontSize = 11.5.sp,
                        color = pal.mute,
                        modifier = Modifier.weight(1f)
                    )
                    TinyBtn("复制") {
                        copyText(ctx, body)
                        Toast.makeText(ctx, "正文已复制", Toast.LENGTH_SHORT).show()
                    }
                    TinyBtn("分享") { if (body.isNotBlank()) shareText(ctx, body) }
                }
            }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            confirmButton = {
                TextButton(onClick = { menu = false }) { Text("关闭", color = pal.mute) }
            },
            title = { Text("这一条", color = pal.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MenuRow(
                        if (notes.byId(noteId)?.pinned == true) "取消置顶" else "置顶"
                    ) { menu = false; notes.togglePin(noteId) }
                    MenuRow("预览排版") { menu = false; preview = true }
                    MenuRow("移到回收站") { menu = false; askDel = true }
                }
            },
            containerColor = pal.bgSoft
        )
    }

    if (askDel) {
        AlertDialog(
            onDismissRequest = { askDel = false },
            confirmButton = {
                TextButton(onClick = {
                    askDel = false
                    /* 先存再删：删的是这一份内容，不是上次落盘的那份 */
                    notes.save(noteId, body, tags)
                    notes.toTrash(noteId)
                    onClose()
                }) { Text("移到回收站", color = pal.inkA1) }
            },
            dismissButton = {
                TextButton(onClick = { askDel = false }) { Text("算了", color = pal.mute) }
            },
            title = { Text("删掉这条？", color = pal.text) },
            text = { Text("会先进回收站，随时能捞回来。", color = pal.dim, fontSize = 13.sp) },
            containerColor = pal.bgSoft
        )
    }

    if (tagAsk) {
        AlertDialog(
            onDismissRequest = { tagAsk = false },
            confirmButton = {
                TextButton(onClick = {
                    val t = tagDraft.trim().removePrefix("#").trim()
                    if (t.isNotEmpty() && !tags.contains(t)) tags = tags + t.take(20)
                    tagAsk = false
                }) { Text("加上", color = pal.inkA1) }
            },
            dismissButton = {
                TextButton(onClick = { tagAsk = false }) { Text("取消", color = pal.mute) }
            },
            title = { Text("加个标签", color = pal.text) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.glassHi)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = pal.text),
                        cursorBrush = SolidColor(pal.inkA1),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (tagDraft.isBlank()) {
                                Text("比如：想法 / 读书 / 待办", fontSize = 13.sp, color = pal.mute)
                            }
                            inner()
                        }
                    )
                }
            },
            containerColor = pal.bgSoft
        )
    }
}

/* ------------------------------ 小零件 ------------------------------ */

/** 对话框里的一行可点动作 */
@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Text(
        text,
        fontSize = 14.sp,
        color = pal.dim,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp)
    )
}

/** 卡片右下角那颗小图标钮（置顶 / 更多） */
@Composable
private fun CardAction(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (active) pal.inkA1 else pal.mute, modifier = Modifier.size(16.dp))
    }
}

/** 卡片里那颗小小的文字钮 */
@Composable
private fun TinyBtn(text: String, strong: Boolean = false, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (strong) pal.glassHi else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = if (strong) pal.inkA1 else pal.dim)
    }
}

/** 标签胶囊（列表卡片上那个更小一档的） */
@Composable
private fun MiniTag(text: String) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glassHi)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.5.sp, color = pal.dim, maxLines = 1)
    }
}
