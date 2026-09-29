# Signal Agent (PC)

A small Node.js server that runs on your Windows PC and serves your music, music-video, movie and TV
folders to the **Signal Player** Android app. It implements the HTTP contract in
[`../docs/API.md`](../docs/API.md): catalogue, Range streaming, downloads (optional 16-bit/44.1 kHz
FLAC transcode), `.lrc` lyrics, artwork, subtitles, artist tag edits, artist-name suggestions,
uploads, watched state (forwarded to Plex) and a job list for the app's Sync activity screen.

- Plain JavaScript (ES modules), Node.js 20 or newer, no build step.
- Dependencies: `music-metadata` (tags, quality, embedded lyrics and covers), `bonjour-service`
  (mDNS `_signal._tcp`), `node-id3` (MP3 tag writes).
- Optional tools on `PATH`: **ffmpeg / ffprobe** (FLAC/WAV/M4A tag writes, 16-44 transcodes, video
  resolution/HDR/chapters). Everything works without them. See [Without ffmpeg](#without-ffmpeg).
- Optional **Plex**: enriches movies and TV with Plex metadata and artwork, and syncs watched state.

## Setup

1. **Install Node.js LTS** (20 or newer) from <https://nodejs.org/>. Accept the defaults, which put
   `node` and `npm` on your PATH.
2. **Install the agent's dependencies.** Open PowerShell in this `agent` folder and run:
   ```powershell
   npm install
   ```
3. **Create the config.** Run `npm start` once. This creates `signal-agent.config.json` in this
   folder. Stop the agent with Ctrl+C, then edit the file. The fields are described under
   [Configuration](#configuration). `signal-agent.config.example.json` is a filled-in example.
4. **Start the agent:**
   ```powershell
   npm start
   ```
   The first scan runs straight away. You can follow its progress on the admin page.
5. **Pair your phone.** In Signal Player, open *Sync → Pair with a PC*. The agent is found
   automatically over mDNS, or you can type `192.168.x.y:8765`. The phone shows a 6-digit code. You
   can approve it in either place:
   - on the admin page at **<http://localhost:8765/>**, which only opens from this PC, by clicking **Approve**
   - in the agent console, by typing `approve 482913`

   Requests expire after 5 minutes. You can revoke paired devices on the admin page.
6. **Allow the port through Windows Firewall.** Run this in an **elevated** PowerShell or Command Prompt:
   ```powershell
   netsh advfirewall firewall add rule name="Signal Agent" dir=in action=allow protocol=TCP localport=8765
   ```
7. **Start the agent at logon (optional).** Run this in an **elevated** PowerShell in this folder:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\install-startup.ps1
   ```
   This registers a Scheduled Task named **"Signal Agent"**. It runs `node <this folder>\src\index.js`
   at logon with no window, and restarts it if it fails. If your media is on mapped network drives,
   add `-Interactive`. Use `-ConfigPath` for a config file somewhere else. To remove the task, run
   `.\uninstall-startup.ps1`. Logs go to `data\agent.log`.

### Away from home: Tailscale

The simplest way to reach the PC from anywhere, without opening router ports, is
[Tailscale](https://tailscale.com/):

1. Install Tailscale on the PC and on the phone, and sign in to both with the same account.
2. The PC gets a `100.x.y.z` address, which is listed in `GET /api/info` → `addresses` and on the admin page.
3. In Signal Player, add that address, or the MagicDNS name such as `studio-pc:8765`, as the *Remote* endpoint.

The agent listens on all interfaces, so nothing else needs configuring. The firewall rule from step 6
also covers the Tailscale adapter.

## Configuration

`signal-agent.config.json` is created with defaults on the first run. By default it lives next to the
`data` directory in this folder. To use another file, pass `--config <file>` or set the
`SIGNAL_AGENT_CONFIG` environment variable.

```json
{
  "name": "STUDIO-PC",
  "port": 8765,
  "dataDir": "./data",
  "libraries": [
    { "id": "l1", "name": "Suno Music",   "type": "music",       "path": "D:\\Music\\Suno" },
    { "id": "l2", "name": "Music Videos", "type": "musicvideos", "path": "D:\\Music Videos" },
    { "id": "l3", "name": "TV Shows",     "type": "tv",          "path": "E:\\TV" },
    { "id": "l4", "name": "Movies",       "type": "movies",      "path": "E:\\Movies" }
  ],
  "plex": { "url": "http://127.0.0.1:32400", "token": "" }
}
```

| Field | Default | Meaning |
|---|---|---|
| `name` | computer name (`os.hostname()`) | Shown in the app and advertised over mDNS. |
| `port` | `8765` | HTTP port. |
| `dataDir` | `./data` | Holds state (`state.json`: agent id, paired devices with **hashed** tokens, tag overrides, progress), the scan cache (`catalog.json`) and `agent.log`. Relative paths resolve against the config file's folder. |
| `libraries` | `[]` | Each entry is `{id, name, type, path}`. `type` is one of `music`, `musicvideos`, `movies`, `tv`. Keep the `id` values stable, because item ids are derived from them. |
| `plex` | empty | `url` is your Plex server URL (e.g. `http://127.0.0.1:32400`). For `token`, see Plex's article *"Finding an authentication token / X-Plex-Token"*. Leave either field empty to turn Plex off. |
| `host` | `0.0.0.0` | (Optional) The address to bind to. |

If `libraries` is empty and Plex is configured, the agent uses the Plex library sections and their
folder paths. It logs them so you can copy them into the config.

## What gets scanned

- **Audio**: `wav flac m4a mp3 aac ogg opus aiff`. The agent reads tags, disc and track numbers,
  duration, codec, bit depth, sample rate, genres, embedded cover and embedded lyrics.
- **Video**: `mp4 mkv m4v mov webm avi`.
  - **Music videos** are attached to an album through their `ALBUM` tag. If that tag is missing, the
    album comes from audio tracks in the same folder, or from a folder named like an existing album.
    A file name like `Artist - Title.mp4` fills in the artist and title.
  - **Movies without Plex** are named from `Title (Year).ext` (scene names like `Title.2025.1080p…`
    also work). The poster comes from `<name>-poster.jpg`, or from `poster.jpg`/`folder.jpg` in the
    movie's own folder. The backdrop comes from `<name>-fanart.jpg`, or from `fanart.jpg`/`backdrop.jpg`.
    `matchedBy` is `"FILENAME"`.
  - **TV without Plex** is read from folders like `Show (Year)/Season 01/Show - S01E02 - Title.ext`,
    or from `1x02` style names. The show poster is `poster.jpg`/`folder.jpg` and the backdrop is
    `fanart.jpg`, both in the show folder. Episode thumbnails are `<name>-thumb.jpg`.
  - **With Plex**, movies and episodes are matched by file path. They then get Plex's title, year,
    summary, cast, rating, content rating, director and artwork (proxied through `/api/art`), plus
    watched state. `matchedBy` is `"PLEX · TMDB"` or `"PLEX · TVDB"`.
  - **Subtitles** are `.srt` or `.vtt` sidecars, e.g. `Movie.srt`, `Movie.en.srt` or `Movie.eng.forced.srt`.
- **Lyrics**:
  - `<file>.lrc` is read with timestamps like `[mm:ss.xx]`. A line can carry several timestamps, and
    `[offset:]`, `[la:es]` and `[lang:es]` tags are honoured.
  - `<file>.en.lrc` is the translation.
  - Otherwise embedded lyrics are used. Unsynced lyrics get `t = index × 4 s`.
  - The language comes from the `[la:]` tag, or else a small Spanish/Portuguese/French/German
    stopword heuristic, falling back to `en`.
- **Item ids** are a short hash of the library id plus the file's relative path, so they stay stable
  across restarts and rescans.
- **Rescans** are incremental: only files whose modification time or size changed are re-read. They
  run at startup, every 10 minutes, a few seconds after a file change (via `fs.watch`), on
  `POST /api/rescan`, and from the admin page's **Rescan** button.

## Without ffmpeg

| Feature | With `ffmpeg` on PATH | Without |
|---|---|---|
| Artist edits for MP3 | Written to the ID3 tag (node-id3) | Same |
| Artist edits for FLAC/WAV/M4A/OGG/… | Streams are copied to a temporary file with new metadata, which then replaces the original | Stored as an override in `data/state.json` and shown in the catalogue, so the phone still sees the new artist. The override is dropped once the file itself changes. |
| `download?quality=16-44` | Lossless audio is streamed as a 16-bit/44.1 kHz FLAC transcode, without Range support | The original file is served with the header `X-Signal-Quality: original` |
| Video width/height/HDR/chapters | From `ffprobe` | Width and height come from MP4/MKV headers. HDR is `false`. Chapters come from MP4/MKV where present. |

## Console commands

When run in a terminal, the agent accepts these commands:

- `approve <code>` and `deny <code>`
- `pending` and `devices`
- `revoke <deviceId>`
- `rescan` and `status`
- `quit`

## Development

```bash
npm install
npm test          # node --test: LRC parsing, Range parsing, suggester, filename parsing, HTTP integration, mocked Plex
npm start -- --config /path/to/test-config.json
```

Source layout (`src/`):

| File | Contents |
|---|---|
| `index.js` | Entry point and console |
| `agent.js` | Wiring, mDNS, timers |
| `server.js` | HTTP routes and admin endpoints |
| `catalog.js` | Scan, cache and catalogue build, tags, uploads, progress |
| `media.js` | Metadata and sidecars |
| `lyrics.js` | LRC parsing and language detection |
| `filenames.js` | Movie and episode name parsing |
| `plex.js` | Plex integration |
| `suggest.js` | Port of `suggestArtists()` from `design/design/player-core.js` |
| `range.js` | Range header parsing |
| `state.js` | Tokens, pairing, overrides |
| `activity.js` | Job list |
| `tags.js` | Tag writes |
| `admin.js` | Admin page |

Security notes:

- Tokens are 32 random bytes, stored as SHA-256 hashes.
- The admin page and its `/admin/*` endpoints only answer requests from `127.0.0.1` or `::1` with a
  `localhost` Host header.
- The admin actions also require an `X-Signal-Admin` header, which blocks cross-site form posts.
