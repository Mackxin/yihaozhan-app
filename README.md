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
| 导航 | **两列瀑布流**，卡片高度跟着内容走；分类收在左上角**汉堡菜单**的抽屉里 |
| 关于 | 站点说明 + 同步状态 + 在浏览器打开网站 |
| 设置 | 右上角齿轮进入，见下表 |

### 设置里能改什么

| 分组 | 项 | 说明 |
|---|---|---|
| 外观 | 主题 | 跟随系统 / 浅色 / 深色，改完当场生效 |
| 阅读 | 正文字号 | 小 / 标准 / 大，文章正文和行高一起缩放，下面有实时预览 |
| 启动 | 打开 App 停在 | 五个页面里选一个，下次打开生效 |
| 内容更新 | 打开时自动拉最新 | 关掉后只在手动点「立即同步」时联网 |
| 存储 | 离线缓存 | 显示占用大小，可清除 |
| 关于 | 版本 / 数据来源 / 开源仓库 | 点后两项跳浏览器 |

顶部所有页面的标题、说明、小字都是**居中**的，齿轮固定在右上角。

## 四、代码结构

```
app/src/main/java/xyz/yihaozhan/app/
├── MainActivity.kt          底部五个 tab（自绘的 54dp 矮栏）+ 设置页开关 + 返回键处理
├── MainVm.kt                状态机：先读缓存再拉网络
├── data/
│   ├── AppData.kt           数据模型（kotlinx.serialization）
│   ├── Prefs.kt             设置项（SharedPreferences + Compose 状态）
│   └── Repo.kt              拉取 + 缓存读写 + 联网判断 + 清缓存
└── ui/
    ├── Theme.kt             浅色/深色配色，沉浸式状态栏
    ├── Markdown.kt          自己写的 Markdown 渲染（标题/列表/引用/代码/表格）
    ├── Common.kt            居中页头（带齿轮/汉堡位）、搜索框、标签、卡片、统计格
    ├── HomeScreen.kt        首页
    ├── SettingsScreen.kt    设置页
    └── Screens.kt           分享 / 文章 / 文章详情 / 导航（抽屉 + 瀑布流）/ 关于
```

技术栈：Kotlin 1.9.24 + Jetpack Compose（Material3）+ kotlinx.serialization + OkHttp。
没有引入导航库（几个 tab 用状态切换就够）、没有引入 Markdown 库（自己写了 200 行）。

底部栏没用 Material3 的 `NavigationBar`：它固定 80dp 高，五个 tab 显得占地方。
这里是自己拼的 54dp `Row`，图标 19dp、文字 10sp。

---

## 五、自己构建

需要 JDK 17 和 Android SDK（`local.properties` 里的 `sdk.dir` 指向 SDK 路径）。

```bash
./gradlew :app:assembleDebug      # 调试包 → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # 正式包 → app/build/outputs/apk/release/
```

正式包开了 R8 压缩（`isMinifyEnabled` + `isShrinkResources`），
从 15 MB 压到 **1.1 MB**。序列化库的 keep 规则在 `app/proguard-rules.pro` 里，
新增 `@Serializable` 类不用改规则（已经整包 keep 了 `data` 包）。

装到已连上的手机/模拟器：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n xyz.yihaozhan.app/.MainActivity
```
