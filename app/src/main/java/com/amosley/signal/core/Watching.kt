package com.amosley.signal.core

import kotlinx.serialization.Serializable

/** Progress saved on the phone (so it shows up right away, even before the PC hears about it). */
@Serializable
data class WatchProgress(val positionMs: Long, val durationMs: Long, val watched: Boolean, val at: Long)

/** One card in Continue Watching / Up Next / Recently Added. [show] is null for movies. */
data class WatchItem(val movie: Movie?, val show: Show?, val episode: Episode?, val sortKey: Long) {
    val id: String get() = episode?.id ?: movie!!.id
    val durationMs: Long get() = episode?.durationMs ?: movie?.durationMs ?: 0
    val offsetMs: Long get() = episode?.viewOffsetMs ?: movie?.viewOffsetMs ?: 0
    val progress: Float get() = if (durationMs > 0) (offsetMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Plex-style home rows for Movies and TV Shows. */
object Watching {
    /** Counts as watched when you reach the credits: 90 % in (Plex uses the same rule). */
    const val WATCHED_FRACTION = 0.9
    /** Less than this and it's not really "started". */
    const val MIN_RESUME_MS = 60_000L

    /** Like Plex: things you haven't touched in 16 weeks drop off Continue Watching / Up Next. */
    const val MAX_AGE_MS = 16L * 7 * 24 * 3600_000
    /** Something you watched this recently shows even before Plex's own list catches up. */
    const val JUST_WATCHED_MS = 24L * 3600_000

    /**
     * Whether a started/next item belongs on the home rows. With Plex ([plexIds] not null) PC items follow Plex's
     * own Continue Watching row; phone-only items (and PCs without Plex) use the 16-week rule.
     */
    private fun onDeck(id: String, fromPc: Boolean, lastViewed: Long?, plexIds: Set<String>?, now: Long, maxAge: Long): Boolean {
        if (maxAge == Long.MAX_VALUE) return true
        val age = now - (lastViewed ?: 0L)
        if (plexIds != null && fromPc) return id in plexIds || age < JUST_WATCHED_MS
        return lastViewed != null && lastViewed > 0 && age < maxAge
    }

    fun isWatched(positionMs: Long, durationMs: Long) = durationMs > 0 && positionMs >= durationMs * WATCHED_FRACTION

    private fun inProgress(offset: Long, watched: Boolean) = !watched && offset >= MIN_RESUME_MS

    /** Episodes in watch order. */
    fun episodesInOrder(show: Show): List<Episode> =
        show.seasons.sortedBy { it.number }.flatMap { s -> s.episodes.sortedBy { it.episode } }

    /** Movies and episodes you stopped part-way through, most recently watched first. */
    fun continueWatching(movies: List<Movie>, shows: List<Show>, plexIds: Set<String>? = null, now: Long = System.currentTimeMillis(), maxAge: Long = MAX_AGE_MS): List<WatchItem> {
        val m = movies.filter { mv -> inProgress(mv.viewOffsetMs, mv.watched) && onDeck(mv.id, mv.origin == Origin.PC, mv.lastViewedAt, plexIds, now, maxAge) }
            .map { WatchItem(it, null, null, it.lastViewedAt ?: it.addedAt) }
        val e = shows.flatMap { sh ->
            episodesInOrder(sh).filter { inProgress(it.viewOffsetMs, it.watched) && onDeck(it.id, it.uri.isEmpty(), it.lastViewedAt, plexIds, now, maxAge) }
                .map { WatchItem(null, sh, it, it.lastViewedAt ?: it.addedAt) }
        }
        return (m + e).sortedByDescending { it.sortKey }
    }

    /**
     * For each show you've started: the first unwatched episode after the last one you finished — which
     * includes a newly added episode after you'd caught up. Shows with an episode in progress are left to
     * Continue Watching. Ordered by most recent activity (watching, or a new episode arriving).
     */
    fun upNext(shows: List<Show>, plexIds: Set<String>? = null, now: Long = System.currentTimeMillis(), maxAge: Long = MAX_AGE_MS): List<WatchItem> = shows.mapNotNull { sh ->
        val eps = episodesInOrder(sh)
        if (eps.any { inProgress(it.viewOffsetMs, it.watched) }) return@mapNotNull null
        val lastDone = eps.indexOfLast { it.watched }
        if (lastDone < 0) return@mapNotNull null
        val next = eps.drop(lastDone + 1).firstOrNull { !it.watched } ?: return@mapNotNull null
        val lastViewed = eps.mapNotNull { it.lastViewedAt }.maxOrNull() ?: 0L
        // A new episode arriving counts as activity too (so a show you'd caught up on comes back).
        if (!onDeck(next.id, next.uri.isEmpty(), maxOf(lastViewed, next.addedAt), plexIds, now, maxAge)) return@mapNotNull null
        WatchItem(null, sh, next, maxOf(lastViewed, next.addedAt))
    }.sortedByDescending { it.sortKey }

    fun recentMovies(movies: List<Movie>, limit: Int = 20): List<Movie> =
        movies.filter { it.addedAt > 0 }.sortedByDescending { it.addedAt }.take(limit)

    /** Newest episodes, one card per show (its newest episode), like Plex's Recently Added. */
    fun recentEpisodes(shows: List<Show>, limit: Int = 20): List<WatchItem> =
        shows.mapNotNull { sh ->
            episodesInOrder(sh).filter { it.addedAt > 0 }.maxByOrNull { it.addedAt }?.let { WatchItem(null, sh, it, it.addedAt) }
        }.sortedByDescending { it.sortKey }.take(limit)

    /** Apply progress saved on the phone when it's newer than what the PC reported. */
    fun applyLocal(m: Movie, p: WatchProgress?): Movie =
        if (p == null || p.at < (m.lastViewedAt ?: 0)) m
        else m.copy(viewOffsetMs = if (p.watched) 0 else p.positionMs, watched = p.watched, lastViewedAt = p.at)

    fun applyLocal(e: Episode, p: WatchProgress?): Episode =
        if (p == null || p.at < (e.lastViewedAt ?: 0)) e
        else e.copy(viewOffsetMs = if (p.watched) 0 else p.positionMs, watched = p.watched, lastViewedAt = p.at)
}
