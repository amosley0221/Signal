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
    fun typeOf(folder: String?, chosen: Map<String, PhoneFolderType>): PhoneFolderType? {
        val f = folder ?: return null
        return chosen.entries
            .filter { (k, _) -> f == k || f.startsWith("$k/") }
            .maxByOrNull { it.key.length }?.value
    }

    private val episodeRe = Regex("(?i)S(\\d{1,2})\\s*E(\\d{1,3})")

    fun split(tracks: List<Track>, videos: List<Movie>, chosen: Map<String, PhoneFolderType>): PhoneMedia {
        val keptTracks = tracks.filter { typeOf(it.folder, chosen) == PhoneFolderType.MUSIC }
        val byType = videos.groupBy { typeOf(it.folder, chosen) }
        val movies = byType[PhoneFolderType.MOVIES].orEmpty()
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
            val m = episodeRe.find(v.title)
            val folderParts = (v.folder ?: "").split('/').filter { it.isNotBlank() }
            val folderShow = folderParts.lastOrNull { !it.matches(Regex("(?i)season\\s*\\d+|specials")) } ?: "TV"
            val showFromName = m?.let { v.title.substring(0, it.range.first).trim(' ', '-', '.', '_') }?.takeIf { it.isNotBlank() }
            val title = m?.let { v.title.substring(it.range.last + 1).trim(' ', '-', '.', '_') }?.ifBlank { null } ?: v.title
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
