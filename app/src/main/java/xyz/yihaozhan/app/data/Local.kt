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
 * 从别的 App 分享进来、还没归类的一条。
 *
 * 为什么先「收着」而不是直接建成随记：分享那一刻人还在别的 App 里，
 * 不该被打断。等回到壹号栈再决定它是「写成随记」还是「加进站点收藏」。
 */
@Serializable
data class InboxItem(
    val id: String = "",
    val text: String = "",
    /** 从原文里抽出来的第一个网址；纯文字分享时为空 */
    val url: String = "",
    val ts: Long = 0L
)

/** 阅读页存下来的一段话 */
@Serializable
data class Extract(
    val id: String = "",
    val postId: String = "",
    val postTitle: String = "",
    val text: String = "",
    val ts: Long = 0L
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

    /**
     * 收藏的**站点网址**。
     *
     * 跟上面那对「文章收藏」是两码事：站点那边 `#/nav` 的「只看收藏」、右上那颗星、
     * 导出书签，认的都是网址。App 以前只有文章维度，网页收藏的站点到手机上看不见，
     * 这一份把那个口径补上（同样只存本机）。
     */
    var favLinks: Set<String> by mutableStateOf(readIds("favLinks"))
        private set

    /** 标了「失效」的站点网址：卡片淡下去、名字划一道，不再抢视线 */
    var dead: Set<String> by mutableStateOf(readIds("dead"))
        private set

    /** 最近打开的站点，新的在前 */
    var recents: List<RecentLink> by mutableStateOf(readRecents())
        private set

    /**
     * 每个站点被点开过几次（网址 → 次数）。足迹里的「最常去」就是按它排的。
     * 跟 `recents` 分工：recents 管「最近」，只留 20 条；这个只管次数，不设上限但会瘦身。
     */
    var visits: Map<String, Int> by mutableStateOf(readCounts("visits"))
        private set

    /**
     * 每天动了多少下（`2026-11-02` → 次数）：点开站点、写一条随记都算。
     * 足迹底下那张热力图就是它 —— 只统计「你自己产生的动作」，不算你读了多少字。
     */
    var daily: Map<String, Int> by mutableStateOf(readCounts("daily"))
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

    /* ------------------------- 站点收藏 / 失效 ------------------------- */

    fun isFavLink(url: String) = url in favLinks
    fun isDead(url: String) = url in dead

    fun toggleFavLink(url: String): Boolean {
        if (url.isBlank()) return false
        val on = url !in favLinks
        favLinks = if (on) favLinks + url else favLinks - url
        writeIds("favLinks", favLinks)
        return on
    }

    fun toggleDead(url: String): Boolean {
        if (url.isBlank()) return false
        val on = url !in dead
        dead = if (on) dead + url else dead - url
        writeIds("dead", dead)
        return on
    }

    fun clearFavLinks() {
        favLinks = emptySet()
        sp.edit().remove("favLinks").apply()
    }

    fun clearDead() {
        dead = emptySet()
        sp.edit().remove("dead").apply()
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
        bumpVisit(url)
    }

    /* ------------------------ 次数 / 每日计数 ------------------------ */

    private fun bumpVisit(url: String) {
        visits = visits + (url to ((visits[url] ?: 0) + 1))
        /* 两千多个站点随手指点几下就能攒满，超过上限把最少的几条扔掉 */
        if (visits.size > MAX_VISITS) {
            visits = visits.entries.sortedByDescending { it.value }.take(MAX_VISITS)
                .associate { it.key to it.value }
        }
        writeCounts("visits", visits)
        bumpDay()
    }

    /** 今天 +1。写随记、点站点都走这里，热力图才有东西可画 */
    fun bumpDay() {
        val k = dayKey(System.currentTimeMillis())
        daily = daily + (k to ((daily[k] ?: 0) + 1))
        if (daily.size > MAX_DAYS) {
            daily = daily.entries.sortedByDescending { it.key }.take(MAX_DAYS)
                .associate { it.key to it.value }
        }
        writeCounts("daily", daily)
    }

    /** 最常去的几个站点：次数从多到少，取前 n 条 */
    fun topVisits(n: Int = 8): List<Pair<String, Int>> =
        visits.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    fun clearVisits() {
        visits = emptyMap()
        sp.edit().remove("visits").apply()
    }

    fun clearDaily() {
        daily = emptyMap()
        sp.edit().remove("daily").apply()
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
        bumpDay()
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

    /* ------------------------------ 待整理（外部分享） ------------------------------ */

    /** 别人（或别的 App）分享进来的东西，新的在前 */
    var inbox: List<InboxItem> by mutableStateOf(readInbox())
        private set

    fun addInbox(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val one = InboxItem(
            id = "b-" + System.currentTimeMillis().toString(36),
            text = t,
            url = urlIn(t),
            ts = System.currentTimeMillis()
        )
        inbox = listOf(one) + inbox
        saveInbox()
        return true
    }

    fun removeInbox(id: String) {
        inbox = inbox.filterNot { it.id == id }
        saveInbox()
    }

    fun clearInbox() {
        inbox = emptyList()
        saveInbox()
    }

    /* ------------------------------ 摘录 ------------------------------ */

    var extracts: List<Extract> by mutableStateOf(readExtracts())
        private set

    val sortedExtracts: List<Extract>
        get() = extracts.sortedByDescending { it.ts }

    fun addExtract(postId: String, postTitle: String, text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        /* 同一篇里同一段不重复存 —— 长按两次不该留两份 */
        if (extracts.any { it.postId == postId && it.text == t }) return false
        val one = Extract(
            id = "e-" + System.currentTimeMillis().toString(36),
            postId = postId,
            postTitle = postTitle,
            text = t,
            ts = System.currentTimeMillis()
        )
        extracts = listOf(one) + extracts
        saveExtracts()
        return true
    }

    fun removeExtract(id: String) {
        extracts = extracts.filterNot { it.id == id }
        saveExtracts()
    }

    fun clearExtracts() {
        extracts = emptyList()
        saveExtracts()
    }

    /* ------------------------------ 全量备份 ------------------------------ */

    /**
     * 把「只在这台手机上」的所有东西打成一个 JSON：
     * 收藏、稍后读、站点收藏、失效标记、最近打开、次数、每日计数、阅读位置、
     * 灵感随记、摘录、待整理，以及设置。
     *
     * 为什么需要它：这些都没有账号、不上云，换手机 / 重装就等于归零。
     * 导一份出去丢网盘，是现在这套架构里唯一靠谱的保险。
     */
    fun exportAllJson(prefs: Prefs): String {
        fun arrOf(list: List<*>) = JSONArray().also { a -> list.forEach { a.put(it) } }
        val o = JSONObject()
        o.put("schema", 1)
        o.put("app", "壹号栈")
        o.put("exported", dateNow())
        o.put("counts", JSONObject().apply {
            put("ideas", ideas.size); put("extracts", extracts.size)
            put("fav", fav.size); put("favLinks", favLinks.size)
            put("inbox", inbox.size); put("recents", recents.size)
        })
        o.put("fav", arrOf(fav.toList()))
        o.put("later", arrOf(later.toList()))
        o.put("favLinks", arrOf(favLinks.toList()))
        o.put("dead", arrOf(dead.toList()))
        o.put("recents", JSONArray().also { a ->
            recents.forEach { r ->
                a.put(JSONObject().put("name", r.name).put("url", r.url).put("ts", r.ts))
            }
        })
        o.put("visits", JSONObject(visits))
        o.put("daily", JSONObject(daily))
        o.put("pos", JSONObject(positions as Map<*, *>))
        o.put("ideas", JSONArray().also { a ->
            ideas.forEach { i ->
                a.put(JSONObject().put("id", i.id).put("text", i.text)
                    .put("ts", i.ts).put("pinned", i.pinned))
            }
        })
        o.put("extracts", JSONArray().also { a ->
            extracts.forEach { e ->
                a.put(JSONObject().put("id", e.id).put("postId", e.postId)
                    .put("postTitle", e.postTitle).put("text", e.text).put("ts", e.ts))
            }
        })
        o.put("inbox", JSONArray().also { a ->
            inbox.forEach { b ->
                a.put(JSONObject().put("id", b.id).put("text", b.text)
                    .put("url", b.url).put("ts", b.ts))
            }
        })
        o.put("prefs", JSONObject(prefs.exportMap()))
        return o.toString(2)
    }

    /**
     * 恢复备份：**合并**，不清空现有的。
     *
     * 为什么是合并不是覆盖：手滑点错一次就把手机里的东西全换掉太狠了。
     * 合并最坏情况只是多几条重复（正文/摘录都按内容去重，实际上不会重复）。
     * 返回这次新加进来的条数；解析失败返回 -1。
     */
    fun importAllJson(text: String, prefs: Prefs): Int {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return -1
        var added = 0

        fun strings(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } }
        }

        fun mapsInt(key: String): Map<String, Int> {
            val j = o.optJSONObject(key) ?: return emptyMap()
            return j.keys().asSequence().associateWith { j.optInt(it, 0) }
                .filterValues { it > 0 }
        }

        fun mapsStr(key: String): Map<String, String> {
            val j = o.optJSONObject(key) ?: return emptyMap()
            return j.keys().asSequence().associateWith { j.optString(it, "") }
                .filterValues { it.isNotBlank() }
        }

        val newFav = strings("fav")
        if (newFav.isNotEmpty()) { fav = fav + newFav; writeIds("fav", fav); added += newFav.size }
        val newLater = strings("later")
        if (newLater.isNotEmpty()) { later = later + newLater; writeIds("later", later); added += newLater.size }
        val newLinks = strings("favLinks")
        if (newLinks.isNotEmpty()) { favLinks = favLinks + newLinks; writeIds("favLinks", favLinks); added += newLinks.size }
        val newDead = strings("dead")
        if (newDead.isNotEmpty()) { dead = dead + newDead; writeIds("dead", dead); added += newDead.size }

        o.optJSONArray("recents")?.let { a ->
            val byUrl = recents.associateBy { it.url }.toMutableMap()
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val u = r.optString("url")
                if (u.isNotBlank() && u !in byUrl) {
                    byUrl[u] = RecentLink(r.optString("name"), u, r.optLong("ts"))
                    added++
                }
            }
            recents = byUrl.values.sortedByDescending { it.ts }.take(MAX_RECENT)
            sp.edit().putString("recents", XYJson.encodeToString(RECENT_LIST, recents)).apply()
        }

        val v = mapsInt("visits")
        if (v.isNotEmpty()) {
            visits = visits.toMutableMap().also { m -> v.forEach { (k, n) -> m[k] = maxOf(m[k] ?: 0, n) } }
            writeCounts("visits", visits)
            added += v.size
        }
        val d = mapsInt("daily")
        if (d.isNotEmpty()) {
            daily = daily.toMutableMap().also { m -> d.forEach { (k, n) -> m[k] = maxOf(m[k] ?: 0, n) } }
            writeCounts("daily", daily)
        }

        val p = mapsStr("pos")
        if (p.isNotEmpty()) {
            p.forEach { (k, val2) -> if (k !in positions) { positions[k] = val2; added++ } }
            posCount = positions.size
            sp.edit().putString("pos", XYJson.encodeToString(POS_MAP, positions)).apply()
        }

        o.optJSONArray("ideas")?.let { a ->
            val seen = ideas.map { it.text.trim() }.toMutableSet()
            val out = ideas.toMutableList()
            for (i in 0 until a.length()) {
                val j = a.optJSONObject(i) ?: continue
                val t = j.optString("text").trim()
                if (t.isEmpty() || t in seen) continue
                seen.add(t)
                out.add(Idea(j.optString("id").ifBlank { "i-" + (j.optLong("ts", 0L).toString(36)) },
                    t, j.optLong("ts", 0L), j.optBoolean("pinned", false)))
                added++
            }
            ideas = out
            saveIdeas()
        }

        o.optJSONArray("extracts")?.let { a ->
            val out = extracts.toMutableList()
            for (i in 0 until a.length()) {
                val j = a.optJSONObject(i) ?: continue
                val t = j.optString("text").trim()
                if (t.isEmpty() || out.any { it.text.trim() == t }) continue
                out.add(Extract(j.optString("id").ifBlank { "e-" + System.currentTimeMillis().toString(36) + i },
                    j.optString("postId"), j.optString("postTitle"), t, j.optLong("ts", 0L)))
                added++
            }
            extracts = out
            saveExtracts()
        }

        o.optJSONArray("inbox")?.let { a ->
            val out = inbox.toMutableList()
            for (i in 0 until a.length()) {
                val j = a.optJSONObject(i) ?: continue
                val t = j.optString("text").trim()
                if (t.isEmpty() || out.any { it.text.trim() == t }) continue
                out.add(InboxItem(j.optString("id").ifBlank { "b-" + System.currentTimeMillis().toString(36) + i },
                    t, j.optString("url"), j.optLong("ts", 0L)))
                added++
            }
            inbox = out
            saveInbox()
        }

        o.optJSONObject("prefs")?.let { j ->
            prefs.importMap(j.keys().asSequence().associateWith { j.get(it) })
        }

        return added
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

    private fun readCounts(key: String): Map<String, Int> = runCatching {
        sp.getString(key, null)
            ?.let { XYJson.decodeFromString(COUNT_MAP, it) }
            ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun writeCounts(key: String, v: Map<String, Int>) {
        sp.edit().putString(key, XYJson.encodeToString(COUNT_MAP, v)).apply()
    }

    private fun readIdeas(): List<Idea> = runCatching {
        sp.getString("ideas", null)
            ?.let { XYJson.decodeFromString(IDEA_LIST, it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun readInbox(): List<InboxItem> = runCatching {
        sp.getString("inbox", null)
            ?.let { XYJson.decodeFromString(INBOX_LIST, it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun saveInbox() {
        sp.edit().putString("inbox", XYJson.encodeToString(INBOX_LIST, inbox)).apply()
    }

    private fun readExtracts(): List<Extract> = runCatching {
        sp.getString("extracts", null)
            ?.let { XYJson.decodeFromString(EXTRACT_LIST, it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun saveExtracts() {
        sp.edit().putString("extracts", XYJson.encodeToString(EXTRACT_LIST, extracts)).apply()
    }

    /** 从一段分享文字里抽第一个网址 —— 分享过来的通常是「标题 + 链接」混在一起 */
    private fun urlIn(s: String): String {
        val m = Regex("https?://[^\\s，。、）)】\\]\"'<>]+").find(s) ?: return ""
        return m.value.trimEnd('.', ',', '。', ';', '；')
    }

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
        private const val MAX_VISITS = 400
        private const val MAX_DAYS = 400

        private val ID_LIST = ListSerializer(String.serializer())
        private val RECENT_LIST = ListSerializer(RecentLink.serializer())
        private val POS_MAP = MapSerializer(String.serializer(), String.serializer())
        private val IDEA_LIST = ListSerializer(Idea.serializer())
        private val INBOX_LIST = ListSerializer(InboxItem.serializer())
        private val EXTRACT_LIST = ListSerializer(Extract.serializer())
        private val COUNT_MAP = MapSerializer(String.serializer(), Int.serializer())

        @Volatile
        private var inst: Local? = null

        /** 时间戳 → `yyyy-MM-dd`（热力图 / 每日计数的格子 key） */
        fun dayKey(ts: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH) + 1
            val d = c.get(Calendar.DAY_OF_MONTH)
            return "$y-" + (if (m < 10) "0$m" else "$m") + "-" + (if (d < 10) "0$d" else "$d")
        }

        fun of(ctx: Context): Local = inst ?: synchronized(this) {
            inst ?: Local(ctx.applicationContext).also { inst = it }
        }
    }
}
