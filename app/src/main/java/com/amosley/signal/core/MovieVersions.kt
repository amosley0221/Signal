package com.amosley.signal.core

/** Several files of the same movie (e.g. a 4K and a 1080p copy) shown as one poster with versions to choose from. */
object MovieVersions {
    private fun key(m: Movie): String? {
        val t = VideoNames.norm(m.title).ifEmpty { return null }
        return "$t|${m.year ?: ""}"
    }

    /** The copy shown on the poster: the one you're watching, else the highest resolution, else the biggest file. */
    private val preferred = compareByDescending<Movie> { it.lastViewedAt ?: 0L }
        .thenByDescending { it.width }.thenByDescending { it.size }.thenBy { it.id }

    fun merge(movies: List<Movie>): List<Movie> {
        val groups = movies.groupBy { key(it) ?: "id:${it.id}" }
        if (groups.size == movies.size) return movies
        val done = HashSet<String>()
        return movies.mapNotNull { m ->
            val k = key(m) ?: "id:${m.id}"
            if (!done.add(k)) return@mapNotNull null
            val g = groups.getValue(k)
            if (g.size == 1) m else {
                val sorted = g.sortedWith(preferred)
                sorted.first().copy(versions = sorted.drop(1))
            }
        }
    }
}
