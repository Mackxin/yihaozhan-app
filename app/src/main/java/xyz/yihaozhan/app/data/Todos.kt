package xyz.yihaozhan.app.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 一条待办。
 *
 * 字段跟站点版 `xingyu.todo.v1` 那套一一对应（站点用短名 t/due/pri，这里用可读名）：
 * 两边都能导出、都能导入，不用写转换代码。
 *
 * [due] 是 `yyyy-MM-dd` 的字符串而不是时间戳 —— 待办只关心哪一天，不关心几点几分；
 * 存字符串还能直接按字典序比大小排「今天/以后」。
 */
@Serializable
data class Todo(
    val id: String = "",
    val text: String = "",
    val done: Boolean = false,
    val due: String = "",
    /** 0 普通 / 1 重要 / 2 紧急 */
    val pri: Int = 0,
    val at: Long = 0L,
    val doneAt: Long = 0L
)

/**
 * 待办仓库。
 *
 * 跟 [Notes] 一个套路：SharedPreferences（`yihaozhan.todo`，键 `items`）+ `mutableStateOf`，
 * 改一条列表和页头统计立刻跟着变。单独一份而不是塞进 [Local]：
 * 待办会比收藏那几个 id 长得多，每次勾一下重写一大坨不划算。
 */
@Stable
class Todos private constructor(ctx: Context) {

    private val sp = ctx.applicationContext
        .getSharedPreferences("yihaozhan.todo", Context.MODE_PRIVATE)

    var items: List<Todo> by mutableStateOf(read())
        private set

    /* ------------------------------ 派生 ------------------------------ */

    val open: List<Todo> get() = items.filter { !it.done }

    /** 今天到期的（做完的也算，否则环永远 100%） */
    val todayAll: List<Todo> get() = items.filter { it.due == today() }

    val todayDone: Int get() = todayAll.count { it.done }

    /** 逾期没做的 */
    val overdue: List<Todo>
        get() {
            val t = today()
            return items.filter { !it.done && it.due.isNotEmpty() && it.due < t }
                .sortedWith(cmp)
        }

    val todayOpen: List<Todo>
        get() = items.filter { !it.done && it.due == today() }.sortedWith(cmp)

    val later: List<Todo>
        get() {
            val t = today()
            return items.filter { !it.done && it.due.isNotEmpty() && it.due > t }.sortedWith(cmp)
        }

    val someday: List<Todo>
        get() = items.filter { !it.done && it.due.isEmpty() }.sortedWith(cmp)

    val doneList: List<Todo>
        get() = items.filter { it.done }.sortedByDescending { it.doneAt }

    /** 本周（周一起算）完成几条 */
    val weekDone: Int
        get() {
            val c = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val from = c.timeInMillis
            return items.count { it.done && it.doneAt >= from }
        }

    /* 组内排序：优先级高的在前，同优先级按到期日近的在前（没日期的垫后），
       最后按加入时间倒序 —— 新加的在上，像一张往下叠的栈 */
    private val cmp = Comparator<Todo> { a, b ->
        when {
            a.pri != b.pri -> b.pri - a.pri
            a.due != b.due -> if (a.due.isEmpty()) 1 else if (b.due.isEmpty()) -1 else a.due.compareTo(b.due)
            else -> b.at.compareTo(a.at)
        }
    }

    /* ------------------------------ 读写 ------------------------------ */

    fun add(text: String, due: String = "", pri: Int = 0): Todo? {
        val t = text.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        val one = Todo(
            id = uid(), text = t.take(200), done = false,
            due = due, pri = pri.coerceIn(0, 2), at = System.currentTimeMillis()
        )
        items = listOf(one) + items
        persist()
        return one
    }

    fun toggle(id: String) {
        val now = System.currentTimeMillis()
        items = items.map {
            if (it.id != id) it
            else it.copy(done = !it.done, doneAt = if (it.done) 0L else now)
        }
        persist()
    }

    fun remove(id: String) {
        items = items.filterNot { it.id == id }
        persist()
    }

    /** 改文字 / 日期 / 优先级，一次传全（null = 这项不动） */
    fun update(id: String, text: String? = null, due: String? = null, pri: Int? = null) {
        val old = items.firstOrNull { it.id == id } ?: return
        val t = text?.replace(Regex("\\s+"), " ")?.trim()
        if (t != null && t.isEmpty()) return          // 改成空就当没改，别把内容弄丢
        val one = old.copy(
            text = t?.take(200) ?: old.text,
            due = due ?: old.due,
            pri = pri?.coerceIn(0, 2) ?: old.pri
        )
        if (one == old) return
        items = items.map { if (it.id == id) one else it }
        persist()
    }

    fun clearDone(): Int {
        val n = items.count { it.done }
        if (n > 0) {
            items = items.filterNot { it.done }
            persist()
        }
        return n
    }

    /* ------------------------------ 备份 ------------------------------ */

