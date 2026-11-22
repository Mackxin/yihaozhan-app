package xyz.yihaozhan.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import xyz.yihaozhan.app.data.Todo
import xyz.yihaozhan.app.data.Todos

/**
 * 待办清单（v2.9）。
 *
 * 网页版 `#/todo` 的原生版，数据各存各的（网页在 `localStorage`，这里在
 * `SharedPreferences`），但字段和导出格式一样 —— 想搬就用「分享」按钮把 JSON 发出去，
 * 网页版「导入」能直接吃。
 *
 * 视觉上照站点那套：玻璃底、圆角、只在主操作上用渐变。自己的一点脾气是左边那条竖线 ——
 * 每条待办都串在一根线上，圆点就是勾选框，完成时圆点填满、文字退成灰并划一道。
 */
@Composable
fun TodoScreen(onBack: () -> Unit) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    val todos = remember { Todos.of(ctx) }

    var filter by remember { mutableStateOf("all") }
    var draft by remember { mutableStateOf("") }
    var draftPri by remember { mutableStateOf(0) }
    var draftDue by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Todo?>(null) }
    val listState = rememberLazyListState()

    /* 不自动聚焦输入框：一进来就弹键盘会把整个列表顶掉，看不到自己昨天写了什么。
       想加的时候点一下输入框就行 —— 跟网页版手机端一个取舍。 */

    val st = todos
    val groups = remember(st.items, filter) { buildGroups(st, filter) }

    Column(Modifier.fillMaxSize()) {
        /* 顶部：返回 + 标题 + 分享（导出一份 JSON 出去） */
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                "待办清单",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { shareText(ctx, todos.exportJson()) }) {
                Icon(Icons.Rounded.Share, "导出为 JSON", tint = pal.dim)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            /* ---------- 页头：标题 + 今日环 + 两个数 ---------- */
            item("head") {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Eyebrow("Todo · 待办")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "待办清单",
                        fontSize = 30.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = pal.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "散着的事，一件件归位。今天只挑要紧的几件做完就够 —— 全部存在这台手机里。",
                        fontSize = 15.sp, lineHeight = 25.sp, color = pal.dim,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(
                            done = st.todayDone,
                            total = st.todayAll.size,
                            size = 78.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        StatBox("${st.open.size}", "待办", Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        StatBox("${st.weekDone}", "本周完成", Modifier.weight(1f))
                    }
                }
            }

            /* ---------- 加一条 ---------- */
            item("add") {
                CardBox(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (draft.isEmpty()) {
                                Text(
                                    "加一件事…",
                                    fontSize = 15.sp, color = pal.mute,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            BasicTextField(
                                value = draft,
                                onValueChange = { if (it.length <= 200) draft = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, color = pal.text),
                                cursorBrush = SolidColor(pal.a1),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    commitAdd(todos, draft, draftDue, draftPri) { draft = "" }
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(pal.grad())
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { commitAdd(todos, draft, draftDue, draftPri) { draft = "" } }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text("记下", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = XY_INK)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    /* 优先级 + 日期：两排小胶囊 */
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PriChip("普通", 0, draftPri) { draftPri = 0 }
                        PriChip("重要", 1, draftPri) { draftPri = 1 }
                        PriChip("紧急", 2, draftPri) { draftPri = 2 }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DueChip("不设日期", "", draftDue) { draftDue = "" }
                        DueChip("今天", "today", draftDue) { draftDue = "today" }
                        DueChip("明天", "tomorrow", draftDue) { draftDue = "tomorrow" }
                    }
                }
            }

            /* ---------- 筛选 ---------- */
            item("filter") {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterPill("全部", "all", filter, st.open.size) { filter = it }
                        FilterPill("今天", "today", filter, st.todayOpen.size) { filter = it }
                        FilterPill("未完成", "open", filter, st.open.size) { filter = it }
                        FilterPill("已完成", "done", filter, st.doneList.size) { filter = it }
                    }
                }
            }

            /* ---------- 分组 ---------- */
            if (groups.isEmpty()) {
                item("empty") {
                    EmptyState("🗂", emptyTitle(filter), emptyDesc(filter))
                }
            } else {
                groups.forEach { g ->
                    /* 一组塞进一个 item：LazyColumn 的 spacedBy 会在每行之间留 8dp，
                       那条竖线就跟着被切成一截一截。放进同一个 Column 里，
                       行与行直接相邻，线才是连着的。 */
                    item("g-${g.key}") {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    g.title,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.1.em, color = pal.dim
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("${g.rows.size}", fontSize = 11.sp, color = pal.mute)
                                if (g.hint.isNotEmpty()) {
                                    Spacer(Modifier.weight(1f))
                                    Text(g.hint, fontSize = 11.5.sp, color = pal.mute)
                                }
                            }
                            val last = g.rows.lastIndex
                            g.rows.forEachIndexed { i, t ->
                                TodoRow(
                                    t = t,
                                    first = i == 0,
                                    last = i == last,
                                    onToggle = { todos.toggle(t.id) },
                                    onOpen = { editing = t }
                                )
                            }
                        }
                    }
                }
            }

            item("foot") {
                Text(
                    "存在这台手机上 · 换手机前点右上角把 JSON 分享出来，网页版「导入」能直接吃",
                    fontSize = 11.sp, lineHeight = 18.sp, color = pal.mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
                )
            }
        }
    }

    /* ---------- 修改对话框 ---------- */
    editing?.let { old ->
        EditDialog(
            todo = old,
            onDismiss = { editing = null },
            onSave = { text, due, pri ->
                todos.update(old.id, text = text, due = due, pri = pri)
                editing = null
            },
            onDelete = {
                todos.remove(old.id)
                editing = null
            }
        )
    }
}

