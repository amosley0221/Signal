# Handoff: Signal — Android media player (Galaxy Z Fold, folded + unfolded)

## Overview
**Signal** (a.k.a. Signal Media Player) is a personal Android media player that combines an Apple-Music-style music experience with Plex-style movie/TV browsing, on top of the user's own files. Music (mostly Suno-generated WAVs, plus FLAC/M4A/MP3), music videos, movies and TV episodes live on an always-on Windows PC ("STUDIO-PC") in the **same folders Plex already indexes**. The phone app streams from the PC, downloads for offline use, and plays local files. Distinctive features:

- Lossless / Hi-Res Lossless badges with bit depth & sample rate per track.
- Synced, scrolling lyrics with per-line seek; bilingual view (original + English) for non-English songs.
- **Fictional artist-name suggester** for untagged Suno tracks (on import, per track, and in batch).
- Album pages that list **music videos beneath the audio tracks** (Apple Music behaviour).
- Movies / TV with Plex-sourced metadata (poster, synopsis, cast, ratings, episode summaries) and season → episode navigation.
- In-app video player with full-screen, chapters, speed, CC, PiP, gestures.
- **Google Cast + Sonos** output picker with room grouping/ungrouping and group volume.
- PC sync screen: per-library Stream / Download new / Download all, Wi-Fi-only, download quality, offline mode, lyric translation, metadata source.
- Two layouts for the Galaxy Z Fold (non-Ultra): **cover screen (folded)** single column with a mini player; **inner screen (unfolded)** two-pane — library left, persistent Now Playing / detail right.

## About the design files
Everything in `design/` is a **design reference built in HTML** — an interactive prototype that shows intended look and behaviour. It is **not production code**. The task is to **recreate these screens natively for Android** (Kotlin + Jetpack Compose is the recommended stack; Media3/ExoPlayer for playback; Cast SDK + Sonos Control API for outputs) following the specs below. If a codebase already exists, follow its conventions; otherwise start a fresh Compose project.

`design/Player B Signal.dc.html` is the prototype (open it in a browser; it loads `player-core.js`, `android-frame.jsx`, `support.js` from the same folder). `design/Signal Player.dc.html` shows folded and unfolded side by side. `player-core.js` contains **all demo data, the state machine, and the artist-name suggester** — read it for exact behaviour.

## Fidelity
**High-fidelity.** Colours, type, spacing, states and copy are final intent. Album art, video frames, posters and backdrops are gradient placeholders — real assets come from file tags / Plex.

