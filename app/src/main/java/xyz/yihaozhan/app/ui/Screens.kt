package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
fun FeedScreen(data: AppPayload, onOpen: (String) -> Unit) {
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
            "分享", "每天一小条：遇到好用的工具、看过的想法，都记在这里。",
            trailing = {
                Text(
                    "${data.stats.feed} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp
                ),
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
    CardBox(onClick = if (f.url.isNotBlank()) ({ onOpen(f.url) }) else null) {
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
fun PostsScreen(data: AppPayload, onOpen: (Post) -> Unit) {
    val posts = remember(data) { data.posts.sortedByDescending { it.date } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("文章", "长一点的东西：建站过程、工具心得、读书笔记。",
            trailing = {
                Text("${posts.size} 篇", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            })
        if (posts.isEmpty()) {
            EmptyState("✍️", "还没有文章", "站点后台「文章」页写一篇，这里就会出来。")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(posts, key = { it.id }) { p ->
                    CardBox(onClick = { onOpen(p) }) {
                        Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                        if (p.excerpt.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                p.excerpt, fontSize = 13.5.sp, lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
fun PostDetailScreen(post: Post, onBack: () -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 40.dp
        )
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                }
                Text("返回", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(post.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(post.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                post.tags.take(4).forEach { TagChip(it) }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            Spacer(Modifier.height(18.dp))
        }
        item { MarkdownView(post.body) }
        item {
            Spacer(Modifier.height(34.dp))
            TextButton(onClick = { onOpen("https://mackxin.github.io/xingyu/#/post/" + post.id) }) {
                Text("在浏览器里看这篇（带目录和评论位置）")
            }
        }
    }
}

/* ============================ 导航 ============================ */

@Composable
fun LinksScreen(data: AppPayload, onOpen: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf("") }

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

    Column(Modifier.fillMaxSize()) {
        PageHeader("导航", "${data.stats.cats} 个分类 · ${data.stats.links} 个站点，随手翻。")
        SearchField(q, { q = it }, "搜站点名、描述、网址", Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagChip("全部", { catId = "" }, active = catId.isEmpty())
            cats.forEach { c ->
                TagChip("${c.icon} ${c.name}".trim(), { catId = if (catId == c.id) "" else c.id }, active = catId == c.id)
            }
        }
        if (flat.isEmpty()) {
            EmptyState("🔍", "没找到", "换个关键词试试。")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(flat, key = { it.second.url + it.second.name }) { (catName, link) ->
                    LinkRow(catName, link, onOpen)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(catName: String, link: LinkItem, onOpen: (String) -> Unit) {
    CardBox(
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = { onOpen(link.url) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(link.icon.ifBlank { link.name.take(1) }, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(link.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                val sub = listOf(catName, link.desc).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        sub, fontSize = 12.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Rounded.ArrowOutward, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/* ============================ 关于 ============================ */

@Composable
fun AboutScreen(vm: MainVm, onOpen: (String) -> Unit) {
    val ui = vm.ui
    val data = ui.data ?: return
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(data.site.name, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (data.site.slogan.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                data.site.slogan, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium
            )
        }
        if (data.site.desc.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                data.site.desc, fontSize = 14.sp, lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("${data.stats.feed}", "条分享", Modifier.weight(1f))
            StatBox("${data.stats.posts}", "篇文章", Modifier.weight(1f))
            StatBox("${data.stats.links}", "个站点", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
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

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
