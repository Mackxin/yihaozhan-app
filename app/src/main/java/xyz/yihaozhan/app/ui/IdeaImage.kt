package xyz.yihaozhan.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import xyz.yihaozhan.app.data.Idea
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** 存图用的配色（从当前主题的 XyPal 取来的 ARGB 整数） */
data class IdeaImgColors(
    val bg: Int, val text: Int, val dim: Int, val mute: Int,
    val ink: Int, val a1: Int, val a2: Int, val a3: Int
)

/** 跟站点 ideaBody 一个意思：去掉 #标签、收掉多余空白 */
private fun ideaPlain(text: String): String =
    text.replace(Regex("#[^\\s#]{1,20}"), "")
        .replace(Regex("[ \\t]+$"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

private fun ideaTagsImg(text: String): List<String> =
    Regex("#[^\\s#]{1,20}").findAll(text).map { it.value.drop(1) }.distinct().toList()

/**
 * 把一条灵感画成一张 PNG 卡片，经系统分享面板发出去（微信/备忘录都行）。
 * 不必把整页截下来 —— 自己用 Canvas 画，文字清晰、底色干净，
 * 也跟站点那套「存图」产出一个味儿。
 */
fun shareIdeaImage(ctx: Context, idea: Idea, c: IdeaImgColors) {
    val W = 1080
    val pad = 64f
    val lineH = 52f
    val headerH = 120f
    val bottom = 64f

    val body = ideaPlain(idea.text)
    val tags = ideaTagsImg(idea.text)
    val tagH = if (tags.isNotEmpty()) 96f else 0f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        color = c.text
        typeface = Typeface.DEFAULT
    }
    val lines = if (body.isEmpty()) listOf("（这条随记是空的）") else wrapText(paint, body, (W - pad * 2).toInt())
    val H = max((pad + headerH + lines.size * lineH + (if (lines.isNotEmpty()) 16f else 0f) + tagH + bottom).toInt(), 240)

    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(c.bg)

    /* 极光渐变：对角铺一层很淡的蓝→紫→青，呼应站点那张卡 */
    val grad = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(c.a1, c.a2, c.a3), null, Shader.TileMode.CLAMP)
    val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad; alpha = 38 }
    cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), gp)

    /* 顶栏：左边站名，右边时间（置顶带 📌） */
    val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; color = c.dim; typeface = Typeface.DEFAULT }
    cv.drawText("壹号栈 · 灵感随记", pad, pad + 50f, hp)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f; color = c.mute; typeface = Typeface.DEFAULT; textAlign = Paint.Align.RIGHT
    }
    cv.drawText(fmtImgStamp(idea.ts, idea.pinned), W - pad, pad + 50f, tp)

    /* 正文 */
    var y = pad + headerH
    for (ln in lines) {
        cv.drawText(ln, pad, y + 34f, paint)
        y += lineH
    }
    y += 16f

    /* 标签：小圆角胶囊 */
    if (tags.isNotEmpty()) {
        var x = pad
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = c.ink; typeface = Typeface.DEFAULT }
        for (t in tags) {
            val label = "#$t"
            val tw = tagPaint.measureText(label) + 36f
            cv.drawRoundRect(RectF(x, y, x + tw, y + 56f), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.a1; alpha = 40 })
            cv.drawText(label, x + 18f, y + 38f, tagPaint)
            x += tw + 16f
        }
    }

    val file = File(ctx.cacheDir, "idea_${idea.id}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    shareFile(ctx, file, "image/png", "分享灵感图片")
}

/** 逐字符量宽度的换行：中文按字断、英文按词断，遇到 \n 强制断行 */
private fun wrapText(paint: Paint, text: String, maxW: Int): List<String> {
    val tokens = mutableListOf<String>()
    val buf = StringBuilder()
    for (ch in text) {
        when {
            ch == '\n' -> { if (buf.isNotEmpty()) { tokens.add(buf.toString()); buf.setLength(0) }; tokens.add("\n") }
            ch.isWhitespace() -> { if (buf.isNotEmpty()) { tokens.add(buf.toString()); buf.setLength(0) }; tokens.add(" ") }
            ch.code in 0x4e00..0x9fff -> { if (buf.isNotEmpty()) { tokens.add(buf.toString()); buf.setLength(0) }; tokens.add(ch.toString()) }
            else -> buf.append(ch)
        }
    }
    if (buf.isNotEmpty()) tokens.add(buf.toString())

    val out = mutableListOf<String>()
    val line = StringBuilder()
    var lineW = 0f
    for (tk in tokens) {
        if (tk == "\n") { out.add(line.toString().trimEnd()); line.setLength(0); lineW = 0f; continue }
        val w = paint.measureText(tk)
        if (lineW + w > maxW && line.isNotEmpty()) {
            out.add(line.toString().trimEnd())
            line.setLength(0); line.append(if (tk == " ") "" else tk)
            lineW = if (tk == " ") 0f else w
        } else {
            line.append(tk); lineW += w
        }
    }
    if (line.isNotEmpty()) out.add(line.toString().trimEnd())
    return out
}

private fun fmtImgStamp(ts: Long, pinned: Boolean): String {
    if (ts <= 0L) return ""
    val d = Date(ts)
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = d }
    val p2 = { n: Int -> if (n < 10) "0$n" else "$n" }
    val hm = p2(cal.get(Calendar.HOUR_OF_DAY)) + ":" + p2(cal.get(Calendar.MINUTE))
    val when0 = if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        (cal.get(Calendar.MONTH) + 1).toString() + "月" + cal.get(Calendar.DATE) + "日 " + hm
    } else cal.get(Calendar.YEAR).toString() + "年" + (cal.get(Calendar.MONTH) + 1) + "月" + cal.get(Calendar.DATE) + "日"
    return (if (pinned) "📌 " else "") + when0
}