/* ============================ 小组件 ============================ */

private class Group(val key: String, val title: String, val rows: List<Todo>, val hint: String = "")

private fun buildGroups(st: Todos, filter: String): List<Group> {
    fun g(key: String, title: String, rows: List<Todo>, hint: String = ""): Group? =
        if (rows.isEmpty()) null else Group(key, title, rows, hint)

    return when (filter) {
        "today" -> listOfNotNull(
            g("over", "已逾期", st.overdue, "先处理这些"),
            g("today", "今天", st.todayOpen)
        )
        "open" -> listOfNotNull(
            g("over", "已逾期", st.overdue),
            g("today", "今天", st.todayOpen),
            g("later", "以后", st.later),
            g("some", "未安排", st.someday)
        )
        "done" -> listOfNotNull(g("done", "已完成", st.doneList))
        else -> listOfNotNull(
            g("over", "已逾期", st.overdue, "别让它们一直挂着"),
            g("today", "今天", st.todayOpen),
            g("later", "以后", st.later),
            g("some", "未安排", st.someday),
            g("done", "已完成", st.doneList)
        )
    }
}

private fun emptyTitle(filter: String) = when (filter) {
    "today" -> "今天没有到期的事"
    "done" -> "还没有完成的"
    "open" -> "没有待办"
    else -> "清单还是空的"
}

private fun emptyDesc(filter: String) = when (filter) {
    "today" -> "给某件事点个日期，它就会出现在这里"
    "done" -> "勾掉第一件试试"
    "open" -> "全都做完了 —— 或者还没开始写"
    else -> "在上面写一件小事 —— 点「记下」就好"
}

private fun commitAdd(todos: Todos, text: String, dueSel: String, pri: Int, clear: () -> Unit) {
    val due = when (dueSel) {
        "today" -> Todos.today()
        "tomorrow" -> Todos.shift(1)
        else -> ""
    }
    if (todos.add(text, due, pri) != null) clear()
}

/** 今日环：底环一档、进度一段渐变，中间是百分比与「2 / 3] */
@Composable
private fun Ring(done: Int, total: Int, size: androidx.compose.ui.unit.Dp) {
    val pal = LocalXyPal.current
    val frac = if (total > 0) done.toFloat() / total else 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val r = (minOf(this.size.width, this.size.height) - stroke) / 2f
            drawCircle(color = pal.glass2, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            if (frac > 0f) {
                drawArc(
                    brush = Brush.linearGradient(listOf(pal.a1, pal.a2, pal.a3)),
                    startAngle = -90f,
                    sweepAngle = 360f * frac,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - r, center.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (total > 0) {
                Text(
                    "${(frac * 100).toInt()}%",
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    /* 浅色主题渐变文字压不住，退回压深那档强调色（跟 StatBox 一个理由） */
                    color = if (pal.dark) pal.a1 else pal.inkA1
                )
                Text(
                    "$done / $total",
                    fontSize = 9.5.sp, color = pal.mute
                )
            } else {
                Text("—", fontSize = 16.sp, color = pal.mute)
                Text("空着", fontSize = 9.sp, color = pal.mute)
            }
        }
    }
}

@Composable
private fun PriChip(label: String, pri: Int, cur: Int, onPick: () -> Unit) {
    val pal = LocalXyPal.current
    val on = cur == pri
    val dot = when (pri) { 1 -> pal.a2; 2 -> pal.star; else -> Color.Transparent }
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) pal.glassHi else pal.glass)
            .clickable { onPick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pri > 0) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(dot.copy(alpha = if (on) 1f else 0.45f)))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label, fontSize = 12.sp,
            color = if (on) pal.text else pal.mute
        )
    }
}

