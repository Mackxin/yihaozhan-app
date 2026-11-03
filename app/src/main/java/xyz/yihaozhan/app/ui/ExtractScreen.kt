package xyz.yihaozhan.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.Extract
import xyz.yihaozhan.app.data.Local
import xyz.yihaozhan.app.data.Post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 摘录本：阅读页长按「存为摘录」攒下来的段落，全在这一页。
 *
 * 跟灵感随记分开的两点理由：
 *  ① 摘录天生带出处（哪篇文章），随记是「我自己写的」，混在一起出处就丢了；
 *  ② 摘录往往是「想回头再读一遍」的，随记是「想以后用上」的，动作不一样。
 * 但两边能互通：「转成随记」会把这一段复制进随记列表（摘录本身保留）。
 *
 * 数据在 [Local]，只存这台手机，不跟 app.json 走。
 */
@Composable
fun ExtractScreen(
    local: Local,
    posts: List<Post>,
    onOpenPost: (Post) -> Unit,
    onBack: () -> Unit
) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    var askClear by remember { mutableStateOf(false) }

    val list = local.sortedExtracts
    /* 按文章归堆：同一篇里的几段得连在一起看才有上下文 */
    val byPost = remember(list) {
        list.groupBy { it.postId }.entries
            .sortedByDescending { e -> e.value.maxOfOrNull { it.ts } ?: 0L }
            .map { e -> e.key to e.value }
    }
    val postById = remember(posts) { posts.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                "摘录",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
            if (list.isNotEmpty()) {
                IconButton(onClick = { askClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, "清空", tint = pal.mute)
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("head") {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Eyebrow("Clipping · 摘录")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "摘录本",
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = pal.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "读文章时长按任意一段就能存进来。摘的是一整段，出处也一起记着 —— 都在本机，不上传。",
                        fontSize = 15.sp,
                        lineHeight = 25.sp,
                        color = pal.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    StatRow {
                        StatBox("${list.size}", "段摘录", Modifier.weight(1f))
                        StatBox("${byPost.size}", "篇来源", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (list.isEmpty()) {
                item("empty") {
                    EmptyState("✂️", "还没有摘录", "随便打开一篇文章，长按觉得好的一段，就能存到这儿。")
                }
            }

            byPost.forEach { (postId, items) ->
                item(key = "src-" + postId) {
                    val post = postById[postId]
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            post?.title ?: items.first().postTitle.ifBlank { "（这篇已经不在站点了）" },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (post != null) pal.inkA1 else pal.mute,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (post != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onOpenPost(post) }
                                            .padding(vertical = 4.dp)
                                    } else Modifier
                                )
                        )
                        Text("${items.size} 段", fontSize = 11.5.sp, color = pal.mute)
                    }
                }
                items(items, key = { it.id }) { one ->
                    ExtractCard(
                        one = one,
                        onToIdea = {
                            if (local.addIdea(it.text)) {
                                Toast.makeText(ctx, "已转成随记", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCopy = {
                            copyText(ctx, it.text)
                            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { local.removeExtract(it.id) },
                        onOpen = { postById[one.postId]?.let(onOpenPost) }
                    )
                }
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            confirmButton = {
                TextButton(onClick = { local.clearExtracts(); askClear = false }) {
                    Text("全清掉", color = pal.inkA1)
                }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text("算了", color = pal.mute) }
            },
            title = { Text("清空摘录本？", color = pal.text) },
            text = {
                Text(
                    "一共 ${list.size} 段，清了就找不回来了（随记里转过去的那几份不受影响）。",
                    color = pal.dim,
                    fontSize = 13.sp
                )
            },
            containerColor = pal.bgSoft
        )
    }
}

/** 一段摘录：左边一道渐变竖线（像书页上划出来的那一条），右边正文 + 出处 + 动作 */
@Composable
private fun ExtractCard(
    one: Extract,
    onToIdea: (Extract) -> Unit,
    onCopy: (Extract) -> Unit,
    onDelete: (Extract) -> Unit,
    onOpen: () -> Unit
) {
    val pal = LocalXyPal.current
    CardBox(padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
        /* height(IntrinsicSize.Min) + fillMaxHeight：让左边那道竖线跟着文字长，
           写死高度的话，摘的段落一长（三五行）竖线就只剩半截，看着像断了 */
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(pal.grad())
            )
            Spacer(Modifier.width(12.dp))
            Text(
                one.text,
                fontSize = 14.5.sp,
                lineHeight = 25.sp,
                fontStyle = FontStyle.Italic,
                color = pal.text,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmtDayShort(one.ts),
                fontSize = 11.5.sp,
                color = pal.mute,
                modifier = Modifier.weight(1f)
            )
            TinyText("看原文", onClick = onOpen)
            TinyText("转随记", strong = true) { onToIdea(one) }
            TinyText("复制") { onCopy(one) }
            TinyText("删掉") { onDelete(one) }
        }
    }
}

@Composable
private fun TinyText(text: String, strong: Boolean = false, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (strong) pal.glassHi else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = if (strong) pal.inkA1 else pal.dim)
    }
}

private fun fmtDayShort(ts: Long): String {
    if (ts <= 0L) return ""
    return SimpleDateFormat("M 月 d 日", Locale.CHINA).format(Date(ts))
}
