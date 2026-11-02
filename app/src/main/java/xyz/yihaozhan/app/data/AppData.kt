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
    val links: List<SiteLink> = emptyList(),
    /* 顶栏各块显隐（站点后台「站点信息 → 顶栏显示」）。
       老 app.json 没这个字段 → 用默认 TopCfg()，全开，跟以前一模一样 */
    val top: TopCfg = TopCfg(),
    /* 导航页三个显示开关（站点后台「站点信息 → 显示项」）。
       默认全关 —— 站点那边默认也是「卡片只留名字」 */
    val nav: NavCfg = NavCfg(),
    /* 外观：站点后台「主题」页选的那套色。null = 老 json，用 App 内置那套 */
    val theme: ThemeCfg? = null,
    /* 首页要用的几块（今日精选 / 赞助 / 建站日期）。null = 老 json */
    val home: HomeCfg? = null,
    /* 关于这一页。null = 老 json */
    val about: AboutCfg? = null,
    /* 设置 → 版本更新：每个版本改了什么（站点仓库 data/changelog.json 烘过来的）。
       老 json 没这段 → 空列表，设置里那一项自动隐藏，不会点进去一片空白 */
    val changelog: List<VersionLog> = emptyList(),
    /* 设置 → 使用手册：Markdown 正文（站点仓库 data/help.md）。
       空 = 没有手册，入口不画 */
    val help: String = "",
    /* 站点的阅读偏好（后台「阅读体验」）：字号 / 行距 / 字体。
       只当**默认值**用 —— 用户在自己手机上改过就以本机设置为准 */
    val read: ReadCfg? = null
) {
    val isEmpty: Boolean get() = feed.isEmpty() && posts.isEmpty() && cats.isEmpty()
}

/**
 * 一条版本记录。`version` 是 2.6.4 这种，`code` 是 versionCode，
 * `items` 就是那一版改动的几条要点（纯文本，App 端加个圆点前缀就是列表）。
 */
@Serializable
data class VersionLog(
    val code: Int = 0,
    val version: String = "",
    val date: String = "",
    val items: List<String> = emptyList()
)

/** 导航页卡片上显示什么：左侧彩色图标 / 名字下那行介绍 / 分类名前的 emoji */
@Serializable
data class NavCfg(
    val icons: Boolean = false,
    val desc: Boolean = false,
    val catIcon: Boolean = false,
    /* 分类名后面的站点数（👗 次元壁垒 96 那个 96）。站点后台可以关，这里默认开 */
    val count: Boolean = true
)

/**
 * 站点的外观配置，值都是 CSS 里读出来的**原样字符串**（`#7c9cff`、`rgba(124,156,255,.055)`、`26px`）。
 *
 * 为什么不在这里解析成颜色：主题这份数据是「别人（站点后台）说了算」的，
 * 一旦格式变了、某个字段空了，解析失败不能把整个 App 弄崩 ——
 * 所以原样传，由 UI 层的 `sitePal()` 兜底：认不出来就用内置那套。
 */
@Serializable
data class ThemeCfg(
    val accent: String = "",
    val accentColor: String = "",
    val glow: String = "normal",     // strong | normal | off
    val radius: String = "normal",   // small | normal | large
    val light: PalCfg = PalCfg(),
    val dark: PalCfg = PalCfg()
)

@Serializable
data class PalCfg(
    val bg: String = "",
    val bgSoft: String = "",
    val glass: String = "",
    val glass2: String = "",
    val glassHi: String = "",
    val text: String = "",
    val dim: String = "",
    val mute: String = "",
    val a1: String = "",
    val a2: String = "",
    val a3: String = "",
    val inkA1: String = "",
    val inkA2: String = "",
    val inkA3: String = "",
    val star: String = "",
    val rSm: String = "",
    val rMd: String = "",
    val rLg: String = "",
    val rXl: String = ""
)

/** 首页那几块：建站日期（算成长天数）、今日精选、赞助 */
@Serializable
data class HomeCfg(
    val startDate: String = "",
    val featuredTitle: String = "今日精选",
    val featuredDesc: String = "",
    val featuredCount: Int = 6,
    /* 指定了哪几条（`日期|标题` 这种 key）；空 = 自动取分享里最新的几条 */
    val featuredPicks: List<String> = emptyList(),
    val sponsorOn: Boolean = true,
    val sponsorTitle: String = "",
    val sponsorDesc: String = "",
    val sponsorNote: String = "",
    val sponsorLink: String = "",
    /* 收款码（后台「首页内容 → 赞助 → 收款码」，最多几张）。
       老 json 没这段 → 空列表，赞助卡只显示文案，跟以前一样 */
    val sponsorCodes: List<SponsorCode> = emptyList()
)

/** 站点的阅读偏好（网页端 `read` 那份配置的只读副本） */
@Serializable
data class ReadCfg(
    val size: Int = 16,          // 正文字号 px（14–20）
    val line: Float = 1.95f,     // 行高倍率（1.6–2.2）
    val font: String = "sans",   // sans | serif
    val toc: Boolean = true
)

/** 一张收款码：src 可能是 data URL（后台直接上传的图），也可能是站点相对路径 */
@Serializable
data class SponsorCode(
    val src: String = "",
    val label: String = "",
    val link: String = ""
)

/** 关于这一页：正文是 Markdown，App 用自己那个渲染器画 */
@Serializable
data class AboutCfg(
    val title: String = "",
    val lede: String = "",
    val updated: String = "",
    val md: String = ""
)

/**
 * 顶栏六块的显隐开关。默认全 true —— 缺字段、老数据、后台没动过，
 * 都是「以前那个样子的顶栏」。
 */
@Serializable
data class TopCfg(
    val brand: Boolean = true,
    val links: Boolean = true,
    val search: Boolean = true,
    val filter: Boolean = true,
    val saved: Boolean = true,
    val theme: Boolean = true
) {
    /** 品牌行整条都没东西可画时（品牌、入口、稍后读、主题全关），这行直接不占高度 */
    val brandRowVisible: Boolean get() = brand || links || saved || theme
    /** 搜索行同理（搜索条 + 筛选胶囊都关了就不留空档） */
    val searchRowVisible: Boolean get() = search || filter
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
    val desc: String = "",
    /* 站点后台给单个站点设的「置顶」：网页端 sortPinned() 用，App 排序照样跟 */
    val pin: Boolean = false
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
    val body: String = "",
    /* 站点后台的「置顶」：置顶的先排前面，其余按日期倒序 */
    val pin: Boolean = false,
    /* 草稿只在后台可见。新 json 已经在生成时滤掉，这里留着兜旧缓存 */
    val draft: Boolean = false
)

/** 解析器：站点以后加字段也不能让 App 崩，所以 ignoreUnknownKeys */
val XYJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
