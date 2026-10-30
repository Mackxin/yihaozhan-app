package xyz.yihaozhan.app.data

import android.content.Context
import android.content.Intent
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

    /* 和 app.json 同目录，以后换域名只改 Repo.URL 一处 */
    val VERSION_URL = Repo.URL.replace("app.json", "app-version.json")
    val APK_URL = Repo.URL.replace("app.json", "app.apk")
    private const val APK_NAME = "update.apk"

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
    suspend fun check(ctx: Context): VersionInfo? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Repo.online(ctx)) return@runCatching null
            val req = okhttp3.Request.Builder()
                .url("$VERSION_URL?t=" + System.currentTimeMillis())
                .header("Cache-Control", "no-cache")
                .build()
            probeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) return@use null
                XYJson.decodeFromString<VersionInfo>(text)
            }
        }.getOrNull()
    }

    /**
     * 下安装包。onProgress 拿到 0..1（不知道总长度时就给 -1）。
     * 下完校验一下大小：GitHub Pages 偶尔会返回一张 404 的 HTML，别拿去安装。
     */
    suspend fun download(
        ctx: Context,
        info: VersionInfo,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val req = okhttp3.Request.Builder().url(APK_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body ?: return@use null
                val total = body.contentLength()
                val out = File(ctx.cacheDir, APK_NAME)
                out.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastSent = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            os.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = done.toFloat() / total
                                /* 别为了 1% 的粒度把界面刷爆 */
                                if (p - lastSent > 0.01f) {
                                    lastSent = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
                onProgress(1f)
                /* 大小对不上说明下到的是错误页，删掉当失败 */
                if (out.length() < 1024 || (info.size > 0 && out.length() != info.size)) {
                    out.delete()
                    return@use null
                }
                out
            }
        }.getOrNull()
    }

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
