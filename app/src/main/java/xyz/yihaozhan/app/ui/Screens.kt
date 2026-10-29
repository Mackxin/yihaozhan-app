package xyz.yihaozhan.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.MainVm
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.FeedItem
import xyz.yihaozhan.app.data.LinkItem
import xyz.yihaozhan.app.data.Post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================ 分享 ============================ */

@Composable
fun FeedScreen(data: AppPayload, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }

    val all = remember(data) { data.feed.sortedByDescending { it.date } }
    val tags = remember(all) {
        all.flatMap { it.tags }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(12).map { it.key }
    }
    val list = remember(q, tag, all) {
        all.filter { f ->
            (tag.isEmpty() || f.tags.contains(tag)) &&
                (q.isBlank() || (f.title + " " + f.desc + " " + f.tags.joinToString(" "))
                    .lowercase().contains(q.trim().lowercase()))
        }
    }
    val groups = remember(list) {
        list.groupBy { it.date }.entries.sortedByDescending { it.key }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "分享",
            "每天一小条：遇到好用的工具、看过的想法，都记在这里。",
            meta = "${data.stats.feed} 条",
            onSettings = onSettings
        )
        SearchField(q, { q = it }, "搜标题、描述、标签", Modifier.padding(horizontal = 20.dp))
        if (tags.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TagChip("全部", { tag = "" }, active = tag.isEmpty())
                tags.forEach { t -> TagChip(t, { tag = if (tag == t) "" else t }, active = tag == t) }
            }
        }
        if (list.isEmpty()) {
            EmptyState("🍃", "没找到", "换个词试试，或者把筛选清掉。")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                        FeedCard(f, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(f: FeedItem, onOpen: (String) -> Unit) {
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
                        f.tags.take(4).forEach { TagChip(it) }
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
fun PostsScreen(data: AppPayload, onOpen: (Post) -> Unit, onSettings: () -> Unit) {
    val posts = remember(data) { data.posts.sortedByDescending { it.date } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "文章",
            "长一点的东西：建站过程、工具心得、读书笔记。",
            meta = "${posts.size} 篇",
            onSettings = onSettings
        )
        if (posts.isEmpty()) {
            EmptyState("✍️", "还没有文章", "站点后台「文章」页写一篇，这里就会出来。")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
}

@Composable
fun PostDetailScreen(
    post: Post,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    fontScale: Float = 1f
) {
    Column(Modifier.fillMaxSize()) {
        /* 返回键留左边，标题居中 */
        Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
            }
            Text(
                "文章",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
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
                    Text(post.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (post.tags.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        post.tags.take(3).forEach {
                            TagChip(it)
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            item { MarkdownView(post.body, fontScale = fontScale) }
            item {
                Spacer(Modifier.height(34.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { onOpen("https://mackxin.github.io/xingyu/#/post/" + post.id) }) {
                        Text("在浏览器里看这篇", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/* ============================ 导航 ============================ */

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LinksScreen(data: AppPayload, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf("") }
    var catOpen by remember { mutableStateOf(false) }

    val pal = LocalXyPal.current
    val cats = remember(data) { data.cats.filter { it.links.isNotEmpty() } }
    val activeCat = remember(catId, cats) { cats.firstOrNull { it.id == catId } }

    val flat = remember(catId, q, cats) {
        val src = if (activeCat != null) listOf(activeCat) else cats
        val keyword = q.trim().lowercase()
        src.flatMap { c ->
            c.links.filter { l ->
                keyword.isEmpty() ||
                    l.name.lowercase().contains(keyword) ||
                    l.desc.lowercase().contains(keyword) ||
                    l.url.lowercase().contains(keyword)
            }.map { c.name to it }
        }
    }

    /* 抽屉开着时返回键先关抽屉 —— 不拦这一下，按返回会把 App 退出 */
    BackHandler(enabled = catOpen) { catOpen = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                "导航",
                "随手翻的一堆站点，按分类收好。",
                meta = "${data.stats.cats} 个分类 · ${data.stats.links} 个站点",
                onSettings = onSettings,
                leading = {
                    IconButton(onClick = { catOpen = true }) {
                        Icon(Icons.Rounded.Menu, "分类", tint = pal.dim)
                    }
                }
            )
            SearchField(q, { q = it }, "搜站点名、描述、网址", Modifier.padding(horizontal = 20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeCat != null) {
                    TagChip("${activeCat.icon} ${activeCat.name}".trim(), { catId = "" }, active = true)
                    Spacer(Modifier.width(8.dp))
                }
                Text("${flat.size} 个", fontSize = 11.5.sp, color = pal.mute)
            }
            if (flat.isEmpty()) {
                EmptyState("🔍", "没找到", "换个关键词试试。")
            } else {
                /* 不做一行一个了：两列瀑布，卡片高度跟着内容走 */
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 24.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        flat,
                        key = { i, item -> "L$i|" + item.second.url + "|" + item.second.name }
                    ) { _, item ->
                        LinkCard(item.first, item.second, onOpen, showCat = activeCat == null)
                    }
                }
            }
        }

        /*
         * 分类抽屉自己做，不用 ModalNavigationDrawer：
         * 它默认「整屏任意横向拖动都能拉开」，正好跟左右翻页抢手势 —— 划一下页面抽屉就被拽出来。
         * 自己画一层浮层，只认汉堡按钮，手势干干净净。
         */
        if (catOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { catOpen = false }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(312.dp),
                color = pal.bgSoft,
                shadowElevation = 0.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 12.dp)) {
                        Text(
                            "分类", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            color = pal.text
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${data.stats.cats} 个分类 · ${data.stats.links} 个站点",
                            fontSize = 12.sp, color = pal.mute
                        )
                    }
                    HairLine()
                    /* 一行摆好几个：五十个分类竖成一列要滑半天，挤成小胶囊一屏扫完，也不用滚动条 */
                    FlowRow(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CatChip("🗂", "全部", data.stats.links, catId.isEmpty()) {
                            catId = ""
                            catOpen = false
                        }
                        cats.forEach { c ->
                            CatChip(c.icon, c.name, c.links.size, catId == c.id) {
                                catId = c.id
                                catOpen = false
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 抽屉里的分类胶囊：图标 + 名字 + 站点数。
 * 选中的用站点那条渐变当底，字压深墨（白字在最浅的青色上只有 1.59:1）。
 */
@Composable
private fun CatChip(
    icon: String,
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(
                if (selected) Modifier.background(pal.grad())
                else Modifier.background(pal.glass)
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon.isNotBlank()) {
            Text(icon, fontSize = 11.5.sp)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            name,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = if (selected) XY_INK else pal.dim
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "$count",
            fontSize = 10.sp,
            color = if (selected) XY_INK.copy(alpha = 0.72f) else pal.mute
        )
    }
}

/** 导航卡片：竖着排，图标 + 名字 + 描述，高度自然跟着内容变 */
@Composable
private fun LinkCard(
    catName: String,
    link: LinkItem,
    onOpen: (String) -> Unit,
    showCat: Boolean = true
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(XyR.md.dp))
            .background(LocalXyPal.current.glass)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpen(link.url) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(link.icon.ifBlank { link.name.take(1) }, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                link.name, fontSize = 13.5.sp, lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (link.desc.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                link.desc,
                fontSize = 11.5.sp, lineHeight = 17.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        /* 已经按分类筛过时不用每张卡再喊一遍分类名 */
        if (showCat) {
            Spacer(Modifier.height(8.dp))
            Text(
                catName,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

/* ============================ 关于 ============================ */

@Composable
fun AboutScreen(vm: MainVm, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val ui = vm.ui
    val data = ui.data ?: return
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        /* 站名和标语首页已经喊过一遍了，这里不再重复，只留站点说明 */
        PageHeader("关于", subtitle = data.site.slogan.ifBlank { null }, onSettings = onSettings)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (data.site.desc.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            data.site.desc, fontSize = 13.5.sp, lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (data.site.since.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            data.site.since, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(22.dp))
                CardBox {
                    Text("内容更新", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (ui.offline) "现在没网，正在看手机里存的那一份。"
                        else "每次打开都会自动拉最新的：不用点、不用等。",
                        fontSize = 13.sp, lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "上次同步：" + lastSyncText(ui.lastSync) + " · 数据版本 " + data.build,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.refresh() }, enabled = !ui.refreshing) {
                            if (ui.refreshing) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (ui.refreshing) "正在拉最新…" else "立即检查更新", fontSize = 13.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (ui.offline) {
                            Icon(
                                Icons.Rounded.CloudOff, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("离线", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                CardBox(onClick = { onOpen(data.site.url) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("在浏览器里打开网站", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Rounded.ArrowOutward, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
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
