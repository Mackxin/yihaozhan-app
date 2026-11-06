package xyz.yihaozhan.app.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.yihaozhan.app.data.MusicLib
import xyz.yihaozhan.app.data.MusicPlay
import xyz.yihaozhan.app.data.Track

/* 通知权限：Android 13 起是运行时权限。不给也照放，只是通知栏看不到控制条
   （以及前台服务那条常驻通知会被系统藏掉）—— 所以进页面就客气地问一次。 */
private fun needNotiPermission(ctx: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 33) return false
    return androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, android.Manifest.permission.POST_NOTIFICATIONS
    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * 本地音乐（内置页）。
 *
 * 跟站点 `#/music` 是同一件事：只放**本机**的音频文件，不上传、不联网。
 * 区别是这一版是原生的 —— 音频用系统的 MediaPlayer 放，切出去还有通知栏控制
 * （网页版塞在 WebView 里，一进后台就可能被系统掐掉）。
 *
 * 页面本身不持有播放状态：一切读 [MusicPlay]，所以离开这一页歌照样在放，
 * 回来时进度和歌词位置都是接着的。
 */
@Composable
fun MusicScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalXyPal.current
    val lib = remember { MusicLib.of(ctx) }
    val tracks = lib.tracks
    var showLyr by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(Unit) { MusicPlay.attach(ctx) }

    /* 通知权限（只问一次；拒了不影响播放） */
    val askNoti = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (needNotiPermission(ctx)) {
            runCatching { askNoti.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    /* 进度心跳：只在播放时跑。400ms 一次够顺滑，也不至于把重组刷爆 */
    LaunchedEffect(MusicPlay.playing, MusicPlay.cur?.id) {
        while (MusicPlay.playing) {
            MusicPlay.syncPos()
            delay(400)
        }
    }

    /* 选歌：系统的文件选择器，一次可以挑好几首 */
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        lib.import(uris) { ok, fail ->
            val msg = when {
                ok > 0 && fail > 0 -> "加进 $ok 首，$fail 首没读出来"
                ok > 0 -> "加进 $ok 首"
                else -> "这几首没读出来（格式不支持？）"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            /* 刚加进来就放第一首，省一次点击 */
            if (ok > 0 && MusicPlay.cur == null) {
                val now = MusicLib.of(ctx).tracks
                if (now.isNotEmpty()) MusicPlay.setQueue(now, 0, autoplay = false)
            }
        }
    }

    /* 外挂歌词：挑 .lrc，按文件名跟已有曲目对上 */
    val pickLrc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        lib.attachLrc(uris) { hit ->
            Toast.makeText(
                ctx,
                if (hit > 0) "配上 $hit 首的歌词" else "没对上：歌词文件名要和歌名一样",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        /* 顶行：返回 + 标题 + 选歌。内置页都这个格局（底栏留着，返回只退这一页） */
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = pal.text)
            }
            Text(
                "本地音乐",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = pal.dim,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            )
            TextButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                Text("＋ 选歌", fontSize = 13.sp, color = pal.inkA1)
            }
        }

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "head") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "本地音乐",
                        fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (tracks.isEmpty()) "放本机的歌，不上传、不联网。"
                        else "${tracks.size} 首 · 共 ${sizeText(lib.totalSize)} · 只在这台手机上",
                        fontSize = 13.sp, lineHeight = 21.sp,
                        textAlign = TextAlign.Center, color = pal.dim
                    )
                    Spacer(Modifier.height(14.dp))
                    HairLine()
                }
            }

            item(key = "player") {
                PlayerCard(
                    lib = lib,
                    showLyr = showLyr,
                    onToggleLyr = { showLyr = !showLyr },
                    onPick = { pickAudio.launch(arrayOf("audio/*")) }
                )
            }

            if (showLyr && !MusicPlay.cur?.lrc.isNullOrBlank()) {
                item(key = "lyr") {
                    LyricsPanel(
                        lrc = MusicPlay.cur?.lrc.orEmpty(),
                        posMs = MusicPlay.posMs,
                        onSeek = { MusicPlay.seekTo(it) }
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                item(key = "sec") {
                    SectionTitle(
                        "曲库 ${tracks.size} 首",
                        action = "配歌词",
                        onAction = { pickLrc.launch(arrayOf("*/*")) }
                    )
                }
                items(tracks, key = { it.id }) { t ->
                    TrackRow(
                        lib = lib,
                        t = t,
                        rev = lib.rev,
                        current = MusicPlay.cur?.id == t.id,
                        playing = MusicPlay.cur?.id == t.id && MusicPlay.playing,
                        onPlay = {
                            val i = tracks.indexOfFirst { it.id == t.id }
                            if (MusicPlay.cur?.id == t.id) MusicPlay.toggle()
                            else MusicPlay.setQueue(tracks, i.coerceAtLeast(0), true)
                        },
                        onLong = { del = t }
                    )
                }
            } else {
                item(key = "empty") {
                    EmptyState(
                        "🎧", "还没有歌",
                        "点右上角「＋ 选歌」从手机里挑几首。文件会复制进 App 私有目录，" +
                            "之后删掉原文件也能照常播。"
                    )
                }
            }
        }
    }

    /* 长按删除：文件一起删，给个确认（删了就找不回来了） */
    del?.let { t ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("删掉这首？", fontSize = 16.sp) },
            text = {
                Text(
                    "「${t.title}」会从曲库里移除，复制过来的音频文件也一起删掉。",
                    fontSize = 13.sp, lineHeight = 21.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (MusicPlay.cur?.id == t.id) MusicPlay.stop()
                    lib.remove(t.id)
                    del = null
                }) { Text("删掉", color = pal.inkA1) }
            },
            dismissButton = {
                TextButton(onClick = { del = null }) { Text("算了", color = pal.dim) }
            }
        )
    }
}

