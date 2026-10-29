package xyz.yihaozhan.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    /** 站点仓库里的数据文件。改域名只改这一处。 */
    private const val URL = "https://mackxin.github.io/xingyu/api/app.json"
    private const val FILE_NAME = "app.json"

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    /** 本地缓存。第一次装没有 → null */
    suspend fun loadCache(ctx: Context): AppPayload? = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (!f.exists() || f.length() < 32) return@withContext null
        runCatching { XYJson.decodeFromString<AppPayload>(f.readText()) }.getOrNull()
    }

    suspend fun lastSyncAt(ctx: Context): Long = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (f.exists()) f.lastModified() else 0L
    }

    /** 拉远端。失败返回 null（调用方保留缓存里的那份）。 */
    suspend fun fetchRemote(ctx: Context): AppPayload? = withContext(Dispatchers.IO) {
        if (!online(ctx)) return@withContext null
        runCatching {
            /* 加个时间戳：GitHub Pages 的 CDN 会缓存 10 分钟，
               不带这个参数可能一直拿到旧文件，「打开即最新」就打折了。 */
            val req = okhttp3.Request.Builder()
                .url("$URL?t=" + System.currentTimeMillis())
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val text = resp.body?.string().orEmpty()
                if (text.length < 32) return@use null
                val payload = XYJson.decodeFromString<AppPayload>(text)
                if (payload.isEmpty) return@use null
                file(ctx).writeText(text)
                payload
            }
        }.getOrNull()
    }

    fun online(ctx: Context): Boolean {
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }
}
