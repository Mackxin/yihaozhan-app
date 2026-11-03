package xyz.yihaozhan.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import xyz.yihaozhan.app.data.LinkItem
import xyz.yihaozhan.app.data.Local
import xyz.yihaozhan.app.data.Repo
import java.util.Calendar
import kotlin.random.Random

/**
 * 桌面小组件：今日三站 + 待读数。
 *
 * 为什么不用 Jetpack Glance：它是「用 Compose 写 RemoteViews」，要多带一套
 * compose-runtime + glance-appwidget；而这个方块只有三行字，用原生 RemoteViews
 * 就够了，零新依赖、离线也能编。
 *
 * 数据从**本地缓存**读（`Repo.loadCacheSync`），不联网 —— 小组件在桌面上被系统
 * 反复叫醒，每次去拉 300KB 的 json 是浪费电；缓存由 App 打开时刷新。
 */
class Widget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    companion object {
        /** 「今日三站」按日期取种子：同一天里怎么刷新都是这三站 */
        private fun picks(ctx: Context, n: Int): List<Pair<String, LinkItem>> {
            val data = Repo.loadCacheSync(ctx) ?: return emptyList()
            val all = data.cats.flatMap { c -> c.links.map { c.name to it } }
                .filter { it.second.url.isNotBlank() }
            if (all.isEmpty()) return emptyList()
            val cal = Calendar.getInstance()
            val seed = "" + cal.get(Calendar.YEAR) + cal.get(Calendar.DAY_OF_YEAR)
            val rnd = Random(seed.hashCode().toLong())
            val out = LinkedHashMap<String, Pair<String, LinkItem>>()
            var guard = 0
            while (out.size < n && guard < all.size * 3) {
                val one = all[rnd.nextInt(all.size)]
                out.putIfAbsent(one.second.url, one)
                guard++
            }
            return out.values.toList()
        }

        /** 打开 App 的某个页面；`url` 不为空时让 App 内预览这一个链接 */
        private fun open(ctx: Context, page: String? = null, url: String? = null): Intent {
            return Intent(ctx, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (page != null) putExtra("page", page)
                if (url != null) putExtra("open_url", url)
            }
        }

        private fun pending(ctx: Context, intent: Intent, code: Int): PendingIntent =
            PendingIntent.getActivity(
                ctx, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /**
         * 画一块。每次只读缓存 + 摆三行字，不做网络请求。
         * `widgetId` 参与 PendingIntent 的 requestCode，否则多个小组件会共用同一个点击意图。
         */
        fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_st)

            val sites = picks(context, 3)
            val rowIds = intArrayOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
            val iconIds = intArrayOf(R.id.w_icon1, R.id.w_icon2, R.id.w_icon3)
            val nameIds = intArrayOf(R.id.w_name1, R.id.w_name2, R.id.w_name3)
            val catIds = intArrayOf(R.id.w_cat1, R.id.w_cat2, R.id.w_cat3)

            rowIds.forEachIndexed { i, rowId ->
                val one = sites.getOrNull(i)
                if (one == null) {
                    rv.setViewVisibility(rowId, View.GONE)
                } else {
                    val (catName, link) = one
                    rv.setViewVisibility(rowId, View.VISIBLE)
                    rv.setTextViewText(iconIds[i], link.icon.ifBlank { link.name.take(1) })
                    rv.setTextViewText(nameIds[i], link.name)
                    rv.setTextViewText(catIds[i], catName)
                    rv.setOnClickPendingIntent(
                        rowId,
                        pending(
                            context,
                            open(context, url = link.url),
                            widgetId * 10 + i + 1
                        )
                    )
                }
            }

            /* 没数据（新装还没联网打开过）就明说，别让人盯着一个空方块猜 */
            if (sites.isEmpty()) {
                rv.setTextViewText(R.id.w_brand, "壹号栈")
                rv.setTextViewText(R.id.w_meta, "打开 App 同步一次")
            } else {
                rv.setTextViewText(R.id.w_brand, "壹号栈 · 今日三站")
                rv.setTextViewText(R.id.w_meta, "")
            }

            /* 底部那行：稍后读 / 收藏 的条数 —— 本机数据，SharedPreferences 里就有 */
            val local = runCatching { Local.of(context) }.getOrNull()
            val later = local?.later?.size ?: 0
            val fav = local?.fav?.size ?: 0
            val box = local?.inbox?.size ?: 0
            val foot = buildString {
                if (box > 0) append("待整理 ").append(box).append("  ·  ")
                append("稍后读 ").append(later)
                append("  ·  收藏 ").append(fav)
            }
            rv.setTextViewText(R.id.w_foot, foot)

            /* 点空白处（整块）就是打开 App 首页 */
            rv.setOnClickPendingIntent(
                R.id.w_root,
                pending(context, open(context, page = "home"), widgetId * 10)
            )

            mgr.updateAppWidget(widgetId, rv)
        }

        /**
         * 让桌面上所有小组件重画一遍。App 里刷到新内容后调它 ——
         * 否则只能等系统那 30 分钟一次的 onUpdate，用户会觉得「小组件是死的」。
         */
        fun refresh(context: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, Widget::class.java))
                if (ids.isEmpty()) return
                val i = Intent(context, Widget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(i)
            }
        }
    }
}