    fun exportJson(): String {
        /* 导出的形状跟网页版导入的识别规则对齐（text / due / priority 都能认） */
        val out = items.map {
            TodoOut(
                id = it.id, text = it.text, done = it.done, due = it.due,
                priority = listOf("普通", "重要", "紧急")[it.pri.coerceIn(0, 2)],
                createdAt = iso(it.at),
                doneAt = if (it.doneAt > 0) iso(it.doneAt) else ""
            )
        }
        return PRETTY.encodeToString(OUT_LIST, out)
    }

    /** 导回来：按 id 去重，返回真正加进来的条数 */
    fun importJson(text: String): Int {
        val arr = parseAny(text)
        if (arr.isEmpty()) return 0
        val have = items.map { it.id }.toMutableSet()
        val add = arr.map { if (it.id.isBlank()) it.copy(id = uid()) else it }
            .filter { it.text.isNotBlank() && have.add(it.id) }
        if (add.isEmpty()) return 0
        items = items + add
        persist()
        return add.size
    }

    /**
     * 三种形状都认：
     *   ① App 自己的 `[{ id, text, done, due, pri, at, doneAt }]`
     *   ② 网页版导出的 `{ v, items: [...] }`（字段是 text / priority / createdAt）
     *   ③ 上面两种的外层再包一层数组的情况
     *
     * 认不出来就返回空 —— 宁可什么都不做，也别把用户的清单冲掉。
     */
    private fun parseAny(text: String): List<Todo> {
        runCatching { PRETTY.decodeFromString(LIST, text) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        val root = runCatching { PRETTY.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val arrEl = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is kotlinx.serialization.json.JsonObject -> root["items"] as? kotlinx.serialization.json.JsonArray
            else -> null
        } ?: return emptyList()

        runCatching { PRETTY.decodeFromJsonElement(LIST, arrEl) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        val outs = runCatching { PRETTY.decodeFromJsonElement(OUT_LIST, arrEl) }.getOrNull()
            ?: return emptyList()
        return outs.map {
            Todo(
                id = it.id, text = it.text, done = it.done, due = it.due.take(10),
                pri = when (it.priority) { "紧急" -> 2; "重要" -> 1; else -> 0 },
                at = System.currentTimeMillis(), doneAt = if (it.done) System.currentTimeMillis() else 0L
            )
        }
    }

    /* ------------------------------ 内部 ------------------------------ */

    private fun read(): List<Todo> {
        val raw = sp.getString("items", null) ?: return emptyList()
        return runCatching { PRETTY.decodeFromString(LIST, raw) }
            .getOrElse { emptyList() }
    }

    private fun persist() {
        sp.edit().putString("items", PRETTY.encodeToString(LIST, items)).apply()
    }

    companion object {
        private val LIST = ListSerializer(Todo.serializer())
        private val OUT_LIST = ListSerializer(TodoOut.serializer())

        private val PRETTY = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        @Volatile
        private var inst: Todos? = null

        fun of(ctx: Context): Todos = inst ?: synchronized(this) {
            inst ?: Todos(ctx.applicationContext).also { inst = it }
        }

        fun uid(): String =
            System.currentTimeMillis().toString(36) + (100000..999999).random().toString(36)

        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

        fun shift(days: Int): String {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, days)
            }
            return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(c.time)
        }

        private fun iso(ts: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.CHINA).format(Date(ts))

        /** 到期日的说法：今天 / 明天 / 逾期 2 天 / 3 天后 / 11-28 */
        fun dueLabel(due: String): String {
            if (due.isBlank()) return ""
            val n = dayDiff(due) ?: return due.take(10)
            return when {
                n == 0 -> "今天"
                n == 1 -> "明天"
                n < 0 -> "逾期 ${-n} 天"
                n <= 6 -> "$n 天后"
                else -> due.substring(5)
            }
        }

        /** 完成时间：今天 / 昨天 / 11 月 28 日 */
        fun doneLabel(ts: Long): String {
            if (ts <= 0L) return ""
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ts))
            return when (dayDiff(d)) {
                0 -> "今天完成"
                -1 -> "昨天完成"
                else -> SimpleDateFormat("M 月 d 日", Locale.CHINA).format(Date(ts)) + "完成"
            }
        }

        /** '-' 分隔的日期跟今天差几天（今天 0，明天 1） */
        fun dayDiff(s: String): Int? {
            val p = s.split("-")
            if (p.size != 3) return null
            val y = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            val d = p[2].toIntOrNull() ?: return null
            val a = Calendar.getInstance().apply {
                clear(); set(y, m - 1, d, 12, 0, 0)
            }.timeInMillis
            val b = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return Math.round((a - b) / 86400000.0).toInt()
        }
    }
}

/**
 * 导出用的形状：网页版的导入既认 `text`，也认中文的 `priority`。
 *
 * 不直接把 [Todo] 吐出去，是因为内部字段（`t`/`pri`/`at`）是给本机省体积的短名，
 * 导出的 JSON 得让人看得懂、也让网页版认得。
 */
@Serializable
private data class TodoOut(
    val id: String,
    val text: String,
    val done: Boolean,
    val due: String,
    val priority: String,
    val createdAt: String,
    val doneAt: String
)
