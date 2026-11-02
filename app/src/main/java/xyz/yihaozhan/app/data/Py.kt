package xyz.yihaozhan.app.data

import java.text.Collator
import java.util.Locale

/**
 * 拼音首字母匹配 —— 网页端 `#/nav` 搜索那套的移植（`app.js` 的 `pyInitials / isSubseq`）。
 *
 * 为什么要有：导航里两千多个站点，正经名字是中文，但手在键盘上时想敲的是字母。
 * 有了它，`zh` 能命中「知乎」、`txsp` 能命中「腾讯视频」、`gh` 能命中「GitHub」。
 *
 * 怎么算出来的：不引字库也不联网，只用 23 个「锚点汉字」——
 * 每个声母挑一个代表字（a→阿、b→八、c→嚓…），用中文排序规则比较
 * 「这个字 >= 哪个锚点」，落在哪一档就是哪个声母。
 * 中文排序是 Unicode 里的拼音序，所以这个比法跟网页端 `localeCompare(…,'zh-CN')` 等价。
 *
 * 代价是只到声母（不知道 zh/ch/sh 和 z/c/s 的区别，也不管声调），
 * 但搜索要的正是「模糊能命中」，精确拼音不是目标。
 */
object Py {

    /* 声母锚点：必须按 key 升序，比较时从后往前找第一个「不大于它」的 */
    private val anchors = arrayOf(
        'a' to "阿", 'b' to "八", 'c' to "嚓", 'd' to "咑", 'e' to "妸", 'f' to "发",
        'g' to "旮", 'h' to "哈", 'j' to "讥", 'k' to "咔", 'l' to "垃", 'm' to "妈",
        'n' to "拿", 'o' to "噢", 'p' to "妑", 'q' to "七", 'r' to "呥", 's' to "仨",
        't' to "他", 'w' to "屲", 'x' to "夕", 'y' to "丫", 'z' to "帀"
    )

    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    /* 一个字算一次就够，五十个分类两千个站点里重复字很多 */
    private val cache = HashMap<Char, String>(512)

    /** 单个字符 → 它的首字母（英文数字原样小写，认不出的给空串） */
    fun char(ch: Char): String {
        cache[ch]?.let { return it }
        val out = when {
            ch in 'a'..'z' -> ch.toString()
            ch in 'A'..'Z' -> ch.lowercaseChar().toString()
            ch in '0'..'9' -> ch.toString()
            ch.code in 0x4E00..0x9FA5 -> {
                var hit = ""
                /* Collator 不是线程安全的；这里只在 UI 线程读，加把锁也不亏 */
                synchronized(collator) {
                    for (i in anchors.indices.reversed()) {
                        if (collator.compare(ch.toString(), anchors[i].second) >= 0) {
                            hit = anchors[i].first.toString()
                            break
                        }
                    }
                }
                hit
            }
            else -> ""
        }
        cache[ch] = out
        return out
    }

    /** 整串的首字母，比如「腾讯视频」→ `txsp` */
    fun initials(s: String): String {
        if (s.isEmpty()) return ""
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(char(c))
        return sb.toString()
    }

    /** 字母按顺序出现就算命中：`gh` → GitHub、`mdn` → MDN（只用于 2–5 位的短查询） */
    private fun isSubseq(q: String, s: String): Boolean {
        var i = 0
        var j = 0
        while (i < q.length && j < s.length) {
            if (q[i] == s[j]) i++
            j++
        }
        return i == q.length
    }

    private fun isPlainKey(q: String) = q.isNotEmpty() && q.all { it in 'a'..'z' || it in '0'..'9' }

    /**
     * 关键词是否命中一个站点。
     *
     * @param name  站点名（主要看这个）
     * @param extra 描述 / 网址 / 分类名，一起参与「普通包含」那一档
     */
    fun hit(query: String, name: String, extra: String = ""): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        /* 第一档：普通包含（中文直接打字、英文直接打名字都走这条） */
        if (name.lowercase().contains(q)) return true
        if (extra.isNotEmpty() && extra.lowercase().contains(q)) return true
        /* 第二档起只对纯字母数字的查询生效，否则中文关键词去比对首字母没意义 */
        if (!isPlainKey(q)) return false
        val ini = initials(name)
        if (ini.contains(q)) return true
        /* 第三档：骨架匹配（只收 2–5 位，太长的子序列几乎必然误命中） */
        return q.length in 2..5 && isSubseq(q, name.lowercase())
    }
}
