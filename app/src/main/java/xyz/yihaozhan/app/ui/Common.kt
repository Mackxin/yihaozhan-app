package xyz.yihaozhan.app.ui

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

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

/**
 * 走系统分享面板把一段文字发出去（微信、备忘录、邮箱都行）。
 * 用 createChooser 而不是裸 ACTION_SEND：有些机型不包 chooser 会直接记住上次那个应用，
 * 想换一个反而没地方换。
 */
fun shareText(ctx: Context, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(
            Intent.createChooser(send, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "没有能分享的应用", Toast.LENGTH_SHORT).show()
    }
}

/** 复制一段文字到剪贴板（卡片长按菜单「复制链接」用） */
fun copyText(ctx: Context, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("壹号栈", text))
    } catch (e: Exception) {
        // 剪贴板被系统限制时静默失败，外面已经弹过 toast
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
 * 经 FileProvider 把本地文件递给系统分享面板（图片 / JSON / .md 都走这个）。
 * 跟更新安装包共用同一个 provider（authority = 包名 + ".fileprovider"），
 * file_paths.xml 已经把整个 cache 目录放开，所以临时文件放 cache 下就能拿到 uri。
 */
fun shareFile(ctx: Context, file: File, mime: String, title: String = "分享到") {
    try {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/** 写一段文本到 cache 目录，返回文件（失败返回 null） */
fun writeCacheText(ctx: Context, name: String, text: String): File? = runCatching {
    val f = File(ctx.cacheDir, name)
    f.writeText(text)
    f
}.getOrNull()

/* ------------------------------ 入场动效 ------------------------------
 * 所有「突然冒出来」的地方都套一个，让它轻轻淡入（弹出还会带一点放大），
 * 不要一下子硬切。时长 220~300ms，easeOut，参考 Material3 的动效节奏。 */

/** 整块内容首次出现时淡入（alpha 0→1） */
@Composable
fun Modifier.enterFade(durationMs: Int = 280): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer { alpha = a.value }
}

/** 对话框 / 面板首次出现时淡入 + 轻微放大（0.96→1），像轻轻浮起来 */
@Composable
fun Modifier.enterPop(durationMs: Int = 230): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        alpha = p.value
        val s = 0.96f + 0.04f * p.value
        scaleX = s
        scaleY = s
    }
}

/**
 * 页面切换用的「高级感」转场：淡入 + 从下方轻轻抬起 + 极轻的一点点放大。
 * 比纯淡入更有层次，内容像是「落定」而不是「啪一下出现」。
 */
@Composable
fun Modifier.enterRise(durationMs: Int = 340): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        alpha = p.value
        /* 未开始时向下偏移 22px，落定时归零；用 px 以免受 density 影响 */
        translationY = (1f - p.value) * 22f
        val s = 0.985f + 0.015f * p.value
        scaleX = s
        scaleY = s
    }
}

/**
 * 「点一下到页面底部、再点一下回顶部」的共用动作（顶栏的品牌和首页大标题都用这一份）。
 *
 * 判断的是**此刻是不是已经滚到底**，而不是自己记一个「上次点了什么」的开关 ——
 * 用户手动滑到半路再点，落点才不会跟他的预期拧着。
 *
 * 2.6.4 起不再固定绑一条 state：顶栏那颗「壹号栈」在**任意页签**上都是这个动作
 * （用户明确要求不再回首页），所以由调用方把当前页的 LazyListState 传进来。
 */
fun toggleEnd(scope: CoroutineScope, state: LazyListState) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    /* 页面还没测量出来（刚切过去/不在屏幕上）就什么都不做，别滚到一个空列表 */
    if (total <= 0) return
    val last = info.visibleItemsInfo.lastOrNull()
    val atEnd = last != null && last.index >= total - 1 &&
        last.offset + last.size <= info.viewportEndOffset + 8
    scope.launch {
        if (atEnd) state.animateScrollToItem(0) else state.animateScrollToItem(total - 1)
    }
}

/** 只有一条列表时的便捷版（首页大标题用）：把 scope 一起记好，调用方不关心协程 */
@Composable
fun rememberEndToggle(state: LazyListState): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(state, scope) { { toggleEnd(scope, state) } }
}

/**
 * 把 `**这段**` 标成重点：站点说明段里的 `<b>` 就是这个效果（正文色 + 700）。
 *
 * 不用整块 Markdown 解析：页头说明只有一件事需要强调 —— 数字。
 * 真上一套 inlineMd，段落里的「—」「·」和斜杠串会被当成语法，反而出错。
 */
