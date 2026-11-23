package xyz.yihaozhan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
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

/**
 * 链接地址存在 AnnotatedString 的哪个标注名下。
 * 只有本文件写、本文件读 —— 外面只拿到「点到的地址字符串」，不需要知道这套机制。
 */
private const val MD_URL = "xy.md.url"

/** 行内语法：`code`、**粗**、*斜*、[文字](链接)、~~删除~~ */
fun inlineMd(src: String): AnnotatedString = buildAnnotatedString { inlineInto(this, src) }

/**
 * 把一段行内 Markdown 拆成带样式的文字，追加到 [sb]。
 *
 * **粗体 / 斜体里面要递归**：文章里到处是 `- **[📦 MacWK](https://macwk.com)**` 这种
 * 「粗体包着链接」，以前把粗体中间那段原样 append 进去，方括号和圆括号就留在了正文里 ——
 * 屏幕上读作「**[📦 MacWK](https://macwk.com)**」一整串，链接也点不动。
 * 现在粗体里再走一遍这一套，链接照样识别、照样能点。
 */
private fun inlineInto(sb: AnnotatedString.Builder, src: String) {
    var i = 0
    while (i < src.length) {
        val c = src[i]
        if (c == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i) {
                sb.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808AFF)))
                sb.append(src.substring(i + 1, end)); sb.pop(); i = end + 1; continue
            }
        }
        if (c == '*' && i + 1 < src.length && src[i + 1] == '*') {
            val end = src.indexOf("**", i + 2)
            if (end > i) {
                sb.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                inlineInto(sb, src.substring(i + 2, end))
                sb.pop(); i = end + 2; continue
            }
        }
        if (c == '*') {
            val end = src.indexOf('*', i + 1)
            if (end > i) {
                sb.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                inlineInto(sb, src.substring(i + 1, end))
                sb.pop(); i = end + 1; continue
            }
        }
        if (c == '~' && i + 1 < src.length && src[i + 1] == '~') {
            val end = src.indexOf("~~", i + 2)
            if (end > i) {
                sb.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                inlineInto(sb, src.substring(i + 2, end))
                sb.pop(); i = end + 2; continue
            }
        }
        if (c == '[') {
            val close = src.indexOf(']', i)
            if (close > i && close + 1 < src.length && src[close + 1] == '(') {
                val end = src.indexOf(')', close)
                if (end > close) {
                    /* 先把链接文字渲染好，再按**绝对下标**给这段挂上地址。
                       不写成 pushStringAnnotation + pop：样式栈和标注栈共用 pop()，
                       外面还套着粗体时退的那一层未必是刚压进去的标注，很容易退错。 */
                    val from = sb.length
                    sb.pushStyle(SpanStyle(color = Color(0xFF4F5BD5), textDecoration = TextDecoration.Underline))
                    /* 链接文字本身也可能带样式（`**[粗体链接](url)**`），这里同样递归一遍 */
                    inlineInto(sb, src.substring(i + 1, close))
                    sb.pop()
                    val url = src.substring(close + 2, end).trim().removeSurrounding("<", ">")
                    /* 空下标段（`[](url)`）不挂标注：AnnotatedString 要求 start < end，
                       而且这种写法本来也没东西可点 */
                    if (url.isNotEmpty() && sb.length > from) {
                        sb.addStringAnnotation(MD_URL, url, from, sb.length)
                    }
                    i = end + 1; continue
                }
            }
        }
        sb.append(c); i++
    }
}

/**
 * 整篇渲染：把解析出来的块一次性铺成一个 Column。
 * 用在不需要「跳到某一节」的地方；文章详情页走 `MdBlockView` 一块一个 item，
 * 这样点目录才能精确滚到那一段。
 */
@Composable
fun MarkdownView(md: String, modifier: Modifier = Modifier, fontScale: Float = 1f) {
    val blocks = parseMarkdown(md)
    Column(
        modifier.fillMaxWidth(),
        /* 段间距也跟着字号缩放：光放大字、间距不动，大字会挤成一坨 */
        verticalArrangement = Arrangement.spacedBy((10 * fontScale).dp)
    ) {
        blocks.forEach { MdBlockView(it, fontScale) }
    }
}

