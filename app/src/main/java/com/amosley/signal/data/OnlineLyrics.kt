package com.amosley.signal.data

import com.amosley.signal.core.Lrc
import com.amosley.signal.core.Lyrics
import com.amosley.signal.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

@Serializable
data class LrclibRecord(
    val id: Long = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/**
 * Looks lyrics up on LRCLIB (https://lrclib.net) — a free, open database of time-synced lyrics that
 * needs no account or API key. Used when a song has no .lrc file and no lyrics on the PC.
 */
class OnlineLyrics(private val http: OkHttpClient) {
    private val base = "https://lrclib.net/api"

    suspend fun find(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val artist = track.artist?.takeIf { it.isNotBlank() } ?: return@withContext null
        val durationSec = track.durationMs / 1000.0
        // 1) Exact signature match (fast, cached by LRCLIB).
        val exact = runCatching {
            val url = "$base/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", artist)
                .apply {
                    track.album?.let { addQueryParameter("album_name", it) }
                    if (durationSec > 0) addQueryParameter("duration", durationSec.toInt().toString())
                }.build()
            get(url.toString())?.let { SignalJson.decodeFromString(LrclibRecord.serializer(), it) }
        }.getOrNull()
        val record = exact?.takeIf { it.hasLyrics } ?: runCatching {
            // 2) Search and pick the closest duration, preferring synced lyrics.
            val url = "$base/search".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", artist)
                .build()
            get(url.toString())?.let { SignalJson.decodeFromString(ListSerializer(LrclibRecord.serializer()), it) }
                ?.let { pick(it, durationSec) }
        }.getOrNull()
        record?.let(::toLyrics)
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "SignalPlayer (https://github.com/amosley0221/Signal)")
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 404) return null
            if (!res.isSuccessful) error("LRCLIB HTTP ${res.code}")
            return res.body?.string()
        }
    }

    companion object {
        private val LrclibRecord.hasLyrics get() = instrumental || !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

        /** Closest duration (within 4 s when known), synced lyrics first. */
        fun pick(results: List<LrclibRecord>, durationSec: Double): LrclibRecord? =
            results.filter { it.hasLyrics }
                .filter { durationSec <= 0 || it.duration == null || abs(it.duration - durationSec) <= 4 }
                .sortedWith(compareBy({ it.syncedLyrics.isNullOrBlank() }, { abs((it.duration ?: durationSec) - durationSec) }))
                .firstOrNull()

        fun toLyrics(r: LrclibRecord): Lyrics? {
            if (r.instrumental) return Lyrics(lines = emptyList(), synced = false, source = "lrclib-instrumental")
            r.syncedLyrics?.takeIf { it.isNotBlank() }?.let { text ->
                val p = Lrc.parse(text)
                if (p.lines.isNotEmpty()) return Lyrics(p.lang, p.lines, p.synced, "lrclib")
            }
            r.plainLyrics?.takeIf { it.isNotBlank() }?.let { text ->
                val p = Lrc.parse(text)
                if (p.lines.isNotEmpty()) return Lyrics(p.lang, p.lines, synced = false, source = "lrclib")
            }
            return null
        }
    }
}
