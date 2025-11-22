# 设计文档：Dandanplay 弹幕 Addon（dfmExperimental）

## 背景与目标
- 目标：在 Android（arm64）Kodi 客户端中提供一个 “addons 插件” 能力，基于 Dandanplay Open API 在视频播放时自动获取匹配的弹幕，并以“弹幕轨”绑定，由 DFM（DanmakuFlameMaster）进行渲染。
- 成果：
  - 播放开始后，计算视频 16MiB MD5，调用 `/api/v2/match` 获取 `episodeId`；
  - 拉取 `/api/v2/comment/{episodeId}`（支持 `withRelated=true` 合并第三方弹幕）；
  - 将返回的 `comments (p,m)` 转换为 Bilibili XML；
  - 以“弹幕轨”的形式绑定到当前媒体会话，交由 DFM 渲染控制（开/关、透明度、速度等）。

## Kodi 插件形态与语言
- 结论：Kodi 的大多数插件（Script/Video/Program/Service/Subtitles 等）均以 Python 3 实现（Kodi v20+ 使用 Python 3.11 运行时）。同时也存在二进制（C++）Add-on 类型，但本方案优先采用 Python Service Add-on。
- 本设计选型：采用 Python `script.service` 后台服务插件监听播放器事件，联动 Android 侧 DFM 渲染；可选提供 `subtitles` 提供者以“仅字幕（ASS）”模式兼容不启用 DFM 的环境。

## 约束与范围
- 仅 dfmExperimental 变体注入实现与 UI；vanilla 不受影响。
- 遵循 Strict Directory Layout：实现代码放置于 `xbmc/src/dfmExperimental/java/**` 与资源 `xbmc/src/dfmExperimental/res/**`。
- 不改动上游通用逻辑与非实验变体编译。

## 整体架构
- 组件划分：
  1) Addon 接口与管理器（AddonManager, Addon接口）
  2) DandanplayClient（Retrofit + OkHttp 拦截器：签名/凭证/UA/解压/备用域名）
  3) HashEngine（16MiB-InputStream-MD5）
  4) CommentFetcher（匹配、搜索回退、弹幕获取、外链弹幕获取）
  5) Converter（Dandan `p,m` → Bilibili XML 或 DFM 直接可用的数据流）
  6) TrackBinder（创建/管理“弹幕轨”，与播放器生命周期联动）
  7) DFMRendererAdapter（封装 DFM 渲染控制：加载、开启/关闭、样式）
  8) CacheLayer（基于 fileId/hash 的本地缓存）

- 跨端协作：
  - Python Add-on（运行于 Kodi Python 运行时）负责：监听播放状态、匹配/拉取弹幕、用户设置与屏蔽规则、与 Android 侧 DFM 的 IPC。
  - Android dfmExperimental 变体（Java）负责：DFM 渲染、轨道开关、样式应用、媒体会话同步、提供 `localhost` IPC 端口。

## Python Add-on 设计

### 形态与目录结构
```
script.service.dandanplay/
  addon.xml                    # 扩展点：xbmc.service（必选），xbmc.subtitle（可选）
  icon.png  fanart.jpg
  service.py                   # 后台主循环：监控播放器 + 调 IPC
  resources/
    settings.xml               # 插件设置（账号、偏好、屏蔽、网络等）
    language/
      resource.language.en_gb/strings.po
      resource.language.zh_cn/strings.po
  lib/
    __init__.py
    api.py                     # Dandanplay Open API v2 封装
    hash.py                    # 16MiB MD5（文件/可读流）
    match.py                   # 匹配与搜索回退策略
    convert.py                 # p,m → Bili XML / DFM JSON（供 IPC）
    cache.py                   # addon_data/… 本地缓存与索引
    ipc.py                     # 与 Android 侧 localhost IPC 客户端
    monitor.py                 # xbmc.Monitor / xbmc.Player 事件编排
```

### addon.xml（要点）
- `extension point="xbmc.service" library="service.py" start="login"`：随 Kodi 启动；也可设置为 `startup="startup"`。
- 依赖申明：`xbmc.python`（版本与 Kodi 目标版本匹配），网络权限（默认具备）。
- 可选扩展：`xbmc.subtitle` 实现“仅字幕（ASS）提供者”通道。

### 事件与状态机
- 监听：`xbmc.Monitor()` + `xbmc.Player()`
  - onAVStart / onPlayBackStarted：开始匹配与弹幕抓取
  - onAVChange / onPlayBackSeek：重定位/重新装载（必要时）
  - onPlayBackPaused / onPlayBackResumed：透传至 DFM 暂停/继续
  - onPlayBackStopped / onPlayBackEnded：卸载弹幕、清理状态

