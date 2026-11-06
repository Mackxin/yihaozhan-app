package xyz.yihaozhan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * 曲库里的一首歌。
 *
 * 音频**复制进 App 私有目录**（`filesDir/music/`），不是拿着 SAF 的 Uri 就用 ——
 * 那个授权活不过重启，用户过两天回来一播放全是「文件打不开」。
 * 代价是占空间（一首 5 MB），换来的是「以后永远能放」。
 */
@Serializable
data class Track(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durMs: Long = 0L,
    /** 音频文件名（相对 filesDir/music） */
    val file: String = "",
    /** 封面文件名（相对 filesDir/music），空串 = 没封面 */
    val cover: String = "",
    /** 歌词原文（LRC 文本），空串 = 没歌词 */
    val lrc: String = "",
    val size: Long = 0L,
    /** 加了多久（毫秒），列表按它倒序：新加的在上 */
    val added: Long = 0L
)

/**
 * 本机曲库：跟站点的「本地音乐」页同一个思路，只是换成原生实现。
 *
 * - 索引在 `filesDir/music/index.json`（曲目不多，一个文件足够；不用数据库）
 * - 音频和封面都是普通文件，删曲目就把文件一起删
 * - 标签优先信文件里写的（MediaMetadataRetriever），没有才用文件名兜底
 *
 * 为什么不像站点那样存 IndexedDB：WebView 的库归浏览器内核管，App 卸载才清干净，
 * 而且拿不到系统级的后台播放。原生这边直接落文件，看得见、删得掉、也能被备份工具带走。
 */
@Stable
class MusicLib private constructor(ctx: Context) {

    private val app = ctx.applicationContext
    private val dir = File(app.filesDir, "music").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val ser = ListSerializer(Track.serializer())

    /**
     * 导入/配歌词跑在后台线程，但回调里要弹 Toast、改界面 —— 那些必须在主线程。
     * 早先直接把回调扔在 Thread 里调，`Toast.makeText` 直接抛
     * `Can't toast on a thread that has not called Looper.prepare()` 把 App 崩了。
     */
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** 曲库，新加的在前 */
    var tracks: List<Track> by mutableStateOf(emptyList())
        private set

    /** 正在干什么（导入时显示「正在读取 3/8」），空串 = 空闲 */
    var busy: String by mutableStateOf("")
        private set

    /** 导入/删除后 +1：封面文件是异步写的，UI 靠它知道该重读磁盘了 */
    var rev: Int by mutableStateOf(0)
        private set

    init {
        load()
    }

