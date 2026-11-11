package xyz.yihaozhan.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /* 这一页要不要下拉刷新。设置页那种「点一下就生效」的页面拉了也没意义，
       而且会平白多出一个转圈（小宝 2.8.1：「设置页面不需要刷新吧」） */
    enabled: Boolean = true,
    /* 下拉进度（0~1）上报给顶栏 —— 指示器画在搜索框右边，不画在页面中间 */
    onPull: (Float) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val threshold = with(density) { 66.dp.toPx() }
    val hold = with(density) { 58.dp.toPx() }

    /* 拉出来多少像素（已经算过阻尼） */
    var pull by remember { mutableFloatStateOf(0f) }
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    /* 这一页到底要不要刷新：手势节点**永远挂着**，只让回调内部判断（见下面那段注释） */
    val currentEnabled by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()

    /* 松手后去哪：过阈值就刷新并停在 hold 位转圈，没过就弹回去 */
    fun settle() {
        if (!currentEnabled) return
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
                if (!currentEnabled) return Offset.Zero
                if (available.y < 0f && pull > 0f) {
                    val used = (-available.y).coerceAtMost(pull)
                    pull -= used
                    return Offset(0f, -used)
                }
                return Offset.Zero
            }

            /*
             * 关键的一条（2.7.8 修）：以前只靠下面那层 pointerInput 兜底，
             * 可列表一旦开始滚，它会先把拖动事件消费掉（`change.isConsumed` 为真），
             * 兜底那层就整个跳过 —— 真机上「下拉刷新没反应」就是这么来的。
             *
             * 这里接 onPostScroll：子列表消费完还剩多少（`available`）就归我们。
             * 列表已经在顶部还继续往下拖时，剩余量才是正的，正好是那个时机。
             */
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (currentEnabled && available.y > 0f && !currentRefreshing) {
                    pull = (pull + available.y * 0.55f).coerceAtMost(threshold * 1.55f)
                    /* 说「我吃掉了」，列表才不会同时起一段回弹 */
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentEnabled && pull > 0f) settle()
                return Velocity.Zero
            }
        }
    }

    /* 刷完了把指示器收回去 */
    LaunchedEffect(refreshing) {
        if (!refreshing && pull > 0f) animate(pull, 0f) { v, _ -> pull = v }
    }

    /* 进度上报：pull 一变就报一次（0~1），顶栏拿它画那段弧。
       用 SideEffect 不用 LaunchedEffect —— 后者每帧都要取消+重启一个协程，纯属浪费 */
    SideEffect { onPull(if (currentEnabled) (pull / threshold).coerceIn(0f, 1f) else 0f) }

    /*
     * ⚠️ 手势 Modifier **必须恒定**，不能按 enabled 增删（2.8.2 修的真机卡死）。
     *
     * 踩过两次：
     *   ① 2.8.1 写成 `if (!enabled) { Box { content() }; return }` —— 两个分支 content
     *      挂在不同深度，enabled 一变整棵子树重建，pager 的动画直接断。
     *   ② 改成 `val gestures = if (enabled) Modifier.nestedScroll(...) else Modifier`
     *      还是卡：手指一松，pager 正要起 fling，这时 enabled 翻成 false 把 nestedScroll
     *      从父链上摘掉 —— fling 的协程被取消，画面就停在「导航/设置各占半屏」不动了。
     *
     * 正解：nestedScroll 和 pointerInput 一直挂着，`enabled` 只在**回调内部**读。
     * 组合结构、Modifier 链都是恒定的，pager 的滚动和 fling 全程不受影响。
     */
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
                        if (currentEnabled) scope.launch { animate(pull, 0f) { v, _ -> pull = v } }
                    }
                ) { change, dy ->
                    if (!currentEnabled) return@detectVerticalDragGestures
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
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, if (currentEnabled) pull.roundToInt() else 0) }
        ) {
            /*
             * 关掉列表自带的「拉伸回弹」（Android 12+ 的 stretch overscroll）：
             * 它会把列表到顶后剩下的那段拖动**自己吃干**，`onPostScroll` 就只拿到 0，
             * 下拉刷新永远不触发。在需要下拉刷新的页面上，这段位移交给上面那颗指示器更值。
             */
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.LocalOverscrollConfiguration provides null
            ) {
                content()
            }
        }
    }
}

/* 2.8.1：页面中间那个转圈搬到顶栏（搜索框右边）去了，这里不再画指示器。
   下拉时内容整体下移就是反馈；弧和转圈由 TopBar 的 RefreshTail 负责。 */
