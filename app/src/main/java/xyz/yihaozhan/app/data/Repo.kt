package xyz.yihaozhan.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 「测一下」的结果：这个地址通不通、多快、里面是哪一版。
 *
 * 只用来回答「我填的镜像地址对不对」，不参与真正的取数流程。
 */
data class ProbeResult(
    val ok: Boolean = false,
    /** 从发请求到收完正文花了多久（毫秒） */
    val ms: Long = 0,
    /** 那份 app.json 里的数据版本，能跟站点对上就说明不是旧副本 */
    val build: String = "",
    /** 正文大小（字节），国内虚拟主机没开 gzip，这个数会比 GitHub 大不少，正常 */
    val bytes: Int = 0,
    /** 失败原因，给人看的一句话 */
    val msg: String = ""
)

/**
 * 数据仓库：远端拉一份、本地存一份。
 *
 * 「每次打开就是最新的」怎么做到的：
 *   1. 冷启动先读本地缓存 → 秒开，没网也有内容；
 *   2. 同时后台去拉 api/app.json，拉到就覆盖缓存并刷新界面。
 * 所以用户看到的永远是「上一秒的云端」，而不需要手动点刷新。
 */
object Repo {

    /**
     * 正式数据源。**这一行永远不要改、也不要删。**
     *
     * 已经装在别人手机上的旧版 APK 里写死的就是它 —— 改地址等于让所有旧版
     * 从此拿不到新内容（JSON 能兼容，但 URL 改不了）。
     * 想换更快的源，用 [sources] 里的「自定义镜像」那一档，别动这里。
     */
    const val URL = "https://mackxin.github.io/xingyu/api/app.json"

    /** 同一个文件的另外两条路：Pages 抽风时还有得试 */
    private const val RAW = "https://raw.githubusercontent.com/Mackxin/xingyu/main/api/app.json"
    private const val JSDELIVR = "https://cdn.jsdelivr.net/gh/Mackxin/xingyu@main/api/app.json"

    private const val FILE_NAME = "app.json"

    /**
     * 这一次是从哪儿拿到的（域名）。设置页会显示出来 ——
     * 用户反馈「内容还是旧的」时，看一眼这个就知道是哪个源在供货。
     */
    var lastSource by androidx.compose.runtime.mutableStateOf("")
        private set

    /** 这一次拉取用了多久（毫秒），同样是排查用 */
    var lastMs by androidx.compose.runtime.mutableStateOf(0L)
        private set

    /** 上一次全部源都失败时的原因（设置页会显示，省得用户来问「为什么还是旧内容」） */
    var lastError by androidx.compose.runtime.mutableStateOf("")
        private set

    /**
     * 候选源，按顺序试，**第一个成功就返回**。
     *
     * 顺序：自定义镜像（选了「我的镜像」且填了地址，国内虚拟主机最快）→ Pages（正式源）→ raw → jsDelivr。
     * jsDelivr 放最后：它按分支缓存十来个小时，内容可能滞后，只当「总比没有强」。
     *
     * [preferMirror] = false 时镜像**完全不参与**（地址仍留着，下次切回来不用重填）。
     */
    fun sources(mirror: String, preferMirror: Boolean): List<String> = buildList {
        val m = mirror.trim()
        if (preferMirror && m.isNotEmpty()) add(m)
        add(URL)
        add(RAW)
        add(JSDELIVR)
    }.distinct()

    /**
     * 同一份文件放在别处时的地址 —— 给更新包用。
     *
     * 镜像目录里通常也摆着 app-version.json / app.apk（`tools/deploy-ftp.py` 会一起传），
     * 所以把 app.json 那段路径换掉就行。镜像没摆这两个文件时这里会 404，
     * 调用方要顺着下一个源再试。
     */
    fun derive(from: String, fileName: String): String = from.replace("app.json", fileName)

    /**
     * 镜像上那份「压过的 app.json」的地址。
     *
     * 国内虚拟主机（IIS 共享版）不让开 gzip 压缩，489 KB 原样传；GitHub Pages 压到 163 KB，
     * 于是同一台机器上镜像反而慢 3~5 倍。解决办法是把文件本身压一份放旁边：
     * `tools/deploy-ftp.py` 会生成 `api/app.json.gz`，App 这边先取它，看到 gzip 魔数就自己解。
     * 老版本 APK 不认识这个文件名、根本不会来取，所以这条路对它们是零影响的。
     */
    private fun gzUrl(u: String): String = if (u.endsWith(".gz")) u else u + ".gz"