## Target devices
- Cover screen (folded): designed at **380 × 900 dp** (Z Fold cover ≈ 21:9). Content edge-to-edge under status bar; gesture nav bar at bottom.
- Inner screen (unfolded): designed at **800 × 740 dp** (≈ 8" near-square). Use `WindowSizeClass` / fold posture to switch layouts; width ≥ 600 dp ⇒ two-pane.
- Video landscape (folded): the whole device rotates to 900 × 380.

## Design tokens
**Type**
- UI: `Space Grotesk` 400/500/600/700 (Google Fonts). Titles: 36 sp /0.95 weight 500 letter-spacing −0.045em (section title), 30 sp weight 500 −0.04em (Now Playing title, folded), 26 sp (unfolded), 28 sp 700 (movie/show title), 22 sp 700 (album title), 18 sp 600 (sheet titles), 15 sp 500 (list rows), 14.5 sp 500/600 (secondary rows), 13.5–12 sp (meta).
- Technical readouts: `JetBrains Mono` 400/500/600 — 11 sp letter-spacing 0.12em uppercase for section labels/status; 10.5 sp 0.06–0.08em for meta rows; 9.5 sp 600 0.08em for badges.
- Lyrics: Space Grotesk 500, 20 sp / 1.2, −0.02em; translation line 14 sp 400.

**Colour** (dark theme only)
- Background `#0B0B0B`; surface / sheets `#141414`; canvas behind device `#070707`.
- Foreground `#ECECEC`; muted text `rgba(236,236,236,.55)`; faint `rgba(236,236,236,.4–.5)`.
- Hairlines `rgba(255,255,255,.08–.15)`; hover `rgba(255,255,255,.04)`.
- **Accent amber** `oklch(0.8 0.16 70)` (≈ `#F2A93B`); accent text-on-amber `#1A1300`. Badge tint `rgba(255,170,60,.16)` with text `oklch(0.84 0.16 70)`.
- Success / online green `oklch(0.8 0.17 150)` (≈ `#3BD17A`), check glyph `#062`.
- Quality badge (Lossless / Hi-Res): mono 9.5 sp on `rgba(255,255,255,.1)`; HI-RES chip in lists uses the amber tint.
- Album/track art placeholder: `linear-gradient(135deg, oklch(L .14 h1), oklch(L−.18 .12 h2))` + 2 px / 9 px stripe overlay; blurred glow variant for backgrounds.

**Shape** — square corners throughout (0–4 px). Buttons 30–34 dp tall with 1 px `rgba(255,255,255,.15)` border; primary buttons filled `#ECECEC` on `#0B0B0B` text or amber-filled for casting/apply. Sheets: folded = bottom sheet, 0 radius, `#141414`, padding 18/20/28; unfolded = centred dialog 480 dp wide, max-height 86 %, shadow `0 30px 80px rgba(0,0,0,.6)`.

**Spacing** — page gutters 20 dp; list rows 8–12 dp vertical padding; row gaps 12–14 dp; art thumbs 52 dp (songs), 44 dp (mini/queue), 116 dp (Now Playing folded), 128×72 dp (episode thumbs), 150×84 dp (album music-video thumbs). Album grid 2 columns (folded) gap 18/16; posters 3 columns 2:3 gap 16/12.

**Motion** — lyrics auto-scroll `smooth`, active line 1.0 opacity, past 0.28, upcoming 0.42, 400 ms opacity transitions; now-playing bars `pulseA` 0.7–1.2 s scaleY; download spinner 0.9 s rotate; toasts 2.6 s, top-centre at 56 dp, `#ECECEC` on `#0B0B0B` text.

## Information architecture
Top-level sections (bottom bar on cover screen, segmented control in left pane when unfolded): **MUSIC · MOVIES · TV SHOWS · SYNC**.
Music sub-tabs (underline tabs, amber underline): **Recently Added · Songs · Albums · Artists · Playlists · Music Videos**.

## Screens

### 1 Library — Recently Added (default)
Header: section title (36 sp) + one-line mono status `■ STUDIO-PC · SYNCED 2 MIN AGO` (green square = online; when offline reads `Offline · showing downloads only` in amber). Right: `+3 NEW` outlined amber chip when new files were found on the PC (opens Import review). Below: sub-tabs. Content: "3 new files on STUDIO-PC — D:\Music\Suno · review artist names, then add — Review" card (surface `rgba(255,255,255,.06)`, 1 px border), then `ADDED THIS WEEK` label and the newest 6 songs as song rows.

**Song row**: 52 dp art · title 15 sp · meta line: `HI-RES` chip (only for 24-bit) · artist (amber text "Unknown artist" if untagged) · source dot (green filled check = downloaded, hollow circle = on PC only, spinner = downloading) · trailing `⋯` (opens Edit sheet). Tap row/art = play. Current track shows 3 animated bars over the art.

### 2 Songs
Same rows for the whole library. Above the list when any track lacks an artist: **"5 songs have no artist — Suggest fictional names from title, lyrics and mood — Fix"** card (amber square icon). Tapping opens the Batch sheet.

### 3 Albums / Artists / Playlists / Music Videos
Albums: 2-col grid, square art, title 14.5 sp 600, artist 13 sp muted. Artists: circular 56 dp art rows with `n songs` and chevron. Playlists: 64 dp art rows; smart playlist "Needs an artist" auto-collects untagged tracks. Music Videos: 16:9 cards with centred white play disc, title, `4K · HDR · 3:48 · Stream from STUDIO-PC`; top-right chip shows/toggles download state.

### 4 Album / Playlist / Artist detail
Blurred glow of the art (420 dp, masked) behind. Back button (38 dp square). If every track is downloaded: pill `● Available offline`. Centered 220 dp art, title 22 sp 700, subtitle in amber (`Artist · Year`), mono meta `ALBUM · 3 SONGS · 1 VIDEO · 11:16`. Buttons: **Play** (filled), **Shuffle** (translucent), **↓** download-all square.
**Track rows**: track number (tabular) or animated bars if playing · title · meta: `Lossless|Hi-Res Lossless` badge, **file-format chip** (`WAV`, `FLAC`, `M4A`, `MP3` outlined mono), `24-bit / 96 kHz · Stream from STUDIO-PC` (ellipsised) · duration · download state button (PC tracks only) · `⋯`. **Order = disc → track number → title** from tags, never filename order.
**Music videos** section below the tracks: `MUSIC VIDEOS` mono label + count; each row = 150×84 thumb with play disc and duration chip, title, `MP4` chip + `4K · HDR · 3:48`, source line, download button. Footer note: "Videos join an album when their ALBUM tag matches, or by dropping them in the album folder on STUDIO-PC. Use ⋯ → Add to album to attach one manually."

### 5 Now Playing (folded: full-screen sheet from the mini player; unfolded: persistent right pane)
Header row: chevron-down close (folded only) · mono `NOW PLAYING · ON DEVICE` / `PLAYING ON KITCHEN` · **LYRICS** toggle (becomes amber **QUEUE** when lyrics are shown) · **CAST** button (outlined; when casting it is amber-filled and reads the room name, e.g. `▭ Kitchen`) · **EDIT**.
Art 116 dp square with a mono readout column: amber `▯ HI-RES LOSSLESS`, `24-BIT / 96 KHZ`, `WAV · SUNO · 3:48`, album name. Title 30 sp (folded) / 26 sp, artist 16 sp muted.
Middle area toggles between **Up next** list (mono index `02`, title, `ARTIST · QUALITY`, duration) and **Lyrics**.
Scrubber: **waveform-style bar** — 36 dp tall repeating 2 px vertical bars every 5 px, `rgba(236,236,236,.22)`, played portion amber; tap/drag to seek. Times in mono below (`0:34` / `-3:14`). Transport: prev / **60 dp amber square play-pause** / next; left of transport a download-state button for PC tracks.

### 6 Lyrics
Lines are tappable buttons stacked with 11 dp vertical padding; the container pads 38 % top / 60 % bottom and smooth-scrolls so the active line sits 38 % from the top. Active line full opacity, earlier 0.28, later 0.42. Tapping a line seeks to its timestamp and resumes play.
For non-English songs a segmented mono toggle appears above the list: **`ES` | `ES + EN` | `EN`** (amber active) with a note `SPANISH · TRANSLATED ON STUDIO-PC`. In `ES + EN` each line shows the translation beneath (14 sp, 60 % opacity).
Empty states: `INSTRUMENTAL · NO LYRICS` or `NO .LRC FOUND ON STUDIO-PC · SUNO LYRICS WILL SYNC ON NEXT SCAN` (centred mono, 45 % opacity).
**Lyrics sources**: `.lrc` sidecar beside the file (PC writes Suno's aligned lyrics into it on scan) → embedded LYRICS tag → none. **Translation**: done on STUDIO-PC at scan time, stored as `<song>.en.lrc`, travels with downloads; phone-only files upload lyric text to the PC when reachable, else fall back to on-device ML Kit translation (label "translated on device").

### 7 Cast / "Play on" sheet
Opened by CAST (Now Playing) or the cast icon in the video overlay. Title **Play on** + mono context note (`CAST TO SONOS OR A TV · AUDIO STREAMS FROM STUDIO-PC, NOT THE PHONE`; when on Sonos: `SONOS STREAMS DIRECTLY FROM STUDIO-PC · 96 KHZ FILES PLAY AT 48 KHZ`; on TV: `VIDEO AND AUDIO ON THE TV · PHONE IS THE REMOTE`). **DONE** returns to where it was opened.
Rows (hairline separators): 10 dp indicator square (amber glowing = active, outlined = inactive) · name 15 sp · mono sub `SONOS ERA 300 · CAST · UP TO 24-BIT / 48 KHZ`. Demo outputs: This phone, Kitchen (Era 300), Living Room (Arc + Sub), Office (One SL), Living Room TV (Chromecast with Google TV).
When a Sonos room is active, every other Sonos room shows **`+ GROUP`** (outlined) / **`GROUPED ✓`** (amber) toggles. Below: `VOLUME · KITCHEN + LIVING ROOM` group volume slider (amber fill) and buttons **UNGROUP ALL** (only if grouped), **GROUP ALL ROOMS**, **BACK TO PHONE** (amber). Footer: grouping/volume are sent to the Sonos system so the group persists after the app closes. Selecting an output toasts `Playing on Kitchen`; the mini player prefixes `▸ Kitchen + Living Room ·`.
Implementation: Google Cast SDK (Cast Connect for Sonos & Chromecast) for playback; Sonos Control API for groups/volume; the receiver pulls media from the PC's HTTP endpoint (or the phone's local server for downloads).

### 8 Mini player (folded only)
64 dp bar above the 56 dp bottom nav, `#141414`, 1 px top hairline, 2 px amber progress line along the top. 44 dp art · title 14.5 sp 600 · mono `ARTIST · 0:34 / 3:48` · amber 44 dp play/pause square · next. Tap anywhere else → Now Playing sheet.

### 9 Bottom nav (folded) / Section control (unfolded)
Folded: 56 dp, mono 10.5 sp 0.12em uppercase `MUSIC · MOVIES · TV SHOWS · SYNC`, active item white with 2 px amber top inset line. Unfolded: segmented control (3 items) under the title; Sync reached via the STUDIO-PC status line.

### 10 Edit track / Suggest artist sheet
Header: art 52 dp, title, `Album · bits`, **Done**. `ARTIST` field showing current value (`EMPTY` amber tag if none). `SUGGESTED FICTIONAL ARTISTS`: optional style-prompt text field (`Optional style prompt — e.g. "a duo from a rainy port city"`), then 5 suggestion rows: name 15 sp 600, mono reason (`Mood · dream pop, slow`, `Title word · "Orchard"`, `Lyric · "…"`, `Existing artist · 2 songs share tag "synthwave"` with `IN LIBRARY` chip, `Solo-artist style · female vocal`, `From your style prompt · "rainy"`), trailing amber **Use**; the chosen row becomes amber-filled with **Applied**. Footer copy explains inputs and that the tag is written back to the file on STUDIO-PC. Algorithm reference: `suggestArtists()` in `player-core.js` (deterministic hash of the title → word banks per mood tag; keep the reasons).

### 11 Batch fix sheet
`Name 5 untagged songs — Tap a name to cycle alternatives`, **Cancel**. Rows: art, title, reason line, amber chip with the current pick and `↻` (cycles). **Apply all 5 names** amber button → toast `5 artists assigned`.

### 12 Import review sheet
`3 new files from STUDIO-PC — D:\Music\Suno · review artists before adding`, **Later**. Per file: mono filename, title + quality badge, tags · duration, proposed name (amber filled chip) + 3 alternative outlined chips, reason. **Add to library & write tags**.

### 13 Movies
3-col poster grid; badges: `PC` (not downloaded), green check (downloaded), amber progress line (in progress). Poster → **Movie detail**: 300 dp backdrop (gradient to background), 110 dp poster, title 28 sp, `2025 · 2h 04m · Sci-fi`, `Directed by … · Stream from STUDIO-PC`. Buttons **Resume · 1:20:36 left** (or Play / Watch again) + download square; progress line. Meta row: certificate chip, `★ 7.9` amber chip, `MATCHED BY PLEX · TMDB`, **FIX MATCH** link. Synopsis 14.5 sp / 1.5, `CAST` line.

### 14 TV Shows → Show page
Poster grid with `PC` badge. Show page: 280 dp banner, title 28 sp, `3 seasons · 24 episodes · Sci-fi · STUDIO-PC · D:\Video\TV`. **Continue · E4 Ballast** primary + download-season square. Meta row (cert, rating, `MATCHED BY PLEX · TVDB`, Fix match), synopsis, cast. Season chips (`Season 1` filled; others outlined `Season 2 · 4 ep`). Episode rows: 128×72 thumb with amber progress line · `E1 · 48 MIN` · title · 2-line summary · watched check · download state.

### 15 Video player (movies, episodes, music videos)
Black background. Top overlay: back, title + `1080p · Subtitles · Direct play · STUDIO-PC`, cast icon, `CC on/off`, speed `1×` (cycles 1 → 1.25 → 1.5 → 2 → 0.75). Centre: `-10` / 72 dp white play-pause disc / `+10`. Bottom: `0:01:02 · Chapter · 0:48:00`, amber scrubber with chapter tick marks, buttons **PiP · Queue · Full screen** (⇄ **Exit** when full). Overlay hides on tap. Subtitles rendered as centred text on `rgba(0,0,0,.6)`.
Below the 16:9 picture (portrait / unfolded, not full-screen): source line + gesture hint, `CHAPTERS` list (active row surface + amber time).
Full screen: cover screen rotates to landscape and the picture fills; inner screen letterboxes 16:9 on black. Hide system bars (immersive). Gestures: left-edge vertical swipe = brightness, right-edge = volume, horizontal drag = seek, double-tap sides = ±10 s.

### 16 Sync (PC)
Title **PC sync**. Server card: `STUDIO-PC`, `192.168.1.20 · Always on`, green `Synced 2 min ago`. `LIBRARIES · SAME FOLDERS AS PLEX` card: rows `Suno Music — D:\Music\Suno — 146 files · 9.8 GB` etc. (Music, Music Videos, TV Shows, Movies) each with a mode dropdown chip cycling **Stream / Download new / Download all**. `DOWNLOADS` card: storage bar (`3.1 GB downloaded · 51 GB free`, amber fill), toggles **Download on Wi-Fi only**, **Download quality** (`Original` ⇄ `Lossless 16/44`, "Original keeps 24-bit WAV; Lossless 16/44 saves ~60%"), **Translate lyrics** (`English` ⇄ `Off`, with the PC/on-device explanation), **Movie & TV metadata — From Plex** (read-only explanation: Plex first, TMDB/TVDB by filename otherwise), **Offline mode** toggle (hides non-downloaded items; playing one toasts `Not downloaded — you're offline`).

### 17 Track actions sheet (⋯ on any song)
Header: art, title, mono `ARTIST · ALBUM`. Rows (15 sp, hairline separators, mono right-hand hints): **Play next** (`TOP OF QUEUE`), **Add to queue** (`END OF QUEUE`), **Add to playlist…**, **Go to artist** (artist name), **Edit tags / suggest artist** (amber `NO ARTIST` chip when untagged), **Download** (PC files). Queue actions close the sheet and toast `Playing next · Title`.

### 18 Queue ("Up next") sheet
Header **Up next** + `PLAYING FROM YOUR QUEUE · LONG-PRESS TO REORDER`; buttons **SHUFFLE** (amber when on; toggling shuffles the remaining queue), **CLEAR**, **DONE**. Pinned "Now playing" row. Queue rows: mono index, art, title, `ARTIST · QUALITY`, **×** remove, drag handle. Empty state: `QUEUE IS EMPTY · USE ⋯ ON ANY SONG → PLAY NEXT`.
**Queue model** (Apple Music semantics): playing a song from a list replaces the queue with the rest of that list (in order, or shuffled); *Play next* inserts at the top; *Add to queue* appends; Next pops the head and pushes the current track to history; Prev restarts if > 3 s in, else goes back through history. Shuffle buttons: Songs tab (**Play / Shuffle**), Albums tab (**Shuffle all albums**), album/artist/playlist detail (**Shuffle**).

### 19 Artist navigation
Artist names are links everywhere: underlined in song-row meta (tap → artist page without starting playback), the album subtitle (`Velvet Static · 2026 ›`), and **Go to artist** in the actions sheet. The artist page lists every song on the phone/PC by that artist with Play / Shuffle.

### 20 Playlists
Playlists tab starts with a dashed **+ New playlist** row. **New playlist** sheet: name field, note `SAVED AS AN .M3U8 IN D:\Music\Suno\Playlists SO PLEX SEES IT TOO`, Cancel / Create. **Add to playlist** sheet (from ⋯): "+ New playlist" then existing user playlists with song counts; tapping adds and toasts. User playlists appear above the built-in/smart ones.

### 21 Pairing flow (first run, or Sync → "Pair again")
Five steps with a segmented progress bar (amber active, grey done, faint upcoming): **Find your PC** (mDNS discovery list: `STUDIO-PC — 192.168.1.20 · Signal Agent 1.0 · Plex detected`, plus "Enter address manually") → **Confirm the code** (6-digit mono code `482 · 913` shown on both devices; phone waits for approval on the PC) → **Choose libraries** (Plex libraries with paths and counts, checkboxes) → **Away from home** (radio cards: *Tailscale — recommended · free · works on mobile data*; *Home only*; *Port forward — advanced*) → **You're connected** (scan checklist: scanning Suno folder, reading Plex metadata, writing .lrc from Suno lyrics, suggesting names) → **Open library**. Back / Continue buttons; defaults: Suno Music = Download new, others = Stream.

### 22 Sync activity
From Sync → "Sync activity — N pending · M need attention". Rows: title, mono detail, state chip (`62%` amber outline + progress bar · `QUEUED` · `WI-FI` waiting · `DONE` green · `FAILED` amber filled with **RETRY / SKIP** · `DECIDE` amber filled for tag conflicts with **KEEP PHONE / KEEP PC** → `Kept PC version`). Row kinds: download, upload (phone-only file → D:\Music\Suno), tag write, watched-state → Plex. Footer: downloads resume; failed items retry 3× with back-off; conflicts only when both sides changed the same tag.

### 23 Sync page additions
- **Can't reach STUDIO-PC** banner (amber outline) with RETRY; status line becomes `STUDIO-PC unreachable · last synced 41 min ago`; downloads keep playing, streaming items are greyed.
- **Storage rules** card: *Remove watched episodes* (24 h after finishing), *Shows set to "Download new" — Keep next 3/5/1 episodes*, *Low storage — downloads pause at 5 GB free*.
- **Signal Agent on STUDIO-PC** card: *Remote access* (Tailscale), *Upload phone-only songs* (copy to D:\Music\Suno so Plex sees them), *Watched state → Plex* (SYNCED), *Pair again / add another PC*.

### 24 Search
Search icon (outlined square with magnifier) sits in the library header next to `+3 NEW`. Search screen: back + 44 dp bordered field (placeholder `Songs, artists, lyrics, movies, episodes…`, × clear). Empty query: `RECENT` chips + a mono note of what's indexed. With a query: mono summary `N RESULTS FOR "…"`, then grouped results in this order — **Songs** (art, title, `ARTIST · QUALITY · SOURCE`, ⋯ actions), **In lyrics** (italic matching line + song), **Artists** (round thumbs), **Albums**, **Playlists**, **Music videos** (16:9 thumbs), **Movies**, **TV shows** (2:3 thumbs), **Episodes** (`Show · S1E4 · 52 min`). Each group capped (6 songs, 4 others, 3 videos). Matching is case-insensitive substring over title, artist, album, synced-lyric lines, synopsis, cast, director and episode summaries. Offline: search downloads only. Empty: `NOTHING ON THE PHONE OR STUDIO-PC MATCHES`. Native: keep a local FTS index (Room FTS4) synced from the PC catalogue so search works offline and instantly.

## Signal Agent (PC) — what Claude Code needs to build
A Windows service (suggest Kotlin/JVM or Node/TypeScript; must run headless at login) exposing a local HTTPS API, discoverable via mDNS (`_signal._tcp`), paired with a 6-digit code exchange that yields a per-device token.
- `GET /libraries` — from Plex API (`/library/sections`) with paths, counts, types.
- `GET /items?library=&since=` — incremental catalogue (tracks, albums, artists, videos, movies, shows/seasons/episodes) with tags, quality (bit depth / sample rate / codec / container), disc & track numbers, ALBUM links for music videos, Plex metadata (poster/backdrop URLs, synopsis, cast, ratings, episode summaries), watched state.
- `GET /stream/:id` — range-request file streaming (direct play); `GET /download/:id?quality=original|16-44` — full file or FLAC transcode; resumable.
- `GET /lyrics/:id` — `.lrc` (+ `.en.lrc`); the agent writes `.lrc` from Suno's aligned lyrics and translates on scan.
- `POST /tags/:id` — write ARTIST etc. into the file; returns conflict if the file changed since the phone's version (compare mtime/hash).
- `POST /suggest-artists` — name suggestions (title, tags, lyrics, existing artists, optional style prompt) — port of `suggestArtists()` or a small LLM call.
- `POST /upload` — phone-only files into the library folder; `POST /progress` — watched/play progress → Plex (`/:/timeline` or scrobble).
- `GET /activity` — job queue with per-item state for the Sync activity screen; websocket for live progress.
Remote access: recommend Tailscale (the app stores the tailnet address as a second endpoint); "Home only" = LAN address only; port-forward = user-supplied host + Let's Encrypt.

## Unfolded (inner screen) layout
Root is a row: **left pane 316 dp** (library: title, status, segmented section control, sub-tabs, list; 1 px right hairline) and **right pane flex** showing, in priority: album/playlist/artist detail → show → movie → sync → otherwise the **persistent Now Playing** (same content as the sheet, 26 sp title, 60 dp lyric rows). Sheets (Edit, Batch, Import, Cast, Queue) render as a centred 480 dp dialog. No mini player and no bottom bar; the STUDIO-PC status line opens Sync. Video takes over the whole screen.

## Interactions & state
State (see `initState()` in `player-core.js`): `queue[]`, `history[]`, `shuffle`, `userPlaylists[]`, `paired`, `pairStep`, `pairLibs`, `remote`, `pcReachable`, `activity{}`, `conflictPick{}`, `autoRemove`, `keepEps`, `section`, `tab`, `screen` (`library | album | playlist | artist | show | movie | video | sync | activity | pair | search`), `query`, `detail`, `season`, `np` (track id), `playing`, `pos`, `sheet` (`null | np | queue | edit | batch | import | cast | actions | addto | newpl`), `lyrics`, `lyricMode` (`orig | both | trans`), `editId`, `chosen{trackId: name}`, `stylePrompt`, `videoId`, `vpos`, `vplaying`, `landscape` (= full screen), `ui` (overlay visible), `speed`, `subs`, `downloads{key: 0..1}`, `offline`, `wifiOnly`, `libMode{}`, `dlQuality`, `translateTo`, `output`, `group[]`, `volume`, `toast`.
Timers: playhead +0.5 s every 500 ms; downloads progress +7 %/tick; toast clears after 2.6 s.
Derived: per-item `srcLabel` (`On device | Downloaded | Downloading 42% | Stream from STUDIO-PC | Playing on Kitchen`), `unavailable` when offline and not downloaded.

## Data / backend notes
- **PC agent** (Windows service on STUDIO-PC): watches the Plex folders, serves files over HTTP on the LAN (and optionally via a tunnel/VPN when away), reads Plex's metadata (Plex API) for movies/TV, writes `.lrc` / `.en.lrc` sidecars, translates lyrics, runs the artist-name suggestion model, and writes chosen tags back to the audio files.
- Phone caches metadata locally (Room DB) so the library browses offline; downloads stored in app-private storage with quality option (original vs. 16/44 FLAC transcode by the PC).
- Track ordering from `DISCNUMBER`/`TRACKNUMBER` tags. Music videos attach to albums by `ALBUM` tag or folder co-location, or manual link.

## Assets
None bundled. Art/posters/backdrops from file tags and Plex. Fonts: Space Grotesk, JetBrains Mono (Google Fonts, OFL). Icons in the prototype are CSS shapes — use Material Symbols (cast, download, check, play/pause, skip, fullscreen, closed_caption, picture_in_picture) in the native app.

## Files
- `design/Player B Signal.dc.html` — the prototype (prop `mode`: `folded` | `unfolded`).
- `design/player-core.js` — data, state machine, artist-name suggester, output/grouping logic.
- `design/Signal Player.dc.html` — both device modes side by side.
- `design/android-frame.jsx`, `design/support.js` — prototype runtime (not for production).
- `screenshots/folded-01…21-*.png` — cover-screen states (incl. search, track actions, queue, sync activity, pairing); `screenshots/unfolded-01-library-and-now-playing.png` — inner-screen two-pane layout. Open the prototype for every other unfolded state.
