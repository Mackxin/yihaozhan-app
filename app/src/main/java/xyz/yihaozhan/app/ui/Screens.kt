package xyz.yihaozhan.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.ui.input.pointer.pointerInput
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
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.data.Py
import xyz.yihaozhan.app.data.FeedItem
import xyz.yihaozhan.app.data.LinkItem
import xyz.yihaozhan.app.data.Post
import xyz.yihaozhan.app.data.Snapshots
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.compose.ui.text.style.TextDecoration

/* ============================ 分享 ============================ */

@Composable
fun FeedScreen(
    data: AppPayload,
    query: String,
    tag: String,
    onOpen: (String) -> Unit,
    onPickTag: (String) -> Unit,
    /* 滚动状态由外层持有：顶栏那颗「壹号栈」在这一页也要能做「到底 / 回顶」 */
    listState: LazyListState = rememberLazyListState()
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
    LaunchedEffect(query, tag) { listState.scrollToItem(0) }

    /* 页头整块也放进列表里当第一个 item —— 一往下滑它就跟着走，
       不再钉在顶上占掉半屏（原来 Column + LazyColumn 就是「置顶」的来源） */
    val days = remember(all) { all.map { it.date }.filter { it.isNotBlank() }.toSet().size }
    val tagCount = remember(all) { all.flatMap { it.tags }.toSet().size }

    /* 那年今日：往年同月同日写过的（今天这一天，去年/前年是什么样） */
    val md = remember { mdToday() }
    val yr = remember { yearNow() }
    val onThisDay = remember(all, md, yr) {
        all.filter { f ->
            f.date.length >= 10 && f.date.substring(5, 10) == md && f.date.take(4) != yr
        }.take(4)
    }

    /* 长按卡片弹出的操作菜单（对应站点那份右键菜单的触屏版） */
    val ctx = LocalContext.current
    var menuFor by remember { mutableStateOf<FeedItem?>(null) }

    Box(Modifier.fillMaxSize()) {
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
        /* 那年今日：同月同日、往年写的那些。只在「没筛标签、没搜词」时出现 ——
           正在找东西的时候顶上插一段回忆，反而碍事 */
        if (onThisDay.isNotEmpty() && tag.isEmpty() && query.isBlank()) {
            item(key = "otd-h") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📅 那年今日",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("$md 这一天", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)                }
            }
            items(onThisDay, key = { "otd" + it.id + it.title }) { f ->
                Column {
                    Text(
                        f.date.take(4) + " 年",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    FeedCard(f, onOpen, onLongPress = { menuFor = f }) { t ->
                        onPickTag(if (tag == t) "" else t)
                    }
                }
            }
            item(key = "otd-gap") { Spacer(Modifier.height(10.dp)) }
        }
        if (list.isEmpty()) {
            item(key = "empty") { EmptyState("🍃", "没找到", "换个词试试，或者把筛选清掉。") }
        } else {            groups.forEach { (date, items) ->
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
                    FeedCard(f, onOpen, onLongPress = { menuFor = f }) { t ->
                        onPickTag(if (tag == t) "" else t)
                    }
                }
            }
        }
    }
    }

    menuFor?.let { f ->
        val hasUrl = f.url.isNotBlank()
        CardMenu(
            title = f.title,
            sub = if (hasUrl) urlHost(f.url) else "没有链接的一条分享",
            onClose = { menuFor = null },
            actions = buildList {
                if (hasUrl) add(CardAction("🔗", "打开链接") { menuFor = null; onOpen(f.url) })
                if (hasUrl) add(CardAction("📋", "复制链接") {
                    menuFor = null
                    copyText(ctx, f.url)
                    Toast.makeText(ctx, "链接已复制", Toast.LENGTH_SHORT).show()
                })
                add(CardAction("📝", "复制标题与链接") {
                    menuFor = null
                    copyText(ctx, (f.title + "\n" + f.url).trim())
                    Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                })
            }
        )
    }
}

