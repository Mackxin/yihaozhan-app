package xyz.yihaozhan.app.data

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 一条随手记。
 *
 * 字段名**故意和站点版一模一样**（`ssj.notes.v2` 里那套）：这样网页版导出的
 * 备份能直接导进来，App 导出的也能在网页版恢复，两边不用写转换代码。
 *
 * `title` 和 `customTitle` 是一对：`customTitle = false` 时标题是**从正文第一行推出来的**
 * （[autoTitleOf]），正文一改标题跟着变；用户手动改过标题才有 `customTitle = true`。
 * 这套逻辑跟站点 `autoTitle()` 完全一样，不然同一条笔记在两边标题会不一样。
 */
@Serializable
data class Note(
    val id: String = "",
    val title: String = "未命名笔记",
    val content: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pinned: Boolean = false,
    val customTitle: Boolean = false,
    val tags: List<String> = emptyList(),
    /** 非空 = 在回收站里。存的是「什么时候删的」，回收站按它倒序 */
    val deletedAt: Long? = null
)

/**
 * 随手记的仓库。
 *
 * 存 SharedPreferences（`yihaozhan.notes`，键 `notes`），跟 [Local] 一个套路：
 * `mutableStateOf` 兜着，改一条列表和页头统计立刻跟着变。
 *
 * 为什么不并进 [Local]：随手记可能很长（几 KB 的 Markdown），跟收藏那几个 id 混在
 * 同一份 JSON 里，每次标星都要重写一大坨；分开放互不牵连。
 */
@Stable
class Notes private constructor(ctx: Context) {

    private val sp = ctx.applicationContext
        .getSharedPreferences("yihaozhan.notes", Context.MODE_PRIVATE)

    var notes: List<Note> by mutableStateOf(read())
        private set

    /** 回收站外的笔记：置顶在前，其余按更新时间倒序 */
    val active: List<Note>
        get() = notes.filter { it.deletedAt == null }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    /** 回收站：最近删的在前 */
    val trash: List<Note>
        get() = notes.filter { it.deletedAt != null }
            .sortedByDescending { it.deletedAt ?: 0L }

