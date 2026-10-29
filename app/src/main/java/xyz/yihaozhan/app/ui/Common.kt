package xyz.yihaozhan.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 打开外部链接：站内页也走浏览器（站点是 SPA，原生没法直接渲染后台那种页面） */
@Composable
fun rememberOpener(): (String) -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) {
        { url: String ->
            if (url.isNotBlank()) openUrl(ctx, url)
        }
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "没有能打开这个链接的应用", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 页面标题区：跟站点 .page-head 一个规格 —— h1 30sp / 字重 800 / 字距 -0.02em，
 * 说明行 13sp 次要色，小字 11.5sp 更淡一档；全部居中。
 * 左右各留 64dp：短标题不会跟左上汉堡、右上齿轮挤在一起，居中也不偏。
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    meta: String? = null,
    onSettings: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null
) {
    val pal = LocalXyPal.current
    Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                textAlign = TextAlign.Center,
                color = pal.text
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    color = pal.dim
                )
            }
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    meta,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.02.em,
                    textAlign = TextAlign.Center,
                    color = pal.mute
                )
            }
        }
        if (leading != null) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp)) { leading() }
        }
        if (onSettings != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 4.dp)) { GearButton(onSettings) }
        }
    }
}

/** 右上角齿轮：站点 .icon-btn 的样子 —— 38dp 高、玻璃底、圆角 12 */
@Composable
fun GearButton(onClick: () -> Unit) {
    val pal = LocalXyPal.current
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            Icons.Rounded.Settings, "设置",
            tint = pal.dim,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 小标题 + 右侧「更多」 */
@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val pal = LocalXyPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).em,
            color = pal.text,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            Text(
                action + " ›",
                fontSize = 12.5.sp,
                color = pal.inkA1,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

/** 搜索框：只用 BasicTextField 自绘，玻璃底 + 全圆角（站点 .searchbox 的样子） */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val pal = LocalXyPal.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(pal.glass)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Search, null,
            tint = pal.mute,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 13.5.sp, color = pal.mute)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp, color = pal.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pal.glassHi)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, "清空", tint = pal.mute, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/**
 * 胶囊：站点 .pill / .tag。
 * 选中态用站点 .head-toggle[aria-pressed=true] 那套 —— 渐变底 + 深墨字，
 * 不用白字（白字压在最浅的青色上只有 1.59:1）。
 */
@Composable
fun TagChip(text: String, onClick: (() -> Unit)? = null, active: Boolean = false) {
    val pal = LocalXyPal.current
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) XY_INK else pal.dim,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(
                if (active) Modifier.background(pal.grad())
                else Modifier.background(pal.glass)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

/** 卡片外壳：玻璃底、圆角 26（站点 --r-lg）、不描边、不投影 */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    /* 列表条目默认透明：站点 .feed-card 就是 background:none，
       靠留白分组。也给里面的标签胶囊留出能看清的底。 */
    glass: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val pal = LocalXyPal.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(XyR.lg.dp))
            .background(if (glass) pal.glass else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding)
    ) { content() }
}

/** 空状态 */
@Composable
fun EmptyState(emoji: String, title: String, desc: String) {
    val pal = LocalXyPal.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(emoji, fontSize = 34.sp)
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
        Text(desc, fontSize = 13.sp, lineHeight = 21.sp, color = pal.dim)
    }
}

/**
 * 数字统计格：站点 .head-stat —— 玻璃底、圆角 16、数字 25sp/800 且是**渐变文字**，
 * 下面一行 12sp 更淡的说明。渐变色只铺数字那么宽，横向拉太长会糊成一片紫。
 */
@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    val pal = LocalXyPal.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pal.glass)
            .padding(horizontal = 18.dp, vertical = 13.dp)
    ) {
        Text(
            value,
            style = TextStyle(
                brush = pal.gradText(180f),
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em
            )
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label, fontSize = 12.sp, letterSpacing = 0.02.em, color = pal.mute
        )
    }
}

/** 细分隔线：站点不用描边，需要分块时才画一条极淡的线 */
@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalXyPal.current.outline)
    )
}