    fun load() {
        tracks = runCatching {
            if (indexFile.exists()) {
                json.decodeFromString(ser, indexFile.readText())
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
            /* 文件被外部清掉（清缓存 / 手动删）时，索引里的残项要自己消失 */
            .filter { it.file.isNotBlank() && File(dir, it.file).exists() }
    }

    private fun save() {
        runCatching { indexFile.writeText(json.encodeToString(ser, tracks)) }
    }

    fun audioFile(t: Track): File = File(dir, t.file)

    fun coverFile(t: Track): File? =
        if (t.cover.isBlank()) null else File(dir, t.cover).takeIf { it.exists() && it.length() > 0 }

    val totalSize: Long get() = tracks.sumOf { it.size }

    /* --------------------------------- 导入 --------------------------------- */

    /**
     * 把选中的音频复制进来。
     *
     * 放后台线程：一批 20 首歌要 copy 100 MB 再逐首解析标签，主线程干这个必 ANR。
     * 回来时只改 Compose 状态（`tracks` / `busy`），这两个都是 mutableStateOf，
     * 从任何线程写都是安全的。
     */
    fun import(uris: List<Uri>, onDone: (ok: Int, fail: Int) -> Unit) {
        if (uris.isEmpty()) return
        busy = "正在读取 0/${uris.size}"
        Thread {
            var ok = 0
            var fail = 0
            uris.forEachIndexed { i, uri ->
                busy = "正在读取 ${i + 1}/${uris.size}"
                val t = runCatching { ingest(uri) }.getOrNull()
                if (t == null) fail++ else ok++
                if (t != null) tracks = (listOf(t) + tracks)
            }
            save()
            busy = ""
            rev++
            main.post { onDone(ok, fail) }
        }.start()
    }

    private fun ingest(uri: Uri): Track? {
        val name = displayName(uri) ?: "audio"
        val ext = name.substringAfterLast('.', "").lowercase()
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val target = File(dir, if (ext.isNotEmpty()) "$id.$ext" else id)

        /* 复制。拿不到流就直接放弃这一首，不让它留一条坏索引 */
        val ok = runCatching {
            app.contentResolver.openInputStream(uri)?.use { ins ->
                target.outputStream().use { ins.copyTo(it) }
            } ?: return null
        }.isSuccess
        if (!ok || target.length() == 0L) {
            target.delete()
            return null
        }

        val meta = readMeta(target)
        val cover = meta.cover?.let { bytes -> saveCover(id, bytes) }.orEmpty()
        val lrc = readLyrics(target)
        android.util.Log.i("XYMusic", "lyrics ${target.name} ${lrc.length} 字 -> ${lrc.take(24).replace("\n", "\\n")}")
        val fallbackTitle = name.substringBeforeLast('.').ifBlank { "未命名" }

        return Track(
            id = id,
            title = meta.title.ifBlank { fallbackTitle },
            artist = meta.artist,
            album = meta.album,
            durMs = meta.durMs,
            file = target.name,
            cover = cover,
            lrc = lrc,
            size = target.length(),
            added = System.currentTimeMillis()
        )
    }

    /** SAF 的显示名（带扩展名）。拿不到就返回 null，调用方用「音频」兜底 */
    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /* ------------------------------ 标签 / 封面 ------------------------------ */

    private class Meta(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durMs: Long = 0L,
        val cover: ByteArray? = null
    )

    /**
     * 元数据交给系统的 [MediaMetadataRetriever]：mp3 / flac / m4a / ogg / wav 它都认，
     * 连内嵌封面都能直接掏出来（`embeddedPicture`）。
     *
     * 自己解 ID3 只为了拿**歌词** —— 那玩意儿 retriever 不给（见 [readLyrics]）。
     * 早先想连标签一起手写，后来发现没必要：格式太多，系统这层已经很稳。
     */
    private fun readMeta(f: File): Meta {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(f.absolutePath)
            fun m(key: Int) = mmr.extractMetadata(key)?.trim().orEmpty()
            val pic = runCatching { mmr.embeddedPicture }.getOrNull()
            val meta = Meta(
                title = m(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = m(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    .ifBlank { m(MediaMetadataRetriever.METADATA_KEY_AUTHOR) },
                album = m(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durMs = m(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L,
                cover = pic
            )
            android.util.Log.i(
                "XYMusic",
                "meta ${f.name} title=${meta.title} artist=${meta.artist} dur=${meta.durMs} pic=${pic?.size ?: -1}"
            )
            meta
        } catch (e: Throwable) {
            android.util.Log.w("XYMusic", "meta fail ${f.name}: ${e.message}")
            Meta()
        } finally {
            runCatching { mmr.release() }
        }
    }

    /**
     * 封面**必须缩**再存：手机拍的专辑图动辄 1500×1500 PNG，一首 1 MB，
     * 三十首就是 45 MB 全是图。列表里只显示 44dp，存 320px JPEG 绰绰有余。
     */
    private fun saveCover(id: String, bytes: ByteArray): String {
        return runCatching {
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
            val maxSide = 320
            val scale = maxSide.toFloat() / max(src.width, src.height).toFloat()
            val bmp = if (scale >= 1f) src else Bitmap.createScaledBitmap(
                src,
                (src.width * scale).toInt().coerceAtLeast(1),
                (src.height * scale).toInt().coerceAtLeast(1),
                true
            )
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 86, out)
            if (bmp !== src) bmp.recycle()
            src.recycle()
            val name = "$id.jpg"
            File(dir, name).writeBytes(out.toByteArray())
            android.util.Log.i("XYMusic", "cover ok $name ${out.size()} bytes")
            name
        }.onFailure {
            android.util.Log.w("XYMusic", "cover fail: ${it.message}")
        }.getOrDefault("")
    }

    /* -------------------------------- 歌词 -------------------------------- */

    /**
     * 找内嵌歌词。三种容器的写法都不一样，所以这里得自己扒：
     *
     * - **mp3**：ID3v2 的 `USLT` 帧，或者 `TXXX` 帧（描述写 `USLT`/`LYRICS`/`UNSYNC`）——
     *   ffmpeg 之类就爱往 TXXX 里塞，只认 USLT 会漏一大批
     * - **flac**：`VORBIS_COMMENT` 里的 `LYRICS=` / `UNSYNCEDLYRICS=`
     * - **m4a**：`©lyr` atom（moov 可能在文件尾部，所以尾巴也要读一段）
     *
     * 全失败返回空串 —— 没歌词是常态，不该让导入失败。
     */
    private fun readLyrics(f: File): String {
        val head = readChunk(f, 0, 262144)
        if (head.isEmpty()) return ""
        id3Lyrics(head)?.let { if (it.isNotBlank()) return it }
        flacLyrics(head)?.let { if (it.isNotBlank()) return it }
        val tail = if (f.length() > 524288) readChunk(f, f.length() - 262144, 262144) else head
        mp4Lyrics(head)?.let { if (it.isNotBlank()) return it }
        mp4Lyrics(tail)?.let { if (it.isNotBlank()) return it }
        return ""
    }

    private fun readChunk(f: File, from: Long, len: Int): ByteArray = runCatching {
        java.io.RandomAccessFile(f, "r").use { raf ->
            raf.seek(from.coerceAtLeast(0))
            val buf = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = raf.read(buf, read, len - read)
                if (n <= 0) break
                read += n
            }
            if (read == len) buf else buf.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))

    private fun id3Lyrics(b: ByteArray): String? {
        if (b.size < 10 || b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) {
            return null
        }
        /* 标签头里的总长度永远是 syncsafe；但**每一帧**的长度不是 ——
           v2.4 才是 syncsafe，v2.3 及以前是普通的 32 位大端。
           这里搞错会把歌词从中间截断（表现为只显示「[0」这种两三个字符）。 */
        val v4 = (b[3].toInt() and 0xff) >= 4
        val size = syncsafe(b, 6, 4)
        val end = minOf(b.size, 10 + size)
        var p = 10
        var best = ""
        while (p + 10 <= end) {
            val id = String(b, p, 4, Charsets.ISO_8859_1)
            /* 帧 id 全是字母数字；碰上填充的 0 就结束 */
            if (!id.all { it.isLetterOrDigit() }) break
            val fs = if (v4) syncsafe(b, p + 4, 4) else be32(b, p + 4)
            if (fs <= 0 || p + 10 + fs > end) break
            val body = p + 10
            when (id) {
                "USLT" -> {
                    /* enc(1) + 语言(3) + 描述(以 0 结尾) + 正文 */
                    val enc = b[body].toInt() and 0x03
                    var q = body + 4
                    q = skipCString(b, q, enc, body + fs)
                    val t = decode(b, q, body + fs - q, enc)
                    if (t.length > best.length) best = t
                }
                "TXXX" -> {
                    /* enc(1) + 描述 + 0 + 值。描述像歌词，或者值本身像 [mm:ss，都收 */
                    val enc = b[body].toInt() and 0x03
                    var q = body + 1
                    val descStart = q
                    q = skipCString(b, q, enc, body + fs)
                    val desc = decode(b, descStart, q - descStart, enc).uppercase()
                    val value = decode(b, q, body + fs - q, enc)
                    val looksLrc = value.contains("[") && value.contains(":")
                    if ((desc.contains("LYRIC") || desc.contains("USLT") || desc.contains("UNSYNC") || looksLrc)
                        && value.length > best.length
                    ) {
                        best = value
                    }
                }
            }
            p = body + fs
        }
        return best.ifBlank { null }
    }

    private fun flacLyrics(b: ByteArray): String? {
        if (b.size < 8 || String(b, 0, 4, Charsets.ISO_8859_1) != "fLaC") return null
        var p = 4
        while (p + 4 <= b.size) {
            val type = b[p].toInt() and 0x7f
            val last = (b[p].toInt() and 0x80) != 0
            val len = be24(b, p + 1)
            val body = p + 4
            if (body + len > b.size) break
            if (type == 4) {
                /* VORBIS_COMMENT：厂商串 + 若干「KEY=value」，长度都是小端 */
                var q = body
                val vendor = le32(b, q)
                q += 4 + vendor
                if (q + 4 <= b.size) {
                    val count = le32(b, q); q += 4
                    var found = ""
                    for (i in 0 until count.coerceAtMost(256)) {
                        if (q + 4 > body + len) break
                        val n = le32(b, q); q += 4
                        if (n <= 0 || q + n > body + len) break
                        val s = String(b, q, n, Charsets.UTF_8)
                        q += n
                        val eq = s.indexOf('=')
                        if (eq > 0) {
                            val k = s.substring(0, eq).uppercase()
                            if (k == "LYRICS" || k == "UNSYNCEDLYRICS" || k == "LYRIC") {
                                val v = s.substring(eq + 1)
                                if (v.length > found.length) found = v
                            }
                        }
                    }
                    if (found.isNotBlank()) return found
                }
            }
            if (last) break
            p = body + len
        }
        return null
    }

    private fun mp4Lyrics(b: ByteArray): String? {
        if (b.size < 16) return null
        val sig = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var i = 0
        while (i + 16 <= b.size) {
            if (b[i] == sig[0] && b[i + 1] == sig[1] && b[i + 2] == sig[2] && b[i + 3] == sig[3]) {
                /* 后面跟一个 data box：size(4) + "data"(4) + 版本/标志(4) + 保留(4) + 正文 */
                var q = i + 4
                if (q + 4 <= b.size && String(b, q, 4, Charsets.ISO_8859_1) == "size") q += 4
                val boxSize = be32(b, q)
                if (q + 16 <= b.size && String(b, q + 4, 4, Charsets.ISO_8859_1) == "data") {
                    val start = q + 16
                    val payload = if (boxSize in 17..(b.size - q)) boxSize - 16 else b.size - start
                    val t = String(b, start, payload.coerceAtMost(b.size - start), Charsets.UTF_8)
                        .trim()
                    if (t.isNotBlank()) return t
                }
            }
            i++
        }
        return null
    }

    /* -------------------------------- 外部 lrc -------------------------------- */

    /**
     * 给已有的歌配外挂 `.lrc`。按文件名匹配（去扩展名、去空格、小写），
     * 跟站点那套 `baseKey()` 一模一样 —— 手机上歌和歌词常常并排放着，名字就是对得上的。
     */
    fun attachLrc(uris: List<Uri>, onDone: (hit: Int) -> Unit) {
        if (uris.isEmpty()) return
        busy = "正在配歌词 0/${uris.size}"
        Thread {
            var hit = 0
            val list = tracks.toMutableList()
            /* 用下标循环而不是 forEachIndexed：「读不出来就跳过这一首」要 continue，
               而 continue 只能在循环里用，不能出现在 lambda 里 */
            for (i in uris.indices) {
                busy = "正在配歌词 ${i + 1}/${uris.size}"
                val uri = uris[i]
                val name = displayName(uri) ?: continue
                val text = runCatching {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull() ?: continue
                if (text.isBlank()) continue
                val key = baseKey(name)
                val at = list.indexOfFirst {
                    baseKey(it.title) == key || baseKey(it.file.substringBeforeLast('.')) == key
                }
                if (at >= 0) {
                    list[at] = list[at].copy(lrc = text)
                    hit++
                }
            }
            tracks = list
            save()
            busy = ""
            rev++
            main.post { onDone(hit) }
        }.start()
    }

    private fun baseKey(s: String): String =
        s.substringBeforeLast('.').replace(" ", "").lowercase()

    /* -------------------------------- 增删 -------------------------------- */

    fun rename(id: String, title: String, artist: String) {
        tracks = tracks.map {
            if (it.id == id) it.copy(title = title.ifBlank { it.title }, artist = artist) else it
        }
        save()
        rev++
    }

    fun remove(id: String) {
        val t = tracks.firstOrNull { it.id == id } ?: return
        File(dir, t.file).delete()
        if (t.cover.isNotBlank()) File(dir, t.cover).delete()
        tracks = tracks.filter { it.id != id }
        save()
        rev++
    }

    fun clearAll() {
        tracks.forEach {
            File(dir, it.file).delete()
            if (it.cover.isNotBlank()) File(dir, it.cover).delete()
        }
        tracks = emptyList()
        save()
        rev++
    }

    companion object {
        @Volatile
        private var inst: MusicLib? = null

        fun of(ctx: Context): MusicLib = inst ?: synchronized(this) {
            inst ?: MusicLib(ctx.applicationContext).also { inst = it }
        }

        /* ------------------------------ 字节小工具 ------------------------------ */

        private fun syncsafe(b: ByteArray, o: Int, n: Int): Int {
            var v = 0
            for (i in 0 until n) {
                if (o + i >= b.size) return v
                v = (v shl 7) or (b[o + i].toInt() and 0x7f)
            }
            return v
        }

        private fun be32(b: ByteArray, o: Int): Int {
            if (o + 4 > b.size) return 0
            return ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
                ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)
        }

        private fun le32(b: ByteArray, o: Int): Int {
            if (o + 4 > b.size) return 0
            return (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
                ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
        }

        private fun be24(b: ByteArray, o: Int): Int {
            if (o + 3 > b.size) return 0
            return ((b[o].toInt() and 0xff) shl 16) or ((b[o + 1].toInt() and 0xff) shl 8) or
                (b[o + 2].toInt() and 0xff)
        }

        /** 跳过一个以 0 结尾的字符串（UTF-16 是 2 字节对齐的 0），返回正文起点 */
        private fun skipCString(b: ByteArray, from: Int, enc: Int, limit: Int): Int {
            var q = from
            if (enc == 1 || enc == 2) {
                while (q + 1 < limit) {
                    if (b[q].toInt() == 0 && b[q + 1].toInt() == 0) return q + 2
                    q += 2
                }
            } else {
                while (q < limit) {
                    if (b[q].toInt() == 0) return q + 1
                    q++
                }
            }
            return q
        }

        /**
         * ID3 的文本编码：0=latin1（中文实际常是 GBK）、1=UTF-16 带 BOM、2=UTF-16BE、3=UTF-8。
         * 0 那档先试 GBK —— 国内软件写的 ID3v2.3 基本都是 GBK，按 latin1 解出来是乱码。
         */
        private fun decode(b: ByteArray, from: Int, len: Int, enc: Int): String {
            if (len <= 0 || from < 0 || from >= b.size) return ""
            val n = minOf(len, b.size - from)
            val bytes = b.copyOfRange(from, from + n)
            val s = runCatching {
                when (enc) {
                    1 -> {
                        if (bytes.size >= 2 && bytes[0].toInt() == 0xFF.toByte().toInt() &&
                            bytes[1].toInt() == 0xFE.toByte().toInt()
                        ) {
                            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                        } else if (bytes.size >= 2 && bytes[0].toInt() == 0xFE.toByte().toInt() &&
                            bytes[1].toInt() == 0xFF.toByte().toInt()
                        ) {
                            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
                        } else {
                            String(bytes, Charsets.UTF_16LE)
                        }
                    }
                    2 -> String(bytes, Charsets.UTF_16BE)
                    3 -> String(bytes, Charsets.UTF_8)
                    else -> {
                        if (bytes.any { (it.toInt() and 0xff) > 0x7f }) {
                            runCatching { String(bytes, charset("GBK")) }
                                .getOrElse { String(bytes, Charsets.ISO_8859_1) }
                        } else {
                            String(bytes, Charsets.ISO_8859_1)
                        }
                    }
                }
            }.getOrElse { "" }
            return s.trim('\u0000', ' ', '\r', '\n')
        }
    }
}
