package xyz.yihaozhan.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import xyz.yihaozhan.app.data.SiteLink
import xyz.yihaozhan.app.data.TopCfg

/** 顶栏右侧那颗筛选胶囊的文案与动作（导航=分类，分享/文章=标签） */
data class TopFilter(val emoji: String, val label: String, val onClick: () -> Unit)

/**
 * 顶栏：站点手机版 `<header>` 那一套（390px 视口量出来的）。
 *
 *   一张圆角 26 的玻璃卡，内边距 7/8/12，上下两块间隔 10：
 *   ① 品牌行：[栈]38dp 小方牌 + 站名 16.5sp/700/字距 .06em，右侧三颗 38dp 动作键
 *   ② 搜索行：40dp 全圆角搜索条（左图标 + 占位文案），右边一颗同样高的筛选胶囊
 *
 * 三颗动作键对应站点顶栏那三颗：✨ 灵感随记、🕐 稍后读、🌙 白天/黑夜。
 * 搜索框在 App 里不就地展开结果，点了打开全屏搜索层 —— 手机上键盘一起来就占半屏，
 * 下拉浮层在键盘上面只剩一条缝，不如整屏给结果。
 */
@Composable
fun TopBar(
    siteName: String,
    dark: Boolean,
    searchText: String,
    links: List<SiteLink> = emptyList(),
    onBrandTap: () -> Unit,
    onLink: (SiteLink) -> Unit = {},
    onSaved: () -> Unit,
    onTheme: () -> Unit,
    onOpenSearch: () -> Unit,
    filter: TopFilter? = null,
    /* 下拉刷新的三件套（2.8.1）：进度弧 / 转圈 / 结果提示，都画在搜索框右端 ——
       以前是页面中间一个大转圈 + 屏幕顶上一条胶囊，小宝嫌占地方。
       ⚠️ 进度传的是 **lambda 不是 Float**（2.8.2）：下拉时这个值每帧都在变，
       传值的话整个调用方（AppRoot）都得跟着重组；传 lambda 就只让 RefreshTail 重组。 */
    pullProgress: () -> Float = { 0f },
    refreshing: Boolean = false,
    refreshTip: String = "",
    /* 后台「站点信息 → 顶栏显示」那六个开关；缺省全开 */
    top: TopCfg = TopCfg()
) {
    val pal = LocalXyPal.current
    /* 六块全关就整块不画 —— 留一个空玻璃壳当「装饰」比没有更怪。
       留 8dp 顶隙，免得页面标题直接贴住状态栏 */
    if (!top.brandRowVisible && !top.searchRowVisible) {
        Spacer(Modifier.height(8.dp))
        return
    }
    /* 下留 14 而不是 10：站点是 header 之后 .app 还有 20px 上内边距，
       这里对不上就会觉得页头贴着顶栏 */
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(pal.glass)
                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
        ) {
            if (top.brandRowVisible) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                /* 品牌：**只剩站名，不再画左边那颗「栈」方牌**（小宝 2026-11-01：
                   「app端的这个图标不要了，壹号栈点击可以跳转到页面底部再点返回顶部」）。
                   2.6.4 起它**在哪个页签上都只是「到底 / 回顶」**，不再兼任「回首页」
                   （小宝：「不再返回首页，只要点击就是滑动到页面底部和返回顶部」）。
                   具体滚哪一条列表由 MainActivity 按当前页传进来。
                   最宽 150dp，站名再长也不许把右边那排键挤没 */
                if (top.brand) {
                Row(
                    Modifier
                        .widthIn(max = 150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBrandTap() }
                        /* 只剩文字，左右各补一点内边距，点按区域才不至于只有字那么窄 */
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        siteName,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.06.em,
                        color = pal.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                }
                /* 自定义入口（站点后台「自定义链接」那几条）：占住中间剩下的宽度，
                   条目多了就地横滑，右侧对齐贴着后面两颗固定键 —— 这样只有一条时
                   也不会在中间飘着。
                   左边没品牌时改左对齐：否则左边一大片空白、几颗键全挤在右边 */
                val hugRight = top.brand
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(
                        6.dp, if (hugRight) Alignment.End else Alignment.Start
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (top.links) links.forEach { l ->
                        val ico = l.icon.ifBlank { l.name.take(1) }
                        TopAction(emoji = ico) { onLink(l) }
                    }
                }
                if (top.saved) {
                    Spacer(Modifier.width(6.dp))
                    TopAction(icon = Icons.Rounded.Schedule) { onSaved() }
                }
                if (top.theme) {
                    Spacer(Modifier.width(8.dp))
                    TopAction(icon = if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode) {
                        onTheme()
                    }
                }
            }
            }
            if (top.brandRowVisible && top.searchRowVisible) Spacer(Modifier.height(10.dp))
            if (top.searchRowVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (top.search) {
                    SearchPill(searchText, Modifier.weight(1f), onOpenSearch) {
                        RefreshTail(pullProgress, refreshing, refreshTip)                    }
                }
                if (top.filter && filter != null) FilterPill(filter)
            }
            }
        }
    }
}

