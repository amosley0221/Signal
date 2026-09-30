package com.amosley.signal.core

/** Groups songs under their main artist, so "21 Savage & Doja Cat" or "21 Savage feat. X" count as 21 Savage. */
object ArtistNames {
    private val feat = Regex("\\s*[(\\[]?\\s*\\b(feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)
    private val sep = Regex("\\s*(?:,|&|;|/|\\s+x\\s+|\\s+and\\s+)\\s*", RegexOption.IGNORE_CASE)

    fun stripFeatures(name: String): String = name.replace(feat, "").trim().ifEmpty { name.trim() }

    /**
     * Track id → main artist: the song's own artist minus "feat." parts. The album artist is used only when it is one
     * of the song's artists (e.g. "Metro Boomin & 21 Savage" on a 21 Savage album) or the song has no artist, so a
     * compilation's "Various Artists" never swallows the real artists.
     * "A, B & C" becomes A only when A is also an artist on its own somewhere in the library
     * (so "Earth, Wind & Fire" or "Simon & Garfunkel" stay whole).
     */
    fun primary(tracks: List<Track>): Map<String, String> {
        val base = HashMap<String, String>()
        tracks.forEach { t ->
            val own = t.artist?.takeIf { it.isNotBlank() }
            val aa = t.albumArtist?.takeIf { it.isNotBlank() }
            val pick = when {
                own == null -> aa
                aa != null && own.contains(aa, ignoreCase = true) -> aa
                else -> own
            }
            pick?.let { base[t.id] = stripFeatures(it) }
        }
        val known = base.values.filter { !sep.containsMatchIn(it) }.map { it.lowercase() }.toHashSet()
        val canonical = HashMap<String, String>()
        base.values.forEach { n -> canonical.putIfAbsent(n.lowercase(), n) }
        return base.mapValues { (_, n) ->
            val head = sep.split(n, limit = 2).first().trim()
            if (head != n && head.lowercase() in known) canonical[head.lowercase()] ?: head else n
        }
    }
}