### 伪代码（简化）
```python
class Service(xbmc.Monitor):
    def __init__(self):
        self.player = xbmc.Player()
        self.ipc = IPCClient(load_token())
        self.settings = Settings()

    def onAVStart(self):
        info = probe_current_media(self.player)
        key  = build_key(info)
        data = Cache.get(key)
        if not data:
            match = DandanAPI.match(info)
            epid  = choose_episode(match, self.settings)
            raw   = DandanAPI.comments(epid, with_related=self.settings.with_related)
            data  = Convert.to_bili_or_dfm(raw)
            Cache.put(key, data)
        self.ipc.load(data, meta=info.meta(), prefs=self.settings.to_dfm_prefs())

    def onPlayBackSeek(self, time, seekOffset):
        self.ipc.control({"action": "seek", "position": self.player.getTime()})

    def onPlayBackPaused(self):
        self.ipc.control({"action": "pause"})

    def onPlayBackResumed(self):
        self.ipc.control({"action": "resume"})

    def onPlayBackStopped(self):
        self.ipc.unload()
```

## IPC 方案：Python ↔ Android（DFM）

### 传输通道
- Android 侧在 dfmExperimental 变体内启动本地 HTTP Server（仅监听 `127.0.0.1`），默认端口 `11235`（可配置）。
- 鉴权：
  - 启动时 Android 侧在 `special://profile/addon_data/script.service.dandanplay/ipc_token.json` 写入一次性 Token；
  - Python Add-on 读取该文件并在后续请求以 `Authorization: Bearer <token>` 头发送；
  - Token 仅限 127.0.0.1，进程内短时有效，应用切换视频/重启即轮换。

### Token 文件
- 实际输出路径（Android 解析后的真实路径）对应 `special://profile/addon_data/script.service.dandanplay/ipc_token.json`；
- JSON 字段：
  ```json
  {
    "port": 11235,
    "token": "f5a1c6...",
    "issuedAt": 1732262400000
  }
  ```
- Add-on 启动时监测该文件，如端口或 token 变更需要重新建连；若文件缺失可轮询等待（DFM 控制器重新启动时会刷新）。

### HTTP 接口
- `POST /v1/danmaku/load`
  - 请求体（示例）：
    ```json
    {
      "source": "dandanplay",
      "episodeId": 12345,
      "title": "番名 S1E01",
      "duration": 1423.5,
      "mediaKey": "file:///sdcard/Movies/xxx.mp4|1234567|1699950000",
      "trackId": "dandanplay:12345",          // 可选，不传则由 IPC Server 自动生成
      "format": "bili-xml",
      "payload": "<xml…>…</xml>",
      "payloadPath": "/data/user/0/org.xbmc.kodi/files/.kodi/userdata/addon_data/script.service.dandanplay/cache/hash.xml",
      "encoding": "plain",                    // 可选：plain/base64/gzip/gzip+base64
      "prefs": {
        "opacity": 0.8, "speed": 1.0, "fontScale": 1.0,
        "maxLines": 8, "maxOnScreen": 80,
        "block": {"types": [7], "keywords": ["剧透"], "regex": []}
      }
    }
    ```
  - 响应：`{ "ok": true, "trackId": "danmaku:current" }`

- `POST /v1/danmaku/control`
  - 请求体：`{"action":"pause|resume|toggle|clear|seek|update_prefs", ...}`
  - 示例：`{"action":"update_prefs", "prefs": {"opacity": 0.6}}`
- 控制动作目前支持：
  - `pause`：暂停弹幕渲染；
  - `resume`：恢复播放（可附带 `positionMs` 与 `speed`）；
  - `seek`：`{"action":"seek","positionMs":1234}`；
  - `set_visibility`：显隐弹幕；
  - `set_speed`：单独调整播放速度；
  - `update_prefs`：传入与 `prefs` 相同结构的配置增量。

- `POST /v1/danmaku/unload`
  - 请求体：可空；卸载当前弹幕轨并释放资源
  - 可附带 `mediaKey`/`trackId` 精确指明要移除的轨道；若省略则默认卸载当前活动轨。

- `GET /v1/danmaku/status`
  - 响应：`{"loaded":true, "position": 123.4, "fps":60, "count": 13500}`

### 性能与大对象传输
- 优先 Bili-XML（压缩后）整包一次性下发；Android 侧解析入内存或流式解析。
- 仅在极端大集数/超大弹幕量时采用分页/分批（可选扩展）。
- 若 Add-on 已将弹幕缓存到本地，可传 `payloadPath`，避免重复上传；IPC Server 会直接打开该文件解析。

## Dandanplay API（Python 侧）
- 与前述 Java 客户端保持等价的接口：`match/search/comment/related/extcomment/send`。
- 签名与压缩：与上文“API 适配”一致；使用 `requests` + `requests.adapters.HTTPAdapter` 实现重试与备用域名回退。
- Hash 计算：`rb = 16*1024*1024` 读取，流式 MD5；对 URL/流媒体则跳过 Hash，走搜索回退。

