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

class MainVm(app: Application) : AndroidViewModel(app) {

    data class Ui(
        val data: AppPayload? = null,
        val booting: Boolean = true,
        val refreshing: Boolean = false,
        val offline: Boolean = false,
        val error: String? = null,
        val lastSync: Long = 0L
    )

    var ui by mutableStateOf(Ui())
        private set

    private val prefs = Prefs.of(app)

    init {
        boot()
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
