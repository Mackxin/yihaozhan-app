package xyz.yihaozhan.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/* 颜色跟站点同一套：浅色主题主色 #3b51b8（站点 --ink-a1），
   深色主题用提亮版 #8fa0ff，底色是站点那种「深墨 + 一点点紫」的夜。 */
private val Brand = Color(0xFF4F5BD5)
private val BrandLight = Color(0xFF6C79F0)
private val Accent = Color(0xFF12A594)

private val LightScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E5FF),
    onPrimaryContainer = Color(0xFF17204A),
    secondary = Accent,
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14161C),
    surface = Color.White,
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFEFF1F8),
    onSurfaceVariant = Color(0xFF5A6172),
    outline = Color(0xFFD6DAE6)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FA0FF),
    onPrimary = Color(0xFF10142A),
    primaryContainer = Color(0xFF262E52),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFF4FE3D0),
    onSecondary = Color(0xFF042621),
    background = Color(0xFF0D0F16),
    onBackground = Color(0xFFEDEFF6),
    surface = Color(0xFF161A24),
    onSurface = Color(0xFFEDEFF6),
    surfaceVariant = Color(0xFF1F2431),
    onSurfaceVariant = Color(0xFFA7B0C4),
    outline = Color(0xFF2C3344)
)

@Composable
fun YiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
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
    MaterialTheme(colorScheme = scheme, content = content)
}
