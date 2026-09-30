# Changelog

Every release of Signal Player is listed here. The newest version is at the top.
The GitHub release notes for each APK come from the matching section below.

## [1.0.5] - 2026-09-30

### Signal Agent on the PC runs quietly in the tray
- No more black terminal window. Signal Agent now lives as a small **icon in the system tray** by the clock, like Plex.
  - Right-click it for **Open Signal Agent**, **Start with Windows**, **Update** and **Quit**.
  - Double-click it to open the setup page.
- The browser only opens by itself the first time, for setup. After that you can close the tab; the agent keeps running.
- A tray notice pops up when a phone asks to pair. Click it to approve.
- Messages are written to a log file (`%APPDATA%\SignalAgent\data\agent.log`) instead of a window.

### The PC agent updates itself
- When a new version is out, the tray shows a notice. Click it to install.
  You can also use the banner on the setup page, or **Settings → PC sync → Update** in the phone app.
- Each download is checked against its published fingerprint (SHA-256) before it replaces the old program.
  The agent restarts by itself in a few seconds, and your settings and paired phones stay.
- **One last manual step:** download this version's `SignalAgent.exe` once and replace your current one.
  Every update after that installs itself.

### Plex setup
- If you paste a Plex token and leave the address empty, `http://127.0.0.1:32400` (Plex on the same PC) is used automatically.
- After saving, the setup page says whether Plex connected ("Connected to Plex · 4 libraries") or what went wrong
  (wrong token, or Plex not answering).

## [1.0.4] - 2026-09-30

### Sorting
- **Songs, Albums, Artists, Movies and TV Shows** each have a **Sort** row at the top of the list. Tap an option to sort by it, and tap it again to flip the order (↑ / ↓).
  - **Songs:** A–Z, Artist, Album, Date added, Year, Length
  - **Albums:** A–Z, Artist, Date added, Year
  - **Artists:** A–Z, Most songs, Date added (newest song)
  - **Movies:** A–Z, Date added, Year, Rating, Length
  - **TV Shows:** A–Z, Date added, Year, Rating
- A–Z ignores a leading "The" or "A", so "The Weeknd" sorts under W. Songs and albums with no year or artist always go to the end.
- Each tab remembers its sort.

## [1.0.3] - 2026-09-30

### Edit every song detail
- The song editor (**Edit** on Now Playing, or **⋯ → Edit song details / artist**) now covers title, artist, album, album artist, genre, year, and track and disc number.
- For songs on your PC, the changes are written into the file on the PC. For songs on the phone, they're saved in Signal.
- The fictional artist-name suggester is still in the same sheet, below the details.

### Album art and artist pictures
- On any album page, tap **Change album art**. On any artist page, tap **Change artist picture**.
- **Choose an image** from your phone, for example art you made. It's cropped square automatically.
- Or **generate one**: six designs made from the name (Horizon, Panels, Sun, Stripes, Glow, Mono). Tap **More designs** for new variations.
  Artist pictures use the artist's initials and show as a round photo on the artist page and in the Artists list.
- Album art for PC albums is also saved as `cover.jpg` in the album folder on the PC, so Plex shows it too.
  This needs the new **SignalAgent.exe** from this release.
- Custom art replaces the song's built-in cover everywhere in Signal. **Remove custom art** goes back to the original.

## [1.0.2] - 2026-09-30

### Add your own lyrics (great for Suno songs)
- New lyrics editor. Open it with **Add lyrics** on the Now Playing lyrics screen, or **⋯ → Add / edit lyrics** on any song.
- **Paste** the lyrics you copied from the Suno app. Section labels like `[Verse]`, `[Chorus]` and `(Instrumental)` are removed automatically.
- **Tap to sync:** the song plays from the start, and you tap the big button as each line begins.
  - **Undo** jumps back a few seconds so you can redo a line.
  - **Finish now** spreads out the lines you haven't tapped yet.
- Saved lyrics scroll and highlight just like an `.lrc` file. They take priority over lyrics found online.
- For songs on your PC, the app also writes a real `.lrc` file next to the song. If the song already had one, the old file is kept as `.lrc.bak`.
- Already have an `.lrc` with timestamps? Paste it and save; no tapping needed.
- Update **SignalAgent.exe** on the PC to this version so the `.lrc` file can be written there.

