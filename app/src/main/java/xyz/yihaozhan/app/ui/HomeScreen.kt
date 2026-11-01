package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.FeedItem
import xyz.yihaozhan.app.data.Post
import kotlinx.coroutines.launch

/**
 * 首页：站点门面 + 两枚快捷入口 + 最近分享 + 最新文章。
 *
 * 原来 App 一打开直接落在「分享」列表上，像从侧门进屋；
 * 首页负责回答「这是个什么地方、最近更新了什么」。
 */
@Composable
fun HomeScreen(
    data: AppPayload,
    offline: Boolean,
    onOpen: (String) -> Unit,
    onGoTab: (Int) -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenAbout: () -> Unit = {},
    /* 列表滚动状态可以由外面持有 —— 顶栏左上角那颗品牌也要能「点一下到底 / 再点回顶」，
       两处入口必须通同一份状态，不然两边的「到底了没」会各算各的 */
    listState: LazyListState = rememberLazyListState()
) {
    val recent = remember(data) { data.feed.sortedByDescending { it.date }.take(5) }
    val latest = remember(data) { orderedPosts(data.posts).take(2) }
    /* 今日精选：后台指定过就只显示那几条，没指定就跟「最近分享」重了，索性整块不出现 */
    val featured = remember(data) {
        val h = data.home ?: return@remember emptyList()
        val all = data.feed.sortedByDescending { it.date }
        if (h.featuredPicks.isEmpty()) emptyList()
        else all.filter { f -> h.featuredPicks.contains(f.date + "|" + f.title) }
    }
    /* 点大标题「壹号栈」：一下滑到页面底部，再点一下回到顶部。标题本身留着不动。
       动作跟顶栏那颗品牌共用一份（Common.kt 的 rememberEndToggle）。 */
    val toggleEnd = rememberEndToggle(listState)

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item { Hero(data, offline, toggleEnd) }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickCard(
                    "逛逛导航",
                    "${data.stats.links} 个站点",
                    { Icons.Rounded.GridView },
                    Modifier.weight(1f)
                ) { onGoTab(3) }
                QuickCard(
                    "读篇文章",
                    "${data.stats.posts} 篇长文",
                    { Icons.AutoMirrored.Rounded.MenuBook },
                    Modifier.weight(1f)
                ) { onGoTab(2) }
            }
        }

        if (featured.isNotEmpty()) {
            item {
                SectionTitle(
                    data.home?.featuredTitle?.ifBlank { "今日精选" } ?: "今日精选",
                    "全部", { onGoTab(1) }
                )
            }
            item {
                val sub = data.home?.featuredDesc.orEmpty()
                if (sub.isNotBlank()) {
                    Text(
                        sub, fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                    )
                }
            }
            items(featured, key = { "hf" + it.id + it.title }) { f ->
                Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    MiniFeedRow(f, onOpen)
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                SectionTitle("最近分享", "全部", { onGoTab(1) })
            }
            items(recent, key = { "h" + it.id + it.title }) { f ->
                Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    MiniFeedRow(f, onOpen)
                }
            }
        }

        if (latest.isNotEmpty()) {
            item { SectionTitle("最新文章", "全部", { onGoTab(2) }) }
            items(latest, key = { "hp" + it.id }) { p ->
                Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    MiniPostRow(p) { onOpenPost(p) }
                }
            }
        }

        /* 关于这一栈：站点 `#/about` 那个门，进来先看个大概再走 */
        item {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                CardBox(onClick = onOpenAbout) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                data.about?.title?.ifBlank { "关于这一栈" } ?: "关于这一栈",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                            val lede = data.about?.lede.orEmpty()
                            if (lede.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    lede, fontSize = 11.5.sp, lineHeight = 18.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Rounded.ArrowOutward, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        /* 赞助：站点首页最下面那块。App 里没放收款码图（不引图片库），
           只留文案 + 一颗「在浏览器里看」，真要扫还是回网页 */
        val home = data.home
        if (home != null && home.sponsorOn && home.sponsorTitle.isNotBlank()) {
            item {
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(LocalXyPal.current.rLg.dp))
                            .background(LocalXyPal.current.glass)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("☕", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                home.sponsorTitle, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                            )
                        }
                        if (home.sponsorDesc.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                home.sponsorDesc, fontSize = 11.5.sp, lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (home.sponsorNote.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                home.sponsorNote, fontSize = 11.sp,
                                color = LocalXyPal.current.mute
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(LocalXyPal.current.glassHi)
                                .clickable {
                                    onOpen(
                                        home.sponsorLink.ifBlank { data.site.url }
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("在浏览器里看", fontSize = 12.sp, color = LocalXyPal.current.inkA1)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.ArrowOutward, null,
                                tint = LocalXyPal.current.inkA1,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
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
        BackToTopFab(listState)
    }
}

/* ------------------------------ 门面 ------------------------------ */

@Composable
private fun Hero(data: AppPayload, offline: Boolean, onTitleClick: () -> Unit = {}) {
    val pal = LocalXyPal.current
    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (data.site.since.isNotBlank()) {
                Text(
                    data.site.since, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                data.site.name,
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                textAlign = TextAlign.Center,
                color = pal.text,
                /* 标题不做任何视觉改动，只是能点：一下到底、再一下回顶（跟站点点标题一个彩蛋） */
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTitleClick() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (data.site.slogan.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    data.site.slogan, fontSize = 15.sp, lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (data.site.desc.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    data.site.desc, fontSize = 13.5.sp, lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (offline) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CloudOff, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "离线中，正在看手机里存的那一份",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            /* 成长天数：站点后台填的「建站日期」算到今天是第几天，跟网页首页那格一个口径。
               没填日期（老 json）就只显示三格，不留一个「0 天」 */
            val days = remember(data) { siteDays(data.home?.startDate.orEmpty()) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("${data.stats.feed}", "条分享", Modifier.weight(1f))
                StatBox("${data.stats.posts}", "篇文章", Modifier.weight(1f))
                StatBox("${data.stats.links}", "个站点", Modifier.weight(1f))
                if (days > 0) StatBox("$days", "天成长", Modifier.weight(1f))
            }
        }
    }
}

/* --------------------------- 快捷入口 / 小卡片 --------------------------- */

/** `2024-01-01` → 到今天第几天；没填 / 格式不对 → 0（不显示那一格） */
private fun siteDays(start: String): Int {
    if (start.length < 10) return 0
    return runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        val d = fmt.parse(start.substring(0, 10)) ?: return 0
        val n = ((System.currentTimeMillis() - d.time) / 86400000L).toInt() + 1
        n.coerceAtLeast(0)
    }.getOrDefault(0)
}

@Composable
private fun QuickCard(
    title: String,
    sub: String,
    icon: () -> androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Row(
        modifier
            .clip(RoundedCornerShape(pal.rMd.dp))
            .background(pal.glass)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(pal.glassHi),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon(), null, tint = pal.inkA1, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniFeedRow(f: FeedItem, onOpen: (String) -> Unit) {
    CardBox(
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = if (f.url.isNotBlank()) ({ onOpen(f.url) }) else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.icon.ifBlank { "•" }, fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.title, fontSize = 14.5.sp, lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listOf(f.date, f.desc.lineSequence().firstOrNull().orEmpty())
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniPostRow(p: Post, onClick: () -> Unit) {
    CardBox(padding = PaddingValues(horizontal = 14.dp, vertical = 13.dp), onClick = onClick) {
        Text(p.title, fontSize = 15.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
        if (p.excerpt.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                p.excerpt, fontSize = 12.5.sp, lineHeight = 20.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