/* ---------------------------------- 播放器 ---------------------------------- */

@Composable
private fun PlayerCard(
    lib: MusicLib,
    showLyr: Boolean,
    onToggleLyr: () -> Unit,
    onPick: () -> Unit
) {
    val pal = LocalXyPal.current
    val t = MusicPlay.cur
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = MusicPlay.durMs.coerceAtLeast(1L)
    val shown = drag ?: MusicPlay.posMs.toFloat()

    CardBox(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(lib, t, 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t?.title?.ifBlank { "未命名" } ?: "还没选歌",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (t == null) "点「＋ 选歌」把歌加进来"
                    else t.artist.ifBlank { "未知歌手" },
                    fontSize = 12.sp,
                    color = pal.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Slider(
            value = (shown / dur.toFloat()).coerceIn(0f, 1f),
            onValueChange = { drag = it * dur },
            onValueChangeFinished = {
                drag?.let { MusicPlay.seekTo(it.toLong()) }
                drag = null
            },
            enabled = t != null,
            colors = SliderDefaults.colors(
                thumbColor = pal.inkA1,
                activeTrackColor = pal.inkA1,
                inactiveTrackColor = pal.glassHi
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(clock(shown.toLong()), fontSize = 11.sp, color = pal.mute)
            Spacer(Modifier.weight(1f))
            Text(clock(if (t == null) 0L else MusicPlay.durMs), fontSize = 11.sp, color = pal.mute)
        }

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CtrlBtn(
                icon = when (MusicPlay.mode) {
                    1 -> Icons.Rounded.RepeatOne
                    2 -> Icons.Rounded.Shuffle
                    else -> Icons.Rounded.Repeat
                },
                desc = MusicPlay.modeName,
                tint = if (MusicPlay.mode == 0) pal.mute else pal.inkA1
            ) { MusicPlay.cycleMode() }

            CtrlBtn(Icons.Rounded.SkipPrevious, "上一首", pal.text) { MusicPlay.prev() }

            /* 主按钮：渐变圆，跟站点的 .mu-play 一个意思。
               Brush / Color 不能写进同一个 if —— 两者共同父类是 Any，
               `background()` 的两个重载都匹配不上（坑里记着这条） */
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .then(
                        if (t == null) Modifier.background(pal.glassHi)
                        else Modifier.background(pal.grad())
                    )
                    .clickable { if (t == null) onPick() else MusicPlay.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (MusicPlay.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (MusicPlay.playing) "暂停" else "播放",
                    Modifier.size(26.dp),
                    tint = if (t == null) pal.dim else XY_INK
                )
            }

            CtrlBtn(Icons.Rounded.SkipNext, "下一首", pal.text) { MusicPlay.next() }

            CtrlBtn(
                icon = Icons.Rounded.Subtitles,
                desc = "歌词",
                tint = if (showLyr) pal.inkA1 else pal.mute
            ) { onToggleLyr() }
        }
    }
}

@Composable
private fun CtrlBtn(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, desc, Modifier.size(22.dp), tint = tint)
    }
}

