package xyz.yihaozhan.app.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
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
    onHome: () -> Unit,
    onLink: (SiteLink) -> Unit = {},
    onSaved: () -> Unit,
    onTheme: () -> Unit,
    onOpenSearch: () -> Unit,
    filter: TopFilter? = null,
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
                   点它现在不是回首页 —— 首页时是「到底 / 回顶」的开关，别的页才回首页，
                   具体动作由 MainActivity 传进来。最宽 150dp，站名再长也不许把右边那排键挤没 */
                if (top.brand) {
                Row(
                    Modifier
                        .widthIn(max = 150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onHome() }
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
                if (top.search) SearchPill(searchText, Modifier.weight(1f), onOpenSearch)
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
private fun SearchPill(text: String, modifier: Modifier, onClick: () -> Unit) {
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
            overflow = TextOverflow.Ellipsis
        )
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
