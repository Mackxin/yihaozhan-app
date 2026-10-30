package xyz.yihaozhan.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 下拉刷新。
 *
 * 没用 material3 的 `PullToRefreshContainer`：它那颗指示器会跟着手指一路往下滑，
 * 拉个一百来像素就正好压在页头标题上，浅色主题下就是一个白圈糊在字上。
 * 这里自己接管手势，指示器**钉在顶部**（拉到一定距离就不再往下走），
 * 内容整体跟手往下让一点，松手过阈值才转起来 —— 也更贴站点那套「玻璃药丸 + 细弧」。
 *
 * 手势走 `nestedScroll`：列表自己还能滚的时候轮不到这里，滚到顶继续拉才被接管。
 */
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pal = LocalXyPal.current
    val density = LocalDensity.current
    val threshold = with(density) { 66.dp.toPx() }
    val hold = with(density) { 58.dp.toPx() }

    /* 拉出来多少像素（已经算过阻尼） */
    var pull by remember { mutableFloatStateOf(0f) }
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val scope = rememberCoroutineScope()

    /* 松手后去哪：过阈值就刷新并停在 hold 位转圈，没过就弹回去 */
    fun settle() {
        if (pull >= threshold && !currentRefreshing) {
            currentOnRefresh()
            scope.launch { animate(pull, hold) { v, _ -> pull = v } }
        } else if (pull > 0f) {
            scope.launch { animate(pull, 0f) { v, _ -> pull = v } }
        }
    }

    val conn = remember(threshold, hold) {
        object : NestedScrollConnection {
            /* 手指往回推：先把拉出来的距离收掉，别让列表跟着起反作用力 */
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && pull > 0f) {
                    val used = (-available.y).coerceAtMost(pull)
                    pull -= used
                    return Offset(0f, -used)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull > 0f) settle()
                return Velocity.Zero
            }
        }
    }

    /* 刷完了把指示器收回去 */
    LaunchedEffect(refreshing) {
        if (!refreshing && pull > 0f) animate(pull, 0f) { v, _ -> pull = v }
    }

    Box(
        modifier
            .fillMaxSize()
            .nestedScroll(conn)
            /*
             * 光靠 nestedScroll 不够：只有「手指按在可滚动的列表上」才会派发滚动事件。
             * 分享/文章/导航这几页的页头是钉在列表外面的，按着页头往下拉一点反应都没有。
             * 所以再加一层指针拖动兜底 —— 列表自己消费掉的事件（`isConsumed`）不抢，
             * 从页头或空白处拉才由这里接管。
             */
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { settle() },
                    onDragCancel = {
                        scope.launch { animate(pull, 0f) { v, _ -> pull = v } }
                    }
                ) { change, dy ->
                    if (change.isConsumed) return@detectVerticalDragGestures
                    if (dy > 0f) {
                        pull = (pull + dy * 0.55f).coerceAtMost(threshold * 1.55f)
                        change.consume()
                    } else if (pull > 0f) {
                        pull = (pull + dy).coerceAtLeast(0f)
                        change.consume()
                    }
                }
            }
    ) {
        /* 内容按实际下拉距离往下走：顶部就露出一段空当，指示器待在里面，不会压到标题 */
        Box(Modifier.fillMaxSize().offset { IntOffset(0, pull.roundToInt()) }) {
            content()
        }

        if (pull > 1f) {
            val progress = (pull / threshold).coerceIn(0f, 1f)
            val indH = with(density) { 34.dp.toPx() }
            /* 空当还没拉出一个指示器的高度时（拉得浅），指示器淡入 —— 不硬压在标题上 */
            val alpha = (pull / indH).coerceIn(0f, 1f)
            val y = ((pull - indH) / 2f).coerceAtLeast(0f)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, y.roundToInt()) }
                    .alpha(alpha)
            ) {
                Spinner(progress, spinning = refreshing || pull >= threshold)
            }
        }
    }
}

/** 小圆底 + 一段弧：拉的时候弧越来越长，到阈值后变成一直转 */
@Composable
private fun Spinner(progress: Float, spinning: Boolean) {
    val pal = LocalXyPal.current
    val spin = if (spinning) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
            label = "spinDeg"
        ).value
    } else {
        0f
    }

    Box(
        Modifier.size(34.dp).clip(CircleShape).background(pal.glass),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(17.dp)) {
            val sw = 2.dp.toPx()
            if (spinning) {
                rotate(spin) {
                    drawArc(
                        color = pal.inkA1,
                        startAngle = 0f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
            } else {
                drawArc(
                    color = pal.inkA1,
                    startAngle = -90f,
                    sweepAngle = 300f * progress,
                    useCenter = false,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
    }
}
