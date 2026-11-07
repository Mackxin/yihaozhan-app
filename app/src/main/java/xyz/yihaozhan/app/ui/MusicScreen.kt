package xyz.yihaozhan.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.yihaozhan.app.data.MusicLib
import xyz.yihaozhan.app.data.MusicPlay
import xyz.yihaozhan.app.data.Playlist
import xyz.yihaozhan.app.data.Track

/* 通知权限：Android 13 起是运行时权限。不给也照放，只是通知栏看不到控制条
   （以及前台服务那条常驻通知会被系统藏掉）—— 所以进页面就客气地问一次。 */
private fun needNotiPermission(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    return androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.POST_NOTIFICATIONS
    ) != PackageManager.PERMISSION_GRANTED
}

/**
 * 读本机音频的权限：Android 13 起从「存储」里拆出来一个 READ_MEDIA_AUDIO，
 * 老系统还得用 READ_EXTERNAL_STORAGE。
 */
private fun audioPerm(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPerm(ctx: Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, audioPerm()) ==
        PackageManager.PERMISSION_GRANTED

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
    var fsLyr by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Track?>(null) }
    var addTo by remember { mutableStateOf<Track?>(null) }
    var del by remember { mutableStateOf<Track?>(null) }
    var scanHits by remember { mutableStateOf<List<Uri>?>(null) }
    /* 歌单相关的四个弹窗各管一件事，别合成一个状态机 —— 合过一次，两个框会一起弹出来 */
    var listMenu by remember { mutableStateOf<Playlist?>(null) }
    var listDel by remember { mutableStateOf<Playlist?>(null) }
    var listEdit by remember { mutableStateOf<Playlist?>(null) }
    var listNew by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }

    /* "" = 全部；否则是某个歌单的 id。切歌单只影响「看哪一列」，不影响正在放的那首 */
    var tab by remember { mutableStateOf("") }
    val list = lib.lists.firstOrNull { it.id == tab }
    val shown = if (list == null) tracks else lib.listTracks(list)

    /* 点某首就按「当前这一列」起队列。
       队列必须**在点击那一刻**现算：写在 items 的 lambda 里会拿到上一次重组的 shown，
       从一个歌单里点歌，播完却接着放全库（踩过）。 */
    val playOne: (Track) -> Unit = { t ->
        val cur = lib.lists.firstOrNull { it.id == tab }
        val q = if (cur == null) lib.tracks else lib.listTracks(cur)
        if (MusicPlay.cur?.id == t.id) {
            MusicPlay.toggle()
        } else {
            val i = q.indexOfFirst { it.id == t.id }
            MusicPlay.setQueue(q, i.coerceAtLeast(0), true)
        }
    }

    LaunchedEffect(Unit) { MusicPlay.attach(ctx) }

    /* 通知权限（只问一次；拒了不影响播放） */
    val askNoti = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (needNotiPermission(ctx)) {
            runCatching { askNoti.launch(Manifest.permission.POST_NOTIFICATIONS) }
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

    /* 扫本机：先要权限，再问「要不要都加进来」—— 别一声不吭把几百首复制进私有目录 */
    val askAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) lib.scanDevice { scanHits = it }
        else Toast.makeText(ctx, "没给权限就搜不到本机的歌，还能用「＋ 选歌」手挑", Toast.LENGTH_LONG)
            .show()
    }
    val doScan: () -> Unit = {
        if (hasAudioPerm(ctx)) lib.scanDevice { scanHits = it }
        else runCatching { askAudio.launch(audioPerm()) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            /* 顶行：返回 + 标题 + 选歌 + 更多。内置页都这个格局（底栏留着，返回只退这一页） */
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
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, "更多", tint = pal.dim)
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
                            when {
                                lib.busy.isNotEmpty() -> lib.busy
                                tracks.isEmpty() -> "放本机的歌，不上传、不联网。"
                                else -> "${tracks.size} 首 · 共 ${sizeText(lib.totalSize)} · 只在这台手机上"
                            },
                            fontSize = 13.sp, lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            color = if (lib.busy.isNotEmpty()) pal.inkA1 else pal.dim
                        )
                        Spacer(Modifier.height(14.dp))
                        HairLine()
                    }
                }

                if (tracks.isNotEmpty()) {
                    item(key = "lists") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            /* 注意 `${}` —— 写成 "全部 $tracks.size" 只会插入整个 list 的 toString，
                               后面那个 .size 变成字面量（踩过） */
                            Pill("全部 ${tracks.size}", tab.isEmpty()) { tab = "" }
                            lib.lists.forEach { pl ->
                                Pill(
                                    "${pl.name} ${pl.ids.size}",
                                    tab == pl.id,
                                    onClick = { tab = pl.id },
                                    onLong = { listMenu = pl }
                                )
                            }
                            Pill("＋ 新建", false) {
                                nameDraft = ""
                                listNew = true
                            }
                        }
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

                /* 睡眠定时：夜里听着睡着是常态，别让手机放一宿 */
                item(key = "sleep") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏱ 睡眠", fontSize = 12.sp, color = pal.mute)
                        listOf(0, 15, 30, 60).forEach { m ->
                            Pill(
                                if (m == 0) "关" else "${m}分",
                                MusicPlay.sleepMin == m
                            ) { MusicPlay.sleepAfter(m) }
                        }
                    }
                }

                if (showLyr && !MusicPlay.cur?.lrc.isNullOrBlank()) {
                    item(key = "lyr") {
                        LyricsPanel(
                            lrc = MusicPlay.cur?.lrc.orEmpty(),
                            posMs = MusicPlay.posMs,
                            onSeek = { MusicPlay.seekTo(it) },
                            onExpand = { fsLyr = true }
                        )
                    }
                }

                if (shown.isNotEmpty()) {
                    item(key = "sec") {
                        SectionTitle(
                            (list?.name ?: "曲库") + " ${shown.size} 首",
                            action = "配歌词",
                            onAction = { pickLrc.launch(arrayOf("*/*")) }
                        )
                    }
                    items(shown, key = { it.id }) { t ->
                        TrackRow(
                            lib = lib,
                            t = t,
                            rev = lib.rev,
                            current = MusicPlay.cur?.id == t.id,
                            playing = MusicPlay.cur?.id == t.id && MusicPlay.playing,
                            onPlay = { playOne(t) },
                            onLong = { menuFor = t }
                        )
                    }
                } else {
                    item(key = "empty") {
                        if (list != null) {
                            EmptyState(
                                "🎼", "「${list.name}」还空着",
                                "长按任意一首歌，选「加入歌单」就能收进来。"
                            )
                        } else {
                            EmptyState(
                                "🎧", "还没有歌",
                                "点右上角「＋ 选歌」从手机里挑几首，或者进 ⋮ 菜单「扫本机音乐」" +
                                    "把手机里的歌一次找出来。文件会复制进 App 私有目录，之后删掉原文件也能照常播。"
                            )
                        }
                    }
                }
            }
        }

        /* 歌词整屏：躺着听时不想看小框。放在 Box 最后，盖在整页上面 */
        if (fsLyr) {
            FullLyrics(
                lrc = MusicPlay.cur?.lrc.orEmpty(),
                title = MusicPlay.cur?.title.orEmpty(),
                artist = MusicPlay.cur?.artist.orEmpty(),
                posMs = MusicPlay.posMs,
                playing = MusicPlay.playing,
                onSeek = { MusicPlay.seekTo(it) },
                onToggle = { MusicPlay.toggle() },
                onClose = { fsLyr = false }
            )
        }
    }

    /* ⋮ 菜单：扫本机 / 新建歌单 / 配歌词 */
    if (moreOpen) {
        CardMenu(
            title = "本地音乐",
            sub = "都在这台手机上，不联网",
            actions = listOf(
                CardAction("🎵", "扫本机音乐") { moreOpen = false; doScan() },
                CardAction("🎼", "新建歌单") {
                    moreOpen = false
                    nameDraft = ""
                    listNew = true
                },
                CardAction("💬", "配外挂歌词（.lrc）") {
                    moreOpen = false
                    pickLrc.launch(arrayOf("*/*"))
                }
            ),
            onClose = { moreOpen = false }
        )
    }

    /* 长按某首歌：加入歌单 / 移出歌单 / 删掉 */
    menuFor?.let { t ->
        val cur = list
        CardMenu(
            title = t.title.ifBlank { "未命名" },
            sub = t.artist.ifBlank { "未知歌手" },
            actions = buildList {
                add(CardAction("📁", if (cur == null) "加入歌单" else "加入别的歌单") {
                    menuFor = null
                    addTo = t
                })
                if (cur != null) add(CardAction("➖", "从「${cur.name}」移出") {
                    menuFor = null
                    lib.pullFromList(cur.id, t.id)
                })
                add(CardAction("🗑", "删掉这首歌", danger = true) {
                    menuFor = null
                    del = t
                })
            },
            onClose = { menuFor = null }
        )
    }

    /* 扫到一堆歌：先报个数，用户点头才复制 */
    scanHits?.let { hits ->
        AlertDialog(
            onDismissRequest = { scanHits = null },
            containerColor = pal.bgSoft,
            title = {
                Text(if (hits.isEmpty()) "没找到新歌" else "找到 ${hits.size} 首", color = pal.text)
            },
            text = {
                Text(
                    if (hits.isEmpty())
                        "手机里能扫到的音乐都已经在曲库里了。也可以点「＋ 选歌」从文件夹里手动挑。"
                    else
                        "会把这 ${hits.size} 首复制进 App（占一份空间），之后删掉原文件也能照常播。现在开始？",
                    fontSize = 13.sp, lineHeight = 21.sp, color = pal.dim
                )
            },
            confirmButton = {
                if (hits.isNotEmpty()) {
                    TextButton(onClick = {
                        val batch = hits
                        scanHits = null
                        lib.import(batch) { ok, fail ->
                            Toast.makeText(
                                ctx,
                                if (fail == 0) "加进 $ok 首" else "加进 $ok 首，$fail 首没读出来",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) { Text("都加进来", color = pal.inkA1) }
                }
            },
            dismissButton = {
                TextButton(onClick = { scanHits = null }) {
                    Text(if (hits.isEmpty()) "好" else "算了", color = pal.dim)
                }
            }
        )
    }

    /* 加入歌单：列出现有歌单，也可以就地新建一个 */
    addTo?.let { t ->
        AlertDialog(
            onDismissRequest = { addTo = null },
            containerColor = pal.bgSoft,
            title = {
                Text("把「${t.title.ifBlank { "未命名" }}」加进…", fontSize = 16.sp, color = pal.text)
            },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (lib.lists.isEmpty()) {
                        Text("还没有歌单，先建一个。", fontSize = 13.sp, color = pal.dim)
                    }
                    lib.lists.forEach { pl ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(pal.rSm.dp))
                                .clickable {
                                    lib.addToList(pl.id, t.id)
                                    addTo = null
                                    Toast.makeText(ctx, "已加进「${pl.name}」", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎼", fontSize = 14.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                pl.name,
                                fontSize = 14.sp,
                                color = pal.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${pl.ids.size} 首", fontSize = 11.sp, color = pal.mute)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addTo = null; nameDraft = ""; listNew = true }) {
                    Text("新建歌单", color = pal.inkA1)
                }
            },
            dismissButton = {
                TextButton(onClick = { addTo = null }) { Text("关掉", color = pal.dim) }
            }
        )
    }

    /* 新建 / 改名歌单（同一个输入框，靠 listEdit 区分） */
    if (listNew || listEdit != null) {
        val editing = listEdit
        AlertDialog(
            onDismissRequest = { listNew = false; listEdit = null },
            containerColor = pal.bgSoft,
            title = {
                Text(if (editing == null) "新建歌单" else "给歌单改名", fontSize = 16.sp, color = pal.text)
            },
            text = {
                TextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(24) },
                    singleLine = true,
                    placeholder = { Text("比如：通勤路上", fontSize = 14.sp, color = pal.mute) },
                    textStyle = TextStyle(fontSize = 14.sp, color = pal.text),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editing == null) {
                        val pl = lib.newList(nameDraft)
                        if (pl != null) tab = pl.id
                    } else {
                        lib.renameList(editing.id, nameDraft)
                    }
                    listNew = false
                    listEdit = null
                }) { Text("确定", color = pal.inkA1) }
            },
            dismissButton = {
                TextButton(onClick = { listNew = false; listEdit = null }) {
                    Text("取消", color = pal.dim)
                }
            }
        )
    }

    /* 长按歌单：改名 / 删歌单 */
    listMenu?.let { pl ->
        AlertDialog(
            onDismissRequest = { listMenu = null },
            containerColor = pal.bgSoft,
            title = { Text("「${pl.name}」", fontSize = 16.sp, color = pal.text) },
            text = {
                Text(
                    "${pl.ids.size} 首。删歌单不会删歌，只是把这份清单去掉。",
                    fontSize = 13.sp, lineHeight = 21.sp, color = pal.dim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    nameDraft = pl.name
                    listMenu = null
                    listEdit = pl
                }) { Text("改名", color = pal.inkA1) }
            },
            dismissButton = {
                TextButton(onClick = { listMenu = null; listDel = pl }) {
                    Text("删歌单", color = pal.dim)
                }
            }
        )
    }

    listDel?.let { pl ->
        AlertDialog(
            onDismissRequest = { listDel = null },
            containerColor = pal.bgSoft,
            title = { Text("删掉歌单「${pl.name}」？", fontSize = 16.sp, color = pal.text) },
            text = { Text("只是去掉这份清单，歌还在曲库里。", fontSize = 13.sp, color = pal.dim) },
            confirmButton = {
                TextButton(onClick = {
                    if (tab == pl.id) tab = ""
                    lib.delList(pl.id)
                    listDel = null
                }) { Text("删掉", color = pal.inkA1) }
            },
            dismissButton = {
                TextButton(onClick = { listDel = null }) { Text("算了", color = pal.dim) }
            }
        )
    }

    /* 长按删除：文件一起删，给个确认（删了就找不回来了） */
    del?.let { t ->
        AlertDialog(
            onDismissRequest = { del = null },
            containerColor = pal.bgSoft,
            title = { Text("删掉这首？", fontSize = 16.sp, color = pal.text) },
            text = {
                Text(
                    "「${t.title}」会从曲库里移除，复制过来的音频文件也一起删掉。",
                    fontSize = 13.sp, lineHeight = 21.sp, color = pal.dim
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

/**
 * 一颗小胶囊：歌单条、睡眠定时都用它。
 * 长按要能挂手势，所以统一走 `pointerInput`（不用 `clickable` 叠一层，
 * 两者会抢指针 —— 曲目行长按踩过这个坑）。
 */
@Composable
private fun Pill(
    text: String,
    active: Boolean,
    /* onLong 放在 onClick 之前：尾随 lambda 会绑到最后一个参数上，
       顺序反了会出现「少传 onClick」的编译错（踩过一次） */
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (active) Modifier.background(pal.inkA1)
                else Modifier.background(pal.glassHi)
            )
            .pointerInput(text, active) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLong?.invoke() }
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (active) XY_INK else pal.dim,
            maxLines = 1
        )
    }
}

