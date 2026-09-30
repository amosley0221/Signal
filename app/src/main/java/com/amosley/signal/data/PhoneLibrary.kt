package com.amosley.signal.data

import com.amosley.signal.core.Episode
import com.amosley.signal.core.Movie
import com.amosley.signal.core.MusicVideo
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Season
import com.amosley.signal.core.Show
import com.amosley.signal.core.Track

/** A folder on the phone that holds media, shown in the folder picker. */
data class PhoneFolder(val path: String, val audio: Int, val video: Int)

/** What the user chose to include from the phone, split by library type. */
data class PhoneMedia(
    val tracks: List<Track> = emptyList(),
    val musicVideos: List<MusicVideo> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val shows: List<Show> = emptyList(),
)

object PhoneLibrary {
    fun folders(tracks: List<Track>, videos: List<Movie>): List<PhoneFolder> {
        val audio = tracks.groupingBy { it.folder ?: "Unknown" }.eachCount()
        val video = videos.groupingBy { it.folder ?: "Unknown" }.eachCount()
        return (audio.keys + video.keys).distinct()
            .map { PhoneFolder(it, audio[it] ?: 0, video[it] ?: 0) }
            .sortedBy { it.path.lowercase() }
    }

    /** The most specific chosen folder that contains [folder] (a choice covers its subfolders). */
    fun typeOf(folder: String?, chosen: Map<String, PhoneFolderType>): PhoneFolderType? = rootOf(folder, chosen)?.value

    fun rootOf(folder: String?, chosen: Map<String, PhoneFolderType>): Map.Entry<String, PhoneFolderType>? {
        val f = folder ?: return null
        return chosen.entries.filter { (k, _) -> f == k || f.startsWith("$k/") }.maxByOrNull { it.key.length }
    }

    /** Sub-folders one level below [path] ("" = storage root), with media counts for everything inside them. */
    fun children(path: String, all: List<PhoneFolder>): List<PhoneFolder> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        val agg = LinkedHashMap<String, IntArray>()
        for (f in all) {
            if (prefix.isNotEmpty() && !f.path.startsWith(prefix)) continue
            val rest = f.path.removePrefix(prefix)
            if (rest.isEmpty()) continue
            val child = prefix + rest.substringBefore('/')
            val a = agg.getOrPut(child) { IntArray(2) }
            a[0] += f.audio; a[1] += f.video
        }
        return agg.map { (k, v) -> PhoneFolder(k, v[0], v[1]) }.sortedBy { it.path.substringAfterLast('/').lowercase() }
    }

    /** Media directly in [path] (not in sub-folders). */
    fun own(path: String, all: List<PhoneFolder>): PhoneFolder? = all.firstOrNull { it.path == path }

    private val episodeRe = Regex("(?i)S(\\d{1,2})\\s*E(\\d{1,3})")

    fun split(tracks: List<Track>, videos: List<Movie>, chosen: Map<String, PhoneFolderType>): PhoneMedia {
        val keptTracks = tracks.filter { typeOf(it.folder, chosen) == PhoneFolderType.MUSIC }
        val byType = videos.groupBy { typeOf(it.folder, chosen) }
        // Movies kept in their own folders ("Movies/Inception (2010)/inception.mkv"): the folder name often
        // carries the proper title and year when the file name doesn't.
        val movies = byType[PhoneFolderType.MOVIES].orEmpty().map { v ->
            val root = rootOf(v.folder, chosen)?.key
            val folderName = v.folder?.takeIf { root != null && it != root }?.substringAfterLast('/')
            val fromFolder = folderName?.let { com.amosley.signal.core.VideoNames.parse(it) }
            if (fromFolder != null && fromFolder.title.isNotBlank() && (v.year == null || fromFolder.year != null) && fromFolder.year != null) {
                v.copy(title = fromFolder.title, year = fromFolder.year)
            } else v
        }
        val musicVideos = byType[PhoneFolderType.MUSIC_VIDEOS].orEmpty().map { v ->
            MusicVideo(
                id = v.id, title = v.title, durationMs = v.durationMs, width = v.width, height = v.height,
                container = v.container, size = v.size, addedAt = v.addedAt, origin = Origin.PHONE, uri = v.uri,
            )
        }
        val shows = byType[PhoneFolderType.TV].orEmpty().let(::groupShows)
        return PhoneMedia(keptTracks, musicVideos, movies, shows)
    }

    /**
     * Phone TV: "Show/Season 1/Show - S01E02 - Title.mp4" or files named with SxxEyy. The show is the
     * text before SxxEyy, or the folder name (skipping "Season N" folders).
     */
    fun groupShows(videos: List<Movie>): List<Show> {
        data class Ep(val show: String, val season: Int, val episode: Int?, val title: String, val v: Movie)
        val eps = videos.map { v ->
            val raw = (v.fileName?.substringBeforeLast('.') ?: v.title).replace(Regex("[._]+"), " ")
            val m = episodeRe.find(raw)
            val folderParts = (v.folder ?: "").split('/').filter { it.isNotBlank() }
            val folderShow = folderParts.lastOrNull { !it.matches(Regex("(?i)season\\s*\\d+|specials")) } ?: "TV"
            val showFromName = m?.let { com.amosley.signal.core.VideoNames.parse(raw.substring(0, it.range.first)).title }?.takeIf { it.isNotBlank() }
            val title = m?.let { com.amosley.signal.core.VideoNames.parse(raw.substring(it.range.last + 1)).title }?.ifBlank { null } ?: v.title
            Ep(showFromName ?: folderShow, m?.groupValues?.get(1)?.toInt() ?: 1, m?.groupValues?.get(2)?.toInt(), title, v)
        }
        return eps.groupBy { it.show.lowercase() }.map { (_, list) ->
            val name = list.first().show
            val seasons = list.groupBy { it.season }.toSortedMap().map { (sn, se) ->
                val sorted = se.sortedWith(compareBy<Ep>({ it.episode ?: Int.MAX_VALUE }, { it.v.title.lowercase() }))
                Season(sn, sorted.mapIndexed { i, e ->
                    Episode(
                        id = e.v.id, season = sn, episode = e.episode ?: (i + 1), title = e.title, durationMs = e.v.durationMs,
                        size = e.v.size, container = e.v.container, width = e.v.width, height = e.v.height, uri = e.v.uri,
                    )
                })
            }
            Show(
                id = "locs:${name.lowercase()}", title = name, seasons = seasons, matchedBy = "ON THIS PHONE",
                addedAt = list.maxOf { it.v.addedAt },
            )
        }.sortedBy { it.title.lowercase() }
    }
}

