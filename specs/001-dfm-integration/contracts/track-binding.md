# Track Binding (Draft) — FR-020

最小契约草案，覆盖“轨绑定/切换”与状态查询的内部接口，供 `DanmakuService` / `DanmakuApi` 实现对齐。

## Bind or Switch Track
POST `app://internal/danmaku/tracks/select`
```json
{
  "mediaKey": "path|size|mtime",
  "trackId": "local-12345"
}
```
- `mediaKey`: `path|size|mtime` 字符串（参考 `MediaKey.serialize()`）
- `trackId`: 由发现器生成的轨标识；同一媒体内唯一
- **Response**: `204 No Content`

## List Candidates
GET `app://internal/danmaku/tracks?mediaKey=path|size|mtime`
```json
{
  "mediaKey": "path|size|mtime",
  "candidates": [
    {
      "id": "local-12345",
      "name": "movie.xml",
      "mediaKey": "path|size|mtime",
      "sourceType": "LOCAL",
      "filePath": "/storage/emulated/0/Movies/movie.xml",
      "score": 100,
      "reason": "同名匹配"
    }
  ]
}
```

## Status (Pull)
GET `app://internal/danmaku/status`
```json
{
  "visible": true,
  "playing": true,
  "positionMs": 120000,
  "speed": 1.0
}
```

## Notes
- 仅覆盖本地轨（`sourceType=LOCAL`）；未来可扩展新来源。
- 错误分类与 UI 文案在 `BiliXmlParser`/`DanmakuEngine` 内部处理，接口沿用 HTTP 语义返回 4xx/5xx。 
