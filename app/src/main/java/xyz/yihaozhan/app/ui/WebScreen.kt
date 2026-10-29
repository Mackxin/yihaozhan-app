package xyz.yihaozhan.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * App 内网页预览。
 *
 * 为什么不直接甩给浏览器：在「导航」里连点几个站点，来回切应用很难受，
 * 而且回来之后列表位置全丢了。这里用 WebView 就地打开，
 * 顶栏留一颗「在浏览器打开」给需要登录/支付那种真得走浏览器的场景。
 *
 * 返回键先退网页历史，退到头才关这一层 —— 不然点进二级页面按返回直接回列表，
 * 前面点的路径就白走了。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, onBack: () -> Unit, onOpenExternal: (String) -> Unit) {
    val ctx = LocalContext.current
    var progress by remember { mutableStateOf(0f) }
    var currentUrl by remember { mutableStateOf(url) }
    var pageTitle by remember { mutableStateOf("") }
    /* 打不开时的中文提示（WebView 自带那页是英文白屏，太生硬） */
    var failed by remember { mutableStateOf<String?>(null) }

    val web = remember {
        /* 千万别在这儿设 layoutParams：给了 MATCH_PARENT，原生 View 会铺满整个
           ComposeView，把上面的顶栏一起盖掉（试过，顶栏在语义树里还在，肉眼全白）。 */
        WebView(ctx).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                    progress = 0.05f
                    failed = null
                    if (u != null) currentUrl = u
                }

                override fun onPageFinished(view: WebView?, u: String?) {
                    /* 必须在这儿归 1：有些站点（尤其错误页）不会回调 onProgressChanged(100)，
                       不归的话转圈会一直挂在那儿，看着像永远在加载。 */
                    progress = 1f
                    if (u != null) currentUrl = u
                    pageTitle = view?.title.orEmpty()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == false) return
                    progress = 1f
                    failed = error?.description?.toString().orEmpty()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                }
            }
            loadUrl(url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.destroy()
        }
    }

    BackHandler(enabled = true) {
        if (web.canGoBack()) web.goBack() else onBack()
    }

    /* 兜底超时：有些站连不上时 WebView 既不报错也不回调加载完成（卡在半路），
       转圈就会一直挂着像卡死。15 秒还没完事就当打不开，给用户两个出口。 */
    LaunchedEffect(url) {
        delay(15_000)
        if (progress < 1f && failed == null) {
            progress = 1f
            failed = "加载超时"
        }
    }

    Column(Modifier.fillMaxSize()) {
        /* 顶栏：返回 / 标题（域名优先）/ 在浏览器打开 */
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (web.canGoBack()) web.goBack() else onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val shown = pageTitle.ifBlank { hostOf(currentUrl) }
                Text(
                    shown,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalXyPal.current.text
                )
                Text(
                    hostOf(currentUrl),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalXyPal.current.mute
                )
            }
            IconButton(onClick = { onOpenExternal(currentUrl) }) {
                Icon(
                    Icons.Rounded.OpenInBrowser, "在浏览器打开",
                    tint = LocalXyPal.current.dim,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        /* 加载进度：只在没加载完时占一条，加载完就彻底收起，不占高度 */
        if (progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = LocalXyPal.current.inkA1,
                trackColor = LocalXyPal.current.outline
            )
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
            if (progress < 1f) {
                Box(
                    Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp).clip(RoundedCornerShape(99.dp)),
                        strokeWidth = 2.dp,
                        color = LocalXyPal.current.inkA1
                    )
                }
            }
            val why = failed
            if (why != null) {
                LoadFailed(
                    host = hostOf(currentUrl),
                    reason = why,
                    onRetry = {
                        failed = null
                        progress = 0.05f
                        web.reload()
                    },
                    onOpenExternal = { onOpenExternal(currentUrl) }
                )
            }
        }
    }
}

/** 打不开时盖掉 WebView 那张英文白屏，换成一句中文 + 两个出口 */
@Composable
private fun BoxScope.LoadFailed(
    host: String,
    reason: String,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val pal = LocalXyPal.current
    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bg)
            .padding(horizontal = 34.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "这个网页打不开",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal.text
        )
        Spacer(Modifier.height(10.dp))
        Text(
            host,
            fontSize = 13.sp,
            color = pal.inkA1,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "可能这个站已经关了，或者现在网络连不上。\n" + reason.ifBlank { "换个时间再试试。" },
            fontSize = 12.sp,
            color = pal.mute,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillBtn("重试", true, onRetry)
            PillBtn("在浏览器打开", false, onOpenExternal)
        }
    }
}

@Composable
private fun PillBtn(label: String, primary: Boolean, onClick: () -> Unit) {
    val pal = LocalXyPal.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .then(
                if (primary) Modifier.background(pal.grad())
                else Modifier.background(pal.glass)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            /* 渐变底上写字用深墨，白字压最浅那档青色不够看 */
            color = if (primary) XY_INK else pal.text
        )
    }
}

/** 从网址里抠出域名，顶栏显示「哪个站」比显示整条 URL 有用 */
internal fun hostOf(u: String): String {
    return runCatching {
        val s = u.substringAfter("//", u)
        val host = s.substringBefore('/').substringBefore('?')
        host.ifBlank { u }
    }.getOrDefault(u)
}

/** 顶栏右侧那颗按钮的空白占位，保持标题居中 */
@Composable
internal fun BarSpacer() {
    Spacer(Modifier.width(48.dp))
}
