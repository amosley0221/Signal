# Signal Player

A personal Android media player for the Galaxy Z Fold. It combines Apple-Music-style music with Plex-style movies and TV,
plays what's on your phone, and streams or downloads from your PC.

## Install on your phone
1. On the phone, open **[Releases](https://github.com/amosley0221/Signal/releases/latest)** and download `Signal-x.y.z.apk`.
2. Open the file. If asked, allow your browser or file manager to install unknown apps.
3. **Updates:** download the newer APK from Releases and open it. It installs over the old version, and your library,
   downloads and settings are kept. You don't need to uninstall.

Each release lists what changed. The full history is in [CHANGELOG.md](CHANGELOG.md).

## Set up syncing with your PC
Signal Player talks to **Signal Agent**, a small program that runs on your Windows PC and serves the same folders that Plex uses.
See [agent/README.md](agent/README.md) for setup: install Node.js, run `npm install`, then `npm start`, and approve the phone
at http://localhost:8765. The first time you open the app it offers to find and pair your PC. You can also skip pairing and use only the music on your phone.

## Features
- Music: lossless and hi-res badges, synced and translated lyrics, queue, playlists, and a fictional artist-name suggester for untagged Suno songs.
- Movies and TV with Plex metadata. The video player has picture-in-picture, subtitles, chapters and gestures.
- Play on Chromecast / Google TV and Sonos, including Sonos grouping and group volume.
- Downloads for offline playback, following per-library rules.
- Layouts for both the folded cover screen and the unfolded inner screen.

## Development
- Android: `./gradlew assembleDebug` (JDK 17, Android SDK 35). Unit tests: `./gradlew testDebugUnitTest`.
- Agent: `cd agent && npm install && npm test`.
- Releases are built by GitHub Actions. The process is described in [CLAUDE.md](CLAUDE.md).
