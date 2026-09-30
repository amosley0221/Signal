# Signal Agent HTTP API (v1)

The Signal Agent runs on the PC (e.g. STUDIO-PC) and serves the media folders to the
Signal Player Android app. Plain HTTP on the LAN (or over Tailscale). Default port **8765**.

## Discovery
- mDNS / DNS-SD service type `_signal._tcp`, port = agent port.
  TXT records: `name=<pc name>`, `version=<agent version>`, `id=<stable agent id>`, `plex=1|0`.
- The app also accepts a manually typed `host[:port]`.

## Auth
Every endpoint except `GET /api/info`, `POST /api/pair/start` and `GET /api/pair/status/:id`
requires the device token, sent either as header `Authorization: Bearer <token>`
or as query parameter `?token=<token>` (used by Cast / Sonos receivers that cannot send headers).
Unauthorized → `401 {"error":"unauthorized"}`.

## Endpoints

### `GET /api/info`
`{"name":"STUDIO-PC","version":"1.0.0","id":"<uuid>","plex":true,"addresses":["192.168.1.20"]}`

### Pairing
- `POST /api/pair/start` body `{"deviceName":"Galaxy Z Fold6"}` →
  `{"requestId":"<id>","code":"482913"}`. The agent shows the same code on the PC
  (console + admin page `http://localhost:<port>/`) and waits for approval there.
- `GET /api/pair/status/:requestId` → `{"status":"pending"|"approved"|"denied"|"expired","token":"<token, only when approved>"}`.
  Requests expire after 5 minutes.

### `GET /api/libraries`
```json
[{"id":"l1","name":"Suno Music","type":"music","path":"D:\\Music\\Suno","count":146,"bytes":10522669875}]
```
`type` ∈ `music | musicvideos | movies | tv`.

### `GET /api/catalog`
Full catalogue. All durations are milliseconds, all times are epoch ms. Optional fields may be omitted or null.
```json
{
  "generatedAt": 1790000000000,
  "tracks": [{
    "id":"a1b2", "libraryId":"l1", "title":"Chrome Hearts Don't Break", "artist":"Velvet Static",
    "album":"Neon Cathedral", "albumArtist":"Velvet Static", "year":2026, "disc":1, "track":1,
    "durationMs":228000, "container":"WAV", "codec":"PCM", "bitDepth":24, "sampleRate":96000,
    "size":131000000, "mtime":1790000000000, "addedAt":1790000000000,
    "genres":["synthwave","night drive"], "hasArt":true, "hasLyrics":true, "hasTranslation":false,
    "lyricsLang":"en", "relPath":"Neon Cathedral/01 Chrome Hearts.wav"
  }],
  "videos": [{
    "id":"v1", "libraryId":"l2", "title":"Chrome Hearts (Official Video)", "artist":"Velvet Static",
    "album":"Neon Cathedral", "durationMs":228000, "width":3840, "height":2160, "hdr":true,
    "container":"MP4", "size":900000000, "addedAt":1790000000000, "hasArt":false
  }],
  "movies": [{
    "id":"m1", "libraryId":"l4", "title":"Low Orbit", "year":2025, "durationMs":7440000,
    "genres":["Sci-fi"], "director":"A. Director", "synopsis":"…", "cast":["Actor One","Actor Two"],
    "rating":7.9, "certificate":"PG-13", "posterUrl":"/api/art/m1?kind=poster",
    "backdropUrl":"/api/art/m1?kind=backdrop", "matchedBy":"PLEX · TMDB",
    "viewOffsetMs":2500000, "watched":false, "lastViewedAt":1790000000000, "width":1920, "height":1080, "container":"MKV",
    "size":4000000000, "addedAt":1790000000000,
    "subtitles":[{"id":"s0","language":"en","label":"English","format":"srt","url":"/api/subtitle/m1/s0"}],
    "chapters":[{"title":"Opening","startMs":0}]
  }],
  "shows": [{
    "id":"sh1", "libraryId":"l3", "title":"The Ballast", "year":2024, "genres":["Sci-fi"],
    "synopsis":"…", "cast":["…"], "rating":8.1, "certificate":"TV-14",
    "posterUrl":"/api/art/sh1?kind=poster", "backdropUrl":"/api/art/sh1?kind=backdrop",
    "matchedBy":"PLEX · TVDB",
    "seasons":[{"number":1,"episodes":[{
      "id":"e1", "season":1, "episode":1, "title":"Pilot", "summary":"…", "durationMs":2880000,
      "viewOffsetMs":0, "watched":false, "lastViewedAt":null, "addedAt":1790000000000, "size":1200000000, "container":"MKV",
      "width":1920, "height":1080, "thumbUrl":"/api/art/e1?kind=thumb", "subtitles":[], "chapters":[]
    }]}]
  }]
}
```
URLs in the catalogue are relative to the agent base URL; the app appends the token.

