package xyz.yihaozhan.app.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * App 自更新。
 *
 * 分发不在应用商店里（私有 APK），所以自己走一遍：「问版本号 → 下安装包 → 拉起系统安装器」。
 * 安装那一步是系统弹的确认框，用户点「安装」才装 —— App 没法（也不该）静默覆盖自己。
 *
 * 安装包和版本号都放在**站点同域**（api/app.apk、api/app-version.json）：
 * GitHub Release 的二进制落在 objects.githubusercontent.com，国内经常连不上；
 * 而 api/app.json 能拉到，就说明同域的安装包也能拉到。
 */
@Serializable
data class VersionInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val size: Long = 0,
    val notes: String = "",
    val updated: String = ""
)

object UpdateRepo {

    /* 正式源（GitHub Pages）。2.7.5 起不再是唯一一条路，见 [versionUrls] */
    val VERSION_URL = Repo.URL.replace("app.json", "app-version.json")
    val APK_URL = Repo.URL.replace("app.json", "app.apk")
    private const val APK_NAME = "update.apk"

    /**
     * 版本清单的候选地址，顺序跟内容源一致（镜像 → Pages → raw → jsDelivr）。
     *
     * 为什么更新包也要跟着镜像走：Pages 在国内实测只有 65 KB/s，
     * 1.5 MB 的包要 24 秒；自己的虚拟主机能到 140 KB/s，11 秒就完了。
     * 镜像里没摆这两个文件的（只传了 app.json）会 404，顺着往下试就是了。
     */
    fun versionUrls(mirror: String, preferMirror: Boolean): List<String> =
        Repo.sources(mirror, preferMirror).map { Repo.derive(it, "app-version.json") }