@Composable
private fun FeedCard(
    f: FeedItem,
    onOpen: (String) -> Unit,
    onLongPress: () -> Unit,
    onTag: (String) -> Unit
) {
    /* 跟站点一样不铺底色：条目靠留白分开，标签胶囊直接浮在极光上 */
    CardBox(
        padding = PaddingValues(
            horizontal = 4.dp,
            vertical = if (Prefs.of(LocalContext.current).cardDensity == 0) 9.dp else 14.dp
        ),
        glass = false,
        onClick = if (f.url.isNotBlank()) ({ onOpen(f.url) }) else null,
        onLongPress = onLongPress
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

/**
 * 阅读顺序：置顶优先，其余按日期倒序 —— 跟站点 `postList()` 一模一样。
 * 草稿在这里再滤一次（新生成的 app.json 已经滤过，这一步是给旧缓存兜底）。
 */
fun orderedPosts(list: List<Post>): List<Post> =
    list.filter { !it.draft }
        .sortedWith(compareByDescending<Post> { it.pin }.thenByDescending { it.date })

@Composable
fun PostsScreen(
    data: AppPayload,
    query: String,
    tag: String,
    onOpen: (Post) -> Unit,
    /* 滚动状态由外层持有：顶栏那颗「壹号栈」在这一页也要能做「到底 / 回顶」 */
    listState: LazyListState = rememberLazyListState()
) {
    val all = remember(data) { orderedPosts(data.posts) }
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
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
                    Row(verticalAlignment = Alignment.Top) {
                        if (p.pin) {
                            Text("📌", fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp, top = 3.dp))
                        }
                        Text(
                            p.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            lineHeight = 24.sp, modifier = Modifier.weight(1f)
                        )
                    }
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
    /* 行距倍率与字体（设置 → 阅读）。默认值 = 现在的样子 */
    lineFactor: Float = 1f,
    serif: Boolean = false,
    local: Local? = null,
    rememberPos: Boolean = true,
    /* 列表里排在后面的那篇（更早写的）/ 前面那篇（更新的）。
       底部做成两张小卡，左右滑动换篇由外层 pager 负责，这里只管按钮 */
    older: Post? = null,
    newer: Post? = null,
    onJump: (Post) -> Unit = {}
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
    var showMore by remember { mutableStateOf(false) }
    val fav = local?.isFav(post.id) == true
    val later = local?.isLater(post.id) == true
    val scrolled = listState.firstVisibleItemIndex > 2
    val shareUrl = "https://mackxin.github.io/xingyu/#/post/" + post.id

    /* ---------------- 朗读（系统 TTS） ----------------
       整篇的纯文字只算一次：标题念一句，然后按块接下去。
       换文章（pager 滑过去）时把上一段念的东西停掉，不然声音会跟着人跑到别的文章里。 */
    val spk = rememberSpeaker()
    val spoken = remember(post.id, blocks) {
        buildString {
            append(post.title).append("。\n")
            blocks.forEach { b -> append(mdBlockText(b)).append("\n") }
        }
    }
    LaunchedEffect(post.id) { spk.stop() }

    /* 长按了哪一段：null = 没弹动作条。下标而不是块本身，切文章时自然失效 */
    var picked by remember(post.id) { mutableStateOf<Int?>(null) }
    /* 动作条开着时，返回键只该收掉它，不该退出整篇 —— 所以这儿要有一个更靠内层的 BackHandler */
    BackHandler(enabled = picked != null) { picked = null }

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
                    icon = if (spk.speaking) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp,
                    desc = if (spk.speaking) "停止朗读" else "朗读这篇",
                    tint = if (spk.speaking) pal.inkA3 else pal.dim
                ) {
                    if (spk.speaking) spk.stop() else spk.speak(spoken)
                }
                BarIcon(
                    icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    desc = if (fav) "取消收藏" else "收藏",
                    tint = if (fav) pal.inkA2 else pal.dim
                ) { local?.toggleFav(post.id) }
                BarIcon(
                    icon = if (later) Icons.Rounded.Schedule else Icons.Rounded.Schedule,
                    desc = if (later) "移出稍后读" else "稍后读",
                    tint = if (later) pal.inkA3 else pal.dim
                ) {
                    /* 标「稍后读」的同时把正文抄一份到本机：站点拉新内容会整份覆盖，
                       站上撤掉的稿子如果只留个 id，点进去就是空白（见 Snapshots 的注释） */
                    local?.let { l ->
                        l.toggleLater(post.id)
                        if (post.id in l.later) Snapshots.save(ctx, post)
                        else Snapshots.forget(ctx, post.id)
                    }
                }
                BarIcon(Icons.Rounded.Share, "分享", pal.dim) {
                    shareText(ctx, post.title + "\n" + shareUrl)
                }
                BarIcon(Icons.Rounded.MoreHoriz, "更多", pal.dim) { showMore = true }
            }

            /* 念着的时候才出现的「语速 / 定时」条。
               不念的时候藏起来 —— 顶栏已经五颗键了，再多就挤。 */
            if (spk.speaking) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VoiceChips(spk)
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
                /* 底部多留一截：右下角那两颗浮动圆（回顶 + 目录）会压到「上一篇/下一篇」卡上，
                   不避开的话最下面那张卡的标签就被盖住了 */
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 76.dp),
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
                    /* 长按一段 → 底部弹动作条（存为摘录 / 复制 / 朗读这段）。
                       这里不再套 SelectionContainer：它自己也吃长按（用来起拖拽选字），
                       两个手势抢同一个事件只会互相打架 —— 而且手机上跨段拖选本来也选不全。
                       换成「长按 = 整段摘」更合手，真要逐字选还能在「在浏览器里看这篇」里选。 */
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(b) {
                                detectTapGestures(onLongPress = { picked = i })
                            }
                    ) {
                        MdBlockView(b, fontScale, lineFactor, serif)
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
                        /* 读到底了顺手翻下一篇：两颗卡左右各一，跟站点阅读页底部一致。
                           只有一个方向有货时让它铺满整行，不留半块空地 */
                        if (older != null || newer != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (older != null && newer != null) {
                                    PostNavCard(
                                        "← 上一篇 · 更早", older, TextAlign.Start,
                                        Modifier.weight(1f), onJump
                                    )
                                    PostNavCard(
                                        "下一篇 · 更新 →", newer, TextAlign.End,
                                        Modifier.weight(1f), onJump
                                    )
                                } else if (older != null) {
                                    PostNavCard(
                                        "← 上一篇 · 更早", older, TextAlign.Start,
                                        Modifier.fillMaxWidth(), onJump
                                    )
                                } else {
                                    PostNavCard(
                                        "下一篇 · 更新 →", newer, TextAlign.End,
                                        Modifier.fillMaxWidth(), onJump
                                    )
                                }
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

        /* 「更多」：存成 .md / 复制全文 / 浏览器打开。
           导出的是 Markdown 原文，不是排版后的 HTML —— 拿到别处还能改、能进笔记软件。 */
        if (showMore) {
            val mdText = remember(post.id, post.body) {
                buildString {
                    append("# ").append(post.title).append("\n\n")
                    if (post.date.isNotBlank()) append(post.date).append("\n\n")
                    append(post.body.trim()).append("\n\n")
                    append("---\n来源：").append(shareUrl).append("\n")
                }
            }
            CardMenu(
                title = post.title,
                sub = "存一份带走，或者复制成纯文本",
                actions = listOf(
                    CardAction("📄", "存为 .md 文件") {
                        showMore = false
                        val f = writeCacheText(ctx, post.id + ".md", mdText)
                        if (f != null) shareFile(ctx, f, "text/markdown")
                        else brief(ctx, "没写出来，磁盘满了？")
                    },
                    CardAction("📋", "复制全文") {
                        showMore = false
                        copyText(ctx, mdText)
                        brief(ctx, "全文已复制")
                    },
                    CardAction("🌐", "在浏览器里打开") {
                        showMore = false
                        onOpen(shareUrl)
                    }
                ),
                onClose = { showMore = false }
            )
        }

        /* 长按某一段之后的动作条。三件事：存为摘录、复制这一段、朗读这一段 */
        val pi = picked
        val blk = if (pi != null) blocks.getOrNull(pi) else null
        if (blk != null) {
            val txt = remember(blk) { mdBlockText(blk) }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { picked = null }
                    .enterFade(180),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        /* 动作条贴着屏幕底：不加这一层，三颗按钮会被系统导航条压掉半截 */
                        .navigationBarsPadding()
                        .padding(14.dp)
                        .clip(RoundedCornerShape(pal.rLg.dp))
                        .background(pal.bgSoft)
                        /* 空回调把点击吃掉，不然点卡片本身也会关掉它 */
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("这一段", fontSize = 12.sp, color = pal.mute)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        txt,
                        fontSize = 14.sp,
                        lineHeight = 23.sp,
                        color = pal.text,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SheetAction("✂️ 存为摘录", Modifier.weight(1f), strong = true) {
                            picked = null
                            if (local == null) {
                                Toast.makeText(ctx, "先打开一篇看看", Toast.LENGTH_SHORT).show()
                            } else {
                                val added = local.addExtract(post.id, post.title, txt)
                                Toast.makeText(
                                    ctx,
                                    if (added) "已存进摘录本" else "这一段已经摘过了",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        SheetAction("复制", Modifier.weight(1f)) {
                            picked = null
                            copyText(ctx, txt)
                            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                        }
                        SheetAction(
                            if (spk.speaking) "停止朗读" else "朗读",
                            Modifier.weight(1f)
                        ) {
                            picked = null
                            if (spk.speaking) spk.stop() else spk.speak(txt)
                        }
                    }
                }
            }
        }
    }
}

