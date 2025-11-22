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
- `dfmExperimental` 专用代码与资源放在 `xbmc/src/dfmExperimental/java`、`xbmc/src/dfmExperimental/res`，主源集需显式排除 `dfmExperimental/**`。
- 变体单测仅放在 `xbmc/src/dfmExperimentalDebugUnitTest/java`，不要使用 `src/test` 或 `src/dfmExperimentalTest` 路径，以避免被主编译拾取。

## 集成步骤（最小路径）
1) Gradle 依赖（xbmc/build.gradle）：
   - repositories: `mavenCentral()`
   - dependencies: `implementation "com.github.ctiao:dfm:0.9.25"`
2) 新增包 `org.xbmc.kodi.danmaku`：
   - `DanmakuOverlayView` 封装 DFM 视图；层级：视频之上、OSD 之下
   - `DanmakuEngine` 负责装配/加载/注入；暴露 `bindPlayer/seekTo/updateSpeed/toggleVisibility`
   - `PlaybackClock` 基于 MediaSession/SoftClock 软对齐
3) 本地发现与解析：
   - `LocalTrackDiscovery` 在视频目录中扫描 `.xml`；
   - `BiliXmlParser` 将 B 站 XML 映射为标准 `DanmakuItem`
4) UI/控制：
   - 在播放页面根容器 `FrameLayout` 动态添加 `DanmakuOverlayView`
   - OSD/设置面板接入开关与参数；持久化键 `MediaKey`

## 运行验证
- 打开本地视频，确保同目录存在同名 `.xml` 弹幕
- 开启弹幕：首次 2s 内出现（SC-001）
- Seek：2s 内恢复到新位置且偏差 ≤200ms（SC-002）
- 切轨：2s 内恢复显示与同步（SC-008）

## 可用性走查（SC-005）
- 安装 dfmExperimental APK，播放本地视频。
- 通过 OSD 路径 `OSD > 弹幕` 切换开关，长按或二级菜单进入“选择弹幕轨”；在设置路径 `设置 > 播放器 > 弹幕` 调整样式/密度/过滤。
- 记录是否能在 1 次尝试内找到入口并完成设置；截取 OSD 与设置路径截图（可放入 `docs/screenshots/danmaku-entry.png`），在 PR 模板粘贴走查结论与截图链接。

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
