package xyz.yihaozhan.app.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject

/** 导航里点开过的一个站点：只留名字和网址，够拼一颗小标签 */
@Serializable
data class RecentLink(
    val name: String = "",
    val url: String = "",
    val ts: Long = 0L
)

/**
 * 灵感随记的一条。跟站点 `#/idea` 那页同一个形状：正文 + 时间 + 置顶，
 * 标签不单独存 —— 从正文里的 `#xxx` 现抠，改正文标签就跟着变。
 */
@Serializable
data class Idea(
    val id: String = "",
    val text: String = "",
    val ts: Long = 0L,
    val pinned: Boolean = false
)

/**
 * 「我自己产生的那部分数据」—— 收藏、稍后读、最近打开、读到哪了、灵感随记。
 *
 * 为什么不跟站点数据混在一起：`app.json` 是站点后台的内容，对所有人生效、只读；
 * 这一份纯属本机，换手机就没了，也绝不上传。两者混在一个文件里，
 * 一次「同步内容」就会把你标过的星冲掉。
 *
 * 存放：SharedPreferences（`yihaozhan.local`），列表走 JSON、阅读位置走一个 map。
 * 用 mutableStateOf 兜着，标星之后收藏页和详情页能立刻跟着变。
 */
@Stable
class Local private constructor(ctx: Context) {

    private val sp = ctx.applicationContext
        .getSharedPreferences("yihaozhan.local", Context.MODE_PRIVATE)

    /** 收藏的文章 id */
    var fav: Set<String> by mutableStateOf(readIds("fav"))
        private set

    /** 稍后读的文章 id */
    var later: Set<String> by mutableStateOf(readIds("later"))
        private set

    /** 最近打开的站点，新的在前 */
    var recents: List<RecentLink> by mutableStateOf(readRecents())
        private set

    /* 阅读位置不进 Compose 状态：它只在进出文章时读一次、离开时写一次，
       进了状态反而会让整篇文章跟着重组。 */
    private var positions: MutableMap<String, String> = readPositions()

    /** 有几篇记着读到哪了。单独拿出来当状态：设置页要显示它，
        而 positions 本身故意不进状态（进进出出文章都变，没必要惊动整篇） */
    var posCount: Int by mutableStateOf(positions.size)
        private set

    fun isFav(id: String) = id in fav
    fun isLater(id: String) = id in later

    fun toggleFav(id: String) {
        if (id.isBlank()) return
        fav = if (id in fav) fav - id else fav + id
        writeIds("fav", fav)
    }

    fun toggleLater(id: String) {
        if (id.isBlank()) return
        later = if (id in later) later - id else later + id
        writeIds("later", later)
    }

    /* ------------------------------ 最近打开 ------------------------------ */

    /** 点开一个站点就记一笔：同一个网址重新点会顶到最前，不会重复占位 */
    fun record(url: String, name: String) {
        if (url.isBlank()) return
        val one = RecentLink(name = name.ifBlank { url }, url = url, ts = System.currentTimeMillis())
        recents = (listOf(one) + recents.filterNot { it.url == url }).take(MAX_RECENT)
        sp.edit()
            .putString("recents", XYJson.encodeToString(RECENT_LIST, recents))
            .apply()
    }

    fun clearRecents() {
        recents = emptyList()
        sp.edit().remove("recents").apply()
    }

    /* ------------------------------ 阅读位置 ------------------------------ */

    /** 上次读到第几个块、偏了多少像素；没读过返回 null */
    fun posOf(id: String): Pair<Int, Int>? {
        val s = positions[id] ?: return null
        val parts = s.split(':')
        if (parts.size != 2) return null
        val i = parts[0].toIntOrNull() ?: return null
        val o = parts[1].toIntOrNull() ?: return null
        return i to o
    }

    fun savePos(id: String, index: Int, offset: Int) {
        if (id.isBlank()) return
        val v = "$index:$offset"
        if (positions[id] == v) return
        positions[id] = v
        /* 只有几十篇也就算了，但这事儿不设上限迟早会胖 —— 超了就丢最早的几篇 */
        if (positions.size > MAX_POS) {
            positions = positions.entries.drop(positions.size - MAX_POS)
                .associateTo(LinkedHashMap()) { it.key to it.value }
        }
        posCount = positions.size
        sp.edit()
            .putString("pos", XYJson.encodeToString(POS_MAP, positions))
            .apply()
    }

