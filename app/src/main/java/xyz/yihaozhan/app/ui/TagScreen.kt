package xyz.yihaozhan.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.Post

/**
 * 标签页：把带某个 `#标签` 的东西全摆出来 —— 分享和文章一起。
 *
 * 以前点标签只是把当前列表原地筛一下，想看「这个标签下还有没有别的」就得自己换到
 * 另一页再筛一遍。这一页把两边合并，按日期倒序排，一眼看到这个标签的全貌。
 */
@Composable
fun TagScreen(
    data: AppPayload,
    tag: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Post) -> Unit
) {
    val pal = LocalXyPal.current
    val feeds = remember(data, tag) {
        data.feed.filter { it.tags.contains(tag) }.sortedByDescending { it.date }
    }
    val posts = remember(data, tag) {
        data.posts.filter { it.tags.contains(tag) }.sortedWith(
            compareByDescending<Post> { it.pin }.thenByDescending { it.date }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text("标签", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal.dim)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                    Text(
                        "#$tag",
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = pal.text
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "分享 ${feeds.size} 条 · 文章 ${posts.size} 篇",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    HairLine()
                }
            }

            if (feeds.isEmpty() && posts.isEmpty()) {
                item(key = "empty") {
                    EmptyState("🏷️", "这个标签下还没有东西", "换个标签看看，或者去站点后台给它挂上内容。")
                }
            }

            if (posts.isNotEmpty()) {
                item(key = "post-title") {
                    Text(
                        "文章", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = pal.mute, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(posts, key = { "p" + it.id }) { p ->
                    CardBox(
                        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        onClick = { onOpenPost(p) }
                    ) {
                        if (p.pin) {
                            Text("📌", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text(
                            p.title, fontSize = 16.5.sp, lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (p.excerpt.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                p.excerpt, fontSize = 13.sp, lineHeight = 21.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(p.date, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (feeds.isNotEmpty()) {
                item(key = "feed-title") {
                    Text(
                        "分享", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = pal.mute, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(feeds, key = { "f" + it.id + it.title }) { f ->
                    CardBox(
                        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        onClick = if (f.url.isNotBlank()) ({ onOpenUrl(f.url) }) else null
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                f.icon.ifBlank { "•" }, fontSize = 17.sp,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    f.title, fontSize = 15.5.sp, lineHeight = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (f.desc.isNotBlank()) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        f.desc, fontSize = 13.sp, lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    f.date, fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item(key = "note") {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        "标签是内容自己带的，改标签在站点后台。",
                        fontSize = 11.sp, color = pal.mute
                    )
                }
            }
        }
    }
}
