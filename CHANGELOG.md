# Changelog

Every release of Signal Player is listed here. The newest version is at the top.
The GitHub release notes for each APK come from the matching section below.

## [1.0.1] - 2026-09-30

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
