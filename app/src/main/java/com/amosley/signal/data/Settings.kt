package com.amosley.signal.data

import kotlinx.serialization.Serializable

enum class LibMode(val label: String) { STREAM("Stream"), DOWNLOAD_NEW("Download new"), DOWNLOAD_ALL("Download all");
    fun next(): LibMode = entries[(ordinal + 1) % entries.size]
}

enum class RemoteMode(val label: String) { TAILSCALE("Tailscale"), HOME_ONLY("Home only"), PORT_FORWARD("Port forward") }

@Serializable
data class PairedPc(
    val id: String,
    val name: String,
    /** LAN base url, e.g. http://192.168.1.20:8765 */
    val lanUrl: String,
    /** Optional second endpoint used away from home (Tailscale address or forwarded host). */
    val remoteUrl: String? = null,
    val token: String,
    val plex: Boolean = false,
    val agentVersion: String? = null,
)

@Serializable
data class Settings(
    val pc: PairedPc? = null,
    /** Library id → mode. Libraries missing from the map are not synced at all. */
    val libModes: Map<String, LibMode> = emptyMap(),
    /** Library id → when its mode was last set. "Download new" only downloads files the PC added after this. */
    val libModeSince: Map<String, Long> = emptyMap(),
    val remoteMode: RemoteMode = RemoteMode.TAILSCALE,
    val wifiOnly: Boolean = true,
    val dlQualityLossless1644: Boolean = false,
    val translateLyrics: Boolean = true,
    val offline: Boolean = false,
    val autoRemoveWatched: Boolean = true,
    val keepEpisodes: Int = 3,
    val lowStoragePauseGb: Int = 5,
    val uploadPhoneOnly: Boolean = false,
    val watchedToPlex: Boolean = true,
    val lyricMode: LyricMode = LyricMode.BOTH,
    val skippedPairing: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    /** PC tracks added after this time with no artist show up in Import review. */
    val importReviewedAt: Long = 0,
    /** Phone folder → what it holds. Folders not in the map are hidden from the library. */
    val phoneFolders: Map<String, PhoneFolderType> = emptyMap(),
    /** False until the user has chosen phone folders (the library prompts them to). */
    val phoneFoldersChosen: Boolean = false,
    /** Look lyrics up on LRCLIB when a song has no .lrc file. */
    val onlineLyrics: Boolean = true,
    /** Look up posters/summaries online (iTunes, TVmaze) for movies and shows that are only on the phone. */
    val onlineVideoInfo: Boolean = true,
    /** Also show Plex's own Continue Watching row (off: only what you watch in Signal). */
    val plexContinueWatching: Boolean = false,
    /** Songs on both the phone and the PC: which copy to show (or both). */
    val duplicates: com.amosley.signal.core.DuplicateMode = com.amosley.signal.core.DuplicateMode.PHONE,
    /** Chosen sort for each library tab. */
    val sorts: Map<com.amosley.signal.core.SortTab, com.amosley.signal.core.SortPref> = emptyMap(),
    /** Equalizer for music played on the phone. */
    val eq: com.amosley.signal.core.EqSettings = com.amosley.signal.core.EqSettings(),
)

enum class PhoneFolderType(val label: String) {
    MUSIC("Music"), MUSIC_VIDEOS("Music Videos"), MOVIES("Movies"), TV("TV Shows");
}

enum class LyricMode { ORIG, BOTH, TRANS }

/** Tag edits made on the phone. Null = unchanged. Applied on top of the file's own tags. */
@Serializable
data class TrackEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val track: Int? = null,
    val disc: Int? = null,
) {
    fun merge(o: TrackEdit) = TrackEdit(
        o.title ?: title, o.artist ?: artist, o.album ?: album, o.albumArtist ?: albumArtist,
        o.genre ?: genre, o.year ?: year, o.track ?: track, o.disc ?: disc,
    )

    /** Fields for the PC agent's POST /api/tags. */
    fun toFields(): Map<String, String> = buildMap {
        title?.let { put("title", it) }; artist?.let { put("artist", it) }; album?.let { put("album", it) }
        albumArtist?.let { put("albumArtist", it) }; genre?.let { put("genre", it) }
        year?.let { put("year", it.toString()) }; track?.let { put("track", it.toString()) }; disc?.let { put("disc", it.toString()) }
    }

    fun apply(t: com.amosley.signal.core.Track) = t.copy(
        title = title ?: t.title, artist = artist ?: t.artist, album = album ?: t.album, albumArtist = albumArtist ?: t.albumArtist,
        genres = genre?.split(';', ',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: t.genres,
        year = year ?: t.year, track = track ?: t.track, disc = disc ?: t.disc,
    )
}
