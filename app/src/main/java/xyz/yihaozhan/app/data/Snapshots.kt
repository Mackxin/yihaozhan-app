package xyz.yihaozhan.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 稍后读的「正文快照」。
 *
 * 为什么需要它：文章本体是站点 `app.json` 烘过来的，拉新内容时会整份换掉 ——
 * 站上撤掉一篇、或者你在设置里「清除缓存」，稍后读列表里那条就只剩个 id，
 * 点进去一片空白。用户的心智是「我存了它」，那就得真的存住。
 *
 * 所以点「稍后读」的那一刻，把标题 / 日期 / 标签 / 正文抄一份到
 * `filesDir/snapshots/<id>.json`；取消稍后读就删掉。列表以站点数据为准，
 * 站点上没有的才用快照兜底 —— 快照只做后备，不抢主数据的位置。
 *
 * 为什么不用 Room：一条就几 KB、数量最多几十条，一个 JSON 文件足够，
 * 也不值得为它多带一套数据库。
 */
@Serializable
data class Snapshot(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val tags: List<String> = emptyList(),
    val excerpt: String = "",
    val body: String = "",
    /** 存下来的时刻：列表排序用它（没日期可排时至少还知道先后） */
    val ts: Long = 0L
)

object Snapshots {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun dir(ctx: Context): File = File(ctx.applicationContext.filesDir, "snapshots")
        .apply { mkdirs() }

    private fun file(ctx: Context, id: String): File = File(dir(ctx), safe(id) + ".json")

    /** id 来自站点，正常是 slug；防一手路径里的斜杠 */
    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_\\-]"), "_")

    fun save(ctx: Context, p: Post) {
        if (p.id.isBlank()) return
        val s = Snapshot(
            id = p.id,
            title = p.title,
            date = p.date,
            tags = p.tags,
            excerpt = p.excerpt,
            body = p.body,
            ts = System.currentTimeMillis()
        )
        runCatching {
            file(ctx, p.id).writeText(json.encodeToString(Snapshot.serializer(), s))
        }
    }

    fun read(ctx: Context, id: String): Snapshot? = runCatching {
        val f = file(ctx, id)
        if (!f.exists()) null else json.decodeFromString(Snapshot.serializer(), f.readText())
    }.getOrNull()

    /** 所有快照，新的在前 */
    fun all(ctx: Context): List<Snapshot> = runCatching {
        dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { f ->
                runCatching {
                    json.decodeFromString(Snapshot.serializer(), f.readText())
                }.getOrNull()
            }
            ?.sortedByDescending { it.ts }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun forget(ctx: Context, id: String) {
        runCatching { file(ctx, id).delete() }
    }

    /** 快照当一篇普通文章用：标题、正文、标签都在，阅读页照常渲染 */
    fun toPost(s: Snapshot): Post = Post(
        id = s.id,
        title = s.title,
        date = s.date,
        tags = s.tags,
        excerpt = s.excerpt,
        body = s.body
    )
}