### Media
- `GET /api/stream/:id` — raw file with HTTP Range support (`206 Partial Content`), correct `Content-Type`.
- `GET /api/download/:id?quality=original|16-44` — full file with `Content-Disposition`; Range supported (resumable).
  `16-44` transcodes lossless audio to 16-bit/44.1 kHz FLAC when `ffmpeg` is on the PATH, otherwise serves the original
  and sets header `X-Signal-Quality: original`.
- `GET /api/art/:id?kind=cover|poster|backdrop|thumb` — image (embedded cover art for tracks, folder.jpg/cover.jpg,
  poster.jpg/fanart.jpg next to videos, or Plex artwork). `404` when none.
- `GET /api/subtitle/:videoId/:subId` — WebVTT or SRT sidecar.
- `GET /api/lyrics/:id` →
  `{"lang":"es","lines":[{"t":0,"text":"Baja la marea"}],"translation":[{"t":0,"text":"The tide goes out"}],"synced":true}`
  (`t` in seconds, float). Sources: `<file>.lrc` → embedded lyrics tag → none (`{"lines":[]}`).
  Translation from `<file>.en.lrc` if present.

### Edits and sync
- `POST /api/lyrics/:id` body `{"lrc":"[00:01.00]First line\n[00:03.50]Second line"}` → `{"ok":true}`.
  Writes `<song>.lrc` next to the file (an existing one is kept once as `<song>.lrc.bak`). Used by the phone's
  lyrics editor (paste + tap-to-sync). `400` for an empty body, `404` for unknown ids.
- `POST /api/art/:id` (id = any song in the album) raw `image/jpeg` or `image/png` body → `{"ok":true}`. Saves `cover.jpg`/`cover.png`
  in the song's folder (existing cover/folder images kept once as `.bak`).
- `POST /api/tags/:id` body `{"artist":"Glass Orchard","baseMtime":1790000000000}` (also accepts `title`, `album`, `albumArtist`,
  `genre` — `;`-separated, `year`, `track`, `disc`) →
  `200 {"ok":true,"mtime":<new mtime>}`; `409 {"error":"conflict","pcArtist":"…"}` when the file's mtime differs from
  `baseMtime` and the PC-side value differs. Send `"force":true` to overwrite.
- `POST /api/suggest-artists` body `{"trackId":"…","prompt":"a duo from a rainy port city"}` →
  `[{"name":"Glass Orchard","why":"Mood · dream pop, slow","kind":"mood"}]`.
- `POST /api/upload?library=<id>&name=<file name>` raw body → `{"ok":true,"id":"<new track id>"}`.
- `POST /api/progress` body `{"id":"m1","positionMs":123000,"watched":false}` → `{"ok":true}`
  (forwarded to Plex when configured).
- `GET /api/activity` → `[{"id":"j1","kind":"scan|tag|upload|plex","title":"…","detail":"…","state":"queued|running|done|failed","progress":0.62}]`.
- `POST /api/rescan` → `{"ok":true}` starts a library rescan.

### Updates
- `GET /api/update` → `{"current":"1.0.4","latest":"1.0.5","available":true,"notes":"…","downloading":false,"error":null}`.
  Only the packaged Windows `SignalAgent.exe` updates itself; from source `available` is always `false`.
- `POST /api/update` → `200 {"ok":true}` when the download started (the agent restarts itself a few seconds later),
  or `409 {"ok":false,"error":"no_update"|"not_supported"}`. Downloads are verified against the release's
  `SignalAgent.exe.sha256` asset (`<hex>  SignalAgent.exe`); releases without it are refused.
