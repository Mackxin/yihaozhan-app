package xyz.yihaozhan.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.MainVm
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.Local
import xyz.yihaozhan.app.data.FeedItem
import xyz.yihaozhan.app.data.LinkItem
import xyz.yihaozhan.app.data.Post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================ 分享 ============================ */

@Composable
fun FeedScreen(
    data: AppPayload,
    query: String,
    tag: String,
    onOpen: (String) -> Unit,
    onPickTag: (String) -> Unit
) {
    val all = remember(data) { data.feed.sortedByDescending { it.date } }
    val list = remember(query, tag, all) {
        all.filter { f ->
            (tag.isEmpty() || f.tags.contains(tag)) &&
                (query.isBlank() || (f.title + " " + f.desc + " " + f.tags.joinToString(" "))
                    .lowercase().contains(query.trim().lowercase()))
        }
    }
    val groups = remember(list) {
        list.groupBy { it.date }.entries.sortedByDescending { it.key }
    }

    /* 筛出来的是另一批条目，不回到顶部就会停在原来那个位置，
       看起来像「筛完只剩空白」 */
    val listState = rememberLazyListState()
    LaunchedEffect(query, tag) { listState.scrollToItem(0) }

    /* 页头整块也放进列表里当第一个 item —— 一往下滑它就跟着走，
       不再钉在顶上占掉半屏（原来 Column + LazyColumn 就是「置顶」的来源） */
    val days = remember(all) { all.map { it.date }.filter { it.isNotBlank() }.toSet().size }
    val tagCount = remember(all) { all.flatMap { it.tags }.toSet().size }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        /* 页头照站点手机版：眉标 → 大标题 → 说明 → 三张统计卡 */
        item(key = "head") {
            PageHeader(
                "灵感分享",
                "每天一小条，记录正在遇见的工具、灵感与小发现。像刷动态一样，回看这一年的数字足迹。",
                eyebrow = "SHARING · 屿光",
                inset = 0.dp,
                side = {
                    StatRow(inset = 0.dp) {
                        StatBox("${all.size}", "条分享", Modifier.weight(1f))
                        StatBox("$days", "个记录日", Modifier.weight(1f))
                        StatBox("$tagCount", "个标签", Modifier.weight(1f))
                    }
                }
            )
        }
        if (list.isEmpty()) {
            item(key = "empty") { EmptyState("🍃", "没找到", "换个词试试，或者把筛选清掉。") }
        } else {
            groups.forEach { (date, items) ->
                item(key = "h-$date") {
                    Text(
                        prettyDate(date),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(items, key = { it.id + it.title }) { f ->
                    /* 卡片上的标签也能筛：看到「AI」想找同类，不用滚回顶上去点那颗胶囊 */
                    FeedCard(f, onOpen) { t -> onPickTag(if (tag == t) "" else t) }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(f: FeedItem, onOpen: (String) -> Unit, onTag: (String) -> Unit) {
    /* 跟站点一样不铺底色：条目靠留白分开，标签胶囊直接浮在极光上 */
    CardBox(
        padding = PaddingValues(horizontal = 4.dp, vertical = 14.dp),
        glass = false,
        onClick = if (f.url.isNotBlank()) ({ onOpen(f.url) }) else null
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(f.icon.ifBlank { "•" }, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(f.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
                if (f.desc.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        f.desc,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (f.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        f.tags.take(4).forEach { t ->
                            TagChip(t, onClick = { onTag(t) })
                        }
                    }
                }
            }
            if (f.url.isNotBlank()) {
                Icon(
                    Icons.Rounded.ArrowOutward, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/* ============================ 文章 ============================ */

@Composable
fun PostsScreen(
    data: AppPayload,
    query: String,
    tag: String,
    onOpen: (Post) -> Unit
) {
    val all = remember(data) { data.posts.sortedByDescending { it.date } }
    /* 统计永远是全站口径（头上那三张卡说的是「一共多少」），筛只影响下面那张列表 */
    val posts = remember(all, query, tag) {
        all.filter { p ->
            (tag.isEmpty() || p.tags.contains(tag)) &&
                (query.isBlank() || (p.title + " " + p.excerpt + " " + p.tags.joinToString(" "))
                    .lowercase().contains(query.trim().lowercase()))
        }
    }
    /* 统计跟站点一样是「篇数 / 字数 / 标签数」，字数按正文纯文字数算 */
    val chars = remember(all) { all.sumOf { postChars(it.body) } }
    val (charText, charUnit) = remember(chars) { fmtChars(chars) }
    val tagCount = remember(all) { all.flatMap { it.tags }.toSet().size }

    /* 页头是列表的第一项：往下滑就跟着走，不钉在顶上 */
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "head") {
            PageHeader(
                "文章",
                "写得长一点的东西：随笔、折腾记录，以及那些值得慢慢想清楚的念头。",
                eyebrow = "WRITING · 手记",
                inset = 0.dp,
                side = {
                    StatRow(inset = 0.dp) {
                        StatBox("${all.size}", "篇文章", Modifier.weight(1f))
                        StatBox(charText, charUnit, Modifier.weight(1f))
                        StatBox("$tagCount", "个标签", Modifier.weight(1f))
                    }
                }
            )
        }
        if (posts.isEmpty()) {
            item(key = "empty") {
                if (all.isEmpty()) {
                    EmptyState("✍️", "还没有文章", "站点后台「文章」页写一篇，这里就会出来。")
                } else {
                    EmptyState("🍃", "没找到", "换个词试试，或者把标签换回「全部标签」。")
                }
            }
        } else {
            items(posts, key = { it.id }) { p ->
                CardBox(
                    padding = PaddingValues(horizontal = 4.dp, vertical = 14.dp),
                    glass = false,
                    onClick = { onOpen(p) }
                ) {
                    Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                    if (p.excerpt.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.excerpt, fontSize = 13.5.sp, lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            p.date, fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        p.tags.take(3).forEach { TagChip(it) }
                    }
                }
            }
        }
    }
}

/**
 * 文章阅读页。
 *
 * 每段正文是一个 LazyColumn item（不是一整块 MarkdownView），换来三件事：
 * ① 目录能精确跳到某一节；② 长文只渲染屏幕里的那几段；
 * ③ 滚动位置能存、能恢复 —— 上次读到哪，这次点进来接着读。
 *
 * 顶栏三颗：收藏、稍后读、分享。都在这一层解决，不用为了标个星退出去。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostDetailScreen(
    post: Post,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    fontScale: Float = 1f,
    local: Local? = null,
    rememberPos: Boolean = true
) {
    val ctx = LocalContext.current
    val pal = LocalXyPal.current
    val scope = rememberCoroutineScope()

    val blocks = remember(post.id, post.body) { parseMarkdown(post.body) }
    /* 目录：标题块 → 它在 LazyColumn 里的 item 下标（第 0 个是标题区，所以 +1） */
    val toc = remember(post.id, post.body) {
        blocks.mapIndexedNotNull { i, b ->
            if (b is MdBlock.Heading && b.level in 2..3 && b.text.isNotBlank()) {
                Triple(b.level, b.text, i + 1)
            } else null
        }
    }

    val start = remember(post.id) { if (rememberPos) local?.posOf(post.id) else null }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = start?.first ?: 0,
        initialFirstVisibleItemScrollOffset = start?.second ?: 0
    )

    /* 离开（返回、切页、换一篇）时把位置存下来 */
    DisposableEffect(post.id, rememberPos) {
        onDispose {
            if (rememberPos && local != null) {
                local.savePos(
                    post.id,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset
                )
            }
        }
    }

    var showToc by remember { mutableStateOf(false) }
    val fav = local?.isFav(post.id) == true
    val later = local?.isLater(post.id) == true
    val scrolled = listState.firstVisibleItemIndex > 2
    val shareUrl = "https://mackxin.github.io/xingyu/#/post/" + post.id

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            /* 顶栏：返回 / 文章名 / 收藏 / 稍后读 / 分享 */
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
                }
                Text(
                    post.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = pal.dim,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
                BarIcon(
                    icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    desc = if (fav) "取消收藏" else "收藏",
                    tint = if (fav) pal.inkA2 else pal.dim
                ) { local?.toggleFav(post.id) }
                BarIcon(
                    icon = if (later) Icons.Rounded.Schedule else Icons.Rounded.Schedule,
                    desc = if (later) "移出稍后读" else "稍后读",
                    tint = if (later) pal.inkA3 else pal.dim
                ) { local?.toggleLater(post.id) }
                BarIcon(Icons.Rounded.Share, "分享", pal.dim) {
                    shareText(ctx, post.title + "\n" + shareUrl)
                }
            }

            /*
             * 正文：每块一个 item，整段可以长按选中复制。
             * 选中用 SelectionContainer 包**每一块**，别包整个 LazyColumn ——
             * 后者在滚动时选区会错位，跨块拖选也拿不全。
             */
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy((10 * fontScale).dp)
            ) {
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text(
                            post.title,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(post.date, fontSize = 12.sp, color = pal.mute)
                            if (post.tags.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                post.tags.take(3).forEach {
                                    TagChip(it)
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                        }
                    }
                }
                itemsIndexed(blocks) { i, b ->
                    SelectionContainer {
                        MdBlockView(b, fontScale)
                    }
                }
                item(key = "foot") {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        HairLine()
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { onOpen(shareUrl) }) {
                                Text("在浏览器里看这篇", fontSize = 13.sp)
                            }
                            TextButton(onClick = { shareText(ctx, post.title + "\n" + shareUrl) }) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("分享", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        /* 右下角两颗浮动小圆：回顶（滚下去才出现）和目录（有标题才出现） */
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (scrolled) {
                RoundBtn(Icons.Rounded.VerticalAlignTop, "回到顶部") {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
            if (toc.isNotEmpty()) {
                RoundBtn(Icons.AutoMirrored.Rounded.List, "文章目录") { showToc = true }
            }
        }

        if (showToc) {
            TocSheet(
                items = toc,
                onPick = { idx ->
                    showToc = false
                    scope.launch { listState.animateScrollToItem(idx) }
                },
                onDismiss = { showToc = false }
            )
        }
    }
}

/** 顶栏上一颗 40dp 的图标按钮 */
@Composable
private fun BarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** 右下角那颗玻璃小圆 */
@Composable
private fun RoundBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(pal.bgSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = pal.dim, modifier = Modifier.size(18.dp))
    }
}

/**
 * 目录：从底部升起来的一张卡，标题多的文章才用得上。
 * 不做成 ModalBottomSheet —— 为了这一张卡片引一整套弹层状态不值当。
 */
@Composable
private fun TocSheet(
    items: List<Triple<Int, String, Int>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(XyR.lg.dp))
                .background(pal.bgSoft)
                .padding(vertical = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "文章目录",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = pal.text, modifier = Modifier.weight(1f)
                )
                Text(
                    "${items.size} 节",
                    fontSize = 11.5.sp, color = pal.mute
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(items) { (level, text, idx) ->
                    Text(
                        text,
                        fontSize = if (level == 2) 13.5.sp else 12.5.sp,
                        lineHeight = 20.sp,
                        color = if (level == 2) pal.text else pal.dim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onPick(idx) }
                            .padding(
                                start = if (level == 2) 18.dp else 32.dp,
                                end = 18.dp, top = 9.dp, bottom = 9.dp
                            )
                    )
                }
            }
        }
    }
}

/* ============================ 导航 ============================ */

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LinksScreen(
    data: AppPayload,
    query: String,
    catId: String,
    onOpen: (String) -> Unit,
    startCollapsed: Boolean = false,
    local: Local? = null
) {
    val pal = LocalXyPal.current
    val allCats = remember(data) { data.cats.filter { it.links.isNotEmpty() } }
    /* 「全部分类」选了某一类就只留它 —— 跟顶栏那颗胶囊联动 */
    val cats = remember(allCats, catId) {
        if (catId.isBlank()) allCats else allCats.filter { it.id == catId }
    }
    val keyword = remember(query) { query.trim().lowercase() }
    val searching = keyword.isNotEmpty()

    /* 搜索时只留有命中的分类，组内也只留命中的链接 —— 搜索结果仍然按分类分组。
       分类名也算命中：站点那边的 hay 是「站名 + 描述 + 分类名 + 网址」，搜 mac 要能带出整个 MAC 类，
       这样顶栏搜索里「看全部 N 个站点」的 N 和这里数出来的才一样 */
    val groups = remember(cats, keyword) {
        cats.mapNotNull { c ->
            val catHit = keyword.isNotEmpty() && c.name.lowercase().contains(keyword)
            val hits = if (keyword.isEmpty()) c.links else c.links.filter { l ->
                catHit ||
                    l.name.lowercase().contains(keyword) ||
                    l.desc.lowercase().contains(keyword) ||
                    l.url.lowercase().contains(keyword)
            }
            if (hits.isEmpty()) null else c to hits
        }
    }

    /* 只记「折起来的分类 id」，空着就等于全展开；
       比给每个分类存一个开关省事，加新分类也不用补默认值。
       用 rememberSaveable 存成 Set：翻几页再回来、转个屏，折叠状态还在 */
    var foldedIds by rememberSaveable {
        mutableStateOf(if (startCollapsed) cats.map { it.id }.toSet() else emptySet())
    }
    fun isOpen(id: String) = searching || id !in foldedIds

    fun setAll(open: Boolean) {
        foldedIds = if (open) emptySet() else groups.map { it.first.id }.toSet()
    }

    /* 全展开还是全折叠，一个按钮切；文案跟着当前状态变，点一下就是「另一头」 */
    val allOpen = groups.isNotEmpty() && groups.all { isOpen(it.first.id) }
    val toggleAll: () -> Unit = { setAll(!allOpen) }

    /* 列表条目会随搜索整批换掉，不重置滚动位置就会停在新结果的中段 */
    val listState = rememberLazyListState()
    LaunchedEffect(keyword) { listState.scrollToItem(0) }

    /* 点开一个站点：先记一笔「最近打开」，再交给外面打开。
       所有入口（分类里的标签、最近那一排）都走这一个函数，记录才不漏。 */
    val visit: (String, String) -> Unit = { name, url ->
        local?.record(url, name)
        onOpen(url)
    }
    val recents = local?.recents.orEmpty()
    val scope = rememberCoroutineScope()
    val farDown = listState.firstVisibleItemIndex > 6

    Box(Modifier.fillMaxSize()) {
        /*
         * 页头也是列表的一项 —— 往下滑整块跟着走，不再钉在顶上。
         *
         * 分类标题 + 该分类的链接（流式小标签），一节一节往下排；点标题折叠/展开。
         * 一节 = 两个懒加载条目（标题、内容），所以五十个分类也只有百来个 item；
         * 每节里面的标签是一次排完的 —— 多的分类上百个，少的几个，量级扛得住。
         */
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 24.dp)
        ) {
            item(key = "head") {
                PageHeader(
                    "壹号栈导航",
                    "攒了多年的老书签全收在这一页 —— 共 **${data.stats.cats}** 类、**${data.stats.links}** 个坐标。" +
                        "顶部搜索框随时筛，最近打开过的会排在列表最前面。",
                    eyebrow = "NAVIGATION · 航图",
                    inset = 0.dp,
                    /* 点标题也能全部展开/折叠 —— 拇指够得着，比去右上角找按钮省事 */
                    onTitleClick = { if (!searching) toggleAll() },
                    /* 站点是「一行统计 + 一颗胶囊」并排居中，这里照搬 */
                    side = {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavMeta("共 **${data.stats.links}** 个站点 · **${data.stats.cats}** 个分类")
                            /* 搜索时是强制展开的，这个按钮就没意义了 */
                            if (!searching) {
                                HeadPill(if (allOpen) "全部折叠" else "全部展开") { toggleAll() }
                            }
                        }
                    }
                )
            }
            /* 搜索时才需要「找到几个」；不搜索时头上那行「共 N 个站点 · M 个分类」已经说清楚了 */
            if (searching) {
                item(key = "count") {
                    Text(
                        "找到 ${groups.sumOf { it.second.size }} 个",
                        fontSize = 11.5.sp, color = pal.mute,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                    )
                }
            }
            if (groups.isEmpty()) {
                item(key = "empty") { EmptyState("🔍", "没找到", "换个关键词试试。") }
            }
            /* 最近打开：搜索时不显示（那时候你要的是「找到什么」，
                   不是「刚看了什么」） */                if (!searching && recents.isNotEmpty()) {
                    item(key = "recent") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "最近打开",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = pal.mute, modifier = Modifier.weight(1f)
                            )
                            TinyBtn("清空") { local?.clearRecents() }
                        }
                    }
                    item(key = "recent-body") {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            recents.forEach { r ->
                                LinkChip(LinkItem(name = r.name, url = r.url)) { visit(r.name, r.url) }
                            }
                        }
                    }
                    item(key = "recent-gap") { Spacer(Modifier.height(12.dp)) }
                }
                groups.forEach { (cat, links) ->
                    item(key = "H|" + cat.id) {
                        CatHeader(
                            icon = cat.icon,
                            name = cat.name,
                            count = links.size,
                            open = isOpen(cat.id),
                            locked = searching,
                            /* 现在展开着（不在折叠集合里）→ 折起来；已经折着 → 打开 */
                            onToggle = {
                                foldedIds = if (cat.id in foldedIds) foldedIds - cat.id
                                else foldedIds + cat.id
                            }
                        )
                    }
                    if (isOpen(cat.id)) {
                        item(key = "B|" + cat.id) {
                            FlowRow(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, start = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                links.forEach { l -> LinkChip(l) { visit(l.name, l.url) } }
                            }
                        }
                    }
                    item(key = "S|" + cat.id) { Spacer(Modifier.height(12.dp)) }
                }
        }
        /* 两千多个站点滑到深处，没这颗圆就回不去了 */
        if (farDown) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 22.dp)) {
                RoundBtn(Icons.Rounded.VerticalAlignTop, "回到顶部") {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        }
    }
}

