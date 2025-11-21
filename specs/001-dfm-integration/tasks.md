# Tasks: DFM 弹幕集成

**Input**: Design documents from `/specs/001-dfm-integration/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: 新增 Java 逻辑须满足 ≥80% 覆盖（Constitution V）。
**Source Sets**: 主源集仅编译 `src/main/java`；实验代码 `src/dfmExperimental/java`；变体单测仅用 `src/dfmExperimentalDebugUnitTest/java`（如仍在 `src/dfmExperimentalTest`，需迁移并更新构建映射）。

**Organization**: 按用户故事分组；默认构建保持与上游一致，功能仅在 `dfmExperimental` 变体生效。

## Format: `[ID] [P?] [Story] Description`

- [P]: 可并行（不同文件，且无未完成依赖）
- [Story]: 用户故事标签（US1、US2、US3）
- 任务描述必须包含精确文件路径

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 构建隔离与依赖接入（Upstream Fidelity 合规）

- [X] T001 在 `xbmc/build.gradle` 增加 flavor 维度 `features`，定义 productFlavors：`vanilla`（默认）与 `dfmExperimental`（实验）；注入 `BuildConfig.DANMAKU_ENABLED`
- [X] T002 在 `xbmc/build.gradle` 配置 `dfmExperimentalImplementation "com.github.ctiao:dfm:0.9.25"` 与 `mavenCentral()` 仓库
- [X] T003 [P] 创建源码与资源目录 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/` 与 `xbmc/src/dfmExperimental/res/`
- [X] T004 更新 `specs/001-dfm-integration/quickstart.md` 增加变体构建说明（`:xbmc:assembleDfmExperimentalDebug` 与 `:xbmc:assembleVanillaDebug`）
- [X] T005 [P] 在 `xbmc/build.gradle` 增加 Jacoco 覆盖率报告与 ≥80% 门槛（目标 `dfmExperimentalDebugUnitTest`）
- [X] T056 在 `xbmc/build.gradle` 配置 `dfmExperimentalDebugUnitTest` 源集映射（单测目录 `src/dfmExperimentalDebugUnitTest/java`），并确保 `main` 排除 `dfmExperimental/**` 与 `dfmExperimentalDebugUnitTest/**`，测试任务与报告输出路径保持可查

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 基础模型与服务骨架（仅 dfmExperimental 变体）

- [X] T006 [P] 创建媒体键模型 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/model/MediaKey.java`
- [X] T007 [P] 创建轨道配置模型 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/model/DanmakuConfig.java`（作为 `DanmakuTrack` 的内嵌配置：样式/密度/过滤/时间偏移）
- [X] T008 [P] 创建弹幕条目模型 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/model/DanmakuItem.java`
- [X] T009 [P] 创建弹幕轨模型 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/model/DanmakuTrack.java`
- [X] T010 [P] 创建轨候选模型 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/model/TrackCandidate.java`
- [X] T011 创建首选项存储（SharedPreferences）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuPreferences.java`
- [X] T012 定义服务接口（与 contracts 映射）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuService.java`
- [X] T013 定义内部 API 适配层（契约方法存根）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/api/DanmakuApi.java`
- [X] T014 补充包级文档注释与错误类型 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/package-info.java`
- [X] T063 在 `specs/001-dfm-integration/contracts/` 增补最小“轨绑定（Track Binding）”契约草案与示例（JSON/接口说明），与 `DanmakuService`/`DanmakuApi` 对齐（对应 FR-020）

**Checkpoint**: 基础就绪，可开始用户故事开发

---

## Phase 3: User Story 1 - 观看中显示并同步弹幕 (Priority: P1) 🎯 MVP

**Goal**: 播放过程中显示弹幕，并与播放进度/暂停/恢复/Seek/前后台保持同步

**Independent Test**: 通过开发注入器注入样例弹幕，验证显示/同步/生命周期；断言 2 秒与 200ms 阈值

### Tests for User Story 1

- [X] T015 [P] [US1] 软时钟推算单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/clock/PlaybackClockTest.java`
- [X] T016 [P] [US1] XML→标准条目映射单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/source/local/BiliXmlParserTest.java`
- [X] T017 [P] [US1] 引擎状态与对齐（play/pause/seek/speed）单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/DanmakuEngineTest.java`
- [X] T018 [P] [US1] 阈值断言（SC-001/SC-002/SC-009、字幕独立性 SC-007）`xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/EngineThresholdsTest.java`
- [X] T065 [US1] 前后台与旋转恢复单测（生命周期 onPause/onResume 与 onConfigurationChanged）`xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/LifecycleRestoreTest.java`

