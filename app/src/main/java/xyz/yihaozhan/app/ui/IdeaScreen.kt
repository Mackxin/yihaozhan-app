package xyz.yihaozhan.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * 站点那页纯本机存：写在 localStorage，换浏览器就没了、也不上传。
 * App 里照这个来 —— 数据落在 [Local]（SharedPreferences），不跟着 app.json 走，
 * 每次「同步内容」都不会把它冲掉。
 *
 * 跟网页版对齐的：`#标签` 自动归类、置顶、就地编辑、每张卡四颗操作、四格统计。
 * 没搬过来的：导出 JSON / .md、导入（那是浏览器下载文件流的玩法），
 * 手机上换成「复制这一条」。
 */
@Composable
fun IdeaScreen(local: Local, onBack: () -> Unit) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var draft by rememberSaveable { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var askClear by remember { mutableStateOf(false) }

    val all = local.sortedIdeas
    /* 标签从正文里现抠：改正文标签就跟着变，不用另外存一份 */
    val tags = remember(all) {
        all.flatMap { ideaTags(it.text) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
    }
    val shown = remember(all, tag) {
        if (tag.isBlank()) all else all.filter { tag in ideaTags(it.text) }
    }
    val listState = rememberLazyListState()

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
                    onText = { draft = it },
                    onSave = {
                        if (local.addIdea(draft)) {
                            draft = ""
                            keyboard?.hide()
                        }
                    }
                )
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
                    }
                }
            }

            if (shown.isEmpty()) {
                item("empty") {
                    if (all.isEmpty()) {
                        EmptyState("💡", "还没有随记", "想到什么就写一句，上面那颗「记下来」按下去就有了。")
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
                    onDelete = { local.removeIdea(one.id) }
                )
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            confirmButton = {
                TextButton(onClick = { local.clearIdeas(); tag = ""; askClear = false }) {
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
    /* 不到一千字就直接报数，「0 千字」看着像算错了 */
    val (charText, charUnit) = remember(chars) {
        if (chars < 1000) chars.toString() to "字" else fmtChars(chars)
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Eyebrow("IDEA · 随记") }
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
            "想到什么就写一句。#标签 会自动归类 —— 全都存在这台手机里，不上传。",
            fontSize = 15.sp,
            lineHeight = 25.sp,
            color = pal.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        /* 站点那页是 2×2 四格：条灵感 / 个标签 / 本周 / 字 */
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
                modifier = Modifier.fillMaxWidth().height(84.dp),
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
    onDelete: () -> Unit
) {
    val pal = LocalXyPal.current
    /* 正文里的 #标签 先抠掉再渲染，不然行首 #设计 会被当成标题 */
    val body = remember(idea.text) { idea.text.replace(TAG_RE, "").trim() }
    val tags = remember(idea.text) { ideaTags(idea.text) }

    CardBox(padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
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
                    modifier = Modifier.fillMaxWidth().height(96.dp)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "取消",
                    fontSize = 13.sp,
                    color = pal.mute,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCancelEdit() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    "保存",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.inkA1,
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
                CardAction(Icons.Rounded.ContentCopy, "复制", false, onCopy)
                CardAction(Icons.Rounded.Edit, "编辑", false, onStartEdit)
                CardAction(Icons.Rounded.Delete, "删除", false, onDelete)
            }
        }
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
