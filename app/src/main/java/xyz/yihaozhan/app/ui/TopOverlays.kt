package xyz.yihaozhan.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.Cat
import xyz.yihaozhan.app.data.Post
import xyz.yihaozhan.app.data.Py

/* ============================ 全站搜索 ============================ */

/** 一条搜索结果 */
private data class Hit(
    val kind: String,
    val icon: String,
    val name: String,
    val sub: String,
    val go: () -> Unit
)

/** 一组结果 */
private data class HitGroup(
    val title: String,
    val hits: List<Hit>,
    val moreLabel: String? = null,
    val onMore: (() -> Unit)? = null
)

/**
 * 全站搜索层：对应站点顶栏那个下拉结果浮层（`.hdr-hits`），
 * 只是手机上整屏铺开 —— 键盘一出来，下拉浮层只剩一条缝。
 *
 * 分组跟站点一样：页面 / 分类 / 站点 / 分享 / 文章，每组先给几条，凑不下的走「看全部」，
 * 点进去就是那一页带着同一串关键词筛选后的样子（关键词是同一条 state）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    data: AppPayload,
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onCategory: (Cat) -> Unit,
    onTab: (Int) -> Unit
) {
    val pal = LocalXyPal.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    /* 进来就聚焦：不用再点一次输入框 */
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }

    /* 应用内整屏浮层，不再另开窗口（理由见 MainActivity 里包住 Scaffold 的那个 Box） */
    BackHandler(enabled = true) { onClose() }
    Box(Modifier.fillMaxSize().background(pal.bg).enterFade()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(pal.glass)
                            .padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Search, null,
                            tint = pal.mute, modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    "搜全站：站点、分类、分享、文章…",
                                    fontSize = 14.sp, color = pal.mute,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQuery,
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 14.sp, color = pal.text
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focus)
                            )
                        }
                        if (query.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(pal.glassHi)
                                    .clickable { onQuery("") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Close, "清空",
                                    tint = pal.mute, modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "取消",
                        fontSize = 14.sp,
                        color = pal.inkA1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }

                val groups = remember(query, data) {
                    buildGroups(data, query, onOpenUrl, onOpenPost, onCategory, onTab, onClose)
                }

                if (query.isBlank()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔎", fontSize = 30.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "输入一个字就能搜",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal.text
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "站点、分类、分享、文章，一次全搜到。",
                            fontSize = 12.5.sp, color = pal.mute
                        )
                    }
                } else if (groups.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🍃", fontSize = 30.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "没搜到「$query」",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal.text
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("换个词试试，或者少打两个字。", fontSize = 12.5.sp, color = pal.mute)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)
                    ) {
                        groups.forEach { g ->
                            item(key = "g-" + g.title) {
                                Text(
                                    g.title,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.1.em,
                                    color = pal.mute,
                                    modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(g.hits) { h ->
                                HitRow(h, query) { h.go() }
                            }
                            if (g.moreLabel != null && g.onMore != null) {
                                item(key = "m-" + g.title) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { g.onMore.invoke() }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            g.moreLabel,
                                            fontSize = 12.5.sp,
                                            color = pal.dim
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun HitRow(hit: Hit, query: String, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0x297C9CFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(hit.icon.ifBlank { "•" }, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlight(hit.name, query, pal.inkA1),
                fontSize = 13.5.sp,
                color = pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hit.sub.isNotBlank()) {
                Text(
                    hit.sub,
                    fontSize = 11.5.sp,
                    color = pal.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(hit.kind, fontSize = 11.sp, color = pal.mute)
    }
}

/** 命中那截染成强调色（站点 `.hdr-hits__name mark`），一眼看出为什么被搜出来 */
private fun highlight(text: String, q: String, accent: Color): AnnotatedString {
    val n = q.trim()
    if (n.isEmpty()) return AnnotatedString(text)
    val i = text.lowercase().indexOf(n.lowercase())
    if (i < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, i))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
            append(text.substring(i, i + n.length))
        }
        append(text.substring(i + n.length))
    }
}

/** 分组：结果条数与站点一致，先给几条、剩下的走「看全部」 */
private fun buildGroups(
    data: AppPayload,
    q: String,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onCategory: (Cat) -> Unit,
    onTab: (Int) -> Unit,
    onClose: () -> Unit
): List<HitGroup> {
    val n = q.trim().lowercase()
    if (n.isEmpty()) return emptyList()
    fun has(s: String) = s.lowercase().contains(n)

    val out = mutableListOf<HitGroup>()

    /* 一组装完就完了：超出条数时自动挂一条「看全部 N 个」，点过去就是那一页带着同一个关键词 */
    fun add(title: String, unit: String, total: Int, tab: Int, hits: List<Hit>) {
        if (hits.isEmpty()) return
        val moreLabel: String? = if (total > hits.size) "看全部 $total $unit" else null
        val onMore: (() -> Unit)? =
            if (moreLabel != null) ({ onClose(); onTab(tab) }) else null
        out += HitGroup(title, hits, moreLabel, onMore)
    }

    /* 页面：App 自己的六个页签 */
    val pages = listOf(
        Triple(0, "首页", "看看今天更新了什么"),
        Triple(3, "导航", "两千多个站点，按分类收好"),
        Triple(1, "分享", "每天一小条"),
        Triple(2, "文章", "写得长一点的东西"),
        Triple(5, "收藏", "标了星的、留着待会儿看的"),
        Triple(4, "设置", "外观、阅读、底栏、更新")
    ).filter { has(it.second) || has(it.third) }.take(3)
    add(
        "页面", "个页面", pages.size, 3,
        pages.map { (tab, name, sub) ->
            Hit("页面", "🧭", name, sub) { onClose(); onTab(tab) }
        }
    )

    /* 分类 / 站点两组走 Py.hit：除了普通包含，还能吃拼音首字母 ——
       跟导航页那个搜索框一个口径，否则搜「zh」这里 0 条、点进去一堆，很割裂 */
    val cats = data.cats.filter { Py.hit(n, it.name, it.desc) }
    add(
        "分类", "个分类", cats.size, 3,
        cats.take(4).map { c ->
            Hit("分类", c.icon.ifBlank { "🗂" }, c.name, "${c.links.size} 个站点") {
                onClose(); onCategory(c)
            }
        }
    )

    val sites = data.cats.flatMap { c -> c.links.map { c to it } }
        .filter { (c, l) -> Py.hit(n, l.name, l.desc + " " + l.url + " " + c.name) }
    add(
        "站点", "个站点", sites.size, 3,
        sites.take(4).map { (c, l) ->
            Hit("站点", "🔗", l.name, c.name + " · " + hostOf(l.url)) { onClose(); onOpenUrl(l.url) }
        }
    )

    val feeds = data.feed.filter {
        has(it.title) || has(it.desc) || it.tags.any { t -> has(t) }
    }
    add(
        "分享", "条分享", feeds.size, 1,
        feeds.take(3).map { f ->
            Hit("分享", f.icon.ifBlank { "✨" }, f.title, f.date + " · " + f.desc) {
                onClose()
                if (f.url.isNotBlank()) onOpenUrl(f.url) else onTab(1)
            }
        }
    )

    /* 正文也进搜索：以前只搜标题/摘要/标签，长文章里的关键词一个都搜不到 ——
       搜「Compose」本该搜出那篇讲 Compose 的，结果什么都没有，看着像丢了数据。
       4 篇长文总共三千来字，本机扫一遍是零成本。 */
    val posts = data.posts.filter {
        has(it.title) || has(it.excerpt) || has(it.body) || it.tags.any { t -> has(t) }
    }
    add(
        "文章", "篇文章", posts.size, 2,
        posts.take(3).map { p ->
            Hit("文章", "📄", p.title, p.date + " · " + p.tags.joinToString(" ")) {
                onClose(); onOpenPost(p)
            }
        }
    )

    return out
}

/* ============================ 分类 / 标签选择 ============================ */

data class PickItem(val id: String, val emoji: String, val label: String, val count: Int = -1)

/**
 * 分类 / 标签选择面板：导航页的「全部分类」、分享 / 文章页的「全部标签」都走这一个。
 *
 * 2.6.6 改：整屏铺开，格子行高**反着算** —— 先看还剩多少地方，再拿它除以行数，
 * 五十个分类十七行也能一屏装完，一个都不用滚（小宝要的是「找分类别让我滚」）。
 * 分类再多就自动加列（3 → 9），先保证每格还看得清；真多到装不下（几百个标签）
 * 才退回可滚的网格，那种情况加列也救不回来。
 */
@Composable
fun PickerSheet(
    title: String,
    allLabel: String,
    items: List<PickItem>,
    current: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    /* 分类名后面的站点数：站点后台「显示项 → 分类显示站点数」说了算 */
    showCount: Boolean = true
) {
    val pal = LocalXyPal.current
    /* 分类有五十来个，翻着找太慢 —— 加个筛框（网页端分类 ≥9 时也是这么干的）。
       打开面板就是一次新的组合，所以关键词不用手动清，关掉自动没了。 */
    var fq by remember { mutableStateOf("") }
    val showFilter = items.size >= 9
    val shown = if (fq.isBlank()) items else items.filter { it.label.contains(fq.trim(), true) }
    /* 「全部」那一格钉在最前面；正在筛的时候不显示（它是「取消筛选」的意思，跟筛框会打架） */
    val cells = if (fq.isBlank()) listOf(PickItem("", "", allLabel, -1)) + shown else shown

    /* 应用内整屏浮层，不再另开窗口（理由见 MainActivity 里包住 Scaffold 的那个 Box）。
       键盘避让靠下面那两个 windowInsetsPadding —— Scaffold 外面那层 Box 已经
       让内容铺到系统栏底下了，所以这里只要把状态栏 / 导航栏 / 键盘的占位补上即可。 */
    BackHandler(enabled = true) { onClose() }
    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bg)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
            )
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = pal.text,
                modifier = Modifier.weight(1f)
            )
            Text(
                "完成",
                fontSize = 13.sp,
                color = pal.inkA1,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        if (showFilter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(pal.glass)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search, null,
                    tint = pal.mute, modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (fq.isEmpty()) {
                        Text(
                            if (title.contains("标签")) "筛标签…" else "筛分类…",
                            fontSize = 12.5.sp, color = pal.mute,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = fq,
                        onValueChange = { fq = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 12.5.sp, color = pal.text
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (fq.isNotEmpty()) {
                    Text(
                        "✕",
                        fontSize = 12.sp,
                        color = pal.mute,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable { fq = "" }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (cells.isEmpty()) {
            Text(
                "没有叫「" + fq.trim() + "」的" + (if (title.contains("标签")) "标签" else "分类"),
                fontSize = 13.sp,
                color = pal.mute,
                modifier = Modifier.padding(vertical = 18.dp)
            )
            return@Column
        }

        /* 剩下多少地方就摊多少行：先按 3 列算行高，太矮就加一列再算，
           直到每格至少 30dp；实在装不下（列已经加到 9）才允许滚。 */
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val gap = 6.dp
            val minCell = 30.dp
            val total = cells.size
            var cols = 3
            var cellH = minCell
            var fits = false
            for (c in 3..9) {
                val rows = (total + c - 1) / c
                val h = (maxHeight - gap * (rows - 1)) / rows
                if (h >= minCell) {
                    cols = c
                    /* 上限 52dp：只有一两个结果时别撑成半屏高的巨型块 */
                    cellH = h.coerceAtMost(52.dp)
                    fits = true
                    break
                }
            }
            if (!fits) {
                cols = 9
                cellH = minCell
            }
            val rows = (total + cols - 1) / cols
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll, enabled = !fits),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                for (r in 0 until rows) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        for (i in 0 until cols) {
                            val idx = r * cols + i
                            if (idx < total) {
                                val p = cells[idx]
                                val isAll = p.id.isEmpty()
                                PickCell(
                                    label = p.label,
                                    emoji = p.emoji,
                                    active = if (isAll) current.isBlank() else current == p.id,
                                    showCount = showCount && !isAll,
                                    count = p.count,
                                    height = cellH,
                                    modifier = Modifier.weight(1f)
                                ) { onPick(p.id) }
                            } else {
                                /* 最后一行不够铺满时补空位，否则剩下的格子会被拉宽 */
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* 网格里的一格：左边名字（长了省略），右边站点数。
   跟站点那排 `.cat-item` 一样的排法（名字 flex:1、数字靠右），两边看着才是一套。
   行高由面板按剩余高度算好传进来，这里不写死。 */
@Composable
private fun PickCell(
    label: String,
    emoji: String,
    active: Boolean,
    showCount: Boolean,
    count: Int,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(pal.rSm.dp))
            .then(
                if (active) Modifier.background(pal.grad())
                else Modifier.background(pal.glass)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                (if (emoji.isNotBlank()) emoji + " " else "") + label,
                fontSize = 12.5.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) XY_INK else pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showCount && count >= 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "$count",
                    fontSize = 10.sp,
                    color = if (active) XY_INK else pal.mute,
                    maxLines = 1
                )
            }
        }
    }
}

/* ============================ 卡片长按菜单 ============================ */

/** 长按卡片弹出来的一项动作 */
data class CardAction(
    val emoji: String,
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 长按站点 / 分享卡片弹出来的底部动作条。
 *
 * 站点那边是右键菜单（手机长按同一套），App 里做成底部面板：拇指够得着、
 * 条目再多也不怕被卡片边缘裁掉。样式跟筛选面板一个模子（26 圆角 + 玻璃底 + 淡入浮起）。
 */
@Composable
fun CardMenu(
    title: String,
    sub: String,
    actions: List<CardAction>,
    onClose: () -> Unit
) {
    val pal = LocalXyPal.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(pal.bgSoft)
                    .navigationBarsPadding()
                    .enterPop()
                    .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 14.dp)
            ) {
                Text(
                    title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        sub, fontSize = 11.5.sp, color = pal.mute,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                actions.forEach { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onClose(); a.onClick() }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(a.emoji, fontSize = 15.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            a.label, fontSize = 14.sp,
                            color = if (a.danger) pal.inkA2 else pal.text
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(pal.glass)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClose() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", fontSize = 13.5.sp, color = pal.dim)
                }
            }
        }
    }
}