### Implementation for User Story 1

- [X] T019 [P] [US1] 定义播放时钟接口 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/clock/PlaybackClock.java`
- [X] T020 [P] [US1] 基于 MediaSession 的时钟实现 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/clock/MediaSessionClock.java`
- [X] T021 [P] [US1] 兜底软时钟实现 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/clock/SoftClock.java`
- [X] T022 [P] [US1] B 站 XML 解析器 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/source/local/BiliXmlParser.java`
- [X] T023 [P] [US1] 覆盖层视图（DFM）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuOverlayView.java`
- [X] T024 [US1] 弹幕引擎（装配/加载/注入/控制）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuEngine.java`
- [ ] T025 [US1] API：可见性/Seek/速度/状态 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/api/DanmakuApi.java`
- [X] T026 [P] [US1] 播放器事件桥接（MediaSession）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/bridge/PlayerEventBridge.java`
- [X] T027 [US1] 视图挂载与层级（Z-Order）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/ui/OverlayMountController.java`
- [X] T028 [US1] OSD 开关入口与动作接入 `xbmc/src/dfmExperimental/res/menu/menu_osd_danmaku.xml` 与 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/ui/OsdActions.java`
- [ ] T029 [US1] 基本日志与错误处理 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuEngine.java`
- [X] T054 [US1] 开发注入器与 Debug 入口 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/dev/DeveloperDanmakuInjector.java` 与调试菜单项，供无文件场景验证（FR-004）
- [X] T055 [US1] 生命周期与旋转恢复处理（onPause/onResume/onConfigurationChanged）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/ui/OverlayMountController.java`
- [ ] T030 [US1] Lint 修复（Alias of T062 质量闸门） `xbmc/build.gradle`

**Checkpoint**: MVP 就绪（dfmExperimental 变体）

---

## Phase 4: User Story 2 - 基于视频路径发现与选择“弹幕轨” (Priority: P2)

**Goal**: 在视频目录发现 .xml 弹幕轨；自动选择/手动切换；2 秒内恢复同步

**Independent Test**: 同名/多个候选/无候选样本验证发现、选择/切换与降级提示

### Tests for User Story 2

- [ ] T031 [P] [US2] 本地轨发现与评分单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/source/local/LocalTrackDiscoveryTest.java`
- [ ] T032 [P] [US2] 切轨恢复同步（≤2s）单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/DanmakuEngineSwitchTrackTest.java`
- [ ] T066 [US2] 手动选择后恢复同步（≤2s）单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/ManualSelectSyncTest.java`
- [ ] T067 [US2] 解析失败/不可读文件错误路径与 UI 提示单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/ParserErrorFlowTest.java`

### Implementation for User Story 2

- [ ] T033 [P] [US2] 本地轨发现器（仅本地路径扫描）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/source/local/LocalTrackDiscovery.java`
- [ ] T034 [US2] API：列举候选与选择当前轨 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/api/DanmakuApi.java`
- [ ] T035 [US2] 引擎：加载所选轨并恢复同步 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuEngine.java`
- [ ] T036 [US2] 偏好：按 MediaKey 记忆最近选择 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuPreferences.java`
- [ ] T037 [US2] 轨列表 UI 与布局 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/ui/TrackSelectionDialog.java` 与 `xbmc/src/dfmExperimental/res/layout/dialog_danmaku_tracks.xml`
- [ ] T038 [US2] 无候选/损坏文件提示文案 `xbmc/src/dfmExperimental/res/values/strings_danmaku.xml`
- [ ] T058 [US2] 解析失败与错误分类处理（不可读/损坏/权限）与替换路径回退 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/source/local/BiliXmlParser.java` 与 `DanmakuEngine` 错误分支
- [ ] T059 [US2] 无候选时的手动文件选择（ACTION_OPEN_DOCUMENT + 校验 + 加载）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/ui/ManualFilePicker.java`
- [ ] T039 [US2] Lint 修复（Alias of T062 质量闸门） `xbmc/build.gradle`

