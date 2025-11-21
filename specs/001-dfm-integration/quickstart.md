# Quickstart: DFM 弹幕集成（Phase 1）

## 环境
- Java 17、AGP 8.11.1、Android SDK platform 36、build-tools 36.0.0
- 可选：`GRADLE_USER_HOME=$(pwd)/.gradle-user` 避免全局 init.gradle 干扰

## 构建
```bash
cd /workspace/kodi-android-arm64
./gradlew :xbmc:assembleDebug -x lint
# 或
make apk
```

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

## 调试建议
- 使用 `Choreographer` 帧回调驱动渲染；日志输出 `clock.nowMs()` 与 DFM 的当前时间做对比
- 高倍速下可临时开启密度限流以观察流畅度

## 参考
- 研究说明：/workspace/kodi-android-arm64/specs/001-dfm-integration/research.md
- 契约（OpenAPI）：/workspace/kodi-android-arm64/specs/001-dfm-integration/contracts/danmaku-openapi.yaml
- 本地参考源码：/workspace/kodi-android-arm64/.tmp_dandan （仅参考实现思路）
