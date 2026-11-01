package xyz.yihaozhan.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import xyz.yihaozhan.app.data.ThemeCfg

/**
 * 视觉全套照抄站点手机版（assets/css/style.css 的 :root / [data-theme="light"]）。
 *
 * 站点那套的三条底线，这里一条不改：
 *   1. **全站不用白**。卡片底色是「极淡的品牌蓝玻璃」叠在极光背景上，
 *      不是白纸糊在灰底上 —— 所以深色浅色都不给纯白。
 *   2. **强调色分两套**：--aN 管光（渐变、图形），--ink-aN 管字。
 *      浅色主题下 #7c9cff 当文字只有 2.2:1，等于隐形，必须换成压深那档。
 *   3. **不用阴影、不用描边**，靠间距和留白分组。圆角 r-sm 12 / r 18 / r-lg 26 / r-xl 34。
 */

/* ---------- 「光」：渐变与图形用（深浅主题同值） ---------- */
val XY_A1 = Color(0xFF7C9CFF)
val XY_A2 = Color(0xFFB47CFF)
val XY_A3 = Color(0xFF4FE3D0)

/* 渐变底上写字的墨色：站点后台选中项和 .head-toggle 都用 #080a12，
   白字压在最浅的 #4fe3d0 上只有 1.59:1。 */
val XY_INK = Color(0xFF080A12)

/** 一套配色。字段对照站点 CSS 变量，注释里标了原名。 */
class XyPal(
    val bg: Color,          // --bg
    val bgSoft: Color,      // --bg-soft
    val glass: Color,       // --glass   卡片底（站点是半透明，这里直接用，同样是叠在极光上）
    val glass2: Color,      // --glass-2 再深一档，用于悬停/次级块
    val glassHi: Color,     // --glass-hi 选中态
    val text: Color,        // --text
    val dim: Color,         // --text-dim
    val mute: Color,        // --text-mute
    val inkA1: Color,       // --ink-a1 文字用强调色
    val inkA2: Color,
    val inkA3: Color,
    val a1: Color,          // --a1 「光」那组，渐变和极光用
    val a2: Color,
    val a3: Color,
    val star: Color,        // --star 收藏星
    val outline: Color,     // 细分隔线（站点 border 是 transparent，只在这里自造）
    /* 圆角四档，跟站点 --r-sm / --r / --r-lg / --r-xl 对上（后台「主题 → 圆角」能改） */
    val rSm: Int = XyR.sm,
    val rMd: Int = XyR.md,
    val rLg: Int = XyR.lg,
    val rXl: Int = XyR.xl,
    /* 极光强度（后台「主题 → 极光」）：off 时整层不画 */
    val glow: Float = 1f,
    val dark: Boolean
) {
    /** 站点 --grad：120deg 蓝 → 紫 → 青 */
    fun grad() = Brush.linearGradient(listOf(a1, a2, a3))

    /** 数字那种「很短一段」的渐变文字，横向铺一个数字宽度就够 */
    fun gradText(width: Float = 260f) = Brush.linearGradient(
        colors = listOf(a1, a2, a3),
        start = Offset.Zero,
        end = Offset(width, 0f)
    )
}

private val Light = XyPal(
    bg = Color(0xFFE8ECF7),
    bgSoft = Color(0xFFE0E5F2),
    /* rgba(211,221,243,.62) 叠在 #e8ecf7 上的合成值 */
    glass = Color(0xFFDBE3F5),
    glass2 = Color(0xFFD1DBF1),
    glassHi = Color(0xFFC7D3EF),
    text = Color(0xFF0D1424),
    dim = Color(0xFF333D57),
    mute = Color(0xFF4E5878),
    inkA1 = Color(0xFF3B51B8),
    inkA2 = Color(0xFF6F3FBD),
    inkA3 = Color(0xFF086155),
    a1 = XY_A1,
    a2 = XY_A2,
    a3 = XY_A3,
    star = Color(0xFF6B4600),
    outline = Color(0x1A0D1424),
    dark = false
)

private val Dark = XyPal(
    bg = Color(0xFF05060B),
    bgSoft = Color(0xFF0A0C15),
    /* rgba(124,156,255,.055) 叠在 #05060b 上的合成值 */
    glass = Color(0xFF0C0E18),
    glass2 = Color(0xFF0F1320),
    glassHi = Color(0xFF141A2A),
    text = Color(0xFFEEF1F8),
    dim = Color(0xFFA7B0C4),
    mute = Color(0xFF7B8496),
    inkA1 = Color(0xFF7C9CFF),
    inkA2 = Color(0xFFB47CFF),
    inkA3 = Color(0xFF4FE3D0),
    a1 = XY_A1,
    a2 = XY_A2,
    a3 = XY_A3,
    star = Color(0xFFFFCF6B),
    outline = Color(0x1AEEF1F8),
    dark = true
)