## 设置项映射（Python ↔ DFM）
- 账号与接口：`appId/appSecret`、`withRelated`、`fallbackSearch`、`altHostEnabled`。
- 显示偏好（同步到 DFM）：`opacity/speed/fontScale/maxLines/maxOnScreen/outline`、`block.types/keywords/regex`、`area(显示区域)`。
- 行为开关：`autoLoadOnPlay`、`pauseWithPlayer`、`clearOnStop`、`reapplyOnSeek`。

## 缓存与回退（Python 侧）
- 路径：`special://profile/addon_data/script.service.dandanplay/cache/`（`xbmcvfs.translatePath` 解析）。
- Key：`episodeId` 优先，其次 `videoKey(hash|url)`；Value：`bili.xml.gz` 与 `meta.json`。
- 策略：命中即用，后台刷新；TTL 7 天 + LRU；网络失败使用缓存兜底。

## 安装与发布
- 目标 Kodi：v20+（Python 3.11）
- 手工安装（Android）：
  1) 打包 `script.service.dandanplay-x.y.z.zip`
  2) 通过“从 Zip 安装”导入；或 `adb push` 至 `Android/data/org.xbmc.kodi/files/.kodi/addons/`
  3) 设置页中填写 `AppId/AppSecret` 与偏好
- CI 打包：`zip -r script.service.dandanplay-x.y.z.zip script.service.dandanplay/ -x "*.pyc" "__pycache__/*"`。

## 备选方案：仅字幕（ASS）模式
- 若 Android 侧未启用 DFM/IP C：Python 走 `xbmc.subtitle` 扩展，按当前媒体生成 `.ass` 文件并返回给 Kodi 作为字幕轨。
- 转换：`p,m` → ASS（滚动弹幕映射为逐行字幕 + 透明度/速度近似化）。
- 局限：无法达到 DFM 的实时/同屏效果；作为兼容备选。

## 调试与验收（Add-on 维度）
- 调试：Kodi 日志（Android 路径：`Android/data/org.xbmc.kodi/files/.kodi/temp/kodi.log`）；Python 侧 `xbmc.log`；Android 侧 `logcat` 过滤 `DFM` 标签。
- 核验用例：
  - 本地/网络流媒体分别匹配成功并渲染；
  - 暂停/继续/Seek 与 DFM 同步；
  - withRelated 开关生效；
  - 缓存命中与后台刷新；
  - 断网/签名失败/备用域名回退；
  - 仅字幕模式可用（在禁用 DFM 时）。

- 数据流（播放会话）：
  - onPlaybackStart → HashEngine 计算哈希 → DandanplayClient.match → 选取 episodeId →
    CommentFetcher.getComments(withRelated) → Converter.toBiliXml → TrackBinder.bind(xml) → DFMRendererAdapter.render()
  - onSeek → DFM 自动随视频时间推进；必要时可触发清屏/复位
  - onStop/onRelease → 卸载弹幕轨与释放 DFM。

## API 适配
- 基础域名：`https://api.dandanplay.net/`（备用：`http://139.217.235.62:16001/`）。
- 关键接口：
  - POST `/api/v2/match`：body 示例 `{ fileHash, fileName, fileSize, videoDuration, matchMode:"hashOnly" }` → `matches[]` → `episodeId`
  - GET `/api/v2/search/episodes?anime=xxx`：匹配失败时按番名回退
  - GET `/api/v2/comment/{episodeId}?withRelated=true`：拉取弹幕（含第三方聚合）
  - GET `/api/v2/related/{episodeId}` + GET `/api/v2/extcomment?url=...`：手动合并三方源（可选）
  - POST `/api/v2/comment/{episodeId}`：发送弹幕（后续拓展）

- 认证模式：
  - 签名验证（推荐）：X-AppId、X-Timestamp、X-Signature（`base64(sha256(AppId + Timestamp + Path + AppSecret))`）
  - 凭证直传（备选）：X-AppId、X-AppSecret
  - 用户态接口：`Authorization: Bearer <token>`（登录后）

- 压缩：优先 `accept-encoding: gzip`，客户端支持 `gzip/deflate`。

## 播放集成
- 生命周期钩子：
  - onPrepare/onStart：异步匹配并预取弹幕；完成后发出“弹幕轨已就绪”事件
  - onSeek：DFM 自动随视频时间推进；必要时可触发清屏/复位
  - onPause/onResume：DFM 暂停/继续（不卸载）
  - onStop/onRelease：卸载弹幕轨、释放 DFM 资源

- 弹幕轨 Track 设计：
  - 新增逻辑类型：`TrackType.DANMAKU`（仅 UI 与会话内生效，不导出到容器）
  - 轨道数据承载：Bilibili XML 字符串（或压缩后内存流）
  - 轨道开关：与字幕并列展示，互不干扰；允许“无弹幕”“从网络（Dandanplay）”“本地弹幕文件”切换

