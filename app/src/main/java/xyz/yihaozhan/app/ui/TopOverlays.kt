package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.Cat
import xyz.yihaozhan.app.data.Post

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

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(pal.bg)) {
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

    val cats = data.cats.filter { has(it.name) || has(it.desc) }
    add(
        "分类", "个分类", cats.size, 3,
        cats.take(4).map { c ->
            Hit("分类", c.icon.ifBlank { "🗂" }, c.name, "${c.links.size} 个站点") {
                onClose(); onCategory(c)
            }
        }
    )

    val sites = data.cats.flatMap { c -> c.links.map { c to it } }
        .filter { (c, l) -> has(l.name) || has(l.desc) || has(l.url) || has(c.name) }
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

    val posts = data.posts.filter {
        has(it.title) || has(it.excerpt) || it.tags.any { t -> has(t) }
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
 * 底部选择面板：导航页的「全部分类」、分享/文章页的「全部标签」都走这一个。
 * 站点在手机上也是底部抽屉（`.cat-sheet`），只是它是双列网格，这里用流式胶囊 —— 分类名长短差得远，胶囊更省地方。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickerSheet(
    title: String,
    allLabel: String,
    items: List<PickItem>,
    current: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    val pal = LocalXyPal.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            /* 点空白处关掉 */
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
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pal.text,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "完成",
                        fontSize = 13.sp,
                        color = pal.inkA1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                val scroll = rememberScrollState()
                /* 分类有五十来个，滚到边界时最后一行会被切一半 —— 底部压一层渐隐把切口糊掉，
                   滚到底（没有更多）就不压，免得最后一行看着发白 */
                val more = scroll.maxValue - scroll.value > 0
                Box(Modifier.heightIn(max = 420.dp)) {
                    Column(Modifier.verticalScroll(scroll)) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PickChip(allLabel, current.isBlank()) { onPick("") }
                            items.forEach { p ->
                                PickChip(
                                    (if (p.emoji.isNotBlank()) p.emoji + " " else "") + p.label +
                                        (if (p.count >= 0) " ${p.count}" else ""),
                                    current == p.id
                                ) { onPick(p.id) }
                            }
                        }
                    }
                    if (more) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, pal.bgSoft)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickChip(label: String, active: Boolean, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(
                if (active) Modifier.background(pal.grad())
                else Modifier.background(pal.glass)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) XY_INK else pal.dim,
            maxLines = 1
        )
    }
}
