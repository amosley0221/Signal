package com.amosley.signal.core

data class LyricHit(val track: Track, val line: String)
data class EpisodeHit(val show: Show, val episode: Episode)

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val lyrics: List<LyricHit> = emptyList(),
    val artists: List<String> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val videos: List<MusicVideo> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val shows: List<Show> = emptyList(),
    val episodes: List<EpisodeHit> = emptyList(),
) {
    val total: Int get() = songs.size + lyrics.size + artists.size + albums.size + playlists.size + videos.size + movies.size + shows.size + episodes.size
}

/** Case-insensitive substring search across the whole library. Groups are capped like the design. */
object Search {
    fun run(
        query: String,
        tracks: List<Track>,
        albums: List<Album>,
        playlists: List<UserPlaylist>,
        videos: List<MusicVideo>,
        movies: List<Movie>,
        shows: List<Show>,
        lyricsIndex: Map<String, List<LyricLine>>,
    ): SearchResults {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchResults()
        fun String?.hit() = this != null && this.lowercase().contains(q)
        val songs = tracks.filter { it.title.hit() || it.artist.hit() || it.album.hit() }
        val lyricHits = tracks.mapNotNull { t ->
            lyricsIndex[t.id]?.firstOrNull { it.text.hit() || it.tr.hit() }?.let { LyricHit(t, it.text) }
        }.filter { h -> songs.none { it.id == h.track.id } }
        val artists = tracks.mapNotNull { it.artist }.distinct().filter { it.hit() }
        return SearchResults(
            songs = songs.take(6),
            lyrics = lyricHits.take(4),
            artists = artists.take(4),
            albums = albums.filter { it.title.hit() || it.artist.hit() }.take(4),
            playlists = playlists.filter { it.name.hit() }.take(4),
            videos = videos.filter { it.title.hit() || it.artist.hit() || it.album.hit() }.take(3),
            movies = movies.filter { m -> m.title.hit() || m.synopsis.hit() || m.director.hit() || m.cast.any { it.hit() } }.take(4),
            shows = shows.filter { s -> s.title.hit() || s.synopsis.hit() || s.cast.any { it.hit() } }.take(4),
            episodes = shows.flatMap { s -> s.allEpisodes.filter { it.title.hit() || it.summary.hit() }.map { EpisodeHit(s, it) } }.take(4),
        )
    }
}
