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
import androidx.compose.material.icons.rounded.Refresh
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

/** 自定义链接一行最多摆几个（小宝 2026-11-28：「一行显示，最多显示五个链接」） */
private const val MAX_TOP_LINKS = 5

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
    /* 2.9.5：下拉刷新改成搜索框右侧一颗**显式按钮**（小宝 2026-11-28：
       「app端和移动端的下拉刷新改成图中那里增加一个刷新按钮」）。
       下拉手势连同顶部那圈进度弧一起去掉了，refreshing 只用来让这颗按钮转圈。 */
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    /* 刷新结果那句「已是最新 / 连不上」现在由页面顶部的胶囊统一提示（MainActivity 的 tip），
       这里只留参数不动，免得调用方还要跟着改 */
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
                /* 自定义入口 2.9.5 起**不再放这里** —— 挪到搜索框下面单独一行（见下面的链接行），
                   以前和稍后读 / 主题挤在一起，条目一多就横滑，看不全。
                   现在这一行只留两块固定内容：稍后读、白天/黑夜。 */
                Spacer(Modifier.weight(1f))
                if (top.saved) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (top.search) {
                    SearchPill(searchText, Modifier.weight(1f), onOpenSearch)
                    /* 刷新键钉在搜索框右边 —— 小宝图 1 红框那个位置 */
                    RefreshAction(refreshing, onRefresh)
                }
                if (top.filter && filter != null) FilterPill(filter)
            }
            }
            /* 自定义链接行（2.9.5）：搜索框下面单独一行，一行最多 5 个、不横滑。
               均分宽度，不足 5 个时用空白顶住 —— 只剩一条时也不会被拉成一整条长条。 */
            if (top.links && links.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /* 只按**实际条数**均分（最多 5 个）。以前补空格子凑够 5 份，
                       结果只配了 3 条时每个只有 1/5 宽，「灵感随记」被截成「灵…」。
                       fill = false：内容比分配宽度短时按内容宽，一条时不会拉满整行。 */
                    links.take(MAX_TOP_LINKS).forEach { l ->
                        TopLinkChip(l, Modifier.weight(1f, fill = false)) { onLink(l) }
                    }
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

/**
 * 搜索框右边那颗刷新键（2.9.5）。
 *
 * 取代原来的「下拉刷新」：以前要滚到顶再往下拽，现在点一下就行，
 * 手机壳厚、贴了膜、或者列表本来就在顶部的时候都不会白拉。
 * 40dp 见方、圆角 14，跟搜索条同高，正好填满小宝图 1 红框那块空当。
 */
@Composable
private fun RefreshAction(refreshing: Boolean, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pal.glassHi)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (!refreshing) onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (refreshing) {
            Spinner16(progress = 1f, spinning = true)
        } else {
            Icon(Icons.Rounded.Refresh, "刷新", tint = pal.dim, modifier = Modifier.size(19.dp))
        }
    }
}

/**
 * 搜索框下面那一行里的自定义链接（2.9.5）。
 *
 * 一条一个 34dp 小胶囊：图标 + 名字。宽度由外层 weight 均分（一行最多 5 个），
 * 名字长了就省略号 —— 以前是「图标只有 emoji、名字全靠悬停」，手机上根本看不出是哪个。
 */
@Composable
private fun TopLinkChip(link: SiteLink, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    val ico = link.icon.ifBlank { link.name.take(1) }
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(pal.glassHi)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(ico, fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            link.name,
            fontSize = 12.sp,
            color = pal.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

/* 2.9.5：原先钉在搜索框右端的 RefreshTail（下拉进度弧 / 转圈 / ✓ ⚠️）整段删掉了 ——
   下拉手势取消，那一格换成了可点的 RefreshAction。刷完的提示仍由页面顶部胶囊负责，
   所以这里不需要「再画一个小尾巴」。Spinner16 留着，刷新键转圈时用。 */

/** 顶栏里那颗 16dp 的小圈：刷新中一直转 */
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