    /**
     * 把响应读成文本：**看到 gzip 魔数就自己解压**。
     *
     * 不能只靠 OkHttp 的自动解压 —— 那只认 `Content-Encoding: gzip` 响应头；
     * 我们这份是「文件本身是 gz」，服务器只会给 `application/gzip`。
     */
    private fun bodyText(resp: okhttp3.Response): String? {
        val raw = resp.body?.bytes() ?: return null
        return String(plainBytes(raw), Charsets.UTF_8)
    }

    /** 是 gzip 就解开，不是就原样返回（服务端有没有真的带上 gzip 都不影响） */
    private fun plainBytes(raw: ByteArray): ByteArray =
        if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
            runCatching {
                java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() }
            }.getOrDefault(raw)
        } else raw

    /**
     * 问镜像要数据：**先试 .gz，没有再退回原文件**。
     *
     * 多花一次 404 的往返（几十毫秒），换掉的是 326 KB 的下载量 —— 值。
     */
    private fun fetchMirrorJson(url: String): String? {
        for (u in listOf(gzUrl(url), url)) {
            try {
                mirrorClient.newCall(requestOf(u)).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val t = bodyText(resp)
                    if (t != null && t.length >= 32) return t
                }
            } catch (t: Throwable) {
                /* 换下一个候选；两个都挂才由调用方报错 */
            }
        }
        return null
    }

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            /* 2.7.5 起把超时压短：以前一个源卡满 15 秒才换下一个，
               四个源串起来最坏要等一分钟，用户看到的就是「转圈半天」。
               现在两个源同时发（见 [raceTexts]），单源 12 秒封顶。 */
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 问镜像专用的 client，比 [client] 更急。
     *
     * 虚拟主机要是挂了这个地址，先等 12 秒再回落 GitHub，用户看着就是「卡住了」；
     * 国内虚拟主机正常时 3 秒内就回，8 秒封顶足够。
     */
    private val mirrorClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }


    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    /**
     * 单独敲一下某个地址 —— 设置里那个「测一下」用的。
     *
     * 跟 [fetchRemote] 的区别：**只问这一个地址**，不回落、不写缓存、不动 [lastSource]。
     * 一次测试要是把界面上的「来自 xxx」改掉了，反而更难判断。
     */
    suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (u.isEmpty()) return@withContext ProbeResult(msg = "地址是空的")
        val t0 = System.currentTimeMillis()
        /* 跟真正取数同一个顺序：先试 app.json.gz，没有再退回原文件。
           不然「测一下」量的是 489 KB 那份（4 秒），实际取数只用 1 秒，报告反而误导人。 */
        var lastCode = 0        /* 两个候选都没成时，至少要知道是不是 404 */
        for (cand in listOf(gzUrl(u), u)) {
            try {
                /* 跟镜像同一条急脾气通道：正常 3 秒内就回，8 秒封顶，别让人干等 */
                mirrorClient.newCall(requestOf(cand)).execute().use { resp ->
                    if (!resp.isSuccessful) { lastCode = resp.code; return@use }
                    /* 先拿字节再解码：报告里那个「多大」要按真实字节数说，
                       String.length 数的是字符，中文一多能虚报出几十 KB */
                    val raw = resp.body?.bytes()
                    val plain = if (raw == null) null else plainBytes(raw)
                    val body = if (plain == null) "" else String(plain, Charsets.UTF_8)
                    val ms = System.currentTimeMillis() - t0
                    if (body.length < 32) return@withContext ProbeResult(ms = ms, msg = "连上了，但内容太短")
                    val p = runCatching { XYJson.decodeFromString<AppPayload>(body) }.getOrNull()
                    /* 报的是解压后的正文大小（跟仓库里那份对得上），传了多少字节不写进界面，
                       免得「怎么才 162 KB」跟 app.json 对不上 */
                    val size = plain?.size ?: 0
                    if (p == null || p.isEmpty) {
                        return@withContext ProbeResult(ms = ms, bytes = size, msg = "不是能用的 app.json")
                    }
                    return@withContext ProbeResult(true, ms, p.build, size, "")
                }
            } catch (t: Throwable) {
                /* 这个候选不通，换下一个；两个都不通才把异常报出去 */
                if (cand == u) {
                    return@withContext ProbeResult(
                        ms = System.currentTimeMillis() - t0,
                        msg = t.message ?: t.javaClass.simpleName
                    )
                }
            }
        }
        ProbeResult(
            ms = System.currentTimeMillis() - t0,
            msg = if (lastCode > 0) "HTTP " + lastCode else "连上了，但没拿到内容"
        )
    }

    /** 本地缓存。第一次装没有 → null */
    suspend fun loadCache(ctx: Context): AppPayload? = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (!f.exists() || f.length() < 32) return@withContext null
        runCatching { XYJson.decodeFromString<AppPayload>(f.readText()) }.getOrNull()
    }

    /**
     * 同步版读缓存 —— 给桌面小组件用。
     *
     * 小组件是 `BroadcastReceiver`，系统给的执行时间很紧（约 10 秒，主线程），
     * 没有地方挂协程；而读一个本地文件 + 解析 300KB json 也就十几毫秒，同步读完更省事。
     */
    fun loadCacheSync(ctx: Context): AppPayload? = runCatching {
        val f = file(ctx)
        if (!f.exists() || f.length() < 32) return null
        XYJson.decodeFromString<AppPayload>(f.readText())
    }.getOrNull()

    suspend fun lastSyncAt(ctx: Context): Long = withContext(Dispatchers.IO) {        val f = file(ctx)
        if (f.exists()) f.lastModified() else 0L
    }

    /**
     * 拉远端。全失败返回 null（调用方保留缓存）。
     *
     * 顺序分两种情形 —— 这个区别很重要，踩过：
     *
     * * **配了镜像** → 只问镜像，它不通才去 GitHub 那边比。
     *   一开始把镜像和 Pages 放在一起竞速，结果 Pages 因为开了 gzip（161 KB）
     *   反倒比虚拟主机（484 KB 没压缩）快，镜像白配了 —— 用户要的是「不依赖 GitHub」，
     *   不是「快 0.1 秒」。
     * * **没配镜像** → 前两个源同时发（Pages + raw），谁先回用谁，剩下的依次兜底。
     *   串行时第一个源卡住就要等满一个超时，以前 15 秒，用户看到的就是「转圈半天」。
     *
     * 另外两个细节：
     *   * jsDelivr **不参与竞速** —— 它按分支缓存十来小时，抢先把旧内容送来最坑；
     *   * 只有最终胜出那一份会被写进缓存，并发写盘会互相截断。
     */
    suspend fun fetchRemote(
        ctx: Context,
        mirror: String = "",
        preferMirror: Boolean = true
    ): AppPayload? =
        withContext(Dispatchers.IO) {
            if (!online(ctx)) return@withContext null
            val t0 = System.currentTimeMillis()
            lastError = ""

            val m = mirror.trim()
            val hasMirror = preferMirror && m.isNotEmpty()
            val all = sources(m, preferMirror)

            var hit: Pair<String, String>? = null

            if (hasMirror) {
                hit = try {
                    /* 镜像走自己的 client：虚拟主机挂掉时别陪着等满 12 秒。
                       内部先试 app.json.gz（体积只有 1/3），没有再退回原文件 */
                    fetchMirrorJson(m)?.let { m to it }
                } catch (t: Throwable) {
                    lastError = hostOf(m) + "：" + (t.message ?: t.javaClass.simpleName)
                    null
                }
                if (hit == null && lastError.isBlank()) lastError = hostOf(m) + "：镜像没给内容"
            }

            if (hit == null) {
                val rest = if (hasMirror) all.filter { it != m } else all
                hit = raceTexts(rest.filter { it != JSDELIVR }.take(2))
                if (hit == null) {
                    for (u in rest) {
                        val txt = try {
                            fetchText(u)
                        } catch (t: Throwable) {
                            lastError = hostOf(u) + "：" + (t.message ?: t.javaClass.simpleName)
                            null
                        }
                        if (txt != null) {
                            hit = u to txt
                            break
                        }
                        if (lastError.isBlank()) lastError = hostOf(u) + "：没拿到内容"
                    }
                }
            }

            val (winner, text) = hit ?: run {
                lastMs = System.currentTimeMillis() - t0
                return@withContext null
            }

            val payload = runCatching {
                XYJson.decodeFromString<AppPayload>(text)
            }.getOrNull()
            if (payload == null || payload.isEmpty) {
                lastError = hostOf(winner) + "：内容不是能用的 JSON"
                lastMs = System.currentTimeMillis() - t0
                return@withContext null
            }

            lastSource = hostOf(winner)
            lastMs = System.currentTimeMillis() - t0
            /* 拉到的这份就是以后的缓存：下次冷启动先端它，秒开 */
            runCatching { file(ctx).writeText(text) }
            payload
        }

    /**
     * 同时请求几个源，谁先给出像样的正文就用谁。
     *
     * 只回正文，**不碰磁盘** —— 写缓存留给调用方做一次，避免两个线程同时写同一个文件。
     * 全失败 → 返回 null（Channel 被关掉，receive 抛异常）。
     */
    private suspend fun raceTexts(urls: List<String>): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            if (urls.isEmpty()) return@withContext null
            if (urls.size == 1) {
                val u = urls[0]
                return@withContext try {
                    fetchText(u)?.let { u to it }
                } catch (t: Throwable) {
                    lastError = hostOf(u) + "：" + (t.message ?: t.javaClass.simpleName)
                    null
                }
            }

            val ch = Channel<Pair<String, String>>(Channel.UNLIMITED)
            val calls = java.util.Collections.synchronizedList(mutableListOf<okhttp3.Call>())
            val pending = java.util.concurrent.atomic.AtomicInteger(urls.size)
            val lock = Any()

            fun finish() {
                synchronized(lock) { if (pending.decrementAndGet() == 0) ch.close() }
            }

            for (u in urls) {
                val call = client.newCall(requestOf(u))
                calls.add(call)
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(c: okhttp3.Call, e: java.io.IOException) {
                        lastError = hostOf(u) + "：" + (e.message ?: e.javaClass.simpleName)
                        finish()
                    }

                    override fun onResponse(c: okhttp3.Call, resp: okhttp3.Response) {
                        var text: String? = null
                        try {
                            if (resp.isSuccessful) text = resp.body?.string()
                        } catch (_: Throwable) {
                        } finally {
                            runCatching { resp.close() }
                        }
                        if (text != null && text.length >= 32) ch.trySend(u to text)
                        finish()
                    }
                })
            }

            val first = try {
                ch.receive()
            } catch (_: Throwable) {
                null
            }
            /* 赢了就走，剩下的请求立刻掐掉 —— 不然还在那儿占着流量把 160 KB 下完 */
            calls.forEach { runCatching { it.cancel() } }
            ch.close()
            first
        }

    private fun requestOf(url: String): okhttp3.Request =
        okhttp3.Request.Builder()
            .url(if (url.contains("?")) url else "$url?t=" + System.currentTimeMillis())
            .header("Cache-Control", "no-cache")
            .build()

    /** 同步取一份正文（长度不够 32 字节的当没拿到 —— 空的或 404 的 HTML） */
    private fun fetchText(url: String): String? {
        client.newCall(requestOf(url)).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string().orEmpty()
            return text.takeIf { it.length >= 32 }
        }
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://", url).substringBefore("/").ifBlank { url }

    fun online(ctx: Context): Boolean {
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    /** 缓存占多少字节 —— 设置页要显示「整站存在手机里占了多少」 */
    fun cacheSize(ctx: Context): Long = runCatching {
        val f = file(ctx)
        if (f.exists()) f.length() else 0L
    }.getOrDefault(0L)

    /** 清掉本地缓存。清完界面还在（内存里还有一份），下次冷启动才需要联网。 */
    fun clearCache(ctx: Context): Boolean = runCatching {
        val f = file(ctx)
        if (f.exists()) f.delete() else true
    }.getOrDefault(false)
}