    fun clearPositions() {
        positions = mutableMapOf()
        posCount = 0
        sp.edit().remove("pos").apply()
    }

    /* ------------------------------ 灵感随记 ------------------------------ */

    /**
     * 已排好序：置顶的在前，其余按时间倒序。
     * 排序放在 getter 里做，写入口就只管存原始顺序，改置顶/改正文都不会乱。
     */
    var ideas: List<Idea> by mutableStateOf(readIdeas())
        private set

    val sortedIdeas: List<Idea>
        get() = ideas.sortedWith(
            compareByDescending<Idea> { it.pinned }.thenByDescending { it.ts }
        )

    fun addIdea(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val one = Idea(
            id = "i-" + System.currentTimeMillis().toString(36),
            text = t,
            ts = System.currentTimeMillis()
        )
        ideas = listOf(one) + ideas
        saveIdeas()
        return true
    }

    /** 改正文：时间不动（跟站点一致，编辑不算新写一条） */
    fun updateIdea(id: String, text: String): Boolean {
        val t = text.trim()
        if (id.isBlank() || t.isEmpty()) return false
        ideas = ideas.map { if (it.id == id) it.copy(text = t) else it }
        saveIdeas()
        return true
    }

    fun togglePinIdea(id: String) {
        ideas = ideas.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
        saveIdeas()
    }

    fun removeIdea(id: String) {
        ideas = ideas.filterNot { it.id == id }
        saveIdeas()
    }

    fun clearIdeas() {
        ideas = emptyList()
        saveIdeas()
    }

    /* 草稿：跟站点一样，打了一半切走也不丢（站点存 localStorage，这里存 SP） */
    var ideaDraft: String
        get() = sp.getString("ideaDraft", "") ?: ""
        set(v) {
            if (v == ideaDraft) return
            sp.edit().putString("ideaDraft", v).apply()
        }

    /** 标签改名：直接改写每条正文里的 `#旧` → `#新`（标签本就是从正文现抠的） */
    fun renameIdeaTag(old: String, newName: String) {
        if (old.isBlank() || newName.isBlank()) return
        val re = Regex("(?<!\\S)#" + Pattern.quote(old) + "(?=[\\s#]|$)")
        var changed = false
        ideas = ideas.map {
            if (re.containsMatchIn(it.text)) {
                changed = true
                it.copy(text = re.replace(it.text, "#$newName"))
            } else it
        }
        if (changed) saveIdeas()
    }

    /** 标签删除：把每条正文里的 `#旧` 抹掉 */
    fun deleteIdeaTag(old: String) {
        if (old.isBlank()) return
        val re = Regex("(?<!\\S)#" + Pattern.quote(old) + "(?=[\\s#]|$)")
        var changed = false
        ideas = ideas.map {
            if (re.containsMatchIn(it.text)) {
                changed = true
                it.copy(text = re.replace(it.text, ""))
            } else it
        }
        if (changed) saveIdeas()
    }

    /** 导出成 JSON（跟站点电脑版同一个形状，两边能互相导入） */
    fun exportIdeasJson(): String {
        val arr = ideas.map { it ->
            val tags = ideaTagsOf(it.text)
            val inner = buildString {
                append("\"text\":\""); append(escapeJson(it.text)); append('"')
                append(",\"tags\":["); append(tags.joinToString { "\"${escapeJson(it)}\"" }); append(']')
                append(",\"ts\":${it.ts}")
                append(",\"date\":\""); append(fmtStamp(it.ts)); append('"')
                append(",\"pinned\":${it.pinned}")
            }
            "{$inner}"
        }
        return "[\n  " + arr.joinToString(",\n  ") + "\n]"
    }