/**
 * 分类标题条：图标 + 名字 + 站点数 + 一个会转的箭头。
 * 整条都能点（箭头那么小，只有点得中才有鬼）。
 */
@Composable
private fun CatHeader(
    icon: String,
    name: String,
    count: Int,
    open: Boolean,
    locked: Boolean,
    onToggle: () -> Unit
) {
    val pal = LocalXyPal.current
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "nav-chev")

    Row(
        Modifier
            .fillMaxWidth()
            /* 不铺底色：分类是「小标题」，卡片才是内容块。
               跟分享页那种日期小标题一个语言，一眼能分开层级 */
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !locked
            ) { onToggle() }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(icon.ifBlank { name.take(1) }, fontSize = 13.sp)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("$count", fontSize = 11.sp, color = pal.mute)
        if (!locked) {
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown, "展开或折叠",
                tint = pal.mute,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rot)
            )
        }
    }
}

/**
 * 导航里的一个站点：只留名字的流式小标签，宽度由名字长短决定。
 * 之前那个「图标 + 名字 + 三行描述」的卡片在手机上太重了 —— 一屏只能看四五个，
 * 两千个站点翻不到头；换成标签之后一屏能扫几十个，图标也省了（网站 favicon 拉不到，
 * 用的都是 emoji，反而花）。
 */