/* ---------------- 站点主题 → Compose 颜色 ---------------- */

private val HEX_RE = Regex("^#([0-9a-fA-F]+)$")
private val FUNC_RE = Regex("^rgba?\\(([^)]*)\\)$", RegexOption.IGNORE_CASE)

/**
 * 解析站点 CSS 里那几种颜色写法：`#rgb` `#rrggbb` `#rrggbbaa` `rgb(...)` `rgba(...)`。
 *
 * 认不出来返回 null —— 宁可退回内置配色，也不让一条奇怪的字符串把整个主题搞黑。
 * 站点那边 `--glass` 是 `rgba(124,156,255,.055)` 这种半透明值，这里保留 alpha，
 * Compose 会把它们叠在极光上，效果跟网页端一致。
 */
fun cssColor(raw: String?): Color? {
    val s = (raw ?: "").trim()
    if (s.isEmpty() || s == "transparent" || s == "none") return null
    HEX_RE.find(s)?.let { m ->
        var h = m.groupValues[1]
        if (h.length == 3 || h.length == 4) {
            /* #abc / #abcd → 每位重复一次 */
            h = h.map { "" + it + it }.joinToString("")
        }
        return when (h.length) {
            6 -> Color(("ff" + h).toLong(16))
            8 -> Color((h.substring(6, 8) + h.substring(0, 6)).toLong(16))
            else -> null
        }
    }
    FUNC_RE.find(s)?.let { m ->
        val parts = m.groupValues[1].split(',').map { it.trim() }
        if (parts.size < 3) return null
        val r = parts[0].toFloatOrNull()?.toInt() ?: return null
        val g = parts[1].toFloatOrNull()?.toInt() ?: return null
        val b = parts[2].toFloatOrNull()?.toInt() ?: return null
        val a = if (parts.size >= 4) parts[3].toFloatOrNull() ?: 1f else 1f
        return Color(
            r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255),
            (a.coerceIn(0f, 1f) * 255f).toInt()
        )
    }
    return null
}

/** `26px` → 26；单位不对就当作没给 */
private fun cssPx(raw: String?, fallback: Int): Int {
    val s = (raw ?: "").trim()
    if (s.isEmpty()) return fallback
    return s.removeSuffix("px").trim().toFloatOrNull()?.toInt()?.coerceIn(4, 48) ?: fallback
}

/**
 * 把站点那套外观落成一套 XyPal。
 *
 * 站点后台能改的东西（色板 / 极光强弱 / 圆角大小）在这里全部生效，
 * 没给、给坏了、老 json 里压根没这一段 —— 一律退回内置那套，App 永远不会「一片黑」。
 */
fun sitePal(dark: Boolean, cfg: ThemeCfg?): XyPal {
    val base = if (dark) Dark else Light
    if (cfg == null) return base
    val c = if (dark) cfg.dark else cfg.light
    fun p(v: String, old: Color) = cssColor(v) ?: old
    return XyPal(
        bg = p(c.bg, base.bg),
        bgSoft = p(c.bgSoft, base.bgSoft),
        glass = p(c.glass, base.glass),
        glass2 = p(c.glass2, base.glass2),
        glassHi = p(c.glassHi, base.glassHi),
        text = p(c.text, base.text),
        dim = p(c.dim, base.dim),
        mute = p(c.mute, base.mute),
        inkA1 = p(c.inkA1, base.inkA1),
        inkA2 = p(c.inkA2, base.inkA2),
        inkA3 = p(c.inkA3, base.inkA3),
        a1 = p(c.a1, base.a1),
        a2 = p(c.a2, base.a2),
        a3 = p(c.a3, base.a3),
        star = p(c.star, base.star),
        outline = base.outline,
        rSm = cssPx(c.rSm, base.rSm),
        rMd = cssPx(c.rMd, base.rMd),
        rLg = cssPx(c.rLg, base.rLg),
        rXl = cssPx(c.rXl, base.rXl),
        glow = when (cfg.glow) {
            "off" -> 0f
            "strong" -> 1.45f
            else -> 1f
        },
        dark = dark
    )
}

val LocalXyPal = staticCompositionLocalOf { Light }

/** 圆角四档的**内置默认值**。站点说了算的时候由 `sitePal()` 覆盖（见 XyPal 的 rXx 字段）。 */
object XyR {
    val sm = 12
    val md = 18
    val lg = 26
    val xl = 34
}

