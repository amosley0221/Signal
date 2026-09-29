package com.amosley.signal.core

import kotlinx.serialization.Serializable

/** Where a playable item physically lives. */
enum class Origin { PHONE, PC }

enum class Quality(val label: String) {
    HIRES("Hi-Res Lossless"), LOSSLESS("Lossless"), LOSSY("Lossy");

    companion object {
        private val losslessContainers = setOf("WAV", "FLAC", "AIFF", "AIF", "ALAC", "APE", "WV")

        fun of(container: String?, codec: String?, bitDepth: Int?, sampleRate: Int?): Quality {
            val c = container?.uppercase().orEmpty()
            val k = codec?.uppercase().orEmpty()
            val lossless = c in losslessContainers || k.contains("ALAC") || k.contains("FLAC") || k.contains("PCM")
            if (!lossless) return LOSSY
            return if ((bitDepth ?: 16) > 16 || (sampleRate ?: 44100) > 48000) HIRES else LOSSLESS
        }
    }
}

@Serializable
data class Track(
    val id: String,
    val origin: Origin = Origin.PC,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val disc: Int? = null,
    val track: Int? = null,
    val durationMs: Long = 0,
    val container: String? = null,
    val codec: String? = null,
    val bitDepth: Int? = null,
    val sampleRate: Int? = null,
    val size: Long = 0,
    val mtime: Long = 0,
    val addedAt: Long = 0,
    val genres: List<String> = emptyList(),
    val hasArt: Boolean = false,
    val hasLyrics: Boolean = false,
    val hasTranslation: Boolean = false,
    val lyricsLang: String? = null,
    /** For phone tracks: content:// uri. For PC tracks: the agent item id. */
    val uri: String = "",
    /** Absolute file path for phone tracks when known (used to find .lrc sidecars). */
    val path: String? = null,
    val libraryId: String? = null,
) {
    val quality: Quality get() = Quality.of(container, codec, bitDepth, sampleRate)
    val isHiRes: Boolean get() = quality == Quality.HIRES
    val tags: List<String> get() = genres.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
    val albumKey: String get() = albumKeyOf(album, albumArtist ?: artist)

    /** "24-bit / 96 kHz" style readout, or null when unknown. */
    val bitsLabel: String?
        get() {
            if (bitDepth == null && sampleRate == null) return null
            val sr = sampleRate?.let { formatKhz(it) }
            return listOfNotNull(bitDepth?.let { "$it-bit" }, sr).joinToString(" / ")
        }
}

fun formatKhz(sampleRate: Int): String {
    val khz = sampleRate / 1000.0
    return if (khz == Math.floor(khz)) "${khz.toInt()} kHz" else "${"%.1f".format(khz)} kHz"
}

fun albumKeyOf(album: String?, artist: String?): String =
    "${album?.trim()?.lowercase() ?: "~singles"}|${if (album == null) "" else artist?.trim()?.lowercase() ?: ""}"

@Serializable
data class MusicVideo(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val hdr: Boolean = false,
    val container: String? = null,
    val size: Long = 0,
    val addedAt: Long = 0,
    val hasArt: Boolean = false,
    val libraryId: String? = null,
) {
    val resLabel: String get() = resolutionLabel(width, height)
}

fun resolutionLabel(width: Int, height: Int): String = when {
    width >= 3000 || height >= 2000 -> "4K"
    height >= 1000 || width >= 1900 -> "1080p"
    height >= 700 || width >= 1200 -> "720p"
    height > 0 -> "${height}p"
    else -> "HD"
}

@Serializable
data class Subtitle(val id: String, val language: String? = null, val label: String? = null, val format: String? = null, val url: String)

@Serializable
data class Chapter(val title: String, val startMs: Long)

@Serializable
data class Movie(
    val id: String,
    val origin: Origin = Origin.PC,
    val title: String,
    val year: Int? = null,
    val durationMs: Long = 0,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val synopsis: String? = null,
    val cast: List<String> = emptyList(),
    val rating: Double? = null,
    val certificate: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val matchedBy: String? = null,
    val viewOffsetMs: Long = 0,
    val watched: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val container: String? = null,
    val size: Long = 0,
    val addedAt: Long = 0,
    val subtitles: List<Subtitle> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    /** content:// uri for phone videos. */
    val uri: String = "",
    val libraryId: String? = null,
)

@Serializable
data class Episode(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val summary: String? = null,
    val durationMs: Long = 0,
    val viewOffsetMs: Long = 0,
    val watched: Boolean = false,
    val size: Long = 0,
    val container: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val thumbUrl: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
)

@Serializable
data class Season(val number: Int, val episodes: List<Episode> = emptyList())

@Serializable
data class Show(
    val id: String,
    val title: String,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val cast: List<String> = emptyList(),
    val rating: Double? = null,
    val certificate: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val matchedBy: String? = null,
    val seasons: List<Season> = emptyList(),
    val libraryId: String? = null,
    val addedAt: Long = 0,
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
    val allEpisodes: List<Episode> get() = seasons.flatMap { it.episodes }
}

@Serializable
data class Library(
    val id: String,
    val name: String,
    val type: String,
    val path: String = "",
    val count: Int = 0,
    val bytes: Long = 0,
)

@Serializable
data class Catalog(
    val generatedAt: Long = 0,
    val tracks: List<Track> = emptyList(),
    val videos: List<MusicVideo> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val shows: List<Show> = emptyList(),
)

@Serializable
data class LyricLine(val t: Double, val text: String, val tr: String? = null)

@Serializable
data class Lyrics(
    val lang: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val synced: Boolean = true,
    val source: String = "pc",
) {
    val hasTranslation: Boolean get() = lines.any { it.tr != null }
}

@Serializable
data class UserPlaylist(val id: String, val name: String, val trackIds: List<String> = emptyList(), val createdAt: Long = 0)

data class Album(
    val key: String,
    val title: String,
    val artist: String?,
    val year: Int?,
    val tracks: List<Track>,
    val videos: List<MusicVideo>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs } + videos.sumOf { it.durationMs }
    val artTrack: Track? get() = tracks.firstOrNull { it.hasArt } ?: tracks.firstOrNull()
}

/** Album-order: disc → track number → title (never filename order). */
val trackOrder: Comparator<Track> = compareBy<Track>({ it.disc ?: 1 }, { it.track ?: Int.MAX_VALUE }, { it.title.lowercase() })

fun groupAlbums(tracks: List<Track>, videos: List<MusicVideo>): List<Album> {
    val byKey = tracks.groupBy { it.albumKey }
    return byKey.map { (key, ts) ->
        val sorted = ts.sortedWith(trackOrder)
        val first = sorted.first()
        val title = first.album ?: "Singles"
        val artist = if (first.album == null) null else first.albumArtist ?: sorted.mapNotNull { it.artist }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val vids = if (first.album == null) emptyList() else videos.filter { it.album != null && it.album.equals(first.album, ignoreCase = true) }
        Album(key, title, artist, sorted.mapNotNull { it.year }.maxOrNull(), sorted, vids)
    }.sortedWith(compareBy<Album> { it.title == "Singles" }.thenBy { it.title.lowercase() })
}