/**
 * 点的这个位置算不算点在某条链接上。
 *
 * 不直接用 `getStringAnnotations(tag, at, at)` 的原因：它内部是**区间包含**判定，
 * 正好点在链接首字、末字外侧那一两像素时，取出来的 offset 会落在链接外面，判定就落空，
 * 手感上就是「明明戳着链接却没反应」。这里改成按**字符距离**算：
 * 落在链接里算命中，差一两个字符也算（手指比字宽），差得更远就不认。
 */
private fun urlNear(text: AnnotatedString, at: Int): String? {
    var best: String? = null
    var bestGap = 3
    for (r in text.getStringAnnotations(MD_URL, 0, text.length)) {
        val gap = when {
            at < r.start -> r.start - at
            at > r.end -> at - r.end
            else -> 0
        }
        if (gap < bestGap) {
            bestGap = gap
            best = r.item
        }
    }
    return best
}

/**
 * 正文里的一小段字：**点到链接就打开，长按整块交给调用方**（阅读页拿它弹「存摘录」）。
 *
 * 为什么两件事非得挤进同一个检测器：`detectTapGestures` 一进来就把 down 事件消费掉，
 * 子级挂上手势之后，父级那层检测器再也收不到 down。以前阅读页是在每块外面套一层 Box
 * 接长按，如果这里再单独给 Text 挂点击，长按就会整块失灵 —— 而存摘录是这页的核心动作，
 * 不能为了点链接丢掉它。所以单击和长按都在这一层判：点到的位置落在链接那段字上就打开，
 * 长按一律往上抛。
 *
 * 两个回调都不传时不挂任何手势：帮助页、关于页外面包着 SelectionContainer，
 * 挂上手势会把「长按选字」抢走，那两页必须保持原样。
 */
@Composable
private fun MdText(
    text: AnnotatedString,
    onLink: ((String) -> Unit)?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    /* 回调每重组一次就是新 lambda，直接拿它当 pointerInput 的 key 会把检测器反复重建，
       滑动时正好把进行中的长按打断。键只用 text，回调走 rememberUpdatedState 取最新。 */
    val linkRef = rememberUpdatedState(onLink)
    val pressRef = rememberUpdatedState(onLongPress)

    val gesture = if (onLink == null && onLongPress == null) Modifier else Modifier.pointerInput(text) {
        detectTapGestures(
            onTap = { pos ->
                val tap = linkRef.value
                val lay = layout
                if (tap != null && lay != null && text.isNotEmpty()) {
                    /* 点在最后一行右侧空白时 getOffsetForPosition 会返回 length（越界一格），
                       往回夹一格，否则这一下明明贴着链接尾巴却取不到标注 */
                    val off = lay.getOffsetForPosition(pos).coerceIn(0, text.length - 1)
                    val url = urlNear(text, off)
                    if (url != null) tap(url)
                }
            },
            onLongPress = { pressRef.value?.invoke() }
        )
    }

    Text(
        text,
        modifier = modifier.then(gesture),
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        onTextLayout = { layout = it }
    )
}

