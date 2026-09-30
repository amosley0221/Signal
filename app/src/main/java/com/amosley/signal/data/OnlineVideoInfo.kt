package com.amosley.signal.data

import com.amosley.signal.core.Episode
import com.amosley.signal.core.Movie
import com.amosley.signal.core.Season
import com.amosley.signal.core.Show
import com.amosley.signal.core.VideoNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Movie details found online for a video stored on the phone. */
@Serializable
data class MovieInfo(
    val title: String,
    val year: Int? = null,
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val certificate: String? = null,
    val durationMs: Long = 0,
    val source: String = "ITUNES",
)

@Serializable
data class EpisodeInfo(val season: Int, val episode: Int, val title: String, val summary: String? = null, val thumbUrl: String? = null, val durationMs: Long = 0)

/** TV show details (and its episode list) found online. */
@Serializable
data class ShowInfo(
    val title: String,
    val year: Int? = null,
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val episodes: List<EpisodeInfo> = emptyList(),
    val source: String = "TVMAZE",
)

/**
 * Looks up movies (Apple's iTunes catalog) and TV shows (TVmaze) — both free and keyless — for videos
 * that are only on the phone, so they get posters and summaries like the ones from Plex.
 */
class OnlineVideoInfo(private val http: OkHttpClient) {

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "SignalPlayer (https://github.com/amosley0221/Signal)").build()
        http.newCall(req).execute().use { res ->
            if (res.code == 404) return null
            if (!res.isSuccessful) error("HTTP ${res.code}")
            return res.body?.string()
        }
    }

    suspend fun searchMovies(query: String): List<MovieInfo> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", query).addQueryParameter("media", "movie").addQueryParameter("entity", "movie")
            .addQueryParameter("limit", "15").build()
        get(url.toString())?.let(::parseItunes).orEmpty()
    }

    /** Best movie for a parsed file name: same normalised title (and year ±1 when known). */
    suspend fun findMovie(title: String, year: Int?): MovieInfo? {
        val results = searchMovies(if (year != null) "$title $year" else title).ifEmpty { searchMovies(title) }
        return pickMovie(results, title, year)
    }

    suspend fun searchShows(query: String): List<ShowInfo> = withContext(Dispatchers.IO) {
        val url = "https://api.tvmaze.com/search/shows".toHttpUrl().newBuilder().addQueryParameter("q", query).build()
        get(url.toString())?.let { parseTvmazeSearch(it) }.orEmpty()
    }

    /** Show details with episodes, by name (TVmaze single search). */
    suspend fun findShow(name: String): ShowInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.tvmaze.com/singlesearch/shows".toHttpUrl().newBuilder()
            .addQueryParameter("q", name).addQueryParameter("embed", "episodes").build()
        get(url.toString())?.let { parseTvmazeShow(SignalJson.parseToJsonElement(it).jsonObject) }
    }

    /** Full details (with episodes) for a show picked from search results. */
    suspend fun showById(id: Long): ShowInfo? = withContext(Dispatchers.IO) {
        get("https://api.tvmaze.com/shows/$id?embed=episodes")?.let { parseTvmazeShow(SignalJson.parseToJsonElement(it).jsonObject) }
    }

    companion object {
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        private fun JsonElement?.obj() = this as? JsonObject
        private fun stripHtml(s: String?) = s?.replace(Regex("<[^>]+>"), "")?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")?.trim()?.ifBlank { null }

        fun parseItunes(json: String): List<MovieInfo> {
            val results = SignalJson.parseToJsonElement(json).jsonObject["results"] as? JsonArray ?: return emptyList()
            return results.mapNotNull { e ->
                val o = e.obj() ?: return@mapNotNull null
                val title = o.str("trackName") ?: return@mapNotNull null
                MovieInfo(
                    title = title,
                    year = o.str("releaseDate")?.take(4)?.toIntOrNull(),
                    synopsis = o.str("longDescription") ?: o.str("shortDescription"),
                    // Apple's artwork URLs take any size: ask for a sharp 2:3 poster.
                    posterUrl = o.str("artworkUrl100")?.replace(Regex("/\\d+x\\d+bb\\."), "/600x900bb."),
                    genres = listOfNotNull(o.str("primaryGenreName")),
                    director = o.str("artistName"),
                    certificate = o.str("contentAdvisoryRating"),
                    durationMs = (o["trackTimeMillis"] as? JsonPrimitive)?.longOrNull ?: 0,
                )
            }
        }

        fun pickMovie(results: List<MovieInfo>, title: String, year: Int?): MovieInfo? {
            val key = VideoNames.norm(title)
            val same = results.filter { VideoNames.norm(it.title) == key || VideoNames.norm(it.title).startsWith(key) }
            return same.firstOrNull { year == null || it.year == null || kotlin.math.abs(it.year - year) <= 1 }
        }

        private fun parseTvmazeShow(o: JsonObject): ShowInfo? {
            val name = o.str("name") ?: return null
            val eps = (o["_embedded"].obj()?.get("episodes") as? JsonArray).orEmpty().mapNotNull { e ->
                val eo = e.obj() ?: return@mapNotNull null
                val season = (eo["season"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val number = (eo["number"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                EpisodeInfo(
                    season, number, eo.str("name") ?: "Episode $number", stripHtml(eo.str("summary")),
                    eo["image"].obj()?.str("original") ?: eo["image"].obj()?.str("medium"),
                    ((eo["runtime"] as? JsonPrimitive)?.intOrNull ?: 0) * 60_000L,
                )
            }
            return ShowInfo(
                title = name,
                year = o.str("premiered")?.take(4)?.toIntOrNull(),
                synopsis = stripHtml(o.str("summary")),
                posterUrl = o["image"].obj()?.str("original") ?: o["image"].obj()?.str("medium"),
                genres = (o["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                rating = (o["rating"].obj()?.get("average") as? JsonPrimitive)?.doubleOrNull,
                episodes = eps,
            )
        }

        /** Search results carry the TVmaze id in [ShowInfo.source] as "TVMAZE:<id>" so a pick can load episodes. */
        fun parseTvmazeSearch(json: String): List<ShowInfo> =
            SignalJson.parseToJsonElement(json).jsonArray.mapNotNull { e ->
                val show = e.obj()?.get("show").obj() ?: return@mapNotNull null
                val id = (show["id"] as? JsonPrimitive)?.longOrNull
                parseTvmazeShow(show)?.copy(source = "TVMAZE:$id")
            }

        fun parseTvmazeShowJson(json: String): ShowInfo? = parseTvmazeShow(SignalJson.parseToJsonElement(json).jsonObject)

        /** Put online details onto a phone movie (keeps what's playable: id, uri, file info). */
        fun apply(m: Movie, info: MovieInfo): Movie = m.copy(
            title = info.title, year = info.year ?: m.year, synopsis = info.synopsis, posterUrl = info.posterUrl,
            backdropUrl = info.posterUrl, genres = info.genres, director = info.director, certificate = info.certificate,
            durationMs = if (m.durationMs > 0) m.durationMs else info.durationMs,
            matchedBy = if (info.source == "ITUNES") "ITUNES" else info.source,
        )

        /** Put online details onto a phone show and its episodes (matched by season/episode number). */
        fun apply(s: Show, info: ShowInfo): Show {
            val byKey = info.episodes.associateBy { it.season to it.episode }
            return s.copy(
                title = info.title, year = info.year, synopsis = info.synopsis, posterUrl = info.posterUrl,
                backdropUrl = info.backdropUrl ?: info.posterUrl, genres = info.genres, rating = info.rating,
                matchedBy = "TVMAZE",
                seasons = s.seasons.map { se ->
                    Season(se.number, se.episodes.map { ep: Episode ->
                        val ei = byKey[se.number to ep.episode] ?: return@map ep
                        ep.copy(title = ei.title, summary = ei.summary, thumbUrl = ei.thumbUrl ?: ep.thumbUrl)
                    })
                },
            )
        }
    }
}