/** Phone videos matched to the same titles in the PC / Plex library: Plex details, played from the phone. */
object PhoneMatch {
    fun movies(pc: List<Movie>, phone: List<Movie>): List<Movie> {
        val replaced = HashSet<String>()
        val merged = phone.map { v ->
            val m = com.amosley.signal.core.VideoNames.matchMovie(com.amosley.signal.core.VideoNames.Parsed(v.title, v.year), pc.filter { it.id !in replaced })
            if (m == null) v else {
                replaced += m.id
                m.copy(origin = Origin.PHONE, uri = v.uri, container = v.container ?: m.container, size = v.size, folder = v.folder, fileName = v.fileName)
            }
        }
        return pc.filter { it.id !in replaced } + merged
    }

    fun shows(pc: List<Show>, phone: List<Show>): List<Show> {
        val byKey = pc.associateBy { com.amosley.signal.core.VideoNames.norm(it.title) }.toMutableMap()
        val extra = mutableListOf<Show>()
        for (ps in phone) {
            val key = com.amosley.signal.core.VideoNames.norm(ps.title)
            val target = byKey[key]
            if (target == null) { extra += ps; continue }
            // Point matching PC episodes at the phone file; add phone-only episodes to their season.
            val seasons = target.seasons.associateBy { it.number }.toMutableMap()
            for (se in ps.seasons) for (ep in se.episodes) {
                val season = seasons[se.number] ?: Season(se.number)
                val i = season.episodes.indexOfFirst { it.episode == ep.episode }
                val eps = season.episodes.toMutableList()
                if (i >= 0) eps[i] = eps[i].copy(uri = ep.uri) else eps += ep
                seasons[se.number] = season.copy(episodes = eps.sortedBy { it.episode })
            }
            byKey[key] = target.copy(seasons = seasons.values.sortedBy { it.number })
        }
        val merged = pc.map { byKey[com.amosley.signal.core.VideoNames.norm(it.title)] ?: it }
        return merged + extra
    }
}
