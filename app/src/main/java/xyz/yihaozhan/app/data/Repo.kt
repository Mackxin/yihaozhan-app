package xyz.yihaozhan.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

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

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

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
     * 拉远端：按 [sources] 顺序试，谁先成功用谁。全失败返回 null（调用方保留缓存）。
     *
     * `mirror` 是设置里填的自定义源，`preferMirror` 决定它排不排第一
     * （空串或 false 就只用内置那三个）。
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
            for (u in sources(mirror, preferMirror)) {
                val got = try {
                    fetchOne(ctx, u)
                } catch (t: Throwable) {
                    /* 一个源失败不该让整次同步失败，但**要留下原因** —— 不然后面只能猜
                       「为什么还是旧数据」（国内几种典型：超时、证书、DNS、明文被拦） */
                    lastError = hostOf(u) + "：" + (t.message ?: t.javaClass.simpleName)
                    android.util.Log.w("XYRepo", "源失败 $u -> $lastError")
                    null
                }
                if (got != null) {
                    lastSource = hostOf(u)
                    lastMs = System.currentTimeMillis() - t0
                    return@withContext got
                }
                if (lastError.isBlank()) lastError = hostOf(u) + "：没拿到内容"
            }
            lastMs = System.currentTimeMillis() - t0
            null
        }

    private fun fetchOne(ctx: Context, url: String): AppPayload? {
        /* 加个时间戳：GitHub Pages / jsDelivr 的 CDN 都会缓存，
           不带这个参数可能一直拿到旧文件，「打开即最新」就打折了 */
        val req = okhttp3.Request.Builder()
            .url(if (url.contains("?")) url else "$url?t=" + System.currentTimeMillis())
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string().orEmpty()
            if (text.length < 32) return null
            val payload = XYJson.decodeFromString<AppPayload>(text)
            if (payload.isEmpty) return null
            /* 拉到的这份就是以后的缓存：下次冷启动先端它，秒开 */
            runCatching { file(ctx).writeText(text) }
            return payload
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