fun markBold(
    text: String,
    base: Color,
    strong: Color = base,
    strongWeight: FontWeight = FontWeight.Bold
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val s = text.indexOf("**", i)
        val e = if (s < 0) -1 else text.indexOf("**", s + 2)
        if (s < 0 || e < 0) {
            withStyle(SpanStyle(color = base, fontWeight = FontWeight.Normal)) {
                append(text.substring(i))
            }
            break
        }
        if (s > i) {
            withStyle(SpanStyle(color = base, fontWeight = FontWeight.Normal)) {
                append(text.substring(i, s))
            }
        }
        withStyle(SpanStyle(color = strong, fontWeight = strongWeight)) {
            append(text.substring(s + 2, e))
        }
        i = e + 2
    }
}

/**
 * 眉标：站点 `.eyebrow` —— 一条 22dp 的渐变短线 + 大写英文 + `·` + 中文。
 * 12sp / 字距 .22em / 更淡一档的灰。文字里的英文由调用方直接写大写，
 * 中文不受 CSS `text-transform:uppercase` 影响，这里也没法自动转。
 */
@Composable
fun Eyebrow(text: String) {
    val pal = LocalXyPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        /* ::before 那条线：宽 22、高 1，用的是 --grad */
        Box(
            Modifier
                .width(22.dp)
                .height(1.dp)
                .background(pal.grad(), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.22.em,
            color = pal.mute
        )
    }
}

/**
 * 页面标题区：**照抄站点手机版的 .page-head**（390px 视口下量出来的那一档）。
 *
 *   eyebrow 12sp/字距.22em  →  14dp  →  h1 30sp/800/行高 1.7/字距 -.02em
 *   →  14dp  →  说明 15sp/行高 1.7/--text-dim  →  16dp  →  side（统计卡或「共 N 个…」+ 胶囊）
 *   整块下留白 20dp。全部居中 —— 手机一屏窄，左对齐的长标题会发偏。
 *
 * 说明段比标题**宽**一档：标题左右各让 56dp 给汉堡/齿轮，说明只跟正文一样留 20dp。
 * 站点上段落是 358px 宽、两行；App 里若也按 56dp 收窄会挤成三四行，节奏就变了。
 *
 * `side` 放统计卡或「共 N 个站点 · M 个分类 + 胶囊」，用它替掉原来那行 meta 小字。
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    meta: String? = null,
    eyebrow: String? = null,
    onSettings: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    side: (@Composable () -> Unit)? = null,
    /* 页头整块塞进 LazyColumn 当第一个 item 时，外面的列表已经留了 20dp，
       这里再留就变 40 —— 那时候传 0.dp */
    inset: androidx.compose.ui.unit.Dp = 20.dp
) {
    val pal = LocalXyPal.current
    Column(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!eyebrow.isNullOrBlank()) Eyebrow(eyebrow)
                Text(
                    title,
                    fontSize = 30.sp,
                    lineHeight = 51.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).em,
                    textAlign = TextAlign.Center,
                    color = pal.text,
                    /* 导航页点标题就能全部展开/折叠，所以标题本身要能点；
                       别的页面不给回调，就只是一行字 */
                    modifier = Modifier
                        .padding(top = if (eyebrow.isNullOrBlank()) 0.dp else 14.dp)
                        .then(
                            if (onTitleClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onTitleClick() }
                                    .padding(horizontal = 8.dp)
                            } else Modifier
                        )
                )
            }
            if (leading != null) {
                Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp)) { leading() }
            }
            if (onSettings != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(end = 4.dp)) { GearButton(onSettings) }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                markBold(subtitle, base = pal.dim, strong = pal.text),
                fontSize = 15.sp,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = inset, end = inset, top = 14.dp)
            )
        }
        if (!meta.isNullOrBlank()) {
            Text(
                meta,
                fontSize = 11.sp,
                letterSpacing = 0.02.em,
                textAlign = TextAlign.Center,
                color = pal.mute,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (side != null) {
            Spacer(Modifier.height(16.dp))
            side()
        }
    }
}

/**
 * 「共 N 个站点 · M 个分类」：站点 `.nav-meta` —— 13sp、更淡的灰，数字用正文色加粗。
 * 数字单独一档色是为了让「10 个」和「1000 个」一眼能扫到。
 */
