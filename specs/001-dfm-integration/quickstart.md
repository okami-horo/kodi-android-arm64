# Quickstart: DFM 弹幕集成（Phase 1）

## 环境
- Java 17、AGP 8.11.1、Android SDK platform 36、build-tools 36.0.0
- 可选：`GRADLE_USER_HOME=$(pwd)/.gradle-user` 避免全局 init.gradle 干扰

## 构建
```bash
cd /workspace/kodi-android-arm64
./gradlew :xbmc:assembleDebug -x lint                  # 主线构建（vanilla + flavor 聚合）
./gradlew :xbmc:assembleDfmExperimentalDebug -x lint   # 弹幕实验变体
./gradlew :xbmc:assembleVanillaDebug -x lint           # 上游一致变体（默认）
./gradlew :xbmc:testDfmExperimentalDebugUnitTest       # 变体专属单测
# 或
make apk
```
- dfmExperimental APK: `xbmc/build/outputs/apk/dfmExperimental/debug/xbmc-dfmExperimental-debug.apk`
- vanilla APK: `xbmc/build/outputs/apk/vanilla/debug/xbmc-vanilla-debug.apk`

## 源集与目录约定
- 主源集仅使用 `xbmc/src/main/java`（及对应 `res`/`AndroidManifest.xml`），禁止挂载平行 `xbmc/java/` 或整棵 `src/`。
- `dfmExperimental` 专用代码与资源放在 `xbmc/src/dfmExperimental/java`、`xbmc/src/dfmExperimental/res`，主源集显式排除 `dfmExperimental/**` 与 `dfmExperimentalDebugUnitTest/**`：
  - 引擎与模型：`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/**`
  - 播放集成与设置页：`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/app/**`
  - UI/OSD 控件：`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/ui/**`，资源在 `xbmc/src/dfmExperimental/res/**`
- 变体单测：仅使用 `xbmc/src/dfmExperimentalDebugUnitTest/java`，不要使用 `src/test` 或 `src/dfmExperimentalTest` 路径，以避免被主编译拾取。

## 集成结构概览

代码已按下列路径完成接线，通常不需要再改动，只需按需扩展：

1) Gradle 依赖与变体（`xbmc/build.gradle`）
   - 插件与版本：AGP 8.11.1，Java 17 toolchain，`flavorDimensions "features"`。
   - 变体：
     - `vanilla`：`BuildConfig.DANMAKU_ENABLED = false`，不包含弹幕 UI/依赖。
     - `dfmExperimental`：`BuildConfig.DANMAKU_ENABLED = true`，启用 DFM 集成。
   - 依赖：
     - `dfmExperimentalImplementation "com.github.ctiao:dfm:0.9.25"`
     - `dfmExperimentalImplementation "androidx.preference:preference:1.2.1"`
     - `implementation "androidx.appcompat:appcompat:1.7.0"`

2) 引擎与数据模型（`org.xbmc.kodi.danmaku`）
   - 渲染封装：`DanmakuOverlayView` 封装 DFM `DanmakuView`，负责将标准 `DanmakuItem` 转成 DFM `BaseDanmaku` 并应用样式/透明度/行数。
   - 引擎：`DanmakuEngine` 负责：
     - 轨缓存与选择（`DanmakuTrack`/`TrackCandidate`）；
     - 播放时钟对齐（基于 `PlaybackClock`、`MediaSessionClock`、`SoftClock`）；
     - 窗口化加载与限流（前后各 60s 窗口 + throttle）；
     - 关键字/类型过滤、同屏数量控制、时间偏移等逻辑；
     - 错误分类与性能采样（写入 `xbmc/build/reports/danmaku/perf/last-run.json`）。
   - 存储：`DanmakuPreferences` 以 `MediaKey(path|size|mtime)` 为 key 持久化“最近弹幕轨”和 per-media 配置；缺省配置由设置页通过 `saveDefaultConfig` 写入。
   - 本地文件支持：
     - `LocalTrackDiscovery` 在当前视频目录扫描 `.xml` 并按同名/前缀等规则打分。
     - `BiliXmlParser` 将 B 站 XML `<d>` 条目映射为标准 `DanmakuItem`。
   - API 适配：`DanmakuApi` 暴露 `listTracks/selectTrack/setVisibility/updateConfig/seek/updateSpeed/status` 等内部契约，并尊重 `BuildConfig.DANMAKU_ENABLED`。