**Checkpoint**: 本地轨发现/选择独立可测

---

## Phase 5: User Story 3 - 调整样式、密度与过滤 (Priority: P3)

**Goal**: 支持字号/速度/透明度/同屏/行数上限、关键字/类型过滤与时间偏移；即时生效并持久化

**Independent Test**: 任意弹幕下调整设置，1 秒内生效与会话内持久化

### Tests for User Story 3

- [ ] T040 [P] [US3] 设置应用与持久化单测 `xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/DanmakuSettingsTest.java`

### Implementation for User Story 3

- [ ] T041 [P] [US3] 偏好字段补充 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuPreferences.java`
- [ ] T042 [US3] 引擎：样式与密度应用 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuEngine.java`
- [ ] T043 [US3] 引擎：关键字/类型过滤与同屏/行数限制 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuEngine.java`
- [ ] T044 [US3] 引擎：时间偏移应用（offsetMs）`xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuEngine.java`
- [ ] T045 [US3] API：更新配置 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/api/DanmakuApi.java`
- [ ] T046 [US3] 设置面板与条目 `xbmc/src/dfmExperimental/res/xml/settings_danmaku.xml` 与 `xbmc/src/dfmExperimental/res/values/strings_danmaku.xml`
- [ ] T060 [US3] 可读性提示文案与呈现（字幕共显情况下的提示，FR-021）`xbmc/src/dfmExperimental/res/values/strings_danmaku.xml` 与设置面板展示位置
- [ ] T047 [US3] Lint 修复（Alias of T062 质量闸门） `xbmc/build.gradle`

**Checkpoint**: 设置项与行为可独立验证

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: 合规、性能与清理

- [ ] T048 [P] 在 `specs/001-dfm-integration/plan.md` 增补 Upstream Fidelity 合规清单（已更新，复核）
- [ ] T049 [P] 引擎性能优化：预取窗口与限流 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/DanmakuEngine.java`
- [ ] T050 [P] 关键路径单测补充（阈值与边界）`xbmc/src/dfmExperimentalDebugUnitTest/java/org/xbmc/kodi/danmaku/`
- [ ] T051 代码清理与日志分级 `xbmc/src/dfmExperimental/java/org/xbmc/kodi/danmaku/`
- [ ] T052 按 quickstart 全流程验证（dfmExperimental 与 vanilla）`xbmc/build/outputs/apk/`
- [ ] T053 可用性走查（SC-005）：更新 `specs/001-dfm-integration/quickstart.md` 加入入口路径截图与步骤，并在 PR 模板粘贴走查结论

- [ ] T061 [P] 性能采样与报告（渲染耗时/掉帧计数；dfmExperimental 下启用），输出至 `xbmc/build/reports/danmaku/perf/`
- [ ] T062 质量闸门整体验证（统一 Lint + UT + 安装可启动校验）；T030/T039/T047 作为该任务别名收敛报告

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1): 无依赖
- Foundational (Phase 2): 依赖 Setup 完成；阻塞所有用户故事
- User Stories (Phase 3+): 依赖 Foundational 完成
  - 可并行：US1、US2、US3 可在不同人员下并行推进
  - 顺序策略：按优先级顺序（P1 → P2 → P3）
- Polish (Final): 依赖所需用户故事完成

### User Story Dependencies

- User Story 1 (P1): 无故事依赖
- User Story 2 (P2): 与 US1 集成点在引擎层，但可独立测试
- User Story 3 (P3): 与 US1/US2 集成点在引擎与存储层，但可独立测试

### Parallel Opportunities

- Setup：T003、T005 可并行
- Foundational：T006~T010 可并行；T011 与 T012/T013 可并行
- US1：T019~T023 可并行；T024 汇总整合；T026 可并行推进
- US2：T033 与 T035 可并行准备；T034 在服务签名确定后并行
- US3：T041 与 T042~T044 可并行推进

---

## Implementation Strategy

- MVP 优先：先完成 US1（显示与时钟对齐）
- 增量交付：US2（发现与切换）→ US3（样式/过滤/偏移）
- 测试先行：对“软时钟推算、XML 映射、切轨恢复、阈值”编写单元测试（先红后绿）