    fun apkUrls(mirror: String, preferMirror: Boolean): List<String> =
        Repo.sources(mirror, preferMirror).map { Repo.derive(it, "app.apk") }


    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    /* 查版本只要一个两百来字节的 json，别跟下载共用一个三分钟的超时：
       网络半死不活时（DNS 卡住、连上了不响应）界面会一直停在「正在检查」。
       这里给一条短 leash，20 秒拿不到就当没网。 */
    private val probeClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** 本地当前版本号。比较大小用它，别比版本名字符串 */
    fun installedCode(ctx: Context): Int = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    fun installedName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** 问一下远端有没有新版。失败返回 null（没网就静静算了，别弹错） */
    suspend fun check(
        ctx: Context,
        mirror: String = "",
        preferMirror: Boolean = true
    ): VersionInfo? = withContext(Dispatchers.IO) {
        if (!Repo.online(ctx)) return@withContext null
        /* 一个源不行就换下一个：国内直连 Pages 经常十几秒没响应，
           而镜像只要 0.2 秒 —— 以前只问 Pages，用户就干等。 */
        for (u in versionUrls(mirror, preferMirror)) {
            val got = runCatching {
                val req = okhttp3.Request.Builder()
                    .url("$u?t=" + System.currentTimeMillis())
                    .header("Cache-Control", "no-cache")
                    .build()
                probeClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body?.string().orEmpty()
                    if (text.isBlank()) return@use null
                    /* versionCode = 0 是「这不是版本清单」的信号（比如拿到了 404 的 HTML） */
                    XYJson.decodeFromString<VersionInfo>(text).takeIf { it.versionCode > 0 }
                }
            }.getOrNull()
            if (got != null) return@withContext got
        }
        null
    }

    /** 上次没下完的残包有多大（0 = 没有）。用来显示「已下 N%」和从那儿接着下 */
    fun partialSize(ctx: Context): Long = runCatching {
        File(ctx.cacheDir, APK_NAME).takeIf { it.exists() }?.length() ?: 0L
    }.getOrDefault(0L)

    /**
     * 下安装包。onProgress 拿到 0..1（不知道总长度时就给 -1）。
     *
     * **断了能接着下**：残包留在 cacheDir 里不删，下次进来先看它有多长，
     * 再带 `Range: bytes=<已有长度>-` 只求剩下那截。
     *   - 服务器回 206 → 追加写，从断的地方接上；
     *   - 回 200（不支持 Range，某些镜像/中转就是这样）→ 丢掉残包从头写；
     *   - 回 416 → 残包跟服务端对不上（服务端换过包），丢掉重来。
     * 一次调用里最多试两轮，第二轮带着第一轮的进度接着来 ——
     * 弱网抖一下不至于白下 1.5 MB。
     *
     * 下完校验大小：GitHub Pages 偶尔会返回一张 404 的 HTML，别拿去安装。
     */
    suspend fun download(
        ctx: Context,
        info: VersionInfo,
        mirror: String = "",
        preferMirror: Boolean = true,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        /* 依次试每个源。第一个源允许接着上次的残包下；换源时先把残包删掉 ——
           不同站点的包未必是同一个构建，拼起来会得到一个装不上的坏包。 */
        for ((idx, url) in apkUrls(mirror, preferMirror).withIndex()) {
            val f = runCatching {
                downloadFrom(ctx, url, info, resume = idx == 0, onProgress = onProgress)
            }.getOrNull()
            if (f != null) return@withContext f
        }
        null
    }

    /**
     * 从**一个**地址下安装包。onProgress 拿 0..1（不知道总长度时给 -1）。
     *
     * **断了能接着下**：残包留在 cacheDir 里不删，下次进来先看它有多长，
     * 再带 `Range: bytes=<已有长度>-` 只求剩下那截。
     *   - 服务器回 206 → 追加写，从断的地方接上；
     *   - 回 200（不支持 Range，某些镜像/中转就是这样）→ 丢掉残包从头写；
     *   - 回 416 → 残包跟服务端对不上（服务端换过包），丢掉重来。
     * 一次调用里最多试两轮，第二轮带着第一轮的进度接着来 ——
     * 弱网抖一下不至于白下 1.5 MB。
     *
     * 下完校验大小：GitHub Pages 偶尔会返回一张 404 的 HTML，别拿去安装。
     */
    private fun downloadFrom(
        ctx: Context,
        url: String,
        info: VersionInfo,
        resume: Boolean,
        onProgress: (Float) -> Unit
    ): File? {
        val out = File(ctx.cacheDir, APK_NAME)
        out.parentFile?.mkdirs()
        if (!resume && out.exists()) out.delete()
        var have = if (out.exists()) out.length() else 0L

        /* 已经下满（上次其实下完了，只是没来得及装）→ 直接交差 */
        if (info.size > 0 && have == info.size) {
            onProgress(1f)
            return out
        }
        /* 残包比目标还大：不是同一个包，别硬拼 */
        if (info.size > 0 && have > info.size) {
            out.delete()
            have = 0
        }
        /* 一上来就把进度摆到断点位置，不然界面会从 0 跳一下 */
        if (info.size > 0 && have > 0) onProgress((have.toFloat() / info.size).coerceIn(0f, 1f))

        var round = 0
        while (round < 2) {
            round++
            val builder = okhttp3.Request.Builder().url(url)
            if (have > 0) builder.header("Range", "bytes=$have-")
            client.newCall(builder.build()).execute().use { resp ->
                when {
                    resp.code == 416 -> {
                        /* 服务端说这个范围不合法 —— 残包跟它对不上，丢掉 */
                        out.delete()
                        have = 0
                    }

                    resp.isSuccessful -> {
                        val body = resp.body
                        if (body != null) {
                            /* 只有「206 且手上有残包」才用追加模式写 */
                            val resumed = resp.code == 206 && have > 0
                            if (!resumed) {
                                if (out.exists()) out.delete()
                                have = 0
                            }
                            val total =
                                if (info.size > 0) info.size else have + body.contentLength()
                            var done = have
                            var lastSent = if (total > 0) have.toFloat() / total else -1f
                            java.io.FileOutputStream(out, resumed).use { os ->
                                body.byteStream().use { input ->
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        val n = input.read(buf)
                                        if (n <= 0) break
                                        os.write(buf, 0, n)
                                        done += n
                                        if (total > 0) {
                                            val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                            /* 别为了 1% 的粒度把界面刷爆 */
                                            if (p - lastSent > 0.01f) {
                                                lastSent = p
                                                onProgress(p)
                                            }
                                        }
                                    }
                                }
                                os.flush()
                            }
                            have = out.length()
                        }
                    }
                }
            }
            /* 下满了，或者大小未知但已经拿到像样的包 → 收工 */
            if (info.size > 0 && have >= info.size) break
            if (info.size <= 0 && have > 1024) break
        }

        if (out.length() < 1024 || (info.size > 0 && out.length() != info.size)) {
            /* 对不上就别拿去安装。残包留着（下次接着下），除非它已经超过目标大小 */
            if (info.size > 0 && out.length() > info.size) out.delete()
            return null
        }
        onProgress(1f)
        return out
    }

    /** 现在是不是连着 Wi-Fi（或有线）。判断不出来就当作「不是」—— 宁可让用户手动放行 */
    fun onWifi(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return@runCatching false
        /* VPN 也算：开着代理时系统报的是 TRANSPORT_VPN，底层多半还是 Wi-Fi，
           只认 WIFI 会把一大半人挡在门外，还看不出为什么 */
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrDefault(false)

    /** 系统允许这个 App 安装别的应用吗（Android 8+ 要用户手动开一次） */
    fun canInstall(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { ctx.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else true

    /** 跳去「安装未知应用」开关页 */
    fun openInstallSetting(ctx: Context) {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.packageName)
                )
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** 拉起系统安装器 */
    fun install(ctx: Context, apk: File): Boolean = runCatching {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 装完就没用了，别一直占着 cache */
    fun clearApk(ctx: Context) {
        runCatching { File(ctx.cacheDir, APK_NAME).takeIf { it.exists() }?.delete() }
    }
}