3) 播放器与 OSD 集成
   - Activity 层：
     - `Main`（`xbmc/src/org/xbmc/kodi/Main.java`）在 `onCreate()` 内通过 `DanmakuHooks.attach(this)` 将 `VideoLayout` 交给弹幕控制器；在 `doFrame()` 中每帧调用 `DanmakuController.onFrame()`。
     - `Main.onCreateOptionsMenu()` 在 dfmExperimental 下动态 inflate `menu_osd_danmaku`，并交给 `DanmakuHooks` 处理；`onOptionsItemSelected()` 同样委托给控制器。
   - MediaSession：
     - `XBMCMediaSession` 在 `updatePlaybackState(PlaybackState)` 与 `onSeekTo(long)` 中同步调用 `DanmakuHooks.dispatchPlaybackState/dispatchSeek`，将 `PlaybackState` 推送给 `DanmakuController` → `PlayerEventBridge` → `DanmakuEngine`。
   - 控制器：
     - `DanmakuController`（`org.xbmc.kodi.danmaku.app`）负责：
       - 创建 `DanmakuEngine` + `DanmakuOverlayView` 并挂载到 `Main` 的 `VideoLayout`;
       - 使用 `XBMCJsonRPC` 轮询 `Player.GetActivePlayers` / `Player.GetItem` 获取当前视频文件路径并构造 `MediaKey`；
       - 在 Choreographer 帧回调中调用 `engine.tick()` 维持时间对齐；
       - 暴露 OSD 菜单行为与屏幕右下角控制条按钮（切换开关、选择轨、调试注入、打开设置）。
   - OSD 菜单：
     - 菜单资源：`xbmc/src/dfmExperimental/res/menu/menu_osd_danmaku.xml`
       - `action_toggle_danmaku`（可勾选）——开启/关闭弹幕；
       - `action_select_danmaku_track`——弹出轨道选择列表；
       - `action_inject_danmaku`——注入 demo 弹幕（开发验证用）；
       - `action_open_danmaku_settings`——打开弹幕设置页。
     - 行为派发：`OsdActions` 将上述菜单项映射为 `DanmakuController` 的回调，统一控制引擎与 UI。

4) 设置页与偏好（`org.xbmc.kodi.danmaku.app`）
   - 设置 Activity：
     - `DanmakuSettingsActivity` 使用 AppCompat + `PreferenceFragmentCompat`，主题为 `DanmakuSettingsTheme`（深色 + ActionBar）。
     - Fragment 内直接加载 `R.xml.settings_danmaku`。
   - 配置存储：
     - `DanmakuSettingsStore` 持有 `danmaku_enabled`、`danmaku_text_scale`、`danmaku_speed`、`danmaku_alpha`、`danmaku_max_on_screen`、`danmaku_keyword_filter`、各类型开关与 `danmaku_offset` 等键；
     - 提供 `buildConfig(SharedPreferences)` 将 UI 值映射为 `DanmakuConfig`；
     - `DanmakuController` 注册 `SharedPreferences` 监听：偏好变更 → 构建新的 `DanmakuConfig` → `engine.updateConfig(config)`，同时更新全局默认配置。

## 使用说明（dfmExperimental 变体）

### 启动与自动发现

1. 构建并安装 dfmExperimental 包：
   ```bash
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :xbmc:assembleDfmExperimentalDebug -x lint
   adb install -r xbmc/build/outputs/apk/dfmExperimental/debug/xbmc-dfmExperimental-debug.apk
   ```
2. 在设备上打开 Kodi Android，播放一个本地视频，并在同一目录准备至少一个 `.xml` 弹幕文件：
   - 推荐：与视频同名的 `.xml`（例如 `video.mkv` 对应 `video.xml`），以获得最高匹配分数。
3. 播放开始后：
   - 引擎会在同目录扫描 `.xml`，生成候选 `DanmakuTrack` 列表并自动选择最优轨；
   - 在正常硬件上，第一批弹幕应在 2 秒内出现（SC-001）。

### 基本控制方式

dfmExperimental 变体提供三种入口来控制弹幕：

1. 遥控器按键
   - `KEYCODE_CAPTIONS`：快速切换“弹幕开关”（相当于勾选/取消 OSD 菜单里的“Toggle danmaku”）。

2. OSD 菜单路径（推荐用于 TV 遥控操作）
   - 在播放器界面打开 Android options menu（具体按键取决于设备，一般为 MENU/⋮）：
     - `Toggle danmaku`：开启/关闭弹幕；勾选状态实时反映当前可见性；
     - `Select danmaku track`：弹出轨道选择对话框；若同目录存在多个 `.xml`，可从列表中选择；
     - `Inject demo danmaku`：注入内置示例弹幕，便于无外部文件时验证渲染与同步；
     - `Danmaku settings`：打开弹幕设置页面（见下一节）。

3. 右下角屏幕控制条（仅 dfmExperimental）
   - 控制条布局：`danmaku_controls_overlay.xml`，默认隐藏，在发现可用媒体时显示于右下角。
   - 含义（自左向右）：
     - 媒体标题：当前视频的 label/file 名称；
     - 眼睛图标：开启/关闭弹幕（等价于菜单 “Toggle danmaku”）；
     - 列表图标：打开轨道选择对话框；
     - 上传图标：注入 demo 弹幕；
     - 设置图标：打开弹幕设置页。

### 弹幕设置页（样式/密度/过滤）

入口：
- 播放时通过 OSD 菜单 `Danmaku settings`；
- 或点击右下角控制条中的“齿轮”图标。