@Composable
fun YiTheme(
    dark: Boolean = isSystemInDarkTheme(),
    /* 站点后台「主题」页那套外观。null = 老数据，用内置配色 */
    cfg: ThemeCfg? = null,
    content: @Composable () -> Unit
) {
    val pal = remember(dark, cfg) { sitePal(dark, cfg) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            /* 内容画到状态栏下面（沉浸式），图标颜色跟着主题走 */
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = pal.inkA1, onPrimary = XY_INK,
            primaryContainer = Color(0xFF262E52), onPrimaryContainer = Color(0xFFDDE2FF),
            secondary = pal.inkA3, onSecondary = XY_INK,
            background = pal.bg, onBackground = pal.text,
            surface = pal.glass, onSurface = pal.text,
            surfaceVariant = pal.glass2, onSurfaceVariant = pal.dim,
            outline = pal.outline, outlineVariant = pal.outline,
            surfaceContainerLowest = pal.bg, surfaceContainerLow = pal.glass,
            surfaceContainer = pal.glass, surfaceContainerHigh = pal.glass2,
            surfaceContainerHighest = pal.glass2,
            surfaceDim = pal.bg, surfaceBright = pal.glass, surfaceTint = pal.inkA1,
            inverseSurface = pal.text, inverseOnSurface = pal.bg
        )
    } else {
        lightColorScheme(
            primary = pal.inkA1, onPrimary = Color.White,
            primaryContainer = Color(0xFFCCD5F5), onPrimaryContainer = Color(0xFF131B3A),
            secondary = pal.inkA3, onSecondary = Color.White,
            background = pal.bg, onBackground = pal.text,
            surface = pal.glass, onSurface = pal.text,
            surfaceVariant = pal.glass2, onSurfaceVariant = pal.dim,
            outline = pal.outline, outlineVariant = pal.outline,
            surfaceContainerLowest = pal.bg, surfaceContainerLow = pal.glass,
            surfaceContainer = pal.glass, surfaceContainerHigh = pal.glass2,
            surfaceContainerHighest = pal.glass2,
            surfaceDim = pal.bg, surfaceBright = pal.glass, surfaceTint = pal.inkA1,
            inverseSurface = pal.text, inverseOnSurface = pal.bg
        )
    }

    CompositionLocalProvider(
        LocalXyPal provides pal,
        /* 没写 color 的 Text 会取 LocalContentColor。Material3 在 Surface 以外
           默认给的是纯黑 —— 深色主题下就变成黑字压黑底。这里统一改成正文色。 */
        LocalContentColor provides pal.text
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * 极光背景：站点那层 .bg__orb 的三团光晕。
 *
 * 页面底色不是一块死板的纯色，左上偏蓝、右上偏紫、底部偏青，
 * 三团柔和的光叠在一起 —— 站点「悬浮在夜色里」的感觉全靠这层。
 * 用径向渐变画，不需要真的模糊，渐变边缘本身就是软的。
 *
 * 性能：**烘成一张 1/4 分辨率的小图再拉伸**，不是每帧现算三个全屏径向渐变。
 * 滑页时这层会被整块重绘，现算的话三张大渐变光栅化就是掉帧主因；
 * 而径向渐变是低频信息，缩到 1/4 再放回去肉眼看不出差别。
 */
@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    val pal = LocalXyPal.current
    var box by remember { mutableStateOf(IntSize.Zero) }
    val aurora = remember(box, pal) {
        if (box.width <= 0 || box.height <= 0) null else bakeAurora(box, pal)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(pal.bg)
            .onSizeChanged { box = it }
    ) {
        if (aurora != null) {
            Image(
                bitmap = aurora,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        content()
    }
}

/** 画一张极光小图。参数和原来那三个 glow() 保持一致，只是画在离屏画布上。 */
private fun bakeAurora(size: IntSize, pal: XyPal): ImageBitmap {
    val scale = 0.25f
    val w = (size.width * scale).toInt().coerceAtLeast(1)
    val h = (size.height * scale).toInt().coerceAtLeast(1)
    /* 浅色主题光晕更淡（站点 html[data-theme="light"] .bg__orb{opacity:.28}）；
       再乘上后台「极光」那档（off=0 / normal=1 / strong=1.45） */
    val k = (if (pal.dark) 1f else 0.62f) * pal.glow
    if (k <= 0f) return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).asImageBitmap()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun glow(cx: Float, cy: Float, r: Float, c: Color, a: Float) {
        val argb = c.toArgb()
        val solid = (255 * a * k).toInt().coerceIn(0, 255) shl 24 or (argb and 0x00FFFFFF)
        paint.shader = RadialGradient(
            cx, cy, r, intArrayOf(solid, 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        /* 铺满整张，半径外那圈由渐变自己收敛成透明 */
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    val wf = w.toFloat()
    val hf = h.toFloat()
    glow(wf * 0.16f, hf * 0.02f, wf * 0.72f, pal.a1, 0.34f)   // 左上：蓝
    glow(wf * 0.96f, hf * 0.15f, wf * 0.62f, pal.a2, 0.30f)   // 右上：紫
    glow(wf * 0.50f, hf * 0.98f, wf * 0.70f, pal.a3, 0.22f)   // 底部：青
    paint.shader = null
    return bmp.asImageBitmap()
}
