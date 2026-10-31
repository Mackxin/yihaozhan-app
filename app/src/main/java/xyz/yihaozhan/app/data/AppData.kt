package xyz.yihaozhan.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * App 全部内容的形状 —— 对应站点仓库里 api/app.json（tools/gen-app-json.js 生成）。
 *
 * 为什么是「一个文件全量」而不是分页接口：站点是纯静态托管，没有后端。
 * 一个 JSON 全量拉下来（约 300KB）既能离线，也不会有「翻到一半要联网」的破口。
 */
@Serializable
data class AppPayload(
    val schema: Int = 1,
    val build: String = "",
    val updatedAt: String = "",
    val site: Site = Site(),
    val stats: Stats = Stats(),
    val cats: List<Cat> = emptyList(),
    val feed: List<FeedItem> = emptyList(),
    val posts: List<Post> = emptyList(),
    /* 顶栏那几颗自定义入口（站点后台「自定义链接」，最多 5 条）。
       老版本 app.json 没有这个字段 —— 默认空列表，顶栏就是「只有三颗固定键」的样子 */
    val links: List<SiteLink> = emptyList()
) {
    val isEmpty: Boolean get() = feed.isEmpty() && posts.isEmpty() && cats.isEmpty()
}

/**
 * 顶栏自定义入口。`url` 可能是站内路由（`#/idea` 这种原样保留），
 * 也可能是完整网址 —— App 里站内路由尽量跳原生页，剩下的交给内置浏览器。
 */
@Serializable
data class SiteLink(
    val icon: String = "",
    val name: String = "",
    val url: String = ""
)

@Serializable
data class Site(
    val name: String = "壹号栈",
    val slogan: String = "",
    val desc: String = "",
    val since: String = "",
    val url: String = ""
)

@Serializable
data class Stats(val cats: Int = 0, val links: Int = 0, val feed: Int = 0, val posts: Int = 0)

@Serializable
data class Cat(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val desc: String = "",
    val links: List<LinkItem> = emptyList()
)

@Serializable
data class LinkItem(
    val name: String = "",
    val url: String = "",
    val icon: String = "",
    val desc: String = ""
)

@Serializable
data class FeedItem(
    val id: String = "",
    val date: String = "",
    val icon: String = "",
    val title: String = "",
    val url: String = "",
    val desc: String = "",
    val tags: List<String> = emptyList()
)

@Serializable
data class Post(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val tags: List<String> = emptyList(),
    val excerpt: String = "",
    val body: String = ""
)

/** 解析器：站点以后加字段也不能让 App 崩，所以 ignoreUnknownKeys */
val XYJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