/* ---------------------------------- 歌词 ---------------------------------- */

@Composable
private fun LyricsPanel(lrc: String, posMs: Long, onSeek: (Long) -> Unit) {
    val pal = LocalXyPal.current
    val lines = remember(lrc) { parseLrc(lrc) }
    val state = rememberLazyListState()

    if (lines.isEmpty()) {
        /* 没有时间轴（纯文本歌词 / 格式没认出来）：原样贴出来，至少能看 */
        val scroll = androidx.compose.foundation.rememberScrollState()
        CardBox(padding = PaddingValues(14.dp)) {
            Text(
                lrc.trim(),
                fontSize = 13.sp, lineHeight = 24.sp, color = pal.dim,
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(scroll)
            )
        }
        return
    }

    val cur = remember(lines, posMs) { lines.indexOfLast { it.first <= posMs } }
    /* 只滚歌词框：外层列表不动，正文位置不丢 */
    LaunchedEffect(cur) {
        if (cur >= 0) state.animateScrollToItem((cur - 2).coerceAtLeast(0))
    }

    CardBox(padding = PaddingValues(vertical = 10.dp)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines.size) { i ->
                val on = i == cur
                Text(
                    lines[i].second,
                    fontSize = 13.sp,
                    lineHeight = 26.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) pal.inkA1 else pal.dim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSeek(lines[i].first) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * LRC 解析：`[mm:ss.xx]歌词`，一行可以挂好几个时间戳（副歌复用同一句时就这么写）。
 * 解析不出时间轴就返回空 —— 调用方会退化成「整段文本」。
 */
private fun parseLrc(text: String): List<Pair<Long, String>> {
    val re = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    val out = mutableListOf<Pair<Long, String>>()
    text.lineSequence().forEach { line ->
        val stamps = re.findAll(line).toList()
        if (stamps.isEmpty()) return@forEach
        val body = line.replace(re, "").trim()
        if (body.isEmpty()) return@forEach
        stamps.forEach { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val frac = m.groupValues[3].let { s ->
                if (s.isEmpty()) 0L else s.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            }
            out += (min * 60_000 + sec * 1_000 + frac) to body
        }
    }
    return out.sortedBy { it.first }
}

/* ---------------------------------- 列表行 ---------------------------------- */

@Composable
private fun TrackRow(
    lib: MusicLib,
    t: Track,
    rev: Int,
    current: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onLong: () -> Unit
) {
    val pal = LocalXyPal.current
    /* 长按删除，所以不能用 clickable（它和 detectTapGestures 抢指针，踩过一次） */
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(pal.rSm.dp))
            .pointerInput(t.id) {
                detectTapGestures(onTap = { onPlay() }, onLongPress = { onLong() })
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(lib, t, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title.ifBlank { "未命名" },
                fontSize = 13.5.sp,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) pal.inkA1 else pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (t.artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    t.artist,
                    fontSize = 11.sp,
                    color = pal.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        /* 正在放的这首把时长换成小喇叭/暂停标 —— 以前是给封面压一层黑，
           浅色主题下那块黑得吓人，也不好认 */
        if (playing) {
            Icon(Icons.Rounded.Pause, "正在播放", Modifier.size(13.dp), tint = pal.inkA1)
            Spacer(Modifier.width(6.dp))
        }
        Text(clock(t.durMs), fontSize = 11.sp, color = pal.mute)
    }
}

/* ---------------------------------- 封面 ---------------------------------- */

@Composable
private fun Cover(lib: MusicLib, t: Track?, size: Dp) {
    val pal = LocalXyPal.current
    /* 封面是导入时缩好的 320px JPEG；按 id + rev 缓存解码结果，别每次重组都读盘 */
    val bmp: ImageBitmap? = remember(t?.id, lib.rev) {
        if (t == null) null else lib.coverFile(t)?.let { f ->
            runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 44.dp) 12.dp else 10.dp))
            .background(pal.glassHi),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("♪", fontSize = (size.value * 0.42f).sp, color = pal.inkA1)
        }
    }
}

/* ---------------------------------- 小工具 ---------------------------------- */

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${if (s < 10) "0$s" else "$s"}"
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / 1073741824.0)
    bytes >= 1024L * 1024L -> String.format("%.0f MB", bytes / 1048576.0)
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