设置项（`settings_danmaku.xml`）：
- 开关：
  - “Enable danmaku”：总开关，默认开启；关闭后会隐藏弹幕但不会影响字幕。
- 样式与密度：
  - “Text size”：弹幕文本整体缩放（50%–200%），对应 `DanmakuConfig.textScaleSp`；
  - “Scroll speed”：滚动速度相对于播放速度的比例（50%–200%），对应 `scrollSpeedFactor`；
  - “Opacity”：不透明度（20%–100%），对应 `alpha`；
  - “Max on screen”：同屏最大弹幕数量（0 表示使用引擎默认）。
- 过滤：
  - “Keyword filter”：逗号分隔的关键字列表；包含任一关键字的弹幕会被过滤；
  - “Allow scrolling/top/bottom/positioned”：分别控制四种弹幕类型是否允许显示。
- 时间对齐：
  - “Timing offset”：以毫秒为单位整体平移弹幕时间（负值提前，正值延后），用于手动对齐不同源文件。
- 可读性提示：
  - “Subtitle readability”：说明在字幕 + 弹幕同时开启时，可通过同屏数量/行数等设置减轻遮挡（不做自动遮挡或混合渲染）。

所有设置改动会立即写入 SharedPreferences，并通过 `DanmakuController` 推送到 `DanmakuEngine`，通常在 1 秒内生效。

### 轨道与手动文件选择

1. 自动发现
   - 当播放本地视频时，`LocalTrackDiscovery` 会在视频所在目录扫描 `.xml` 文件；
   - 优先级：与视频同名 > 以视频名为前缀/后缀 > 含视频名的文件名 > 其他 `.xml`；
   - 轨候选通过 `TrackSelectionDialog` 展示，包含文件名与匹配原因（同名/前缀/包含/扩展名匹配）。

2. 手动选择
   - 若没有发现候选轨，或者用户希望选择其他路径的文件，可以：
     - 打开 OSD 菜单 `Select danmaku track`（当列表为空时，会提示“未找到弹幕文件”，并提供“Choose file”按钮）；或
     - 直接使用控制条中的轨道按钮和随后出现的“选择文件”按钮。
   - 文档选择流程：
     - 系统会弹出标准 `ACTION_OPEN_DOCUMENT` 文件选择器，过滤为 `text/xml`；
     - 选择成功后，应用会将选中的 `Uri` 复制到应用私有目录 `files/danmaku/manual` 下并读取；
     - 解析成功后，该文件立即作为当前轨加载并同步到当前播放进度。

3. 调试注入
   - 任意时间可通过 OSD 菜单或控制条上的“注入”按钮调用 `DeveloperDanmakuInjector`：
     - 将向当前 MediaKey 对应的引擎绑定一组示例弹幕（包含滚动/顶部/底部等类型），便于调整样式和验证同步行为；
     - 适合在没有实际 `.xml` 文件时做快速 smoke test。

## 运行验证
- 打开本地视频，确保同目录存在同名 `.xml` 弹幕：
  - 首次开启弹幕后 2 秒内应看到弹幕（SC-001）。
- Seek：使用遥控或 OSD 控件进行快进/快退或拖动：
  - 在 2 秒内恢复到新位置对应的弹幕显示，且肉眼误差 ≤200ms（SC-002）。
- 切轨：在存在多条候选弹幕文件时，通过 OSD 或控制条选择不同轨：
  - 切换后 2 秒内恢复同步与显示（SC-008）；样式与过滤偏好保持一致。

## 可用性走查（SC-005）
- 安装 dfmExperimental APK，播放本地视频。
- 从以下任意入口完成“打开弹幕 + 切换轨 + 调整样式”三步：
  - OSD 路径：打开 options menu → `Toggle danmaku` → `Select danmaku track` → `Danmaku settings`；
  - 控制条路径：使用右下角控制条依次点击“眼睛/列表/齿轮”图标。
- 记录：
  - 是否能在 1 次尝试内找到入口并完成设置；
  - 是否理解“无弹幕文件/解析失败”时的提示行为；
  - 可选：截取 OSD 与设置路径截图（可放入 `docs/screenshots/danmaku-entry.png`），在 PR 模板粘贴走查结论与截图链接。

## 性能采样
- dfmExperimental 变体默认输出采样报告：`xbmc/build/reports/danmaku/perf/last-run.json`（准备耗时、窗口刷新与限流计数）。
- 重新验证前可清理该目录，运行播放或单测后检查最新报告；如目录不可写会自动降级为仅日志。

## 调试建议
- 使用 `Choreographer` 帧回调驱动渲染；日志输出 `clock.nowMs()` 与 DFM 的当前时间做对比
- 高倍速下可临时开启密度限流以观察流畅度

## 参考
- 研究说明：/workspace/kodi-android-arm64/specs/001-dfm-integration/research.md
- 契约（OpenAPI）：/workspace/kodi-android-arm64/specs/001-dfm-integration/contracts/danmaku-openapi.yaml
- 本地参考源码：/workspace/kodi-android-arm64/.tmp_dandan （仅参考实现思路）