- DFM 渲染：
  - 使用 `BiliDanmakuParser`/`DanmakuFlameMaster` 加载 XML 流
  - 参数映射：字号、行数、速度、描边、透明度、显示区域、同屏数、屏蔽词
  - 倍速：保持与播放器倍速同步

## 配置与设置
- Dandanplay 设置项（dfmExperimental 专属）：
  - AppId（必需）
  - AppSecret（必需）
  - withRelated（默认开启）
  - 备用域名启用开关
  - 登录（获取 token，用于发弹幕等）
  - 失败回退策略（自动搜索/手动选择）

- DFM 设置项（已有/扩展）：字号、速度、透明度、描边、屏蔽词、同屏上限。

## 缓存策略
- Key：`videoId`（优先文件绝对路径 MD5；网络流用 URL+长度等特征），或 `dandan_episodeId`
- 值：Bilibili XML 压缩缓存 + 元数据（来源、episodeId、withRelated 标志、时间戳）
- 失效：TTL（如 7 天）；清理策略按容量与 LRU
- 命中：优先使用缓存，异步刷新；版本字段避免旧格式污染

## 错误处理与回退
- 网络失败：指数退避重试（上限 2~3 次）；首帧渲染失败时可延迟加载
- 匹配失败：自动回退到 `search/episodes`；若多候选，弹出选择器
- 签名失败：提示检查 AppId/AppSecret 与设备时间
- 解压失败/格式错误：记录并回退到“无弹幕”

## 安全与合规
- AppSecret 不写入日志；使用安全存储；日志脱敏
- 校时：签名模式依赖 `X-Timestamp`，建议在设置页提供“时间校对”指引
- 限流：接口有行为检测，客户端增加本地限速与缓存策略

## 实现计划（TODOs）
1. Addon 框架与接口
   - 定义 `DanmakuAddon` 接口（init/start/stop/bind/enable/disable）与 `AddonManager`
   - 注册 DandanplayAddon 实现（dfmExperimental 变体）
2. DandanplayClient & 拦截器
   - Retrofit/OkHttp：签名拦截器、开发者凭证拦截器、UA、解压、备用域名
   - API 定义：match/search/comment/related/extcomment/send
3. HashEngine
   - 16MiB-MD5 计算（文件/流）
   - 对接当前播放源（本地/远程）的 InputStream 抽象
4. CommentFetcher & Converter
   - 匹配→获取→转换 Bilibili XML
   - withRelated 开关；回退搜索
5. TrackBinder & DFMRendererAdapter
   - 新增 TrackType.DANMAKU；轨道管理与 UI 集成
   - DFM 加载/切换/销毁；与倍速、暂停、seek 同步
6. 缓存层
   - 基于 `episodeId` 与 `videoId/hash` 的本地缓存
   - 读写与失效策略
7. 设置页面（dfmExperimental）
   - AppId/AppSecret、withRelated、备用域名开关、登录
8. Telemetry & 日志
   - 关键链路埋点（匹配耗时、渲染首帧时间、命中率）
9. 文档与验证
   - README/使用说明；测试用例与手动验收脚本

## 验收与测试
- 单测（dfmExperimentalDebugUnitTest）：
  - 哈希计算、签名头生成、参数构造、XML 转换
- 集成测试（手动/设备）：
  - 本地文件/HTTP 流媒体：自动匹配、拉取、渲染、切换轨
  - withRelated 开/关对比；备用域名切换
  - 异常场景：断网、签名错误、服务端压缩、格式异常

## 里程碑
- M1（基础链路）：Hash → match → comment → XML → DFM 渲染（本地文件）
- M2（完善）：withRelated、缓存、设置页、搜索回退
- M3（稳定）：登录与发弹幕、埋点、优化与问题清单收敛

## 参考资料
- 弹弹play Open API v2：match / comment / extcomment / search / 认证说明
- DFM（DanmakuFlameMaster）与 Bilibili XML 解析流程
- 本仓库 dfmExperimental 变体与目录规范

---

附：关键实现要点（摘要）
- 签名请求头：`X-AppId`、`X-Timestamp(秒)`、`X-Signature = base64(sha256(AppId+Timestamp+Path+AppSecret))`
- `Path` 仅为 URL 路径（示例：`/api/v2/comment/123`），不含查询参数
- 16MiB-MD5：流式读取（256KiB chunk），直至 16MiB 或 EOF
- `p` 字段解析：`time,mode,color,timestamp,...`；`m` 为内容
- Bilibili XML 单行：`<d p="time,mode,25,color,timestamp,0,0,0">内容</d>`
- UI：以“弹幕轨”加入 Track 面板，可开关/切换来源/调参数
