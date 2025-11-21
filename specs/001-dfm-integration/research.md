# Research: DFM 弹幕集成（Phase 0）

## Use exa 参考与本地样例
- 参考库：DanmakuFlameMaster（DFM）开源库与资料（依赖坐标 `com.github.ctiao:dfm:0.9.25`）。
- Android PlaybackState 文档：利用 `position`/`playbackSpeed`/`lastPositionUpdateTime` 推算当前位置。
- 本地参考：`.tmp_dandan`（DanDanPlayForAndroid 源码）内的弹幕加载/设置经验；仅做思路参考，不直接复制。

## 决策清单

### 1) 渲染引擎
- Decision: 采用 DFM（Java AAR），暂不引入 `ndkbitmap`。
- Rationale: 覆盖 B 站 XML 全类型，成熟稳定；纯 Java 集成快，arm64 友好。
- Alternatives: 自研渲染（高成本/高风险）；OpenGL 自绘（周期长）。

### 2) 数据格式与解析
- Decision: 本阶段仅支持 B 站 XML（.xml），扩展名识别；解析映射如下：
  - `d` 节点文本 → `text`
  - `p` 属性（逗号分隔）：`time`, `mode`, `size`, `color`, `timestamp`, …
  - `mode` 映射：1/2/3=滚动，4=底部固定，5=顶部固定，7=定位/高级
  - 颜色为十进制整型；字号映射到 sp；定位型按 DFM 支持项转换
- Rationale: 规格已明确“仅 .xml”；易于离线解析。
- Alternatives: JSON/ASS/SSA（本阶段 Out of Scope）。

### 3) 类型范围
- Decision: 支持滚动/顶部/底部/定位（与规格 FR-024 一致）。
- Rationale: 规格需求；DFM 原生支持。
- Alternatives: MVP 仅滚动（更快但与已确认范围冲突）。

### 4) 层级关系
- Decision: 弹幕位于视频之上、OSD 之下；OSD 不触发弹幕淡出/避让。
- Rationale: 交互简单、最少改动；满足可用性目标（SC-005）。
- Alternatives: 与 OSD 同层自动淡出（复杂，非本阶段目标）。

### 5) 播放时钟对齐
- Decision: 软时钟 `PlaybackClock`：以 `PlaybackState` 的 `position/speed/lastUpdateTime` 作为锚点推算；无可用状态时退化到 UI 回调或 SoftClock。
- Rationale: 不修改 Kodi so；可满足 ≤200ms 对齐目标（SC-002）。
- Alternatives: 直接从播放器内核取时钟（需要更深侵入）。

### 6) 倍速策略
- Decision: 事件时机随媒体时间推进；滚动弹幕像素速度按倍速同比例缩放。
- Rationale: 与用户感知一致，易实现；已在规格确认。
- Alternatives: 速度不缩放（高倍速难读）；>1.5x 抽样（可作为后续增强）。

### 7) 发现与持久化
- Decision: 本地发现同目录 .xml；匹配策略参考字幕（同名优先）；偏好以 MediaKey（绝对路径+size+mtime）为键。
- Rationale: 离线可用，满足“最少改动”。
- Alternatives: 哈希/媒体库ID优先（实现复杂度更高）。

### 8) 性能与预取
- Decision: 预取窗口（例如 ±60s），Seek 后丢弃旧窗口；同屏/行数限制避免拥堵；逐帧（或 30/60Hz）驱动。
- Rationale: 满足 SC-001/003；与 DFM 最佳实践一致。
- Alternatives: 全量一次性注入（内存压力大）。

## 参考链接
- DFM 项目页（多镜像，版本示例：0.9.25）
- Android PlaybackState 文档（position、playbackSpeed、lastPositionUpdateTime，用于锚点推算）

## 结论
以上决策满足当前规格的范围与质量门槛，且不需要修改 Kodi so。所有“NEEDS CLARIFICATION”已在规格或本研究中落定，无阻塞项。