    /** 导出成 Markdown：按时间分小标题，能直接贴进任何笔记软件 */
    fun exportIdeasMd(): String {
        val sb = StringBuilder()
        sb.append("# 灵感随记\n\n> 导出于 ").append(dateNow()).append("，共 ").append(ideas.size).append(" 条。\n\n")
        ideas.forEach {
            sb.append("## ").append(fmtStamp(it.ts)).append(if (it.pinned) " · 📌" else "").append('\n')
            val tags = ideaTagsOf(it.text)
            if (tags.isNotEmpty()) sb.append("\n_").append(tags.joinToString(" ") { "#$it" }).append("_\n")
            sb.append('\n').append(it.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    /** 从 JSON 合并导入：去重（按 trim 后的正文），返回新增条数；解析失败返回 -1 */
    fun importIdeasJson(text: String): Int {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return -1
        val seen = ideas.map { it.text.trim() }.toMutableSet()
        val out = ideas.toMutableList()
        var added = 0
        for (i in 0 until arr.length()) {
            val o = runCatching { arr.getJSONObject(i) }.getOrNull() ?: continue
            val t = (o.optString("text").ifBlank { o.optString("content") }).trim()
            if (t.isEmpty() || t in seen) continue
            seen.add(t)
            val ts = o.optLong("ts", 0L).let { if (it == 0L) parseDate(o.optString("date")) else it }
            out.add(Idea(id = "i-" + System.currentTimeMillis().toString(36) + added, text = t, ts = ts))
            added++
        }
        if (added > 0) {
            ideas = out.sortedByDescending { it.ts }
            saveIdeas()
        }
        return added
    }

    private fun saveIdeas() {
        sp.edit().putString("ideas", XYJson.encodeToString(IDEA_LIST, ideas)).apply()
    }

    /* ------------------------------ 存取 ------------------------------ */

    private fun readIds(key: String): Set<String> = runCatching {
        sp.getString(key, null)
            ?.let { XYJson.decodeFromString(ID_LIST, it).toSet() }
            ?: emptySet()
    }.getOrDefault(emptySet())

    private fun writeIds(key: String, v: Set<String>) {
        sp.edit().putString(key, XYJson.encodeToString(ID_LIST, v.toList())).apply()
    }

    private fun readRecents(): List<RecentLink> = runCatching {
        sp.getString("recents", null)
            ?.let { XYJson.decodeFromString(RECENT_LIST, it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun readPositions(): MutableMap<String, String> = runCatching {
        sp.getString("pos", null)
            ?.let { XYJson.decodeFromString(POS_MAP, it) }
            ?.toMutableMap()
            ?: mutableMapOf()
    }.getOrDefault(mutableMapOf())

    private fun readIdeas(): List<Idea> = runCatching {
        sp.getString("ideas", null)
            ?.let { XYJson.decodeFromString(IDEA_LIST, it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /* ------------------------------ 导出/导入的小工具 ------------------------------ */

    /** 正文里的 `#标签`：井号开头、接下来是中文/字母/数字/下划线，遇到其它字符就断 */
    private fun ideaTagsOf(text: String): List<String> {
        val re = Regex("#[\\u4e00-\\u9fa5A-Za-z0-9_]+")
        return re.findAll(text).map { it.value.drop(1) }.distinct().toList()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun fmtStamp(ts: Long): String {
        if (ts <= 0L) return ""
        val d = Date(ts)
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { time = d }
        val sameYear = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        val hm = p2(cal.get(Calendar.HOUR_OF_DAY)) + ":" + p2(cal.get(Calendar.MINUTE))
        return if (sameYear) "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DATE)}日 $hm"
        else "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DATE)}日"
    }

    private fun dateNow(): String {
        val d = Calendar.getInstance()
        return "${d.get(Calendar.YEAR)}/${p2(d.get(Calendar.MONTH) + 1)}/${p2(d.get(Calendar.DATE))} " +
                "${p2(d.get(Calendar.HOUR_OF_DAY))}:${p2(d.get(Calendar.MINUTE))}"
    }

    private fun parseDate(s: String): Long {
        if (s.isBlank()) return System.currentTimeMillis()
        for (f in arrayOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd HH:mm")) {
            runCatching { SimpleDateFormat(f, Locale.CHINA).parse(s)?.time }.getOrNull()?.let { return it }
        }
        return System.currentTimeMillis()
    }

    private fun p2(n: Int) = if (n < 10) "0$n" else "$n"

    companion object {
        private const val MAX_RECENT = 20
        private const val MAX_POS = 60

        private val ID_LIST = ListSerializer(String.serializer())
        private val RECENT_LIST = ListSerializer(RecentLink.serializer())
        private val POS_MAP = MapSerializer(String.serializer(), String.serializer())
        private val IDEA_LIST = ListSerializer(Idea.serializer())

        @Volatile
        private var inst: Local? = null

        fun of(ctx: Context): Local = inst ?: synchronized(this) {
            inst ?: Local(ctx.applicationContext).also { inst = it }
        }
    }
}
