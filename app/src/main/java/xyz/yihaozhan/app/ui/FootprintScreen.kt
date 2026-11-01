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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.Local
import java.util.Calendar

/**
 * 足迹 —— 网页端 `#/insight` 的原生版。
 *
 * 站点那页讲的是「你在这个站留下了什么」：常去哪些站、收藏了多少、一年写了多少。
 * App 里的「你」有一半数据在手机上（收藏、最近打开、灵感随记），
 * 所以这页把两边合起来算：内容按 `app.json` 的日期统计，个人痕迹按本机 Local 统计。
 *
 * 一条规矩：**只读，不改任何东西**。想在哪儿取消收藏还是去收藏页，
 * 这页只负责「让你看一眼自己都干了些什么」。
 */
@Composable
fun FootprintScreen(
    data: AppPayload,
    local: Local,
    onOpenUrl: (String) -> Unit,
    /* 2.6：这页从底栏页签改成了顶栏自定义入口进去的内置页，
       跟「关于」「灵感随记」一样左上角给一颗返回 */
    onBack: () -> Unit = {}
) {
    val pal = LocalXyPal.current
    val recents = local.recents

    /* 一年的产出：分享 + 文章 + 灵感，按月份堆在一起。老内容超过一年的也算进去，
       所以横轴写的是「1–12 月」，不写年份 —— 它表示的是「一年里的分布」 */
    val months = remember(data, local.ideas) {
        val arr = IntArray(12)
        data.feed.forEach { f -> monthOf(f.date)?.let { m -> arr[m]++ } }
        data.posts.forEach { p -> monthOf(p.date)?.let { m -> arr[m]++ } }
        local.ideas.forEach { i -> monthOf(isoOf(i.ts))?.let { m -> arr[m]++ } }
        arr.toList()
    }
    val total = months.sum()
    val allDays = remember(data) {
        (data.feed.map { it.date } + data.posts.map { it.date })
            .filter { it.isNotBlank() }.toSet().size
    }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text("足迹", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal.dim)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "head") {
                PageHeader(
                    "足迹",
                    "这里能看到你在这个栈里留下了什么：收藏了几处、读过多少、写了多少。数据都在你自己手机上。",
                    eyebrow = "INSIGHT · 足迹",
                    inset = 0.dp,
                    side = {
                        StatRow(inset = 0.dp) {
                            StatBox("${local.favLinks.size}", "收藏站点", Modifier.weight(1f))
                            StatBox("${local.fav.size}", "收藏文章", Modifier.weight(1f))
                            StatBox("${local.ideas.size}", "灵感随记", Modifier.weight(1f))
                        }
                    }
                )
            }

            item(key = "chart") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(pal.rLg.dp))
                        .background(pal.glass)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "一年里的产出",
                            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "共 $total 条 · $allDays 个记录日",
                            fontSize = 11.sp, color = pal.mute
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    /* 12 根柱子：高度按当月条数 / 最多的那一个月；一条都没有就留一条浅底 */
                    val peak = (months.maxOrNull() ?: 0).coerceAtLeast(1)
                    Row(
                        Modifier.fillMaxWidth().height(88.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        months.forEachIndexed { i, n ->
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(
                                            (n.toFloat() / peak * 0.82f).coerceAtLeast(0.03f)
                                        )
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (n == 0) pal.glass2 else pal.inkA1)
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "${i + 1}",
                                    fontSize = 9.sp, color = pal.mute
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "柱子是「那条内容自己的日期」，不含你读了多久 —— 阅读时长不上传。",
                        fontSize = 10.5.sp, lineHeight = 16.sp, color = pal.mute
                    )
                }
            }

            item(key = "rd-title") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "最近打开",
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = pal.mute, modifier = Modifier.weight(1f)
                    )
                    if (recents.isNotEmpty()) {
                        TinyBtn("清空") { local.clearRecents() }
                    }
                }
            }

            if (recents.isEmpty()) {
                item(key = "empty") {
                    EmptyState("🧭", "还没有足迹", "去导航页随便点开一个站点，这里就会记下来。")
                }
            } else {
                items(recents.take(20), key = { "r" + it.url }) { r ->
                    CardBox(
                        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onClick = { onOpenUrl(r.url) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.name.ifBlank { r.url }, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    urlHost(r.url), fontSize = 11.sp, color = pal.mute,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                agoText(r.ts), fontSize = 11.sp, color = pal.mute
                            )
                        }
                    }
                }
            }

            item(key = "note") {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    HairLine()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "读到第几段（${local.posCount} 篇记过位置）、灵感随记、收藏 —— 全存在本机，"
                            + "换手机就带不走，也没有任何一份上传。",
                        fontSize = 11.sp, lineHeight = 18.sp, color = pal.mute
                    )
                }
            }
        }
        }
        BackToTopFab(listState)
    }
}

/* ------------------------------ 小工具 ------------------------------ */

/** `2026-10-31` → 9（月下标，0 开始）；不认识就 null */
private fun monthOf(iso: String): Int? {
    if (iso.length < 7) return null
    val m = iso.substring(5, 7).toIntOrNull() ?: return null
    return if (m in 1..12) m - 1 else null
}

/** 时间戳 → `yyyy-MM-dd`，只为了取月份 */
private fun isoOf(ts: Long): String {
    if (ts <= 0L) return ""
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val y = c.get(Calendar.YEAR)
    val m = c.get(Calendar.MONTH) + 1
    val d = c.get(Calendar.DAY_OF_MONTH)
    return "$y-" + (if (m < 10) "0$m" else "$m") + "-" + (if (d < 10) "0$d" else "$d")
}

/** 「刚刚 / 3 分钟前 / 昨天 / 5 天前 / 3 个月前」——足迹里只需要一个模糊的远近 */
private fun agoText(ts: Long): String {
    if (ts <= 0L) return ""
    val diff = System.currentTimeMillis() - ts
    if (diff < 0) return "刚刚"
    val min = diff / 60000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "$min 分钟前"
        min < 60 * 24 -> "${min / 60} 小时前"
        min < 60 * 24 * 30 -> "${min / (60 * 24)} 天前"
        else -> "${min / (60 * 24 * 30)} 个月前"
    }
}
