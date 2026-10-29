# 壹号栈 · 安卓 App

把 [mackxin.github.io/xingyu](https://mackxin.github.io/xingyu/) 上的全部内容装进手机：
**首页 / 分享 / 文章 / 导航 / 关于** 五个页面 + 一个设置页，原生 Compose 界面，不套网页壳。

打开就是最新的，没网也能看。

---

## 一、装到手机上（三步）

1. 手机浏览器打开下载页，点最新版的 `yihaozhan-*.apk`
   👉 https://github.com/Mackxin/yihaozhan-app/releases/latest
2. 系统提示「**禁止安装未知应用**」时，点「设置」→ 允许这个浏览器安装 → 返回继续
3. 装完桌面会出现「壹号栈」图标，形状是三条从短到长的横杠

> 支持 Android 7.0（API 24）及以上。
> 这个包用调试证书签名（个人自用，不上应用商店），所以每次覆盖安装新版本都不用卸载旧的。

---

## 二、内容怎么更新

App 自己不生产内容，只负责展示。内容都在**站点仓库**里，链路是这样：

```
站点后台改内容
   ↓
node tools/gen-app-json.js          # 在星屿仓库里跑，把内容烘成 api/app.json
   ↓
git push                            # 推到 GitHub Pages
   ↓
用户下次打开 App → 自动拉到新数据（不用点任何按钮）
```

**只有一件事要记住：站点内容改完之后，必须重跑一次 `gen-app-json.js` 并推送。**
不然 App 里看到的还是上一版。

命令（在星屿站点仓库根目录）：

```bash
node tools/gen-app-json.js            # 生成 api/app.json
node tools/gen-app-json.js --check    # 只看跟线上差了什么，不写文件
git add api/app.json && git commit -m "更新 App 数据" && git push
```

### 打开就是最新的，靠的是这个机制

| 环节 | 做什么 |
|---|---|
| 冷启动瞬间 | 先读手机本地缓存 → **秒开**，不转圈 |
| 同时后台 | 带 `?t=当前时间戳` 去拉 `api/app.json`，绕过 CDN 缓存 |
| 拉到新版 | 覆盖本地缓存 + 刷新界面 |
| 拉不到（没网 / 超时） | 静默留在缓存那一份，「关于」页打上「离线」标 |

数据源地址写在 `app/src/main/java/xyz/yihaozhan/app/data/Repo.kt` 的 `URL` 常量，
换成自己的站点只要改这一行。

---

## 三、界面说明

| 页面 | 里面是什么 |
|---|---|
| 首页 | 站名 / 标语 / 简介居中，三格统计，两枚快捷入口，最近分享 5 条 + 最新文章 2 篇 |
| 分享 | 按日期分组，顶部搜索 + 标签筛选 |
| 文章 | 文章列表；点进去是正文（Markdown：标题/列表/引用/代码/表格） |
| 导航 | **两列瀑布流**，卡片高度跟着内容走；分类收在左上角**汉堡菜单**的浮层里，一行两个胶囊、只上下滚 |
| 关于 | 站点说明 + 同步状态 + 在浏览器打开网站 |
| 设置 | 右上角齿轮进入，见下表 |

**左右滑动翻页**：五个页签按底栏顺序排成一条横向的轨道，往左滑是下一页、往右滑是上一页，
底栏高亮跟着动；也可以直接点底栏跳过去。滑动用的是 `HorizontalPager`，前后各预取一页，
所以不会滑到一半才开始搭页面。

### 设置里能改什么

| 分组 | 项 | 说明 |
|---|---|---|
| 外观 | 主题 | 跟随系统 / 浅色 / 深色，改完当场生效 |
| 阅读 | 正文字号 | 小 / 标准 / 大，文章正文和行高一起缩放，下面有实时预览 |
| 启动 | 打开 App 停在 | 五个页面里选一个，下次打开生效 |
| 底部标签栏 | 显示图标 | **默认关**：只留文字时药丸矮一截，更不抢内容 |
| 底部标签栏 | 排序与显示 | 上下箭头调顺序，右侧开关决定显示或隐藏；**至少留一个**，最后一个的开关会锁住 |
| 内容更新 | 打开时自动拉最新 | 关掉后只在手动点「立即同步」时联网 |
| 存储 | 离线缓存 | 显示占用大小，可清除 |
| 关于 | 版本 / 数据来源 / 开源仓库 | 点后两项跳浏览器 |

顶部所有页面的标题、说明、小字都是**居中**的，齿轮固定在右上角。

## 三点五、链接在 App 里打开

导航里点任何一条站点，不会跳出去开浏览器，而是**当前页面内就地打开**（WebView）：

- 顶栏：左边返回、中间网页标题 + 域名、右边「在浏览器打开」
- 返回键**先退网页里的历史**，退到头才关掉预览层 —— 不然点进二级页按返回会直接回列表，前面的路白走了
- 网页和 App 用的是同一个极光背景，不会突然出现一块刺眼的白
- 打不开时（域名挂了 / 没网 / 15 秒还没响应）盖掉 WebView 那张英文白屏，
  换成一句中文 + 「重试」+「在浏览器打开」

`AndroidManifest.xml` 里开了 `usesCleartextTraffic="true"`：
站点数据里不少老链接还是 `http://`，Android 9 以后默认禁明文，不开就是 `ERR_CLEARTEXT_NOT_PERMITTED` 一片红。

## 四、外观：照抄站点手机版

App 不是一套自创的 Material 皮肤，而是把站点 `assets/css/style.css` 里的设计变量搬了过来
（见 `ui/Theme.kt` 的 `XyPal`）。改外观请先改站点那套变量，再同步这里，别单独发明颜色。

| 站点变量 | App 里 | 深色 | 浅色 |
|---|---|---|---|
| `--bg` | `pal.bg` | `#05060B` | `#E8ECF7` |
| `--glass` | `pal.glass`（卡片底） | `#0C0E18` | `#DBE3F5` |
| `--text` | `pal.text` | `#EEF1F8` | `#0D1424` |
| `--text-dim` | `pal.dim` | `#A7B0C4` | `#333D57` |
| `--text-mute` | `pal.mute` | `#7B8496` | `#4E5878` |
| `--a1/a2/a3` | `XY_A1/A2/A3`（渐变） | `#7C9CFF` `#B47CFF` `#4FE3D0` | 同值 |
| `--ink-a1` | `pal.inkA1`（文字用强调色） | `#7C9CFF` | `#3B51B8` |
| `--r-sm / --r / --r-lg` | `XyR.sm/md/lg` | 12 / 18 / 26 dp | 同值 |

三条硬规矩（跟站点一致，别破坏）：

1. **不用纯白**。卡片是「极淡的品牌蓝玻璃」，浅色主题也比页面底色略深一点点。
2. **强调色分两套**：`XY_A1~A3` 只用于渐变和图形；当**文字/图标**颜色时一律用 `inkAN`，
   浅色主题下把 `#7C9CFF` 直接当字色只有 2.2:1，等于隐形。
3. **不用阴影、不用描边**，靠间距和留白分组；圆角只用三档。
   渐变底上写字用深墨 `XY_INK`（`#080A12`），白字压在最浅的青色上只有 1.59:1。

另外两处「站点感」是画出来的：

- **极光背景**：`AuroraBackground` 用三个径向渐变画站点 `.bg__orb` 那三团光（左上蓝、右上紫、底部青），浅色主题整体减淡到 62%。
  **它是烘成一张 1/4 分辨率的小图再拉伸的**，不是每帧现算三个全屏渐变 —— 滑页时这层会整块重绘，
  现算就是掉帧主因（模拟器实测 p50 从 44ms 降到 32ms）；径向渐变是低频信息，缩到 1/4 再放回去肉眼看不出差别。
- **悬浮药丸底栏**：站点手机端 `.dock` 是一颗浮起来的胶囊，App 也做成胶囊条，不是通到底的横栏。
  图标默认不显示（只留文字时 ~44dp 高），显示/顺序/显隐都在设置里改，**只剩一个时药丸自动收成居中小胶囊**。

## 五、代码结构

```
app/src/main/java/xyz/yihaozhan/app/
├── MainActivity.kt          横滑翻页容器 + 悬浮药丸底栏（可排序列/显隐）+ 返回键分层
├── MainVm.kt                状态机：先读缓存再拉网络
├── data/
│   ├── AppData.kt           数据模型（kotlinx.serialization）
│   ├── Prefs.kt             设置项（SharedPreferences + Compose 状态，含底栏顺序/显隐）
│   └── Repo.kt              拉取 + 缓存读写 + 联网判断 + 清缓存
└── ui/
    ├── Theme.kt             配色（照站点 CSS 变量）+ 极光背景（烘成位图）+ 沉浸式状态栏
    ├── Markdown.kt          自己写的 Markdown 渲染（标题/列表/引用/代码/表格）
    ├── Common.kt            居中页头（带齿轮/汉堡位）、搜索框、标签、卡片、统计格
    ├── HomeScreen.kt        首页
    ├── SettingsScreen.kt    设置页
    ├── WebScreen.kt         App 内网页预览（顶栏 + WebView + 打不开的中文提示页）
    └── Screens.kt           分享 / 文章 / 文章详情 / 导航（分类浮层 + 瀑布流）/ 关于
```

技术栈：Kotlin 1.9.24 + Jetpack Compose（Material3）+ kotlinx.serialization + OkHttp。
没有引入导航库（几个 tab 用状态切换就够）、没有引入 Markdown 库（自己写了 200 行）。

底部栏没用 Material3 的 `NavigationBar`：它固定 80dp 高，五个 tab 显得占地方，也不能自定义顺序。
这里是自己拼的悬浮胶囊 `Row`，图标 19dp、文字 10.5~12.5sp。

导航页的分类也没用 `ModalNavigationDrawer`：它默认**整屏横向拖动都能把抽屉拽出来**，
和左右滑动翻页抢手势（设 `gesturesEnabled = false` 仍会误开）。所以改成自制浮层
（`Box` 遮罩 + `Surface` + `FlowRow`），手势互不干扰。

---

## 六、自己构建

需要 JDK 17 和 Android SDK（`local.properties` 里的 `sdk.dir` 指向 SDK 路径）。

```bash
./gradlew :app:assembleDebug      # 调试包 → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # 正式包 → app/build/outputs/apk/release/
```

正式包开了 R8 压缩（`isMinifyEnabled` + `isShrinkResources`），
从 15 MB 压到 **1.2 MB**。序列化库的 keep 规则在 `app/proguard-rules.pro` 里，
新增 `@Serializable` 类不用改规则（已经整包 keep 了 `data` 包）。

装到已连上的手机/模拟器：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n xyz.yihaozhan.app/.MainActivity
```
