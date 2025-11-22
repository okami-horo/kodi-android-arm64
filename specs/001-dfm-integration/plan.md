# Implementation Plan: DFM 弹幕集成

**Branch**: `001-dfm-integration` | **Date**: 2025-11-21 | **Spec**: /workspace/kodi-android-arm64/specs/001-dfm-integration/spec.md
**Input**: Feature specification from `/workspace/kodi-android-arm64/specs/001-dfm-integration/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

- 目标：在 `xbmc` Android 模块集成 DFM（DanmakuFlameMaster）弹幕覆盖层，支持本地 B 站 XML 弹幕，提供轨道发现/选择、样式与过滤、倍速/Seek/前后台对齐，满足 SC-001～SC-009。
- 技术路线：
  - 引入 DFM 依赖（纯 Java，优先不引入 ndkbitmap）；封装 `DanmakuOverlayView` + `DanmakuEngine`；
  - 播放时钟对齐：`PlaybackClock` 软时钟，优先 MediaSession 的 PlaybackState（position/speed/lastUpdateTime）作为锚点推算；
  - 本地轨发现：视频所在目录扫描，扩展名 .xml 匹配；
  - 弹幕类型：滚动/顶部/底部/定位全支持；倍速按比例缩放滚动速度；
  - 层级：视频之上、OSD 之下；不自动淡出；
  - 偏好：以 MediaKey(绝对路径+size+mtime) 持久化弹幕轨与配置。
  - 开发注入：提供 `DeveloperDanmakuInjector` 与 Debug 入口，保障无文件场景可验证（对应 FR-004）。
  - 手动选择：在无候选时提供基于 ACTION_OPEN_DOCUMENT 的手动选择流程（对应 FR-007）。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 17（Android 工具链），AGP 8.11.1  
**Primary Dependencies**: DanmakuFlameMaster `com.github.ctiao:dfm:0.9.25`；AndroidX  
**Storage**: SharedPreferences（按 MediaKey 持久化轨与配置）；无数据库  
**Testing**: `:xbmc:testDebugUnitTest`（JUnit4），Android Lint  
**Target Platform**: Android API 24–36，arm64-v8a  
**Project Type**: Mobile（Android 应用模块 xbmc）  
**Performance Goals**: 60fps 目标渲染；SC-003 卡顿 ≤1 次且 <0.5s  
**Constraints**: Seek 后 ≤2s 恢复；对齐误差 ≤200ms（SC-002、SC-009）；不修改 Kodi so  
**Scale/Scope**: 单集弹幕 ≤50k 行（设计上限 100k），同屏限流与行数限制保证流畅

### Build Variants & Upstream Compliance
- Flavor 维度：`features`
- ProductFlavors：
  - `vanilla`（默认）：不包含弹幕功能代码与资源，保持与上游功能一致（Upstream Fidelity）
  - `dfmExperimental`：仅在该变体下包含弹幕相关源码/资源与依赖，用于开发与验证
- 依赖与源码组织：
  - 依赖坐标仅在 `dfmExperimentalImplementation` 生效：`com.github.ctiao:dfm:0.9.25`
  - 源代码约束：`main` 仅指向 `xbmc/src/main/java`（与 `res`/`AndroidManifest.xml`），显式排除 `dfmExperimental/**` 与 `dfmExperimentalDebugUnitTest/**`，禁止新增平行 `xbmc/java/` 或整棵通配。
  - 变体源码专用目录：`dfmExperimental` 仅指向 `xbmc/src/dfmExperimental/java` 与 `xbmc/src/dfmExperimental/res`。
  - 变体单测：仅使用 `xbmc/src/dfmExperimentalDebugUnitTest/java`；不得放入通用 `src/test` 或 `src/dfmExperimentalTest` 以避免被主编译拾取。
  - 提供 `BuildConfig.DANMAKU_ENABLED=true`（dfmExperimental）/`false`（vanilla）用于运行时保护
  - Manifest/packaging：按仓库规范保持 `android { packaging { jniLibs { useLegacyPackaging = true } } }`，并排除未裁剪符号库（`**/libkodi.unstripped.so`）避免打入 APK。

- OSD 接入策略（Upstream Fidelity 保障）：
  - 不修改上游 OSD 功能代码；dfmExperimental 仅通过变体专属资源与可插拔控制器（`OverlayMountController`、`OsdActions`）挂接入口；
  - 若无法挂接则回退为仅“设置面板入口”，仍满足 FR-001/FR-017；
  - 任何上游引用需在 `docs/UPSTREAM_ORIGINS.md` 记录来源与理由。

- 测试源集与任务：
  - 显式配置 `dfmExperimentalDebugUnitTest` 源集，使 `src/dfmExperimentalDebugUnitTest/java` 生效；杜绝使用 `src/test` 或 `src/dfmExperimentalTest`。
  - 关键 UT 包含：软时钟推算、XML→条目映射、切轨恢复、阈值断言、生命周期与旋转恢复。

## Constitution Check

GATE: 本计划在设计前后各自检视一次，以下为承诺与执行要点。

- Upstream Fidelity: 默认 `vanilla` 变体不改 Kodi so，且不包含弹幕相关代码/资源；弹幕功能仅在 `dfmExperimental` 变体中以增量方式提供。若引用上游文件，将在 `docs/UPSTREAM_ORIGINS.md` 记录来源与缘由。
- Reproducible Env: 遵循 AGENTS.md 版本约束；提供可复制命令（含可选 `GRADLE_USER_HOME=$(pwd)/.gradle-user`）。
- Build Path: 快速构建 `./gradlew :xbmc:assembleDebug -x lint`、`:xbmc:assembleDfmExperimentalDebug -x lint`，变体单测 `./gradlew :xbmc:testDfmExperimentalDebugUnitTest`；打包 `make apk`。
- Security & Signing: 仅使用 `KODI_ANDROID_*` 调试签名变量；Release 走 CI Secrets；不引入新权限。
- Quality Gates: 新增 UT 覆盖时钟推算和 XML→DFM 转换；Lint 需通过；安装 APK + monkey 启动校验。

## Upstream Fidelity Checklist
- [X] `vanilla` 变体默认编译（BuildConfig.DANMAKU_ENABLED=false），排除 dfmExperimental 源/资源，保持上游行为一致。
- [X] 弹幕依赖与 OSD/设置入口仅在 `dfmExperimental` 变体注册；未直接修改上游 OSD 代码路径。
- [X] 打包仍排除 `**/libkodi.unstripped.so`，保留 legacy jniLibs packaging 与现有签名配置。
- [X] Manifest 权限/组件未新增；`assembleVanillaDebug` 与 `assembleDfmExperimentalDebug` 均可生成可安装 APK。
- [X] 如引用上游文件需在 `docs/UPSTREAM_ORIGINS.md` 记录（本次无新增引用）。

## Project Structure

### Documentation (this feature)

```text
/workspace/kodi-android-arm64/specs/001-dfm-integration/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
xbmc/
└── src/dfmExperimental/
    ├── java/org/xbmc/kodi/danmaku/
    │   ├── PlaybackClock.java            # 软时钟适配（MediaSession 优先）
    │   ├── MediaSessionClock.java        # 从 PlaybackState 锚点推算
    │   ├── SoftClock.java                # 兜底软时钟
    │   ├── DanmakuOverlayView.java       # 封装 DFM View/SurfaceView
    │   ├── DanmakuEngine.java            # 装配/加载/注入/控制
    │   ├── DanmakuPreferences.java       # 偏好存取（MediaKey 维度）
    │   ├── api/DanmakuApi.java           # 内部契约适配
    │   ├── bridge/PlayerEventBridge.java # 播放器/MediaSession 事件桥接
    │   ├── dev/DeveloperDanmakuInjector.java  # 开发注入器（FR-004）
    │   ├── model/
    │   │   ├── MediaKey.java
    │   │   ├── DanmakuTrack.java         # 内含配置 DanmakuConfig（样式/过滤/偏移）
    │   │   ├── DanmakuConfig.java        # 作为 Track 的内嵌配置对象
    │   │   ├── DanmakuItem.java
    │   │   └── TrackCandidate.java
    │   └── source/local/
    │       ├── BiliXmlParser.java        # 本地 B 站 XML 解析
    │       └── LocalTrackDiscovery.java  # 同目录扫描 + 匹配
    └── res/
        ├── layout/dialog_danmaku_tracks.xml
        ├── xml/settings_danmaku.xml
        ├── menu/menu_osd_danmaku.xml
        └── values/strings_danmaku.xml
```

**Structure Decision**: Android 单模块（xbmc）内以 `dfmExperimental` 变体增加 `org.xbmc.kodi.danmaku` 包与资源；默认 `vanilla` 变体不包含此功能代码与依赖，从而保持与上游功能一致（Upstream Fidelity）。不新建独立 module；遵守 Constitution 的“上游一致性 + 最少改动”。

**Performance Measurement**: 在 `DanmakuEngine` 中加入轻量采样（渲染耗时/掉帧计数）并产出报告，支撑 SC-003 评估（不在 vanilla 变体启用）。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 引入第三方库 DFM | 满足弹幕全类型与高性能渲染 | 自研渲染成本高、周期长，风险大 |
| 本地 XML 解析器 | 符合“仅 .xml”与离线可用 | 在线接口依赖网络，不在本阶段范围 |
