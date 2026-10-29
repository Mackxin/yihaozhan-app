package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 一个够用的 Markdown 渲染器 —— 只为站点文章这几种写法服务：
 * 标题、段落、列表、引用、代码块、表格、行内粗体/斜体/行内码/链接。
 *
 * 为什么不引第三方（richtext / markwon）：它们各带一坨依赖和 Compose 版本约束，
 * 而站点正文的语法面就这么窄，自己按行解析反而更稳、不带包体积。
 * 站点那边 `##` 渲染成 h3（见 XYMarkdown），这里保持一致：h1/h2/h3 都相应缩小一档。
 */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Para(val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Bullets(val items: List<String>) : MdBlock()
    data class Numbers(val items: List<String>) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Table(val rows: List<List<String>>) : MdBlock()
}

private val RE_HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val RE_BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
private val RE_NUMBER = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
private val RE_QUOTE = Regex("^>\\s?(.*)$")
private val RE_FENCE = Regex("^```(\\w*)\\s*$")
private val RE_TABLE_SEP = Regex("^\\|?[\\s:-]*-[\\s|:-]*$")

fun parseMarkdown(md: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = md.replace("\r\n", "\n").split('\n')
    var i = 0
    val para = StringBuilder()

    fun flushPara() {
        if (para.isNotBlank()) out += MdBlock.Para(para.toString().trim())
        para.setLength(0)
    }

    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()

        /* 代码块：整段吃掉，直到下一个 ``` */
        val fence = RE_FENCE.find(t)
        if (fence != null) {
            flushPara()
            val lang = fence.groupValues[1]
            val buf = StringBuilder()
            i++
            while (i < lines.size && RE_FENCE.find(lines[i].trim()) == null) {
                buf.appendLine(lines[i]); i++
            }
            i++ // 跳过收尾的 ```
            out += MdBlock.Code(lang, buf.toString().trimEnd())
            continue
        }

        if (t.isEmpty()) { flushPara(); i++; continue }

        val h = RE_HEADING.find(t)
        if (h != null) {
            flushPara()
            out += MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim())
            i++; continue
        }

        /* 表格：第一行是表头，第二行是 |---|，之后每行一格 */
        if (t.startsWith("|") && i + 1 < lines.size && RE_TABLE_SEP.matches(lines[i + 1].trim())) {
            flushPara()
            val rows = mutableListOf<List<String>>()
            rows += splitRow(t)
            i += 2
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows += splitRow(lines[i].trim()); i++
            }
            out += MdBlock.Table(rows)
            continue
        }

        val b = RE_BULLET.find(line)
        if (b != null) {
            flushPara()
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val m = RE_BULLET.find(lines[i]) ?: break
                items += m.groupValues[1].trim(); i++
            }
            out += MdBlock.Bullets(items); continue
        }

        val n = RE_NUMBER.find(line)
        if (n != null) {
            flushPara()
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val m = RE_NUMBER.find(lines[i]) ?: break
                items += m.groupValues[2].trim(); i++
            }
            out += MdBlock.Numbers(items); continue
        }

        val q = RE_QUOTE.find(line)
        if (q != null) {
            flushPara()
            val buf = StringBuilder()
            while (i < lines.size) {
                val m = RE_QUOTE.find(lines[i]) ?: break
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(m.groupValues[1].trim()); i++
            }
            out += MdBlock.Quote(buf.toString()); continue
        }

        if (para.isNotEmpty()) para.append(' ')
        para.append(t)
        i++
    }
    flushPara()
    return out
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

/** 行内语法：`code`、**粗**、*斜*、[文字](链接)、~~删除~~ */
fun inlineMd(src: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        val c = src[i]
        if (c == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808AFF)))
                append(src.substring(i + 1, end)); pop(); i = end + 1; continue
            }
        }
        if (c == '*' && i + 1 < src.length && src[i + 1] == '*') {
            val end = src.indexOf("**", i + 2)
            if (end > i) {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                append(src.substring(i + 2, end)); pop(); i = end + 2; continue
            }
        }
        if (c == '*') {
            val end = src.indexOf('*', i + 1)
            if (end > i) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(src.substring(i + 1, end)); pop(); i = end + 1; continue
            }
        }
        if (c == '~' && i + 1 < src.length && src[i + 1] == '~') {
            val end = src.indexOf("~~", i + 2)
            if (end > i) {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(src.substring(i + 2, end)); pop(); i = end + 2; continue
            }
        }
        if (c == '[') {
            val close = src.indexOf(']', i)
            if (close > i && close + 1 < src.length && src[close + 1] == '(') {
                val end = src.indexOf(')', close)
                if (end > close) {
                    pushStyle(SpanStyle(color = Color(0xFF4F5BD5), textDecoration = TextDecoration.Underline))
                    append(src.substring(i + 1, close)); pop()
                    i = end + 1; continue
                }
            }
        }
        append(c); i++
    }
}

@Composable
fun MarkdownView(md: String, modifier: Modifier = Modifier, fontScale: Float = 1f) {
    val blocks = parseMarkdown(md)
    val mono = FontFamily.Monospace
    /* 字号倍数来自设置页；行高一起放大，不然大字会挤在一起 */
    fun sz(v: Float) = (v * fontScale).sp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    inlineMd(b.text),
                    fontSize = sz(
                        when (b.level) {
                            1 -> 24f
                            2 -> 21f
                            3 -> 18f
                            else -> 16f
                        }
                    ),
                    fontWeight = FontWeight.Bold,
                    lineHeight = sz(30f),
                    modifier = Modifier.padding(top = if (b.level <= 2) 8.dp else 4.dp)
                )

                is MdBlock.Para -> Text(
                    inlineMd(b.text), fontSize = sz(15.5f), lineHeight = sz(30f),
                    color = MaterialTheme.colorScheme.onBackground
                )

                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Text(
                        inlineMd(b.text), fontSize = sz(15f), lineHeight = sz(29f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEach {
                        Row {
                            Text("•", fontSize = sz(16f), modifier = Modifier.padding(end = 8.dp))
                            Text(inlineMd(it), fontSize = sz(15.5f), lineHeight = sz(29f))
                        }
                    }
                }

                is MdBlock.Numbers -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEachIndexed { idx, it ->
                        Row {
                            Text("${idx + 1}.", fontSize = sz(16f), modifier = Modifier.padding(end = 8.dp))
                            Text(inlineMd(it), fontSize = sz(15.5f), lineHeight = sz(29f))
                        }
                    }
                }

                is MdBlock.Code -> Text(
                    b.code,
                    fontFamily = mono, fontSize = sz(13f), lineHeight = sz(21f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                )

                /* 表格在手机上横向铺不开，改成「一行一条、格子用 · 隔开」。
                   文章里那张表只有两三列，这样读反而比横向滚动舒服。 */
                is MdBlock.Table -> Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp)
                ) {
                    b.rows.forEachIndexed { idx, row ->
                        Text(
                            inlineMd(row.joinToString("  ·  ")),
                            fontSize = sz(14f), lineHeight = sz(23f),
                            fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
