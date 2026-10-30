package xyz.yihaozhan.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import xyz.yihaozhan.app.data.AppPayload
import xyz.yihaozhan.app.data.Prefs
import xyz.yihaozhan.app.data.Repo
import xyz.yihaozhan.app.data.UpdateRepo
import xyz.yihaozhan.app.data.VersionInfo

class MainVm(app: Application) : AndroidViewModel(app) {

    data class Ui(
        val data: AppPayload? = null,
        val booting: Boolean = true,
        val refreshing: Boolean = false,
        val offline: Boolean = false,
        val error: String? = null,
        val lastSync: Long = 0L
    )

    /**
     * 自更新的状态机。
     *
     * idle → (查到了) latest != null → downloading → apk != null（等系统安装器接手）
     * `latest` 只在**确实比本机新**时才非空，界面不用自己再比一遍。
     */
    data class Upd(
        val checking: Boolean = false,
        val latest: VersionInfo? = null,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val apk: java.io.File? = null,
        val error: String? = null
    )

    var ui by mutableStateOf(Ui())
        private set

    var upd by mutableStateOf(Upd())
        private set

    /** 本机版本号/版本名，比较和解显示都用它 */
    val myCode: Int by lazy { UpdateRepo.installedCode(getApplication()) }
    val myVersion: String by lazy { UpdateRepo.installedName(getApplication()) }

    private val prefs = Prefs.of(app)

    init {
        boot()
        /* 启动顺手问一句有没有新版：就几十字节的 JSON，失败也不吭声。
           设置里关掉「启动时检查新版」就不问，从关于那一行手动点也一样能查。 */
        if (prefs.autoCheckUpdate) checkUpdate(silent = true)
    }

    /**
     * 冷启动：先端出缓存（有就立刻能看），再去拉云端。
     * 顺序反过来会有「白屏转圈等网络」的体验，那就不是「打开即最新」，而是「打开即等待」。
     */
    private fun boot() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val cache = Repo.loadCache(ctx)
            ui = ui.copy(
                data = cache,
                booting = cache == null,
                lastSync = Repo.lastSyncAt(ctx)
            )
            /* 设置里关掉「打开时自动拉最新」就只在手动点同步时联网；
               但手机里一份都没有时还是得联网一次，否则就是空壳 */
            if (cache == null || prefs.autoSync) pull()
        }
    }

    fun refresh() = pull()

    /* ------------------------------ 自更新 ------------------------------ */

    /**
     * 问一下有没有新版。
     * silent = true 时不把「连不上」当错误报出来（启动时那次就是这么用的）。
     */
    fun checkUpdate(silent: Boolean = false) {
        if (upd.checking || upd.downloading) return
        viewModelScope.launch {
            upd = upd.copy(checking = true, error = null)
            try {
                val info = UpdateRepo.check(getApplication())
                val newer = info?.takeIf { it.versionCode > myCode }
                upd = upd.copy(
                    checking = false,
                    /* 拉不到（离线/超时）时保留上次查到的，但同样得比本机新才算 */
                    latest = newer ?: upd.latest?.takeIf { it.versionCode > myCode },
                    error = if (!silent && info == null) "连不上，过会儿再试" else null
                )
            } catch (e: Throwable) {
                /* 兜底：别让 checking 卡住，否则按钮永远转圈、再也点不动 */
                upd = upd.copy(checking = false, error = if (silent) null else "连不上，过会儿再试")
            }
        }
    }

    /** 下安装包，进度写进 upd.progress */
    fun downloadUpdate() {
        val info = upd.latest ?: return
        if (upd.downloading) return
        val ctx = getApplication<Application>()
        /* 一个包 1.2 MB，流量党不该被悄悄扣：设置里没关这条，就等 Wi-Fi */
        if (prefs.updateWifiOnly && !UpdateRepo.onWifi(ctx)) {
            upd = upd.copy(error = "等连上 Wi-Fi 再下（设置里可以关掉这条）")
            return
        }
        viewModelScope.launch {
            upd = upd.copy(downloading = true, progress = 0f, error = null, apk = null)
            val f = UpdateRepo.download(getApplication(), info) { p ->
                upd = upd.copy(progress = p)
            }
            upd = if (f != null) {
                upd.copy(downloading = false, progress = 1f, apk = f, error = null)
            } else {
                upd.copy(downloading = false, progress = 0f, error = "下载失败，检查下网络再试")
            }
        }
    }

    /** 用户点了「稍后」：把这一版收起来，别一直杵着 */
    fun dismissUpdate() {
        if (upd.downloading) return
        UpdateRepo.clearApk(getApplication())
        upd = upd.copy(apk = null, progress = 0f, latest = null, error = null)
    }

    private fun pull() {
        if (ui.refreshing) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            ui = ui.copy(refreshing = true, error = null)
            val fresh = Repo.fetchRemote(ctx)
            ui = if (fresh != null) {
                ui.copy(
                    data = fresh,
                    refreshing = false,
                    booting = false,
                    offline = false,
                    error = null,
                    lastSync = System.currentTimeMillis()
                )
            } else {
                val has = ui.data != null
                ui.copy(
                    refreshing = false,
                    booting = false,
                    offline = !has || !Repo.online(ctx),
                    error = if (has) null
                    else "第一次打开需要联网一次\n联上之后整站就存进手机了，以后没网也能看"
                )
            }
        }
    }
}
