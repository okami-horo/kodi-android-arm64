# Data Model: DFM 弹幕集成（Phase 1）

## Entities

### MediaKey
- key: `String`（组合：绝对路径 + 文件大小 + 修改时间）
- fields:
  - `path: String`（绝对路径）
  - `size: long`
  - `mtime: long`
- rules:
  - 组合唯一；序列化为 `path|size|mtime` 形式用于存储

### DanmakuTrack
- fields:
  - `id: String`（MediaKey + 轨源标识哈希）
  - `name: String`
  - `sourceType: enum {LOCAL}`
  - `filePath: String`（.xml 路径）
  - `config: DanmakuConfig`
- relations:
  - `mediaKey: MediaKey`（1:N — 同一媒体可有多条轨候选）

### DanmakuConfig
- fields:
  - `textScaleSp: float`
  - `scrollSpeedFactor: float`
  - `alpha: float [0..1]`
  - `maxOnScreen: int`
  - `maxLines: int`
  - `keywordFilter: List<String>`
  - `typeEnabled: {scroll:boolean, top:boolean, bottom:boolean, positioned:boolean}`
  - `offsetMs: long`（正/负）
- rules:
  - 即时生效；按轨保存优先，回退全局默认

### TrackCandidate
- fields:
  - `track: DanmakuTrack`
  - `score: int`（命名匹配分）
  - `reason: String`（同名/近似/其他）

### DanmakuItem（标准化）
- fields:
  - `timeMs: long`
  - `type: enum {SCROLL, TOP, BOTTOM, POSITIONED}`
  - `text: String`
  - `color: int`
  - `textSizeSp: float`
  - `alpha: float`
  - `position: {x: float, y: float}?`（定位型）
- rules:
  - 从 B 站 XML 映射而来；在注入时转为 DFM 的 `BaseDanmaku`

## Validation Rules
- 仅允许 `.xml` 扩展名的轨道加入候选（FR-022）。
- 切换轨后 2s 内需恢复同步（FR-018）。
- Seek 后 2s 内需恢复同步（SC-002）。
- 位置/时间对齐正确率 ≥95%（SC-009）。

## State & Transitions
- Idle → Prepared（加载并解析 .xml）→ Active（渲染中）
- Active ↔ Paused（前后台/暂停）
- Active → Switching（切轨）→ Active（恢复对齐）
- 任意 → Released（销毁）