/** 动作条上的一颗按钮：三颗等宽，主操作（存摘录）给玻璃底提一档 */
@Composable
private fun SheetAction(
    text: String,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (strong) pal.glassHi else pal.glass)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) pal.inkA1 else pal.dim,
            maxLines = 1
        )
    }
}

/**
 * 阅读页底部那一小张「上一篇 / 下一篇」卡：上面一行小标签说方向，
 * 下面一行是标题（两行封顶）。没有那一篇就整块不画，不留空壳。
 */@Composable
private fun PostNavCard(
    label: String,
    post: Post?,
    align: TextAlign,
    modifier: Modifier = Modifier,
    onPick: (Post) -> Unit
) {
    if (post == null) {
        /* 占位保持两列等宽：只剩一张卡时它不该被拉成整行 */
        Spacer(modifier)
        return
    }
    val pal = LocalXyPal.current
    Column(
        modifier
            .clip(RoundedCornerShape(pal.rLg.dp))
            .background(pal.glass)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onPick(post) }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = pal.mute, textAlign = align,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(5.dp))
        Text(
            post.title,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = pal.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
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

/** 阅读页右下角那两颗玻璃小圆（回顶 / 目录）。列表页那颗独立的回顶圆钮 2.6.4 已删，
    回顶改成点顶栏的「壹号栈」；阅读页没有底栏也没有顶栏品牌，所以这组留着。 */
@Composable
private fun RoundBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glass)
            .border(BorderStroke(1.dp, pal.glassHi), RoundedCornerShape(99.dp))
            .enterPop(180)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = pal.dim, modifier = Modifier.size(20.dp))
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
                .clip(RoundedCornerShape(pal.rLg.dp))
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
    local: Local? = null,
    /* 滚动状态由外层持有：顶栏那颗「壹号栈」在这一页也要能做「到底 / 回顶」 */
    listState: LazyListState = rememberLazyListState()
) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    val allCats = remember(data) { data.cats.filter { it.links.isNotEmpty() } }
    /* 「全部分类」选了某一类就只留它 —— 跟顶栏那颗胶囊联动 */
    val cats = remember(allCats, catId) {
        if (catId.isBlank()) allCats else allCats.filter { it.id == catId }
    }
    val keyword = remember(query) { query.trim().lowercase() }
    val searching = keyword.isNotEmpty()

    /* 站点收藏（网址维度）——跟站点那边「只看收藏」一个口径 */
    val favSet = local?.favLinks.orEmpty()
    val deadSet = local?.dead.orEmpty()
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    /* 长按弹出的那张菜单：记网址，渲染时再取名字 */
    var menuFor by remember { mutableStateOf<LinkItem?>(null) }

    /* 搜索时只留有命中的分类，组内也只留命中的链接 —— 搜索结果仍然按分类分组。
       命中判定走 `Py.hit`：普通包含 + 拼音首字母（zh→知乎、txsp→腾讯视频）+ 骨架（gh→GitHub），
       跟网页端 matchLink 一个口径。分类名也塞进 extra —— 搜 mac 要能带出整个 MAC 类，
       这样顶栏搜索里「看全部 N 个站点」的 N 和这里数出来的才一样 */
    val groups = remember(cats, keyword, onlyFav, favSet) {
        cats.mapNotNull { c ->
            val catHit = keyword.isNotEmpty() && Py.hit(keyword, c.name)
            val hits = if (keyword.isEmpty()) c.links else c.links.filter { l ->
                catHit || Py.hit(keyword, l.name, l.desc + " " + l.url + " " + c.name)
            }
            val shown = if (onlyFav) hits.filter { it.url in favSet } else hits
            if (shown.isEmpty()) null else c to shown
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
    LaunchedEffect(keyword) { listState.scrollToItem(0) }

    /* 点开一个站点：先记一笔「最近打开」，再交给外面打开。
       所有入口（分类里的标签、最近那一排）都走这一个函数，记录才不漏。 */
    val visit: (String, String) -> Unit = { name, url ->
        local?.record(url, name)
        onOpen(url)
    }
    val recents = local?.recents.orEmpty()
    val scope = rememberCoroutineScope()

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
                            /* 两千个站点，翻十年也翻不到底 —— 让骰子替人挑一个。
                               跳过标了失效的，不然十次有三次开到打不开的 */
                            if (!searching) {
                                HeadPill("🎲 随便逛逛") {
                                    val pool = data.cats.asSequence()
                                        .flatMap { it.links.asSequence() }
                                        .filter { it.url !in deadSet }
                                        .toList()
                                    if (pool.isNotEmpty()) onOpen(pool.random().url)
                                }
                            }
                            /* 只看收藏：只有真收藏过才露出来，免得空按 */
                            if (favSet.isNotEmpty()) {
                                HeadPill(
                                    "⭐ 收藏 ${favSet.size}",
                                    active = onlyFav
                                ) { onlyFav = !onlyFav }
                            }
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
                item(key = "empty") {
                    when {
                        onlyFav && !searching ->
                            EmptyState("⭐️", "还没收藏站点", "长按任意一个站点，点「收藏」就收到这儿。")
                        onlyFav -> EmptyState("🔍", "收藏里没找到", "取消「只看收藏」再试一次。")
                        else -> EmptyState("🔍", "没找到", "换个关键词试试。")
                    }
                }
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
                            /* 「分类名前的 emoji」也是站点后台的开关，关掉就只留名字 */
                            icon = if (data.nav.catIcon) cat.icon else "",
                            name = cat.name,
                            /* -1 = 不显示站点数（站点后台「显示项 → 分类显示站点数」关掉时） */
                            count = if (data.nav.count) links.size else -1,
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
                            /* 「显示项 → 介绍」开着就一行一个（名字 + 介绍），
                               否则还是标签云 —— 跟网页端「卡片列表 / 紧凑标签」两种密度对得上 */
                            if (data.nav.desc) {
                                Column(
                                    Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    links.forEach { l ->
                                        LinkRow(
                                            link = l,
                                            fav = l.url in favSet,
                                            dead = l.url in deadSet,
                                            showIcon = data.nav.icons,
                                            onLongPress = { menuFor = l },
                                            onOpen = { visit(l.name, l.url) }
                                        )
                                    }
                                }
                            } else {
                                FlowRow(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, start = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    links.forEach { l ->
                                        LinkChip(
                                            l,
                                            fav = l.url in favSet,
                                            dead = l.url in deadSet,
                                            showIcon = data.nav.icons,
                                            onOpen = { visit(l.name, l.url) },
                                            onLongPress = { menuFor = l }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "S|" + cat.id) { Spacer(Modifier.height(12.dp)) }
                }
        }
    }

    /* 长按卡片弹出来的那几件事：收藏 / 复制链接 / 标记失效 */
    menuFor?.let { l ->
        val fav = l.url in favSet
        val dead = l.url in deadSet
        CardMenu(
            title = l.name,
            sub = l.url,
            actions = listOfNotNull(
                CardAction(if (fav) "☆" else "⭐", if (fav) "取消收藏" else "收藏到本机") {
                    if (local != null) {
                        val on = local.toggleFavLink(l.url)
                        Toast.makeText(
                            ctx, if (on) "已收藏，导航页可「只看收藏」" else "已取消收藏",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                CardAction("🔗", "复制链接") {
                    copyText(ctx, l.url)
                    Toast.makeText(ctx, "链接已复制", Toast.LENGTH_SHORT).show()
                },
                CardAction(
                    "⚠️", if (dead) "恢复正常" else "标记失效（打不开了）",
                    danger = !dead
                ) {
                    if (local != null) {
                        val on = local.toggleDead(l.url)
                        Toast.makeText(
                            ctx, if (on) "已标记失效，卡片会淡下去" else "已恢复正常",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ),
            onClose = { menuFor = null }
        )
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
        /* 站点后台「显示项 → 分类图标」关着就整块不画（网页端也是纯文字小标题），
           开着但这一类本来就配了 emoji 才显示 */
        if (icon.isNotBlank()) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 13.sp)
            }
            Spacer(Modifier.width(9.dp))
        }
        Text(
            name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count >= 0) Text("$count", fontSize = 11.sp, color = pal.mute)
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
private fun LinkChip(
    link: LinkItem,
    fav: Boolean = false,
    dead: Boolean = false,
    /* 站点后台「显示项 → 图标」开着时，名字前面带一个小 emoji */
    showIcon: Boolean = false,
    /* onOpen 故意放最后：老的 `LinkChip(item) { 打开 }` 尾随 lambda 才不会绑错 */
    onLongPress: () -> Unit = {},
    onOpen: () -> Unit
) {
    val pal = LocalXyPal.current
    Row(
        Modifier
            .clip(RoundedCornerShape(pal.rSm.dp))
            .background(pal.glass)
            /* 不用 combinedClickable：它要 @OptIn(ExperimentalFoundationApi)，
               而本项目把实验 API 当 error。detectTapGestures 一样能同时拿单击和长按 */
            .pointerInput(link.url) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fav) {
            Text("★", fontSize = 10.sp, color = pal.inkA2)
            Spacer(Modifier.width(4.dp))
        }
        if (showIcon && link.icon.isNotBlank()) {
            Text(link.icon, fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            link.name,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = if (dead) pal.mute else pal.text,
            textDecoration = if (dead) TextDecoration.LineThrough else null
        )
    }
}

/**
 * 「带介绍」的那一版站点行（站点后台「显示项 → 介绍」打开时才走这条）。
 *
 * 标签云适合扫，但想看介绍的时候一行一个才放得下 —— 跟网页端卡片列表一个意思：
 * 名字一行、介绍（没写介绍就显示域名）一行，收藏的带颗星，失效的压淡加删除线。
 */
@Composable
private fun LinkRow(
    link: LinkItem,
    fav: Boolean,
    dead: Boolean,
    showIcon: Boolean,
    onLongPress: () -> Unit,
    onOpen: () -> Unit
) {
    val pal = LocalXyPal.current
    /* 卡片密度（设置 → 列表）：紧凑就少留一点上下白，一屏多看好几条 */
    val compact = Prefs.of(LocalContext.current).cardDensity == 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(pal.rMd.dp))
            .background(pal.glass)
            .pointerInput(link.url) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 13.dp, vertical = if (compact) 7.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon && link.icon.isNotBlank()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text(link.icon, fontSize = 13.sp) }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) {
                    Text("★", fontSize = 10.sp, color = pal.inkA2)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    link.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (dead) pal.mute else pal.text,
                    textDecoration = if (dead) TextDecoration.LineThrough else null
                )
            }
            val sub = link.desc.ifBlank { urlHost(link.url) }
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    sub, fontSize = 11.5.sp, lineHeight = 16.sp, color = pal.mute,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 「全部展开 / 全部折叠」那颗小胶囊 */
@Composable
fun TinyBtn(text: String, onClick: () -> Unit) {
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

/** 收藏页「站点」那一段的一行：网址 + 现查到的名字/分类 */
private data class SavedLink(val name: String, val cat: String, val url: String)

/** 从网址里抠出域名（去掉协议和 www.），列表里显示成一行的第二半 */
fun urlHost(url: String): String {
    val s = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    return s.substringBefore('/').ifBlank { url }
}

fun prettyDate(iso: String): String {
    if (iso.length < 10) return iso
    return runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(iso.substring(0, 10))
        if (d == null) iso else SimpleDateFormat("M 月 d 日 · EEEE", Locale.CHINA).format(d)
    }.getOrDefault(iso)
}

/** 今天的「月-日」：`10-31`。跟 `it.date.substring(5, 10)` 一个口径，用来比「同月同日」 */
private fun mdToday(): String {
    val c = java.util.Calendar.getInstance()
    val m = c.get(java.util.Calendar.MONTH) + 1
    val d = c.get(java.util.Calendar.DAY_OF_MONTH)
    return (if (m < 10) "0$m" else "$m") + "-" + (if (d < 10) "0$d" else "$d")
}

private fun yearNow(): String = System.currentTimeMillis().let {
    SimpleDateFormat("yyyy", Locale.CHINA).format(Date(it))
}

private fun lastSyncText(ts: Long): String {
    if (ts <= 0L) return "还没同步过"
    val sdf = SimpleDateFormat("M 月 d 日 HH:mm", Locale.CHINA)
    return sdf.format(Date(ts))
}

/* ============================ 收藏 ============================ */

/**
 * 收藏 / 稍后读 / 站点收藏。
 *
 * 站点那边「收藏」指的是**站点**（导航页那颗星、「只看收藏」、导出书签），
 * 而「稍后读」指的是链接；App 以前只有文章维度，网页上标过的站点在手机里看不见。
 * 现在三段并排：文章收藏 / 文章稍后读 / 站点收藏，口径跟网页端对上了。
 *
 * 存在本机、不上传：`app.json` 只装公共内容，私人标记一律不出手机。
 */
@Composable
fun SavedScreen(
    data: AppPayload,
    local: Local,
    onOpenPost: (Post) -> Unit,
    onOpenUrl: (String) -> Unit,
    /* 外面（顶栏自定义入口 #/later、#/saved）要求「进来就落在哪一段」，
       用一次性 hint 传进来，读完立刻清掉，免得每次重组都把人拽回第一段 */
    segHint: Int? = null,
    onSegHint: () -> Unit = {},
    /* 滚动状态由外层持有：顶栏那颗「壹号栈」在这一页也要能做「到底 / 回顶」 */
    listState: LazyListState = rememberLazyListState()
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(segHint) {
        if (segHint != null && segHint in 0..2) {
            tab = segHint
            onSegHint()
        }
    }

    val posts = data.posts
    val favs = remember(local.fav, posts) {
        posts.filter { it.id in local.fav }.sortedByDescending { it.date }
    }
    /* 稍后读：站点数据优先；站点上已经没有的（撤稿 / 清过缓存）用本机快照兜底，
       否则列表还在、点进去空白，看着像坏了 */
    val ctx = LocalContext.current
    val laters = remember(local.later, posts) {
        val ids = local.later
        val live = posts.filter { it.id in ids }
        val liveIds = live.map { it.id }.toSet()
        val ghosts = Snapshots.all(ctx)
            .filter { it.id in ids && it.id !in liveIds }
            .map { Snapshots.toPost(it) }
        (live + ghosts).sortedByDescending { it.date }
    }

    /* 站点里网址 → (名字, 分类名)：收藏只存网址，显示时再回 app.json 里查一遍名字；
       查不到（站点被删了）就退回域名，不至于显示一条裸网址以外什么都没有 */
    val linkIndex = remember(data) {
        val m = HashMap<String, Pair<String, String>>()
        data.cats.forEach { c -> c.links.forEach { l -> m[l.url] = l.name to c.name } }
        m
    }
    val favLinks = local.favLinks
    val savedLinks = remember(favLinks, linkIndex) {
        favLinks.map { u ->
            val hit = linkIndex[u]
            SavedLink(
                name = hit?.first
                    ?: u.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                cat = hit?.second ?: "",
                url = u
            )
        }.sortedBy { it.name.lowercase() }
    }

    val shown = if (tab == 0) favs else laters
    /* 换一段就回到顶部，否则从长列表切到短列表会停在一个越界的位置 */
    LaunchedEffect(tab) { listState.scrollToItem(0) }

    /* 页头也是列表的一项：往下滑跟着走，不占着顶上不放 */
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "head") {
            PageHeader(
                "收藏",
                "文章标了星的、留着待会儿看的，加上导航里收藏的站点，都在这台手机里。",
                eyebrow = "SAVED · 收藏",
                inset = 0.dp,
                /* 原来是一条胶囊切换条，挪到页头跟站点的 `.head-toggle` 一个位置、一个样子 */
                side = {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeadPill("收藏 ${favs.size}", active = tab == 0) { tab = 0 }
                        HeadPill("稍后读 ${laters.size}", active = tab == 1) { tab = 1 }
                        HeadPill("站点 ${savedLinks.size}", active = tab == 2) { tab = 2 }
                    }
                }
            )
        }
        if (tab == 2) {
            /* ---- 站点收藏 ---- */
            if (savedLinks.isEmpty()) {
                item(key = "empty") {
                    EmptyState("⭐️", "还没收藏站点", "在导航页长按任意一个站点，点「收藏」就收到这儿。")
                }
            } else {
                items(savedLinks, key = { "L" + it.url }) { s ->
                    CardBox(
                        padding = PaddingValues(horizontal = 4.dp, vertical = 13.dp),
                        glass = false,
                        onClick = { onOpenUrl(s.url) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.name, fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold, lineHeight = 22.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    listOf(s.cat, urlHost(s.url)).filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    fontSize = 11.5.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = LocalXyPal.current.mute
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { local.toggleFavLink(s.url) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Star, "取消收藏",
                                    tint = LocalXyPal.current.inkA2,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (shown.isEmpty()) {
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
}

/* -------------------------- 阅读页：朗读语速 / 定时 -------------------------- */

/** 一句话提示。App 里到处都是 Toast.makeText，这里收一处，省得每处都写三行 */
private fun brief(ctx: android.content.Context, msg: String) =
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

/**
 * 念着的时候顶在正文上沿的一排小胶囊：语速四档 + 定时停止。
 *
 * 为什么只在「正在念」时出现：平时看不见，就不占地方，也不用担心顶栏挤。
 * 整排可以横滑 —— 小屏上七颗胶囊排不满一行，硬塞会压成两行把正文挤下去。
 */
@Composable
private fun VoiceChips(spk: Speaker) {
    val pal = LocalXyPal.current
    listOf(0.8f to "0.8×", 1f to "1.0×", 1.25f to "1.25×", 1.5f to "1.5×").forEach { (r, label) ->
        MiniChip(label, spk.rate == r) { spk.updateRate(r) }
    }
    Box(Modifier.width(1.dp).height(16.dp).background(pal.outline))
    listOf(0, 15, 30, 60).forEach { m ->
        MiniChip(
            text = if (m == 0) "不定时" else "定时 $m 分",
            active = m != 0 && spk.sleepMin == m
        ) { spk.sleepAfter(m) }
    }
}

@Composable
private fun MiniChip(text: String, active: Boolean, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Text(
        text,
        fontSize = 11.5.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) XY_INK else pal.dim,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(
                if (active) Modifier.background(pal.grad())
                else Modifier.background(pal.glassHi)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
