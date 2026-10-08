# Changelog

Every release of Signal Player is listed here. The newest version is at the top.
The GitHub release notes for each APK come from the matching section below.

## [1.0.53] - 2026-10-08

- **Fixed: some songs played with no sound.** Apple Lossless (ALAC) songs, the lossless .m4a files from iTunes and Apple Music such as My Beautiful Dark Twisted Fantasy, need a decoder the phone doesn't have. The player then ran through the song in silence. Signal now includes its own decoder (FFmpeg) for these and any other audio format the phone can't play. If a song still can't be played, Signal says so and skips it instead of playing silence.

## [1.0.52] - 2026-10-08

- **AirPods listening mode in Now Playing.** With LibrePods installed and AirPods connected, Now Playing shows **Off · Transparency · Adaptive · Noise Cancel** under the play controls. Tap one to switch modes without leaving Signal. Signal asks LibrePods to make the change, so LibrePods must be installed and connected to your AirPods. The buttons hide when no headphones are connected or when you're playing on Sonos or a TV.

## [1.0.51] - 2026-10-08

- **Autoplay (like Apple Music's ∞).** When an album, playlist or your queue is about to end, Signal adds similar songs from your library and keeps playing until you stop it. It picks by what you've been listening to: the same artist, artists who appear on songs with them (features), similar genres and a similar era. Recent songs count most, so it follows along as the mood changes. It doesn't repeat songs you've just heard, plays at most two songs by one artist in each batch of ten, and never plays the same artist twice in a row. The queue shows these songs under "∞ Autoplay". Turn it on or off with **∞ Autoplay** in the queue or under Settings → Playback. It's on by default.

## [1.0.50] - 2026-10-08

- **Store has its own tab.** The Qobuz store moved from Playlists to a new **Store** tab in Music, to the right of Music Videos. The store fills the area under the tabs. Signal remembers the page you were on when you switch to another tab and come back.
- "More by … on Qobuz" on artist pages and the "Qobuz ›" links for songs a playlist import couldn't find now open in the Store tab.

## [1.0.49] - 2026-10-07

- **Buy music on Qobuz from inside Signal.** In Music → Playlists, tap **Buy music on Qobuz** to open the Qobuz store in Signal. Sign in to your Qobuz account (use email and password, since Google sign-in doesn't work inside apps) and buy as usual. Payment goes straight to Qobuz. Signal never sees your card.
- **Purchases go straight into your library.** On the download page of a purchase, tap **Download** next to the album or a song. Signal saves it to Music/Signal on the phone, unzips albums, and adds the songs to your library. Progress shows at the bottom of the store.
- **Downloaded in Chrome instead?** Tap **Add file** in the store and pick the downloaded ZIP or song. Signal adds it the same way.
- **"Find on Qobuz" shortcuts:** an artist's page has "More by … on Qobuz", and songs a playlist import couldn't find have a "Qobuz ›" link.

## [1.0.48] - 2026-10-07

- **Import playlists from Apple Music.** In Music → Playlists, tap **Import from Apple Music** and paste a playlist's share link. You can also tap Share in Apple Music and pick Signal. Signal reads the song list and matches each song to your library by title and artist, ignoring "(feat. …)", "Remastered" tags and accents. You can rename the playlist before creating it, and see the songs you don't have.
- **Import exported playlist files too.** For long or private playlists, export the playlist from the Apple Music or iTunes app on your PC (File → Library → Export Playlist, as .txt or .xml) and pick the file in the same screen. .m3u playlists work as well.

## [1.0.47] - 2026-10-07

- **Sonos bass, treble and loudness.** While playing on Sonos, open **Play on** and tap **Bass & treble** under the volume to adjust each speaker's bass and treble (-10 to +10) and switch Loudness on or off. When rooms are grouped, each room gets its own controls. These are the same settings as the EQ in the Sonos app, so they stay on the speaker. **Reset** puts a room back to Sonos's defaults.

## [1.0.46] - 2026-10-07

- **Equalizer.** Tap **EQ** at the top of Now Playing for a 10-band equalizer (31 Hz to 16 kHz, ±12 dB) with presets: Bass Boost, Bass Reducer, Treble Boost, Vocal, Hip-Hop, R&B, Pop, Rock, Electronic, Jazz, Classical, Acoustic and Late Night. You can also drag the bands to make your own curve. It applies to music played on the phone, including headphones, AirPods and car Bluetooth, and is remembered between sessions. Sonos and Cast play the file themselves, so the EQ doesn't reach them.

## [1.0.45] - 2026-10-02

- **Sonos: songs that are on the phone always play.** If a Sonos speaker can't fetch a song from the PC, Signal now sends it the copy downloaded on the phone instead, rather than giving up with "couldn't load". Once that happens, later songs go straight to the phone copy.
- **Signal follows the PC to a new home-network address.** Signal remembered the PC's home address from when you paired and sent speakers there forever. If the router later gave the PC a different address, the phone still reached the PC over Tailscale, but Sonos couldn't. Signal now updates the address on its own.

## [1.0.44] - 2026-10-01

- **TV shows with the same name no longer replace each other.** Plex has two shows called "Monster (2022)": the Dahmer series and the one with the Ed Gein and Lizzie Borden seasons. Signal was showing the Dahmer series twice and hiding the other one. Both now appear, each with its own seasons and poster.

## [1.0.43] - 2026-10-01

- **Sonos:** the on-screen volume message shown when you press the volume buttons ("Office + 1 volume · 21") now shows the speaker's real new level, reported back by Sonos, instead of the app's guess.

## [1.0.42] - 2026-10-01

- **Sonos group volume fixed.** With rooms grouped, a volume button press could make one room jump down (e.g. Office 24 → 8) while another barely moved. Signal now asks the group to note each room's share before every change, as Sonos requires. Volume button presses now nudge the whole group up or down together, keeping each room's level relative to the others, and the slider shows the group's real new volume.
- The group volume is re-read after grouping or ungrouping, and fast button presses are sent one at a time.

## [1.0.41] - 2026-10-01

### Sonos groups
- **Per-room volume:** when rooms are grouped, **Play on** shows a slider for each room under the group slider, like the Sonos app. The group slider and the phone's volume buttons still move the whole group together, and the room sliders refresh to match.
- **Group name:** while grouped, the speaker button, "Playing on" and the mini player read **"Office + 1"** (or + however many rooms are grouped).

## [1.0.40] - 2026-10-01

- **Sonos volume buttons, take two.** While music plays on a Sonos speaker, the phone's volume buttons now change the speaker's volume whenever Signal is open, and the new volume shows briefly on screen. In 1.0.39 Android kept the buttons on the phone, because Signal's own player was paused while the speaker played.
- In the background (screen off or another app open), Signal now tells Android it's playing on the speaker. This lets the volume buttons reach the speaker there too, and the notification's play/pause now controls the speaker.

## [1.0.39] - 2026-10-01

- **Sonos:** the phone's volume buttons now control the speaker while music plays on Sonos, including the whole group when rooms are grouped. The phone's volume panel shows the speaker's volume, and the slider in **Play on** follows along. On the phone speaker or headphones, the buttons work as usual.

## [1.0.38] - 2026-10-01

- **Sonos:** fixed songs sometimes not starting on a speaker until something else nudged it, followed by a "couldn't load this song" message. The first Play command could arrive before the speaker had finished loading the song, and the speaker ignored it. Signal now sends Play again every few seconds, up to four more times, before deciding a song couldn't load. It also keeps watching the speaker even if that first Play is refused.

## [1.0.37] - 2026-10-01

### More reliable Sonos and Cast
- Fixed Sonos (and TVs) sometimes failing with "couldn't load this song". When the phone reached the PC over Tailscale (for example, because the PC was slow to answer at home), the speaker was given the PC's Tailscale address, which speakers can't reach. Speakers and TVs now always get the PC's home-network address.
- A speaker now has 10 seconds instead of 4 to start a song before Signal reports that it couldn't load it, so slower starts aren't cut off.

## [1.0.36] - 2026-10-01

- **Music › Recently Added** now shows large album covers, like the Albums tab, instead of a song-by-song list. Each album appears once, newest first, under "Added this week" and "Earlier". Songs without an album get their own tile marked **Single**; tap it to play. This works folded and unfolded, with more tiles per row on the wider screen.

## [1.0.35] - 2026-10-01

### Plex-style Movies and TV Shows
- **Movies** and **TV Shows** now have tabs, like Music:
  - **Recommended:** rows of picks. Movies: Continue watching, Recently released, Recently added and your top genres. TV Shows: Continue watching, Up next, Recently added, Start watching (shows you haven't begun) and your top genres. Genre rows have **See all ›**.
  - **Browse:** every title in a grid, with sorting and the A–Z / scrub strip.
  - **Categories:** pick a genre to see everything in it.
- **Unfolded:** the Music / Movies / TV Shows bar is gone. Tap the big section title ("Movies ▾") to switch sections.

## [1.0.34] - 2026-10-01

- The "songs have no artist" card has moved from the top of **Songs** to **Settings → Music**, with the same **Fix** button. The **Needs an artist** smart playlist is still under Playlists.

## [1.0.33] - 2026-10-01

### Unfolded: the library uses the whole screen
- On the unfolded Fold, the library now fills the whole screen until you open something, with more posters and albums per row. It no longer sits beside an empty "Nothing playing" panel.
- Opening an album, show, movie or artist splits the screen: the library on the left, that page on the right. Back returns to the full-width library, which keeps your place.
- While music plays, a mini player sits at the bottom. Tapping it opens Now Playing on the right; the arrow at its top left (or Back) closes it again.

## [1.0.32] - 2026-10-01

### Download a whole season or series
- A show's page now has **Download Season N** and **Download All N seasons** buttons under the season tabs. Each shows the number of episodes and how much space they need. Once started, the button shows how many are downloaded ("7 / 12 downloaded"), then a check mark when done. These replace the small unlabeled download icon next to Play.
- **Remove watched episodes** now only removes episodes you finished in Signal after downloading them, a day after you watched them. Before, downloading a series you had already watched in Plex would have removed most of it again the next day.

## [1.0.31] - 2026-10-01

### Much faster TV and movie scans
- **Signal Agent (PC):** a video that Plex already knows no longer has to be opened during a scan. Its length and resolution come from Plex instead. Without ffprobe on the PC, reading each large video file could take several seconds or more, which made a big TV folder take hours. Videos Plex doesn't know are still read from the file.
- Update the agent: the scan picks up where it left off, and the remaining episodes go quickly.
- Chapter lists for these videos aren't read from the file anymore, so the player may not list chapters for them.

## [1.0.30] - 2026-10-01

### Signal Agent restarts itself instead of staying closed
- **Signal Agent (PC):** if something unexpected goes wrong, the agent now starts a fresh copy of itself within a few seconds, and the phone reconnects on its own. Before, it simply closed and stayed closed until you started it again.
- A dropped connection, such as switching videos while one is still loading or the phone leaving Wi-Fi, is no longer treated as a fatal error.
- To avoid a restart loop, the agent stops restarting itself after 3 crashes within 10 minutes.
- Each crash is written to `last-crash.txt` and `agent.log` in `%APPDATA%\SignalAgent`, so the cause can be found and fixed.

## [1.0.29] - 2026-10-01

### Continue Watching follows what you watch in Signal
- **Continue Watching** now shows the movies and episodes you started in Signal and didn't finish, from the last 16 weeks. Before, it mostly followed Plex's own row, so a movie you started in Signal could be missing.
- **Up Next** shows the next episode of shows you've been watching in Signal. That includes a newly added episode after you had caught up.
- New setting: **Settings → Include Plex's Continue Watching** (off by default). Turn it on to also see what's in Plex's row, such as things you watched on the TV or in the Plex app.

## [1.0.28] - 2026-10-01

### Movies and TV from the PC start faster
- **App:** background downloads now wait while a movie or episode streams from the PC, not just while music plays. Before, up to three song downloads competed with the video for the PC's disk and Wi-Fi.
- **App:** the video player shows a spinner and "Loading from PC…" while it loads, instead of a play button that looks like nothing is happening.
- **App:** if a video can't play, the player now says why, instead of staying on a black screen. The two main cases are a format the phone can't decode (try Cast to a TV) and a PC that can't be reached. A **Try again** button reloads it.
- **Signal Agent (PC):** files are read in 1 MB pieces instead of 64 KB, which keeps a movie flowing from a busy hard drive. While something is streaming, the library scan now reads only one file at a time.

## [1.0.27] - 2026-10-01

### Album covers load once and stay
- **App:** covers are kept on the phone until the song file changes. Before, the PC told the phone to keep them for only one hour, so after that every cover was downloaded again. When the PC was busy scanning, many of those downloads failed and showed blank squares.
- **App:** a cover is reused whether the phone reaches the PC at home or over Tailscale. Before, switching between the two downloaded every cover again. The cover cache on the phone is now 512 MB.
- **Signal Agent (PC):** covers stored inside song files are extracted once and saved, so they load instantly afterwards. Before, the song file was read again for every cover request.
- **Signal Agent (PC):** more album covers are found in folders. This includes Windows Media Player's "AlbumArt_…_Large.jpg" files, any image named like a cover, and a folder's only image. Update the agent to get this; the covers show up after its next check for changes.

## [1.0.26] - 2026-10-01

### Artists stay separate on combined albums
- After combining a soundtrack under "Various Artists", each performer (e.g. Buck Tillery) now keeps their own entry in **Artists** instead of being listed under "Various Artists". The album itself stays combined in **Albums**.
- The Artists tab now uses each song's own artist. The album artist is used only when it is one of the song's artists (e.g. "Metro Boomin & 21 Savage" on a 21 Savage album) or when a song has no artist.
- An artist's page now also shows albums they appear on, such as a soundtrack.

## [1.0.25] - 2026-10-01

### Combine soundtracks and compilations into one album
- When other albums have the same name under different artists (a soundtrack like "Vacancy: The Soundtrack"), the album page now offers **Combine**. It gives every song the same album artist ("Various Artists", or any name you type) so they show as one album. The album art you already applied is kept for the combined album.
- For songs on the PC, the album artist is also written into the files, so Plex and other players group them the same way.
- **Adding more songs later:** when you give a song an album name that already has an album artist, the song joins that album automatically and gets its cover. You don't need to fill in the album artist yourself.

## [1.0.24] - 2026-09-30

### Sonos grouping no longer crashes the app
- Fixed a crash when a Sonos speaker refused a request, for example asking a room for its volume right after grouping or ungrouping it. The request now fails quietly and the app keeps going.
- Every Sonos action in **Play on** (group, ungroup, group all, volume) is now protected the same way, and so are background tasks across the app. A speaker or the PC turning down a request can no longer close Signal.

## [1.0.23] - 2026-09-30

### Fast scrolling in every sort
- Long lists now always have a strip on the right edge, whatever the sort. This includes Movies and TV Shows, which are sorted by Date added unless you change them.
  - Sorted by name, artist or album: the A–Z letters, as before.
  - Sorted by Date added, Year, Rating, Length or Most songs: a scrub bar. Drag it to move through the list, and a bubble shows where you are, such as "Mar 2025", "2019", "8.1" or "1h 52m". The amber thumb shows your position in the list.
- Sorting artists by Date added now also counts songs where they are the main artist with featured guests.

## [1.0.22] - 2026-09-30

### Continue Watching matches Plex
- **Continue Watching** and **Up Next** for PC movies and shows now follow Plex's own Continue Watching row. Anything Plex has dropped (old, or removed with "Remove from Continue Watching") no longer shows. Something you just started in Signal appears right away and stays once Plex lists it.
- Videos only on the phone (and PCs without Plex) drop off after 16 weeks without watching, like Plex's default.
- A show's page still offers the episode where you left off, however long ago.
- **Signal Agent (PC):** reads Plex's Continue Watching row every 5 minutes, and again shortly after you watch something in Signal. Update the agent to get this.

### Duplicate movies show once
- Several files of the same movie (same title and year, e.g. a 4K and a 1080p copy) now show as one poster in All movies, Recently added and Continue watching.
- The movie page lists the versions (resolution, format, size, on phone) so you can pick which one plays.

### Sonos rooms
- Each Sonos room is listed once. Surround speakers, subs and the second speaker of a stereo pair no longer show up as separate "rooms"; Signal plays to the room's main speaker.
- Fixed "Sonos Sonos Roam" labels.

## [1.0.21] - 2026-09-30

### Sonos plays songs stored on the phone again
- Fixed Sonos staying silent and skipping song after song when playing music stored on the phone while Tailscale is on. The phone gave the speaker its Tailscale address (100.x), which speakers at home can't reach. It now always gives the home Wi-Fi address.
- If a speaker can't load a song, playback now stops with a message instead of skipping through the whole queue.

### Easier to close sheets
- Sheets like **Play on** now stop below the status bar with a gap at the top, and **Done** / **Cancel** are bigger buttons.

## [1.0.20] - 2026-09-30

### Sonos with Tailscale, take three
- Fixed Sonos speakers not appearing even though they answered the search. Since 1.0.13, Signal forced every request to a speaker onto the raw Wi-Fi connection to get around the VPN. Tailscale refuses that, so every request failed. Requests now go the normal way first (Tailscale doesn't carry home-network traffic) and use the Wi-Fi-only route only as a fallback.
- Adding a speaker by IP now also works while Tailscale is on.
- If a speaker still can't be reached, the error is shown, which makes the cause easier to find.

## [1.0.19] - 2026-09-30

### No more freezes while streaming during a scan
- **Signal Agent (PC):** while a song is streaming to your phone (or to Sonos), the library scan reads only 2 files at a time instead of up to 8, so the song gets the disk first. Full speed resumes when playback stops. This fixes songs freezing for several seconds while the PC was scanning several folders at once.
- **App:** background downloads wait while a song is streaming from the PC, and continue when it stops or when you play something stored on the phone.

## [1.0.18] - 2026-09-30

### Artists with featured guests
- Songs by "21 Savage & Doja Cat", "21 Savage, Burna Boy & Metro Boomin" or "21 Savage feat. Drake" are now listed under **21 Savage** instead of as separate artists.
- The album artist tag is used when a song has one. A name like "Earth, Wind & Fire" or "Simon & Garfunkel" stays whole unless its first name is also an artist in your library on its own.
- Tapping the artist on a song opens the main artist's page.

### Smoother streaming from the PC
- Wi-Fi stays awake while a song streams from the PC. Before, it could nap with the screen off and the sound would cut out.
- Songs load further ahead (up to 5 minutes, instead of 50 seconds) so a busy PC or a Wi-Fi hiccup doesn't interrupt playback.
- Downloads in the background no longer make the app rebuild the whole library several times a second, which could starve audio playback on big libraries.

## [1.0.17] - 2026-09-30

### No more duplicate songs
- A song that is both on your phone and on the PC now shows once instead of twice. This happens, for example, when you copy an album from the phone into the PC's music folder. Songs count as the same when the title, artist, album and length (within 3 seconds) match.
- In **Settings → Songs on both phone and PC**, choose which copy is shown and played:
  - **Phone copy** (the default) plays without the PC.
  - **PC copy** keeps tag edits and lyrics in sync with the PC.
  - **Show both** lists both copies, as before.

## [1.0.16] - 2026-09-30

### Sonos with Tailscale on
- Fixed Sonos speakers not being found while Tailscale (or another VPN) is on. Android reports a VPN as a Wi-Fi network too, so the search could run over Tailscale instead of your home Wi-Fi.
- Each part of the search now runs on its own, so one failing no longer skips the others.
- If no Sonos speaker is found, **Play on** shows what was searched and lets you add a speaker by its IP address (Sonos app → Settings → System → About My System). Signal remembers it and adds the other rooms in your house automatically.
- A **Search again** button reruns the search.

## [1.0.15] - 2026-09-30

### Downloads make sense now
- **Clear choices per library.** In Settings → PC sync, tapping a library's mode opens a menu with four explained options: **Stream**, **Download new**, **Download all** and **Hidden**. Before, each tap moved to the next mode, and one tap past "Download all" hid the whole library.
- **Switching a library to Stream (or Hidden) stops its queued downloads right away.** Files already downloaded stay on the phone. Songs you download by hand are never cancelled by this.
- **"Download new" means new.** It only downloads files added to the PC after you chose it. Before, while the PC was still scanning, every song it hadn't reported yet counted as "new", so the phone started downloading most of the library on its own. After an app update, that queue was lost.
- **Faster downloads.** Three files download at a time instead of one.
- **Lighter syncing.** Lyrics are looked up once when a song is queued for download. Before, they were requested again for every queued song on every sync, which kept the PC busy.

### Playing when the PC is busy or unreachable
- If the PC can't be reached, Music, Movies and TV Shows show a warning line again. Tapping a song that can't play now explains why, instead of doing nothing.
- The phone now waits longer for a busy PC before deciding it's unreachable, and it checks again every 30 seconds instead of every 10 minutes.

### Signal Agent (PC)
- The agent stays responsive while it scans. Progress is shared with the phone at most every 30 seconds, the library index is saved in a compact form, and the catalogue is prepared once per change instead of for every request.

## [1.0.14] - 2026-09-30

### App
- An artist's albums are now listed by release year, newest first. A sort bar above them lets you switch to A–Z or Date added; tap the selected option again to reverse the order.

### Signal Agent (PC)
- Up to three library folders now scan at the same time, so a big Music folder no longer holds up Movies, TV Shows and Music Videos.
- Updating or restarting the agent during a scan keeps the progress already saved; only files it hasn't read yet are scanned.

## [1.0.13] - 2026-09-30

### Faster, more reliable Sonos discovery
- Speakers found before now appear almost instantly when you open **Play on**. Signal remembers them and checks them directly.
- Speakers are found and controlled over Wi-Fi even when a VPN is on. A VPN used to block the Sonos search, so speakers could take a long time to show up or not show up at all.
- The search is repeated a few times, because Wi-Fi often drops the broadcast. Once one speaker answers, Signal asks it for every other room in the house.
- If the broadcast search still finds nothing, Signal checks the Wi-Fi network directly for Sonos speakers.
- Each room appears as soon as it answers, instead of all at once at the end.

## [1.0.12] - 2026-09-30

### A–Z scrub bar for big libraries
- Songs, Albums, Artists, Movies and TV Shows now have an A–Z strip on the right edge, like Apple Music. Tap a letter to jump to it, or drag along the strip to scrub through the list; a large letter shows where you are.
- It follows the current sort. When songs are sorted by Artist or Album, the letters jump by artist or album name. Like the sorting, it ignores a leading "The" or "A".
- The strip appears on long lists sorted A–Z, by Artist or by Album. It is hidden when a list is sorted by date, year, length or rating.

## [1.0.11] - 2026-09-30

- The "PC name · synced X min ago" line no longer appears at the top of Music, Movies and TV Shows. PC connection status is now shown only in Settings.

## [1.0.10] - 2026-09-30

### Posters and details for movies and shows on your phone
- Movies and TV shows stored only on your phone now get a poster, summary, year, genre and rating automatically:
  - **Movies** come from Apple's movie catalog (the Apple TV store).
  - **TV shows** come from **TVmaze**, including episode titles, summaries and stills.
  - Neither needs an account.
- If the same title is in your PC's Plex library, Plex's details are still used first, and the file plays from the phone.
- Wrong title? Open the movie or show and tap **Fix match** (or **Find details**). Search by name and pick the right one,
  or choose **Use the file name instead**.
- Details are saved on the phone, so each title is only looked up once.
- Turn this off in **Settings → Find movie & TV details online**.

## [1.0.9] - 2026-09-30

### Signal Agent updates install reliably
- Fixed: "Update failed: could not rename the running program (EPERM)". Windows sometimes won't let a running
  program replace itself, for example while Windows Defender is scanning it.
- Updates now work like this: Signal Agent downloads and verifies the new version, then closes, and a small helper
  swaps the files and starts it again.
  - If the file is still busy, the helper keeps retrying for up to 30 seconds.
  - If the swap still fails, it puts the old version back, restarts it, and the setup page tells you the update
    didn't install.
- **One more manual download:** versions 1.0.5–1.0.8 have the old updater, so download this version's
  `SignalAgent.exe` once and replace your current file. After that, updates install by themselves.
- Tip: keep `SignalAgent.exe` in a permanent folder, for example `C:\Users\<you>\SignalAgent\`, not Downloads.
  After moving it, turn **Start with Windows** off and on again.

### Fixes
- The setup page no longer shows the "Welcome! Let's set up Signal Agent" box after your folders are set up.

## [1.0.8] - 2026-09-30

### Signal Agent re-scans much less
- Signal Agent remembers everything it has read. On startup, and after an update, it only **checks for new or
  changed files** ("Check Music for changes"). Songs and videos it already knows aren't read again, and your
  library is available on the phone immediately from the saved index.
- When you add, rename or delete files, only **that** folder is checked, a few seconds later.
- The big "check everything" pass now runs every 6 hours as a safety net (it used to run every 10 minutes).
  File watching catches changes in between.
- Plex details are refreshed on full checks only, not on every file change.

## [1.0.7] - 2026-09-30

### PC library shows up while it scans (Signal Agent)
- Fixed: nothing appeared on the phone until **every** PC folder had finished scanning. A big TV folder could hide
  a fully scanned music library for a long time. Each folder now appears as soon as it's done.
- Large folders show up bit by bit while they scan: every few hundred files, instead of all at the end.
- Scan progress is saved as it goes, so restarting the PC or the agent doesn't start the scan over.
- A single damaged or very slow video file can no longer hold up the scan: it's skipped after 30 seconds.
- Video folders scan faster (more files read at once).
- While the PC is still scanning, the phone checks for new items every minute instead of every 10.

### Phone folder picker
- **Settings → Folders on this phone** is now a folder browser. Tap **+ Add a folder**, start at the top of your
  phone's storage, open folders, then choose **Use this folder for Music / Music Videos / Movies / TV Shows**.
- Everything inside the folder is included and organised automatically:
  - **Music** uses its tags (artist and album).
  - **TV Shows** reads Show / Season folders and names like `S01E02`.
  - **Movies** reads names like `Title (2010)`, from either the file or its folder.
- Your chosen folders are listed with **Change** and **Remove** buttons.

### Fixes
- The phone now shows the PC's current Signal Agent version. It used to keep showing the version from when you
  first paired (1.0.0).

## [1.0.6] - 2026-09-30

### Favorites
- Tap the **♥** next to the song title on Now Playing, or use **⋯ → Add to Favorites** on any song.
- A **Favorites** playlist at the top of Playlists collects every favorite song automatically. Favorites show a small ♥ in song lists.

### Continue watching, Up next, Recently added (like Plex)
- **Movies** and **TV Shows** now open with:
  - **Continue watching:** what you stopped part-way through, with how much time is left.
  - **Up next** (TV): the next episode after the last one you finished. If you've caught up on a show and a new episode is added, it appears here, and the show moves to the front of the row.
  - **Recently added:** the newest movies, and the newest episode of each show, marked "NEW".
- An episode counts as watched once you reach the credits (90 % in, the same rule Plex uses).
- Where you are in a movie or episode is saved on the phone right away, and synced to the PC and Plex when it's reachable.
- A show's **Continue** button now follows the same rules.

### Movies and shows stored on the phone
- Videos on the phone now get a proper title and year from release-style file names.
  For example, `Minions.and.Monsters.2026.2160p.WEB-DL…` becomes **Minions and Monsters · 2026**.
- If the same movie or show is in your PC's Plex library, Signal shows Plex's poster, summary, cast and ratings
  while still playing the file from your phone. Your progress syncs back to Plex.
- Phone episodes named like `Show.S01E04…` join the matching show and season from Plex.
- "Fix match" no longer appears for phone files. If a title isn't in your Plex library, the page says so.

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