/* ---------------------------------- 歌词 ---------------------------------- */

@Composable
private fun LyricsPanel(
    lrc: String,
    posMs: Long,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit
) {
    val pal = LocalXyPal.current
    val lines = remember(lrc) { parseLrc(lrc) }

    CardBox(padding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("歌词", fontSize = 12.sp, color = pal.mute, modifier = Modifier.weight(1f))
            Text(
                "整屏看",
                fontSize = 12.sp,
                color = pal.inkA1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onExpand() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        if (lines.isEmpty()) {
            /* 没有时间轴（纯文本歌词 / 格式没认出来）：原样贴出来，至少能看 */
            Text(
                lrc.trim(),
                fontSize = 13.sp, lineHeight = 24.sp, color = pal.dim,
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            )
        } else {
            val state = rememberLazyListState()
            val cur = lines.indexOfLast { it.first <= posMs }
            /* 只滚歌词框：外层列表不动，正文位置不丢 */
            LaunchedEffect(cur) {
                if (cur >= 0) state.animateScrollToItem((cur - 2).coerceAtLeast(0))
            }
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
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
}

/**
 * 整屏歌词：字放大、背景压住整页，躺着看也够。点某句跳过去，点空白或右上角关掉。
 * 自带 [BackHandler] —— 不然按返回是退出整个音乐页（连歌单一起没了）。
 */
@Composable
private fun FullLyrics(
    lrc: String,
    title: String,
    artist: String,
    posMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onClose: () -> Unit
) {
    val pal = LocalXyPal.current
    val lines = remember(lrc) { parseLrc(lrc) }
    val state = rememberLazyListState()

    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            /* 必须**不透明**：早先写成 pal.bg.copy(alpha = .98f)，实测整页会透出背后的
               播放器和列表（0.98 也不该透这么多，但现象就是那样），换纯色就干净了 */
            .background(pal.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                /* 内层再吃一次点击：否则点歌词也会把整屏关掉 */
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(horizontal = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { "未知曲目" },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artist.ifBlank { "未知歌手" },
                        fontSize = 12.sp, color = pal.dim, maxLines = 1
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "暂停" else "播放", tint = pal.text
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, "关闭", tint = pal.dim)
                }
            }
            Spacer(Modifier.height(10.dp))

            if (lines.isEmpty()) {
                Text(
                    lrc.trim().ifBlank { "这首歌没有歌词。可以点「配歌词」塞一个 .lrc 文件进来。" },
                    fontSize = 15.sp, lineHeight = 30.sp, color = pal.dim,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            } else {
                val cur = lines.indexOfLast { it.first <= posMs }
                LaunchedEffect(cur) {
                    if (cur >= 0) state.animateScrollToItem((cur - 3).coerceAtLeast(0))
                }
                LazyColumn(state = state, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(lines.size) { i ->
                        val on = i == cur
                        Text(
                            lines[i].second,
                            fontSize = if (on) 19.sp else 16.sp,
                            lineHeight = 34.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = if (on) pal.inkA1 else pal.mute,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSeek(lines[i].first) }
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                        )
                    }
                }
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
    /* 长按弹菜单，所以不能用 clickable（它和 detectTapGestures 抢指针，踩过一次） */
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
        /* 正在放的这首把时长旁边加一个小暂停标 —— 以前是给封面压一层黑，
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