@Composable
fun NavMeta(text: String) {
    val pal = LocalXyPal.current
    Text(
        markBold(text, base = pal.mute, strong = pal.text, strongWeight = FontWeight.Bold),
        fontSize = 13.sp,
        lineHeight = 20.sp,
        maxLines = 1
    )
}

/**
 * 页头里的胶囊：站点 `.head-toggle` —— 36dp 高、左右 15dp、全圆角，
 * 未选中是玻璃底，选中直接铺渐变 + 深墨字（白字压最浅的青色只有 1.59:1）。
 */
@Composable
fun HeadPill(text: String, active: Boolean = false, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(99.dp))
            /* 分支写在 Modifier 里：Brush 和 Color 的共同父类是 Any，
               `if (...) brush else color` 会退化成 Any，两个 background 重载都匹配不上 */
            .then(
                if (active) Modifier.background(pal.grad())
                else Modifier.background(pal.glass)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) XY_INK else pal.dim,
            maxLines = 1
        )
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

/**
 * 搜索框：只用 BasicTextField 自绘，玻璃底 + 全圆角（站点 .searchbox 的样子）。
 *
 * 失焦就把输入法收掉：手机上一旦弹过键盘，切页/点别处后它还杵在那儿挡半屏，
 * 是最烦的一种「不听话」。这里在聚焦状态变化时主动 hide，
 * 外层还有一层「点空白处清焦点」，两头都堵上。
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val pal = LocalXyPal.current
    val keyboard = LocalSoftwareKeyboardController.current
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
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { st -> if (!st.isFocused) keyboard?.hide() }
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
    /* 没显式传回调时，点标签就打开「这个标签的全部内容」那一页（2.7 新增）。
       这样文章卡、阅读页、分享卡上的标签都不用各自接一遍回调。 */
    val openTag = LocalOpenTag.current
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
            .clickable { if (onClick != null) onClick() else openTag(text) }
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
    /* 临时的高亮色（比如随机回顾时那张卡闪一下），不给就用默认玻璃底 */
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    /* 长按（站点那套右键菜单的触屏版）。给了就自己接管手势，
       因为 clickable 和 detectTapGestures 抢同一个 pointer 会互相打架 */
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val pal = LocalXyPal.current
    val gesture = when {
        onLongPress != null -> Modifier.pointerInput(onClick, onLongPress) {
            detectTapGestures(
                onTap = { onClick?.invoke() },
                onLongPress = { onLongPress.invoke() }
            )
        }
        onClick != null -> Modifier.clickable { onClick() }
        else -> Modifier
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(pal.rLg.dp))
            .background(tint ?: if (glass) pal.glass else Color.Transparent)
            .then(gesture)
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
 * 数字统计卡：站点手机版的 `.head-stat` —— 三张等宽、内容居中。
 *
 * 卡片底不是纯玻璃，而是**同色系的极淡渐变**（蓝 13% → 紫 10% → 青 8%）再补一条
 * 16% 的蓝描边：数字本身就是渐变的，卡底再压一层才像「一家人」，不然数字浮在灰玻璃上发脏。
 * 数字 21sp/800 渐变文字，说明 11.5sp（提过一档的 --text-dim，压在浅渐变上才不发灰）。
 *
 * 渐变只铺数字那么宽：横着拉太长会糊成一片紫，看不出「蓝→青」。
 */
@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    val pal = LocalXyPal.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x217C9CFF), Color(0x1AB47CFF), Color(0x144FE3D0)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            }
            .border(1.dp, Color(0x297C9CFF), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        /* 数字按位数缩字号：一行放四张统计卡时每张只剩 ~50dp 宽，
           「2078」用 21sp 会被折成「207 / 8」。位数越多越小，永远单行 ——
           softWrap=false 是保底：哪怕算窄了也只会挤，不会断成两行。 */
        val numSize = when {
            value.length >= 6 -> 13.sp
            value.length == 5 -> 14.5.sp
            value.length == 4 -> 16.5.sp
            value.length == 3 -> 19.sp
            else -> 21.sp
        }
        val numStyle = TextStyle(
            fontSize = numSize,
            lineHeight = (numSize.value + 3f).sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).em
        )
        Text(
            value,
            /* 浅色主题退回实色 --ink-a1：渐变最浅那段压在白玻璃上只有 ~2:1，
               站点也是这么处理的（html[data-theme="light"] .head-stat b） */
            style = if (pal.dark) numStyle.copy(brush = pal.gradText(120f))
            else numStyle.copy(color = pal.inkA1),
            maxLines = 1,
            softWrap = false
        )
        Text(
            /* 行高跟站点对齐（span 用正文字号 1.7）：不写死的话卡片会比站点的矮一截 */
            label, fontSize = 11.5.sp, lineHeight = 19.sp,
            letterSpacing = 0.02.em, color = pal.dim, maxLines = 1
        )
    }
}

