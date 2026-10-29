package xyz.yihaozhan.app.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    val glass: Color,       // --glass   卡片底（已按合成结果算成实色）
    val glass2: Color,      // --glass-2 再深一档，用于悬停/次级块
    val glassHi: Color,     // --glass-hi 选中态
    val text: Color,        // --text
    val dim: Color,         // --text-dim
    val mute: Color,        // --text-mute
    val inkA1: Color,       // --ink-a1 文字用强调色
    val inkA2: Color,
    val inkA3: Color,
    val outline: Color,     // 细分隔线（站点 border 是 transparent，只在这里自造）
    val dark: Boolean
) {
    /** 站点 --grad：120deg 蓝 → 紫 → 青 */
    fun grad() = Brush.linearGradient(listOf(XY_A1, XY_A2, XY_A3))

    /** 数字那种「很短一段」的渐变文字，横向铺一个数字宽度就够 */
    fun gradText(width: Float = 260f) = Brush.linearGradient(
        colors = listOf(XY_A1, XY_A2, XY_A3),
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
    outline = Color(0x1AEEF1F8),
    dark = true
)

val LocalXyPal = staticCompositionLocalOf { Light }

/** 圆角三档，跟站点 --r-sm / --r / --r-lg 对上 */
object XyR {
    val sm = 12
    val md = 18
    val lg = 26
}

@Composable
fun YiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val pal = if (dark) Dark else Light
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
 */
@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    val pal = LocalXyPal.current
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            /* 浅色主题光晕更淡（站点 html[data-theme="light"] .bg__orb{opacity:.28}） */
            val k = if (pal.dark) 1f else 0.62f

            fun glow(cx: Float, cy: Float, r: Float, c: Color, a: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = a * k), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }

            glow(w * 0.16f, h * 0.02f, w * 0.72f, XY_A1, 0.34f)   // 左上：蓝
            glow(w * 0.96f, h * 0.15f, w * 0.62f, XY_A2, 0.30f)   // 右上：紫
            glow(w * 0.50f, h * 0.98f, w * 0.70f, XY_A3, 0.22f)   // 底部：青
        }
        content()
    }
}