@Composable
private fun DueChip(label: String, key: String, cur: String, onPick: () -> Unit) {
    val pal = LocalXyPal.current
    val on = cur == key
    Text(
        label, fontSize = 12.sp,
        color = if (on) pal.text else pal.mute,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) pal.glassHi else pal.glass)
            .clickable { onPick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

@Composable
private fun FilterPill(label: String, key: String, cur: String, count: Int, onPick: (String) -> Unit) {
    val pal = LocalXyPal.current
    val on = cur == key
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(if (on) Modifier.background(pal.grad()) else Modifier.background(pal.glass))
            .clickable { onPick(key) }
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) XY_INK else pal.mute
        )
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                "$count", fontSize = 11.sp,
                color = if (on) XY_INK.copy(alpha = 0.7f) else pal.mute
            )
        }
    }
}

/**
 * 一条待办。
 *
 * 左边那根线是这一页的「记号」：每行自己画一段竖线，首行从圆心起、末行到圆心止，
 * 拼起来就是贯穿整组的一条。`height(IntrinsicSize.Min)` 是为了让竖线容器能
 * `fillMaxHeight` 到这一行的高度 —— 不然它只知道自己 30dp 宽。
 */
@Composable
private fun TodoRow(
    t: Todo,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    val pal = LocalXyPal.current
    val n = if (t.due.isEmpty()) null else Todos.dayDiff(t.due)
    val overdue = !t.done && n != null && n < 0

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onOpen() },
        verticalAlignment = Alignment.Top
    ) {
        /* 左轨：竖线 + 圆点 */
        Box(Modifier.width(30.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val dotCy = 12.dp.toPx()
                val top = if (first) dotCy else 0f
                val bottom = if (last) dotCy else this.size.height
                if (bottom > top) {
                    /* 实色而不是竖向渐变：一段一段拼起来的线，每段各带渐隐的话，
                       行与行之间会看到一道道"断口"，像没连上 */
                    drawLine(
                        color = pal.a1.copy(alpha = if (pal.dark) .38f else .55f),
                        start = Offset(cx, top),
                        end = Offset(cx, bottom),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(
                        if (t.done) Modifier.background(pal.grad())
                        else Modifier
                            .background(pal.bg)
                            .border(
                                1.5.dp,
                                if (overdue) pal.star else pal.a1.copy(alpha = .55f),
                                CircleShape
                            )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (t.done) {
                    Icon(Icons.Rounded.Check, "已完成", tint = XY_INK, modifier = Modifier.size(13.dp))
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                t.text,
                fontSize = 14.5.sp, lineHeight = 22.sp,
                color = if (t.done) pal.mute else pal.text,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            val bits = buildList {
                if (t.due.isNotEmpty()) add(Todos.dueLabel(t.due))
                if (t.pri > 0) add(if (t.pri == 2) "紧急" else "重要")
                if (t.done) add(Todos.doneLabel(t.doneAt))
            }
            if (bits.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    bits.forEachIndexed { i, s ->
                        if (i > 0) {
                            Box(
                                Modifier.padding(horizontal = 6.dp).size(3.dp)
                                    .clip(CircleShape).background(pal.mute.copy(alpha = .5f))
                            )
                        }
                        Text(
                            s, fontSize = 11.5.sp,
                            color = when {
                                i == 0 && overdue -> pal.star
                                else -> pal.mute
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 修改一条：文字 + 优先级 + 日期 + 删除。手机上没有「双击改名」，点整行进这里 */
@Composable
private fun EditDialog(
    todo: Todo,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
    onDelete: () -> Unit
) {
    val pal = LocalXyPal.current
    var text by remember { mutableStateOf(todo.text) }
    var pri by remember { mutableStateOf(todo.pri) }
    var due by remember { mutableStateOf(todo.due) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(pal.bgSoft)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("改这一条", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = pal.text)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Close, "关闭", tint = pal.mute, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 200) text = it },
                textStyle = TextStyle(fontSize = 15.sp, color = pal.text, lineHeight = 24.sp),
                cursorBrush = SolidColor(pal.a1),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.glass)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriChip("普通", 0, pri) { pri = 0 }
                PriChip("重要", 1, pri) { pri = 1 }
                PriChip("紧急", 2, pri) { pri = 2 }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DueChip("不设日期", "", due) { due = "" }
                DueChip("今天", Todos.today(), due) { due = Todos.today() }
                DueChip("明天", Todos.shift(1), due) { due = Todos.shift(1) }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.glass)
                        .clickable { onDelete() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("删除", fontSize = 13.5.sp, color = pal.star, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.grad())
                        .clickable { onSave(text, due, pri) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", fontSize = 13.5.sp, color = XY_INK, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
