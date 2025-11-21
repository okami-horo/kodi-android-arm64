• 以下方案将弹幕“核心处理与渲染”内置到项目（DFM 集成），而“数据来源”通过插件化扩展提供。核心先落地，数据源随后以插件补充（示例：集成 DanDanPlayForAndroid 中的 DanDanPlay 弹幕 API 作为独立数据源插件）。目标依旧是“最少改动、最快落地、arm64 友好”。

  设计目标

  - 内置 DFM 核心：时间同步、渲染配置、生命周期、过滤器与最小注入 API。
  - 插件化数据源：通过标准 SPI 发现/装配数据源，实现可插拔的“弹幕抓取与解析”。
  - 解耦播放器：仅做“播放状态 → 弹幕引擎”的单向联动映射，不侵入播放器核心。
  - 性能优先：纯 Java AAR，先不引入 ndkbitmap，后续可选按需优化。

  总体方案（Core + Plugin）

  - Core（本仓库内置）
      - 渲染：DFM `DanmakuView/DanmakuSurfaceView` + `DanmakuContext`，统一封装成 `DanmakuOverlayView` 与 `DanmakuEngine`。
      - 时间同步：从播放器回调获取 positionMs，负责 start/pause/seek 同步控制。
      - 过滤与样式：支持字号、速度、透明度、描边、同屏上限、行数上限、关键字过滤等。
      - 数据接口：定义 `DanmakuSourcePlugin` SPI（见下）与标准模型 `DanmakuItem`。
  - Plugin（独立模块/外部 AAR）
      - 实现 `DanmakuSourcePlugin`，对接各站/协议/本地文件。
      - 以 Android ContentProvider + meta-data 注册，核心运行时利用 `PackageManager` 发现并装配。
      - 示例插件：DanDanPlay 数据源（使用 DanDanPlayForAndroid 中已有 API 封装）。

  依赖与模块

  - 在 `xbmc/build.gradle` 增加：
      - repositories { mavenCentral() }
      - dependencies { implementation "com.github.ctiao:dfm:0.9.25" }
  - 暂不引入 ndkbitmap，避免 arm64 ABI/维护成本。若后续需要，可单独编译并下发至 `xbmc/lib/arm64-v8a/`。

  核心视图与引擎（Core）

  - 新增包装视图 `DanmakuOverlayView`（继承 `DanmakuView` 或 `DanmakuSurfaceView`），建议路径：
      - `xbmc/src/main/java/org/xbmc/kodi/danmaku/DanmakuOverlayView.java`
      - 初始化 `DanmakuContext`：
          - 样式：`setDanmakuStyle(描边, 粗细)`、`setScaleTextSize(...)`、`setScrollSpeedFactor(...)`、`setDanmakuTransparency(...)`
          - 性能：`preventOverlapping(...)`、`enableDanmakuDrawingCache(true)`、`setMaximumVisibleSizeInScreen(...)`、`setMaximumLines(...)`
          - 刷新：`DrawHandler.updateMethod = 2`
      - 生命周期：`onPrepared()->start()`、`onPause()->pause()`、`onResume()->resume()`、`onDestroy()->release()`
      - 注入：`addTextDanmaku(String text, int color, long delayMs)`
  - 覆盖到视频层：
      - `SurfaceView` 播放内核：优先用 `DanmakuSurfaceView` 并 `setZOrderMediaOverlay(true)` 或 `setZOrderOnTop(true)`
      - `TextureView`/普通 View：使用 `DanmakuView`
      - 在播放器根容器（FrameLayout）动态 `addView(overlay, MATCH_PARENT, MATCH_PARENT)`
  - 引擎 `DanmakuEngine`：
      - 负责装配/调度：管理 `DanmakuOverlayView`、`DanmakuContext`、数据源插件、缓存和注入节流
      - 提供 API：`bindPlayer(controls)`、`loadSources(mediaInfo, hints)`、`seekTo(...)`、`updateSpeed(...)`、`toggleVisibility(...)`

  播放器联动（保持最小映射）

  - 播放控制映射：
      - play/resume → `engine.resume()`（首次 `onPrepared` 后 `engine.start()`）
      - pause/buffering → `engine.pause()`
      - seekTo(positionMs) → `engine.seekTo(positionMs + danmakuOffsetMs)`
      - release/stop → `engine.release()`
      - 倍速（可选）：仅在验证同步 OK 的内核上开启（Exo 一般可，IJK 需额外校验）
  - 首帧/缓冲结束后再 `start()`，确保与视频时基对齐。

  数据源插件（SPI）

  - 接口（Java 草案，位于 `org.xbmc.kodi.danmaku.source`）：
      - `interface DanmakuSourcePlugin {`  
        `  String id();`  // 唯一标识  
        `  String displayName();`  
        `  boolean supports(MediaInfo media, Map<String, Object> hints);`  
        `  DanmakuSession open(MediaInfo media, Map<String, Object> hints);`  
        `}`
      - `final class DanmakuItem { long timeMs; int color; float textSizeSp; int type; String text; }`
      - `interface DanmakuSession {`  
        `  /** 一次性或分片拉取，返回标准化弹幕条目 */`  
        `  List<DanmakuItem> loadRange(long fromMs, long toMs);`  
        `  void close();`  
        `}`
  - 发现与装配（Android 推荐方案）：
      - 插件以 `ContentProvider` 注册，并在 `<provider>` 节点 `meta-data` 提供 `org.xbmc.kodi.danmaku.PLUGIN`，值指向实现类全名或 XML。
      - 核心用 `PackageManager` 查询含该 `meta-data` 的 Provider，反射构造 `DanmakuSourcePlugin` 完成装配。
      - 兼容方案：同时支持 `META-INF/services/...`（`ServiceLoader`）以便非 Android 宿主复用。
  - 统一模型：插件输出 `DanmakuItem` 列表，Core 负责转换为 DFM 的 `BaseDanmaku` 并注入/预缓冲。

  示例插件：DanDanPlay 数据源（独立实现）

  - 复用 DanDanPlayForAndroid 中既有 API 封装（匹配/获取弹幕列表），在插件内部完成：
      - `supports(media)`：利用标题、剧集、文件名/哈希等做匹配
      - `open(media)`：建立会话并首次拉取关键时间窗（如 [t, t+N]）
      - `loadRange(from,to)`：按范围拉取在线弹幕并转换为 `DanmakuItem`
  - 认证/限流：遵循官方 API 要求（UA/Token/频率），由插件自管；Core 只消费标准模型。
  - 交付：作为独立 AAR/模块发布，引入时自动装配，无插件时 Core 仅保留“无数据源 + 动态注入”能力。

  缓存与性能

  - 预取策略：按播放进度滚动窗口拉取（如 ±60s），Seek 后丢弃过期窗口并刷新。
  - 缓存：内存（LRU by timeMs）+ 可选磁盘（简易 JSON/LiteDB），TTL 由插件/站点建议决定。
  - DFM 侧优化：限制同屏数量/行数，小窗时按比例缩小字号与描边；必要时关闭高成本特效。
  - 混淆：建议 keep `master.flame.danmaku.**` 与核心 `org.xbmc.kodi.danmaku.**` 接口。

  验收与回退

  - 构建调试包：`./gradlew :xbmc:assembleDebug -x lint`
  - 核心验收：
      - 覆盖层可见；动态注入文字弹幕正常滚动
      - 播放/暂停/Seek 同步；旋转/前后台可恢复
  - 插件验收（引入 DanDanPlay 插件后）：
      - 根据媒体信息自动匹配并拉取弹幕；在播放窗口内滚动渲染
  - 回退：如覆盖异常（看不到弹幕），切换 `DanmakuView`/`DanmakuSurfaceView` 并尝试 `setZOrderMediaOverlay(true)`。

  实施步骤清单

  - 阶段 A（核心落地）
      - Gradle 依赖：新增 MavenCentral + `implementation "com.github.ctiao:dfm:0.9.25"`
      - 视图/引擎：实现 `DanmakuOverlayView` 与 `DanmakuEngine`
      - 播放器联动：映射 play/pause/seek/release；加入 1 条延时注入的测试弹幕
      - 配置面板（可选）：字号、速度、透明度、最大同屏/行数、偏移
  - 阶段 B（插件 SPI）
      - 定义 `DanmakuSourcePlugin`/`DanmakuSession`/`DanmakuItem` 接口与数据模型
      - 实现 Provider + meta-data 的插件发现与装配逻辑
      - 核心完成统一转换与注入路径（List<DanmakuItem> → BaseDanmaku）
  - 阶段 C（首个插件：DanDanPlay）
      - 独立模块/仓库创建插件，实现 `supports/open/loadRange`
      - 复用 DanDanPlayForAndroid 的 API 客户端；加入基础重试与限流
      - 验收：普通视频源可匹配拉取并渲染

  后续增强

  - B 站 XML/ASS 本地文件解析器（作为插件提供）
  - 过滤器系统：关键词/正则/用户屏蔽表；多语种/简繁转换
  - 离线缓存与预下载；倍速同步下的弹幕时间缩放
  - 质量/稳定性：UT 覆盖数据模型转换；集成测试覆盖 Seek/旋转/前后台场景
