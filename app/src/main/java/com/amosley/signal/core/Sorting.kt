package com.amosley.signal.core

import kotlinx.serialization.Serializable

/** Sort options per library tab. Each key has a natural default direction (e.g. newest first for dates). */
enum class SortKey(val label: String, val defaultDescending: Boolean) {
    TITLE("A–Z", false),
    ARTIST("Artist", false),
    ALBUM("Album", false),
    ADDED("Date added", true),
    YEAR("Year", true),
    DURATION("Length", true),
    SONGS("Most songs", true),
    RATING("Rating", true),
}

@Serializable
data class SortPref(val key: SortKey, val descending: Boolean)

enum class SortTab(val options: List<SortKey>, val default: SortPref) {
    SONGS(listOf(SortKey.TITLE, SortKey.ARTIST, SortKey.ALBUM, SortKey.ADDED, SortKey.YEAR, SortKey.DURATION), SortPref(SortKey.TITLE, false)),
    ALBUMS(listOf(SortKey.TITLE, SortKey.ARTIST, SortKey.ADDED, SortKey.YEAR), SortPref(SortKey.TITLE, false)),
    ARTISTS(listOf(SortKey.TITLE, SortKey.SONGS, SortKey.ADDED), SortPref(SortKey.TITLE, false)),
    MOVIES(listOf(SortKey.TITLE, SortKey.ADDED, SortKey.YEAR, SortKey.RATING, SortKey.DURATION), SortPref(SortKey.ADDED, true)),
    SHOWS(listOf(SortKey.TITLE, SortKey.ADDED, SortKey.YEAR, SortKey.RATING), SortPref(SortKey.TITLE, false)),
}

object Sorting {
    /** Case-insensitive, ignores a leading "The " / "A " so "The Weeknd" sorts under W. */
    fun titleKey(s: String?): String {
        val t = s?.trim()?.lowercase() ?: return "￿"
        return t.removePrefix("the ").removePrefix("a ").ifEmpty { t }
    }

    private fun <T> Comparator<T>.dir(desc: Boolean) = if (desc) reversed() else this

    /** Missing values (no year, unknown artist…) always go last, whatever the direction. */
    private fun <T, K : Comparable<K>> nullsLast(desc: Boolean, sel: (T) -> K?): Comparator<T> = Comparator { a, b ->
        val x = sel(a); val y = sel(b)
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            desc -> y.compareTo(x)
            else -> x.compareTo(y)
        }
    }

    fun songs(list: List<Track>, p: SortPref): List<Track> {
        val byTitle = compareBy<Track> { titleKey(it.title) }
        val cmp: Comparator<Track> = when (p.key) {
            SortKey.ARTIST -> nullsLast<Track, String>(p.descending) { it.artist?.let(::titleKey) }.then(compareBy { titleKey(it.album) }).then(trackOrder)
            SortKey.ALBUM -> nullsLast<Track, String>(p.descending) { it.album?.let(::titleKey) }.then(trackOrder)
            SortKey.ADDED -> compareBy<Track> { it.addedAt }.dir(p.descending).then(byTitle)
            SortKey.YEAR -> nullsLast<Track, Int>(p.descending) { it.year }.then(byTitle)
            SortKey.DURATION -> compareBy<Track> { it.durationMs }.dir(p.descending).then(byTitle)
            else -> byTitle.dir(p.descending)
        }
        return list.sortedWith(cmp)
    }

    fun albums(list: List<Album>, p: SortPref): List<Album> {
        val byTitle = compareBy<Album> { titleKey(it.title) }
        val cmp: Comparator<Album> = when (p.key) {
            SortKey.ARTIST -> nullsLast<Album, String>(p.descending) { it.artist?.let(::titleKey) }.then(compareBy<Album> { it.year ?: 0 }).then(byTitle)
            SortKey.ADDED -> compareBy<Album> { a -> a.tracks.maxOfOrNull { it.addedAt } ?: 0 }.dir(p.descending).then(byTitle)
            SortKey.YEAR -> nullsLast<Album, Int>(p.descending) { it.year }.then(byTitle)
            else -> byTitle.dir(p.descending)
        }
        return list.sortedWith(cmp)
    }

    /** Artists as (name, song count); [addedAt] = newest song per artist. */
    fun artists(list: List<Pair<String, Int>>, addedAt: Map<String, Long>, p: SortPref): List<Pair<String, Int>> {
        val byName = compareBy<Pair<String, Int>> { titleKey(it.first) }
        val cmp = when (p.key) {
            SortKey.SONGS -> compareBy<Pair<String, Int>> { it.second }.dir(p.descending).then(byName)
            SortKey.ADDED -> compareBy<Pair<String, Int>> { addedAt[it.first] ?: 0 }.dir(p.descending).then(byName)
            else -> byName.dir(p.descending)
        }
        return list.sortedWith(cmp)
    }

    fun movies(list: List<Movie>, p: SortPref): List<Movie> {
        val byTitle = compareBy<Movie> { titleKey(it.title) }
        val cmp: Comparator<Movie> = when (p.key) {
            SortKey.ADDED -> compareBy<Movie> { it.addedAt }.dir(p.descending).then(byTitle)
            SortKey.YEAR -> nullsLast<Movie, Int>(p.descending) { it.year }.then(byTitle)
            SortKey.RATING -> nullsLast<Movie, Double>(p.descending) { it.rating }.then(byTitle)
            SortKey.DURATION -> compareBy<Movie> { it.durationMs }.dir(p.descending).then(byTitle)
            else -> byTitle.dir(p.descending)
        }
        return list.sortedWith(cmp)
    }

    fun shows(list: List<Show>, p: SortPref): List<Show> {
        val byTitle = compareBy<Show> { titleKey(it.title) }
        val cmp: Comparator<Show> = when (p.key) {
            SortKey.ADDED -> compareBy<Show> { it.addedAt }.dir(p.descending).then(byTitle)
            SortKey.YEAR -> nullsLast<Show, Int>(p.descending) { it.year }.then(byTitle)
            SortKey.RATING -> nullsLast<Show, Double>(p.descending) { it.rating }.then(byTitle)
            else -> byTitle.dir(p.descending)
        }
        return list.sortedWith(cmp)
    }
}