@Composable
private fun LinkChip(link: LinkItem, onOpen: () -> Unit) {
    val pal = LocalXyPal.current
    Text(
        link.name,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        color = pal.text,
        modifier = Modifier
            .clip(RoundedCornerShape(XyR.sm.dp))
            .background(pal.glass)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onOpen() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** 「全部展开 / 全部折叠」那颗小胶囊 */
@Composable
private fun TinyBtn(text: String, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Text(
        text,
        fontSize = 11.5.sp,
        color = pal.dim,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glass)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

/* ============================ 工具 ============================ */

private fun prettyDate(iso: String): String {

    if (iso.length < 10) return iso
    return runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(iso.substring(0, 10))
        if (d == null) iso else SimpleDateFormat("M 月 d 日 · EEEE", Locale.CHINA).format(d)
    }.getOrDefault(iso)
}

private fun lastSyncText(ts: Long): String {
    if (ts <= 0L) return "还没同步过"
    val sdf = SimpleDateFormat("M 月 d 日 HH:mm", Locale.CHINA)
    return sdf.format(Date(ts))
}

/* ============================ 收藏 ============================ */

/**
 * 收藏 / 稍后读。
 *
 * 站点那边也有这两个功能，但那是存在你浏览器里的；App 里这一份是本机的，
 * 两边互不同步 —— 也不该同步：`app.json` 只装公共内容，私人标记一律不出手机。
 *
 * 两段用胶囊切，比上下铺两段省一屏；标过的文章按日期倒序，跟文章页一个顺序。
 */
@Composable
fun SavedScreen(
    posts: List<Post>,
    local: Local,
    onOpenPost: (Post) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(0) }

    val favs = remember(local.fav, posts) {
        posts.filter { it.id in local.fav }.sortedByDescending { it.date }
    }
    val laters = remember(local.later, posts) {
        posts.filter { it.id in local.later }.sortedByDescending { it.date }
    }
    val shown = if (tab == 0) favs else laters

    /* 页头也是列表的一项：往下滑跟着走，不占着顶上不放 */
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "head") {
            PageHeader(
                "收藏",
                "标了星的、留着待会儿看的，都在这台手机里。",
                eyebrow = "SAVED · 收藏",
                inset = 0.dp,
                /* 原来是一条胶囊切换条，挪到页头跟站点的 `.head-toggle` 一个位置、一个样子 */
                side = {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeadPill("收藏 ${favs.size}", active = tab == 0) { tab = 0 }
                        HeadPill("稍后读 ${laters.size}", active = tab == 1) { tab = 1 }
                    }
                }
            )
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                if (tab == 0) {
                    EmptyState("⭐️", "还没收藏", "文章右上角那颗星点一下，就收到这儿。")
                } else {
                    EmptyState("🕐", "稍后读是空的", "文章右上角那颗时钟点一下，先记着待会儿看。")
                }
            }
        } else {
            items(shown, key = { it.id }) { p ->
                    CardBox(
                        padding = PaddingValues(horizontal = 4.dp, vertical = 14.dp),
                        glass = false,
                        onClick = { onOpenPost(p) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.title, fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold, lineHeight = 23.sp
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(p.date, fontSize = 11.5.sp, color = LocalXyPal.current.mute)
                                if (p.excerpt.isNotBlank()) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        p.excerpt, fontSize = 13.sp, lineHeight = 20.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (tab == 0) local.toggleFav(p.id) else local.toggleLater(p.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (tab == 0) Icons.Rounded.Star else Icons.Rounded.Schedule,
                                    "移出",
                                    tint = if (tab == 0) LocalXyPal.current.inkA2 else LocalXyPal.current.inkA3,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
        }
    }
}