/** 顶栏动作键：38dp 方键、圆角 12、玻璃底、18dp 图标 */
@Composable
private fun TopAction(
    icon: ImageVector? = null,
    emoji: String? = null,
    onClick: () -> Unit
) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(pal.glassHi)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = pal.dim, modifier = Modifier.size(18.dp))
        } else {
            Text(emoji ?: "", fontSize = 15.sp)
        }
    }
}

/** 搜索条：40dp 高、全圆角，只当入口用（点了开全屏搜索层） */
@Composable
private fun SearchPill(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
    tail: @Composable RowScope.() -> Unit = {}
) {
    val pal = LocalXyPal.current
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glassHi)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = pal.mute, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text.ifBlank { "搜全站：站点、分类、分享、文章…" },
            fontSize = 14.sp,
            color = if (text.isBlank()) pal.mute else pal.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            /* fill = false：占位文案短的时候别把右边撑满，尾巴（刷新状态）才好贴右 */
            modifier = Modifier.weight(1f, fill = false)
        )
        tail()
    }
}

/**
 * 搜索框右端那一小块：下拉进度弧 / 刷新中的转圈 / 刷完的 ✓ ⚠️。
 *
 * 为什么放这儿（2.8.1 小宝定的）：原来指示器是页面正中一颗 34dp 的大圆、
 * 刷完再在屏幕顶上弹一条胶囊，两样都压内容；搜索框右端本来就有块空当，
 * 借它显示，刷新完自己消失，页面一点不动。
 *
 * 三种状态互斥：拖动中画弧（越长越接近阈值）→ 松手后一直转 → 出结果后 ✓ 或 ⚠️。
 */
@Composable
private fun RefreshTail(pullProgress: () -> Float, refreshing: Boolean, tip: String) {
    val pal = LocalXyPal.current
    /* 在这里读（而不是在 TopBar 里读）：值是每帧变的，读的一方才重组 */
    val pull = pullProgress()
    val failed = tip.contains("连不上") || tip.contains("失败")
    val show = refreshing || pull > 0.02f || tip.isNotBlank()
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tip.isNotBlank() && !refreshing) {
                Text(
                    (if (failed) "⚠️ " else "✓ ") + tip,
                    fontSize = 11.5.sp,
                    color = if (failed) pal.dim else pal.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spinner16(progress = pull, spinning = refreshing)
            }
        }
    }
}

/** 顶栏里那颗 16dp 的小圈：拉的时候弧越来越长，松手转起来 */
@Composable
private fun Spinner16(progress: Float, spinning: Boolean) {
    val pal = LocalXyPal.current
    val spin = if (spinning) {
        val t = rememberInfiniteTransition(label = "topspin")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(820, easing = LinearEasing)),
            label = "topspinDeg"
        ).value
    } else 0f
    Canvas(Modifier.padding(horizontal = 6.dp).size(16.dp)) {
        val sw = 1.8.dp.toPx()
        if (spinning) {
            rotate(spin) {
                drawArc(
                    color = pal.inkA1, startAngle = 0f, sweepAngle = 260f,
                    useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        } else {
            drawArc(
                color = pal.inkA1, startAngle = -90f, sweepAngle = 300f * progress,
                useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}

/** 筛选胶囊：40dp 高、左右 13dp、图标 + 文案 + 下拉箭头 */
@Composable
private fun FilterPill(filter: TopFilter) {
    val pal = LocalXyPal.current
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glassHi)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { filter.onClick() }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(filter.emoji, fontSize = 13.sp)
        Spacer(Modifier.width(7.dp))
        Text(
            filter.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = pal.text,
            maxLines = 1
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Rounded.KeyboardArrowDown, null,
            tint = pal.mute,
            modifier = Modifier.size(15.dp)
        )
    }
}