/** 单独渲染一个块。文章详情页拿它当 LazyColumn 的 item，目录才跳得准 */
@Composable
fun MdBlockView(
    b: MdBlock,
    fontScale: Float = 1f,
    /* 行距倍率：1 = 原来的松紧。设置页「行距」三档换算好传进来（1.7 / 1.95 / 2.2 除以基准） */
    lineFactor: Float = 1f,
    /* 正文字体：站点和设置里都能选衬线 / 无衬线 */
    serif: Boolean = false,
    /* 点正文里的 `[文字](链接)`。不传就只显示、点了没反应（帮助页那种纯阅读的地方） */
    onLink: ((String) -> Unit)? = null,
    /* 长按这一块（阅读页 → 存为摘录 / 复制 / 朗读）。不传就不接长按 */
    onLongPress: (() -> Unit)? = null
) {
    val mono = FontFamily.Monospace
    /* 字号倍数来自设置页；行高一起放大，不然大字会挤在一起 */
    fun sz(v: Float) = (v * fontScale).sp
    fun lh(v: Float) = (v * fontScale * lineFactor).sp
    val bodyFam = if (serif) FontFamily.Serif else FontFamily.Default

    when (b) {
        is MdBlock.Heading -> MdText(
            inlineMd(b.text),
            onLink, onLongPress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (b.level <= 2) 8.dp else 4.dp),
            fontSize = sz(
                when (b.level) {
                    1 -> 24f
                    2 -> 21f
                    3 -> 18f
                    else -> 16f
                }
            ),
            fontWeight = FontWeight.Bold,
            lineHeight = sz(30f)
        )

        is MdBlock.Para -> MdText(
            inlineMd(b.text),
            onLink, onLongPress,
            modifier = Modifier.fillMaxWidth(),
            fontSize = sz(15.5f), lineHeight = lh(30f),
            fontFamily = bodyFam,
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
            MdText(
                inlineMd(b.text),
                onLink, onLongPress,
                /* weight 而不是 wrap：热区铺满右边整行，短句子也能整段长按 */
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                fontSize = sz(15f), lineHeight = lh(29f),
                fontFamily = bodyFam,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            b.items.forEach {
                Row {
                    Text("•", fontSize = sz(16f), modifier = Modifier.padding(end = 8.dp))
                    MdText(
                        inlineMd(it),
                        onLink, onLongPress,
                        modifier = Modifier.weight(1f),
                        fontSize = sz(15.5f), lineHeight = lh(29f), fontFamily = bodyFam
                    )
                }
            }
        }

        is MdBlock.Numbers -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            b.items.forEachIndexed { idx, it ->
                Row {
                    Text(
                        "${idx + 1}.",
                        fontSize = sz(16f),
                        fontFamily = bodyFam,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    MdText(
                        inlineMd(it),
                        onLink, onLongPress,
                        modifier = Modifier.weight(1f),
                        fontSize = sz(15.5f), lineHeight = lh(29f), fontFamily = bodyFam
                    )
                }
            }
        }

        is MdBlock.Code -> MdText(
            AnnotatedString(b.code),
            onLink, onLongPress,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(14.dp),
            fontFamily = mono, fontSize = sz(13f), lineHeight = sz(21f)
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
                MdText(
                    inlineMd(row.joinToString("  ·  ")),
                    onLink, onLongPress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    fontSize = sz(14f), lineHeight = sz(23f),
                    fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/* ============================ 块 → 纯文字 ============================ */

/**
 * 一块正文取它的纯文字：摘录、复制、朗读都走这一份。
 *
 * 为什么要剥行内标记：`**重点**`、`` `代码` ``、`[文字](链接)` 这些是**给屏幕看的**，
 * 存进摘录本或交给 TTS 念出来时，星号和反引号只会碍事 —— 念出来是「星号星号」。
 */
fun mdBlockText(b: MdBlock): String = when (b) {
    is MdBlock.Heading -> stripInline(b.text)
    is MdBlock.Para -> stripInline(b.text)
    is MdBlock.Quote -> b.text.lines().joinToString("\n") { stripInline(it) }
    is MdBlock.Bullets -> b.items.joinToString("\n") { "· " + stripInline(it) }
    is MdBlock.Numbers ->
        b.items.mapIndexed { i, s -> "${i + 1}. " + stripInline(s) }.joinToString("\n")
    is MdBlock.Code -> b.code.trimEnd()
    /* 表格按「一行一条、格子用空格隔开」——跟屏幕上那个渲染保持一致 */
    is MdBlock.Table -> b.rows.joinToString("\n") { row -> row.joinToString("  ") }
}

private val RE_MD_IMG = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
private val RE_MD_LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
private val RE_MD_CODE = Regex("`([^`]*)`")

/** 剥掉行内标记，只留字 */
fun stripInline(s: String): String {
    var t = s
    t = RE_MD_IMG.replace(t) { it.groupValues[1] }
    t = RE_MD_LINK.replace(t) { it.groupValues[1] }
    t = RE_MD_CODE.replace(t) { it.groupValues[1] }
    t = t.replace("**", "").replace("~~", "").replace("__", "")
    /* 单个 `*` / `_` 当强调标记去掉；正文里真有星号的地方很少，权衡下来这样更干净 */
    t = t.replace(Regex("(?<![\\w])[*_](?=\\S)"), "").replace(Regex("(?<=\\S)[*_](?![\\w])"), "")
    return t.trim()
}