/** 页头那排统计卡的容器：等宽、间距 10dp、左右跟正文一样留 20dp（塞进列表时传 0） */
@Composable
fun StatRow(
    inset: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = inset),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
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

/**
 * 正文纯文字字数 —— 跟站点 `MD.stats().chars` 一个口径：剥掉标记、只数非空白字符。
 *
 * 只为了页头上那个「3.1 千字」的牌子，不需要和站点一字不差；
 * 但必须比 `body.length` 靠谱 —— 一篇多代码块的文章，正文里一半是 ``` 和缩进。
 */
fun postChars(md: String): Int {
    var s = md
    s = s.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("~~~[\\s\\S]*?~~~"), " ")
    /* 图片只留 alt、链接只留文字 */
    s = s.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)")) { it.groupValues[1] }
    s = s.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)")) { it.groupValues[1] }
    /* 表格分隔行整行不要，剩下竖线当空格 */
    s = s.replace(
        Regex("^[ \\t]*\\|?[ \\t]*:?-{2,}:?[ \\t]*(\\|[ \\t]*:?-{2,}:?[ \\t]*)*\\|?[ \\t]*$", RegexOption.MULTILINE),
        " "
    )
    s = s.replace("|", " ")
    return s.count { !it.isWhitespace() && it !in "#>*`_~" }
}

/** 千字 / 万字：站点那条规则 —— 到一万换「万字」，其余一律「千字」，保留一位小数且去掉 .0 */
fun fmtChars(n: Int): Pair<String, String> {
    /* 固定用 US 的格式化：某些区域把小数点写成逗号，「3,1 千字」就不像数字了 */
    return if (n >= 10000) {
        val v = n / 1000.0 / 10.0
        String.format(Locale.US, "%.1f", v).removeSuffix(".0") to "万字"
    } else {
        val v = n / 1000.0
        String.format(Locale.US, "%.1f", v).removeSuffix(".0") to "千字"
    }
}


/* ============================ 标签页入口 ============================ */

/**
 * 「点标签 → 打开这个标签的独立页」。
 *
 * 用组合本地而不是层层传参数：标签胶囊散落在分享卡、文章卡、阅读页三处，
 * 中间隔了四五层组件，为了一个回调把参数一路串下去不划算。
 * `MainActivity` 在根部提供一次，谁想用就取。
 */
val LocalOpenTag = androidx.compose.runtime.compositionLocalOf<(String) -> Unit> { {} }

/* ============================ 网络图片（收款码） ============================ */

/**
 * 一张从后台来的图（收款码）。`src` 有两种写法：
 *  - `data:image/png;base64,…`：后台直接上传的图，随 app.json 一起过来；
 *  - `https://…/xx.png`：站点上的图片地址。
 *
 * 为什么不引 Coil：整个 App 只有这一处要画网络图，为它多一个依赖不划算。
 * 这里做三件小事：内存缓存（同一张不重复下）、失败给一行小字、加载中给个占位。
 */
private val imgClient by lazy { OkHttpClient() }
private val imgCache = HashMap<String, Bitmap?>()

private fun loadBitmap(src: String): Bitmap? = runCatching {
    if (src.startsWith("data:", ignoreCase = true)) {
        val comma = src.indexOf(',')
        if (comma < 0) return null
        val b64 = src.substring(comma + 1)
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } else {
        val res = imgClient.newCall(Request.Builder().url(src).build()).execute()
        res.use { r ->
            val bytes = r.body?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}.getOrNull()

@Composable
fun RemoteImg(
    src: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val pal = LocalXyPal.current
    var bmp by remember(src) { mutableStateOf(imgCache[src]) }
    var failed by remember(src) { mutableStateOf(false) }
    LaunchedEffect(src) {
        if (src.isBlank() || imgCache.containsKey(src)) return@LaunchedEffect
        val b = withContext(Dispatchers.IO) { loadBitmap(src) }
        imgCache[src] = b
        if (b == null) failed = true else bmp = b
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(pal.glass2),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        when {
            b != null -> Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "收款码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().size(size)
            )
            failed -> Text("图打不开", fontSize = 11.sp, color = pal.mute)
            else -> Text("加载中…", fontSize = 11.sp, color = pal.mute)
        }
    }
}
