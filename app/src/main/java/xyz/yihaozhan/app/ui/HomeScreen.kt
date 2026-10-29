package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
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
    onSettings: () -> Unit,
    onOpen: (String) -> Unit,
    onGoTab: (Int) -> Unit,
    onOpenPost: (Post) -> Unit
) {
    val recent = remember(data) { data.feed.sortedByDescending { it.date }.take(5) }
    val latest = remember(data) { data.posts.sortedByDescending { it.date }.take(2) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item { Hero(data, onSettings, offline) }

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

        item {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
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

/* ------------------------------ 门面 ------------------------------ */

@Composable
private fun Hero(data: AppPayload, onSettings: () -> Unit, offline: Boolean) {
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
                color = pal.text
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("${data.stats.feed}", "条分享", Modifier.weight(1f))
                StatBox("${data.stats.posts}", "篇文章", Modifier.weight(1f))
                StatBox("${data.stats.links}", "个站点", Modifier.weight(1f))
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) { GearButton(onSettings) }
    }
}

/* --------------------------- 快捷入口 / 小卡片 --------------------------- */

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
            .clip(RoundedCornerShape(XyR.md.dp))
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
