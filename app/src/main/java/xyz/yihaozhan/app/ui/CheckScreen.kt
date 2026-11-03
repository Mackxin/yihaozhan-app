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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.LinkItem
import xyz.yihaozhan.app.data.Local
import java.util.concurrent.TimeUnit

/**
 * 站点巡检（2.6.7）。
 *
 * 站点存在 app.json 里，几年下来难免有打不开的（换了域名、关了站）。
 * 这一页随机抽一批真的去敲一下：能通的留，打不开的可以一键标「失效」，
 * 标过之后首页 / 导航里的卡片会淡下去、名字划一道，不再抢视线（复用 [Local.dead]）。
 *
 * 只抽一部分而不是全查：三百个站点全敲一遍要几分钟，而且很费流量；
 * 每次抽「一批」，多点几次就轮过来了。
 */
@Composable
fun CheckScreen(
    data: AppPayload,
    local: Local,
    onOpen: (String) -> Unit,
    onBack: () -> Unit
) {
    val pal = LocalXyPal.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    /* 去重：同一个网址在好几个分类里出现时只查一次 */
    val all = remember(data) {
        val seen = LinkedHashMap<String, Pair<String, LinkItem>>()
        data.cats.forEach { c ->
            c.links.forEach { l -> if (l.url.isNotBlank()) seen.putIfAbsent(l.url, c.name to l) }
        }
        seen.values.toList()
    }

    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<CheckRow>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }

    val deadList = remember(data, local.dead) {
        all.filter { local.dead.contains(it.second.url) }
    }

    fun start() {
        if (running || all.isEmpty()) return
        /* 一次抽查 24 个：手机上 6 并发 × 4 轮，大约 5~10 秒出结果 */
        val picks = all.shuffled().take(24)
        scope.launch {
            running = true
            done = 0
            total = picks.size
            rows = emptyList()
            val out = coroutineScope {
                picks.chunked(6).flatMap { batch ->
                    /* 一批 6 个并发；一批收完再算一次进度 ——
                       在 IO 线程里直接改 Compose 的状态不安全，也没必要逐条刷 */
                    val res = batch.map { one ->
                        async(Dispatchers.IO) { probe(one.second.name, one.second.url) }
                    }.awaitAll()
                    done += res.size
                    res
                }
            }
            rows = out.sortedWith(compareBy({ it.ok }, { it.name }))
            running = false
            scanned = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                "站点巡检",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("head") {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Eyebrow("Health · 巡检")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "站点巡检",
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = pal.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "随机抽 24 个站点真的敲一下，把打不开的挑出来。查出来的可以一键标「失效」——" +
                            "标过的卡片会淡下去，不会从列表里删掉。",
                        fontSize = 15.sp,
                        lineHeight = 25.sp,
                        color = pal.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    StatRow {
                        StatBox("${all.size}", "个站点", Modifier.weight(1f))
                        StatBox("${local.dead.size}", "标了失效", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            /* 分支写在 Modifier 里：Brush 和 Color 的共同父类是 Any，
                               `if (…) brush else color` 会退化成 Any，两个 background 重载都匹配不上 */
                            .then(
                                if (running) Modifier.background(pal.glass)
                                else Modifier.background(pal.grad())
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { start() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                running -> "正在检查… $done / $total"
                                scanned -> "再抽 24 个查一遍"
                                else -> "开始检查"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (running) pal.dim else XY_INK
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (rows.isNotEmpty()) {
                item("h-result") { SubTitle("这次查了 ${rows.size} 个 · 打不开 ${rows.count { !it.ok }} 个") }
                items(rows, key = { "r-" + it.url }) { r ->
                    CheckCard(r) {
                        val on = local.toggleDead(r.url)
                        Toast.makeText(
                            ctx,
                            if (on) "已标为失效：${r.name}" else "已恢复：${r.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            /* 之前标过失效的都在这儿，点一下就能摘掉那个标记 ——
               不然标错了就再也回不来（列表里那张卡已经淡得看不清了） */
            if (deadList.isNotEmpty()) {
                item("h-dead") { SubTitle("标了失效的 ${deadList.size} 个") }
                items(deadList, key = { "d-" + it.second.url }) { one ->
                    CardBox(
                        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onClick = { onOpen(one.second.url) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                one.second.name,
                                fontSize = 13.5.sp,
                                color = pal.mute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "恢复",
                                fontSize = 12.sp,
                                color = pal.inkA1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { local.toggleDead(one.second.url) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (all.isEmpty()) {
                item("empty") {
                    EmptyState("🗂", "还没有站点", "同步一次内容之后再来。")
                }
            }
        }
    }
}

/** 一次检查的结果 */
private data class CheckRow(
    val name: String,
    val url: String,
    val ok: Boolean,
    val code: Int,
    val ms: Long
)

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalXyPal.current.dim,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
}

@Composable
private fun CheckCard(r: CheckRow, onToggleDead: () -> Unit) {
    val pal = LocalXyPal.current
    CardBox(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (r.ok) "🟢" else "🔴", fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.name,
                    fontSize = 13.5.sp,
                    color = if (r.ok) pal.text else pal.text.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    r.url,
                    fontSize = 10.5.sp,
                    color = pal.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (r.ok) "${r.ms} ms" else if (r.code > 0) "HTTP ${r.code}" else "连不上",
                fontSize = 11.sp,
                color = if (r.ok) pal.mute else pal.inkA2,
                maxLines = 1
            )
            if (!r.ok) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "标失效",
                    fontSize = 12.sp,
                    color = pal.inkA1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleDead() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/* ------------------------------ 真的去敲一下 ------------------------------ */

private val probeClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

/**
 * 探一个网址通不通。
 *
 * 先 HEAD（省流量），被拒（405/403/501）再退回 GET 只取头一个字节 ——
 * 不少站点禁 HEAD，直接判「打不开」是误杀。
 */
private suspend fun probe(name: String, url: String): CheckRow = withContext(Dispatchers.IO) {
    val t0 = System.currentTimeMillis()
    fun call(method: String): Pair<Boolean, Int> = runCatching {
        val b = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (yihaozhan-app)")
        if (method == "HEAD") b.head() else b.get().header("Range", "bytes=0-0")
        probeClient.newCall(b.build()).execute().use { r ->
            (r.code in 200..399 || r.code == 416) to r.code
        }
    }.getOrDefault(false to 0)

    var (ok, code) = call("HEAD")
    if (!ok && (code == 403 || code == 405 || code == 501 || code == 0)) {
        val (ok2, code2) = call("GET")
        if (ok2) {
            ok = true
            code = code2
        } else if (code == 0) {
            code = code2
        }
    }
    CheckRow(name = name, url = url, ok = ok, code = code, ms = System.currentTimeMillis() - t0)
}
