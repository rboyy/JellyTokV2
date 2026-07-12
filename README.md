# JellyTok

一款仿抖音交互的 Android 视频播放器，连接 [Jellyfin](https://jellyfin.org) 媒体服务器，让你在手机上以竖屏滑动的方式浏览和播放自己的媒体库内容。

## 功能特性

- **竖屏视频流** — 上下滑动切换视频，自动播放，和刷抖音一样的体验
- **手势控制** — 左右拖动调节播放进度，左侧上下滑动调节亮度，右侧上下滑动调节音量
- **双击点赞** — 双击屏幕收藏视频到 Jellyfin 收藏夹
- **横屏适配** — 横屏视频自动旋转并适配屏幕比例
- **服务器管理** — 支持添加、切换、删除多个 Jellyfin 服务器
- **收藏 & 文件夹** — 浏览收藏夹和媒体文件夹，支持分类浏览
- **搜索** — 按名称搜索媒体库内容
- **ExoPlayer 播放** — 基于 ExoPlayer 的高性能视频播放，支持多种视频格式

## 技术栈

- Kotlin
- ExoPlayer 2.17
- ViewPager2 + RecyclerView
- Jellyfin REST API
- ViewBinding
- Gradle (Groovy DSL)

## 构建

```bash
# 克隆项目
git clone https://github.com/rboyy/JellyTokV2.git

# 用 Android Studio 打开项目，或命令行构建
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17 和 Android SDK。首次打开时 Android Studio 会自动生成 `local.properties` 配置 SDK 路径。

## 使用

1. 启动应用后输入 Jellyfin 服务器地址（如 `http://192.168.1.100:8096`）
2. 输入用户名和密码登录
3. 开始上下滑动浏览媒体库中的视频

## 截图

| 服务器管理 | 视频播放 | 收藏 |
|:---:|:---:|:---:|
| ![server](docs/screenshots/server.png) | ![player](docs/screenshots/player.png) | ![favorites](docs/screenshots/favorites.png) |

## 致谢

- UI 交互基于 [TiktokApp](https://www.jianshu.com/p/f1f452abc328) 开源项目
- [Jellyfin](https://jellyfin.org) — 开源媒体服务器
- [ExoPlayer](https://exoplayer.dev) — Google 开源播放器

## License

MIT