## [1.0.1] - 2026-09-30

### New Settings tab
- The bottom **Sync** tab is now **Settings**. PC sync is the first item in Settings, and your phone folders are listed right below it.
  On the unfolded screen, open Settings with the gear next to search.

### Lyrics without .lrc files
- Songs without a `.lrc` file now get lyrics automatically from **LRCLIB**, a free online lyrics library with time-synced lyrics for millions of songs.
  The app matches by title, artist and song length. Lyrics are saved on the phone, so they work offline afterwards.
- Lyrics that aren't time-synced still show, with a "Not time-synced" note.
- Turn this off in **Settings → Lyrics** if you prefer. Songs with no artist aren't looked up, to avoid wrong matches, so add an artist first. Giving a song an artist makes Signal look again.

### Choose which phone folders to use
- In **Settings**, set each folder on your phone to Music, Music Videos, Movies or TV Shows. You can also get there from the prompt in your library.
  Set each folder to Music, Music Videos, Movies or TV Shows. Folders you leave Off stay hidden, so ringtones,
  recordings and the phone's sample music no longer show up.
- Choosing a folder includes the folders inside it.
- Videos on the phone can now appear as music videos, movies or TV shows. TV episodes are grouped by show and season from names like "Show - S01E02 - Title".

### Sonos and Chromecast
- Songs and videos stored on the phone now play on Sonos and Chromecast. The app shares them with the speaker over your Wi-Fi.
  Before this, Sonos showed "Play failed (500)" for songs that weren't on the PC.
- Downloaded songs also play on speakers when the PC is off.
- The "Pause failed" error no longer appears when switching speakers.

### Easier PC setup
- The PC side is now a single download: get `SignalAgent.exe` from this release and double-click it. You don't need Node.js or a command line anymore.
- Setup happens in your browser. Pick your music, music video, movie and TV folders with a folder browser, and optionally add Plex.
- A "Start with Windows" switch keeps the agent running in the background after you restart.
- The setup page shows your PC's addresses, so you can type one into the phone if it isn't found automatically.
- The phone's Sync screen now points to the new download.

## [1.0.0] - 2026-09-29

First release of Signal Player: a music, movie and TV player for your phone that syncs with your PC.

### Music
- Browse Recently Added, Songs, Albums, Artists, Playlists and Music Videos, using songs on the phone and on your PC together.
- Lossless and Hi-Res Lossless badges show bit depth and sample rate for each song.
- Albums play in disc → track → title order and show their music videos under the songs.
- Now Playing has a waveform scrubber and an Up Next list. The "Up next" queue lets you play next, add to the queue, reorder, remove, shuffle and clear.
- Synced lyrics scroll with the song, and tapping a line jumps to it. Songs in other languages can show the original, the English translation, or both.
- The fictional artist-name suggester works on one song, a batch of untagged songs, or new files the PC just found. On PC songs the chosen name is written back to the file.
- Create your own playlists. Two smart playlists are built in: "Needs an artist" and "Recently added".
- Music keeps playing in the background, with lock-screen and notification controls.

### Movies & TV
- Movie and show pages use Plex metadata: posters, synopsis, cast and ratings. Shows can be browsed season by season.
- The video player supports full screen, picture-in-picture, subtitles, chapters, playback speed, and double-tap to skip ±10 s. Swipe on the left edge for brightness and on the right edge for volume.
- Resume points and watched state sync back to Plex.

### Play on speakers and TVs
- Play on Chromecast and Google TV devices.
- Play on Sonos rooms over your home network, with grouping and group volume.

### Sync with your PC (needs Signal Agent on the PC)
- Pair with a 6-digit code. The phone finds your PC automatically on the same Wi-Fi.
- Each library can be set to Stream, Download new or Download all. You can limit downloads to Wi-Fi only, choose Original or Lossless 16/44 quality, and use offline mode.
- Downloads resume where they stopped and retry failed items. The Sync activity screen shows each item and lets you settle tag conflicts.
- Storage rules can remove watched episodes, keep the next few episodes downloaded, and pause downloads when storage is low.
- Works away from home through Tailscale or a port-forwarded address.

### Search
- Search songs, artists, albums, playlists, lyrics, music videos, movies, shows and episodes all at once.
