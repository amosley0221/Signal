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
)

enum class PhoneFolderType(val label: String) {
    MUSIC("Music"), MUSIC_VIDEOS("Music Videos"), MOVIES("Movies"), TV("TV Shows");
}

enum class LyricMode { ORIG, BOTH, TRANS }