    /** 所有用过的标签（去重，按出现次数从多到少） */
    val allTags: List<String>
        get() = active.flatMap { it.tags }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }

    /* ------------------------------ 读 / 写 ------------------------------ */

    fun byId(id: String): Note? = notes.firstOrNull { it.id == id }

    /** 新建一条空笔记并返回它（编辑器直接进） */
    fun create(): Note {
        val now = System.currentTimeMillis()
        val one = Note(id = uid(), title = "", content = "", createdAt = now, updatedAt = now)
        notes = listOf(one) + notes
        persist()
        return one
    }

    /**
     * 保存正文 / 标签。标题跟着正文走（除非用户手动改过）。
     * 返回 true 表示有改动。
     */
    fun save(id: String, content: String, tags: List<String>): Boolean {
        val old = byId(id) ?: return false
        val c = content
        val t = if (old.customTitle) old.title else autoTitleOf(c)
        if (c == old.content && t == old.title && tags == old.tags) return false
        val one = old.copy(
            content = c,
            title = t,
            tags = tags.distinct(),
            updatedAt = System.currentTimeMillis()
        )
        notes = notes.map { if (it.id == id) one else it }
        persist()
        return true
    }

    /** 手动改名。传空串 = 恢复自动标题（跟站点那套一致） */
    fun rename(id: String, title: String) {
        val old = byId(id) ?: return
        val v = title.trim()
        val one = if (v.isEmpty()) {
            old.copy(customTitle = false, title = autoTitleOf(old.content))
        } else {
            old.copy(customTitle = true, title = v.take(40))
        }
        notes = notes.map { if (it.id == id) one else it }
        persist()
    }

    fun togglePin(id: String) {
        notes = notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
        persist()
    }

    fun addTag(id: String, tag: String) {
        val t = tag.trim().removePrefix("#").trim().take(20)
        if (t.isEmpty()) return
        val old = byId(id) ?: return
        if (old.tags.contains(t)) return
        notes = notes.map {
            if (it.id == id) it.copy(tags = it.tags + t, updatedAt = System.currentTimeMillis()) else it
        }
        persist()
    }

    fun removeTag(id: String, tag: String) {
        val old = byId(id) ?: return
        notes = notes.map {
            if (it.id == id) it.copy(tags = it.tags.filterNot { x -> x == tag }) else it
        }
        persist()
    }

    /* ------------------------------ 回收站 ------------------------------ */

    fun toTrash(id: String) {
        val now = System.currentTimeMillis()
        notes = notes.map { if (it.id == id) it.copy(deletedAt = now) else it }
        persist()
    }

    fun restore(id: String) {
        notes = notes.map {
            if (it.id == id) it.copy(deletedAt = null, updatedAt = System.currentTimeMillis()) else it
        }
        persist()
    }

    /** 彻底删掉一条 */
    fun purge(id: String) {
        notes = notes.filterNot { it.id == id }
        persist()
    }

    fun clearTrash() {
        notes = notes.filter { it.deletedAt == null }
        persist()
    }

    /** 编辑器退出时用：一条空笔记（没标题没正文没标签）就别留着占地方 */
    fun dropIfEmpty(id: String) {
        val n = byId(id) ?: return
        if (n.content.isBlank() && n.tags.isEmpty() && !n.customTitle) {
            notes = notes.filterNot { it.id == id }
            persist()
        }
    }

    /* ------------------------------ 统计 ------------------------------ */

    /** 正文总字数：跟站点一样剥掉 Markdown 标记再数非空白字符 */
    val totalChars: Int
        get() = active.sumOf { plainChars(it.content) }

    /** 本周（周一起算）新增几条 */
    val thisWeek: Int
        get() {
            val c = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val from = c.timeInMillis
            return active.count { it.createdAt >= from }
        }

    /* ------------------------------ 备份 ------------------------------ */

    fun exportJson(): String = PRETTY.encodeToString(NOTE_LIST, notes)

    /** 导回来：按 id 去重，返回真正加进来的条数 */
    fun importJson(text: String): Int {
        /* 两种形状都认：网页版备份是一整个数组；App 的「整份备份」里是 { notes: [...] } */
        val arr: List<Note> = try {
            PRETTY.decodeFromString(NOTE_LIST, text)
        } catch (e: Throwable) {
            try {
                val obj = PRETTY.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
                val el = obj?.get("notes")
                if (el == null) emptyList() else PRETTY.decodeFromJsonElement(NOTE_LIST, el)
            } catch (e2: Throwable) {
                emptyList()
            }
        }
        if (arr.isEmpty()) return 0
        val have = notes.map { it.id }.toMutableSet()
        val add = arr.filter { it.content.isNotBlank() || it.title.isNotBlank() }
            .map { if (it.id.isBlank()) it.copy(id = uid()) else it }
            .filter { have.add(it.id) }
        if (add.isEmpty()) return 0
        notes = (add + notes).sortedByDescending { it.updatedAt }
        persist()
        return add.size
    }

    /**
     * 从「网页版随手记」搬一次数据。
     *
     * 以前 App 是拿 WebView 打开站点 `#/note` 的，那些笔记躺在 WebView 的
     * `localStorage['ssj.notes.v2']` 里（按 origin 隔离）。现在换成原生页，
     * 不搬过来的话用户会看到空列表，以为自己的笔记丢了。
     *
     * 做法：开一个**临时的** WebView，用 `loadDataWithBaseURL(origin, …)` 把文档的
     * origin 顶成站点域名，然后 `evaluateJavascript` 把那个键读出来。
     * 不加载任何真实页面，所以又快又不花流量。
     *
     * 只搬一次（`migrated` 标记），并且**只有本地为空时**才搬 —— 用户后来的编辑不会被冲掉。
     */
    suspend fun migrateFromWeb(ctx: Context): Int {
        if (notes.isNotEmpty()) return 0
        if (sp.getBoolean("migrated", false)) return 0
        var best: List<Note> = emptyList()
        for (origin in ORIGINS) {
            val raw = runCatching { readWebStorage(ctx, origin) }.getOrNull()
            if (raw.isNullOrBlank()) continue
            val list = runCatching {
                PRETTY.decodeFromString(NOTE_LIST, raw)
            }.getOrNull() ?: continue
            if (list.size > best.size) best = list
        }
        sp.edit().putBoolean("migrated", true).apply()
        if (best.isEmpty()) return 0
        notes = best.map { if (it.id.isBlank()) it.copy(id = uid()) else it }
        persist()
        return best.size
    }

    /** 读某个 origin 下的 `ssj.notes.v2`（WebView 必须在主线程建） */
    private suspend fun readWebStorage(ctx: Context, origin: String): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                /* 用传进来的 Activity context 建 WebView：applicationContext 建 WebView
                   在个别机型上会因为拿不到主题而崩，读 localStorage 又不值得冒这个险 */
                val wv = WebView(ctx)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                var done = false
                fun finish(v: String?) {
                    if (done) return
                    done = true
                    runCatching { wv.destroy() }
                    if (cont.isActive) cont.resume(v)
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "(function(){try{return localStorage.getItem('ssj.notes.v2')||''}catch(e){return ''}})()"
                        ) { v -> finish(unquoteJs(v)) }
                    }
                }
                runCatching {
                    wv.loadDataWithBaseURL(origin, "<html><body></body></html>", "text/html", "utf-8", null)
                }.onFailure { finish(null) }
                cont.invokeOnCancellation { runCatching { wv.destroy() } }
            }
        }

    /* ------------------------------ 内部 ------------------------------ */

    private fun read(): List<Note> = runCatching {
        sp.getString("notes", null)?.let { PRETTY.decodeFromString(NOTE_LIST, it) } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun persist() {
        runCatching { sp.edit().putString("notes", PRETTY.encodeToString(NOTE_LIST, notes)).apply() }
    }

    companion object {
        /** 站点在哪几个 origin 上跑过 —— App 里打开随手记时，笔记就存在其中一个下面 */
        private val ORIGINS = listOf(
            "https://mackxin.github.io/xingyu/",
            "http://yihaozhan.xyz/xingyu/"
        )

        private val NOTE_LIST = ListSerializer(Note.serializer())

        private val PRETTY = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        @Volatile
        private var inst: Notes? = null

        fun of(ctx: Context): Notes = inst ?: synchronized(this) {
            inst ?: Notes(ctx.applicationContext).also { inst = it }
        }

        /** `evaluateJavascript` 回来的是 JSON 字面量：`"abc"` / `null` / `""` */
        private fun unquoteJs(v: String?): String? {
            if (v == null || v == "null") return null
            return runCatching {
                val parsed = JSONTokener(v).nextValue()
                if (parsed is String) parsed else null
            }.getOrNull()
        }

        fun uid(): String =
            System.currentTimeMillis().toString(36) + (100000..999999).random().toString(36)

        /** 从正文第一行推标题：跟站点 `autoTitle()` 一模一样，两边显示才对得上 */
        fun autoTitleOf(content: String): String {
            val line = content.split("\n")
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("```") }
                ?: return "未命名笔记"
            var t = line
                .replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^[-*+]\\s+"), "")
                .replace(Regex("^\\d+[.)]\\s*"), "")
                .replace(Regex("[`*_~>#\\[\\]]"), "")
                .trim()
            if (t.isEmpty()) t = "未命名笔记"
            return t.take(24)
        }

        /** 卡片上那两行摘要：剥掉标记、压成一行。
            只剩标题的笔记返回空串 —— 让卡片直接不画摘要，别写一句「空白笔记」占位 */
        fun snippetOf(content: String, title: String): String {
            var c = content
                .replace(Regex("(?m)^#{1,6}\\s*"), "")
                .replace(Regex("[`*_~>#\\[\\]]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (title.isNotEmpty() && c.startsWith(title)) c = c.removePrefix(title).trim()
            return c.take(56)
        }

        /** 纯文字字数（剥掉标记再数非空白字符） */
        fun plainChars(content: String): Int {
            var s = content
                .replace(Regex("```[\\s\\S]*?```"), " ")
                .replace(Regex("`[^`]*`"), " ")
                .replace(Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
                .replace(Regex("(?m)^\\s{0,3}[#>*+-]\\s*"), "")
                .replace(Regex("\\s+"), "")
            return s.length
        }

        fun dayText(ts: Long): String {
            if (ts <= 0L) return ""
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            val now = Calendar.getInstance()
            if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            ) {
                return "今天 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))
            }
            return SimpleDateFormat("M 月 d 日", Locale.CHINA).format(Date(ts))
        }
    }
}
