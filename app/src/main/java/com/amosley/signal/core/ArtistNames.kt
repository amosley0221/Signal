package com.amosley.signal.core

/** Groups songs under their main artist, so "21 Savage & Doja Cat" or "21 Savage feat. X" count as 21 Savage. */
object ArtistNames {
    private val feat = Regex("\\s*[(\\[]?\\s*\\b(feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)
    private val sep = Regex("\\s*(?:,|&|;|/|\\s+x\\s+|\\s+and\\s+)\\s*", RegexOption.IGNORE_CASE)

    fun stripFeatures(name: String): String = name.replace(feat, "").trim().ifEmpty { name.trim() }

    /**
     * Track id → main artist. Uses the album artist when set, else the track artist, minus "feat." parts.
     * "A, B & C" becomes A only when A is also an artist on its own somewhere in the library
     * (so "Earth, Wind & Fire" or "Simon & Garfunkel" stay whole).
     */
    fun primary(tracks: List<Track>): Map<String, String> {
        val base = HashMap<String, String>()
        tracks.forEach { t -> (t.albumArtist?.takeIf { it.isNotBlank() } ?: t.artist)?.let { base[t.id] = stripFeatures(it) } }
        val known = base.values.filter { !sep.containsMatchIn(it) }.map { it.lowercase() }.toHashSet()
        val canonical = HashMap<String, String>()
        base.values.forEach { n -> canonical.putIfAbsent(n.lowercase(), n) }
        return base.mapValues { (_, n) ->
            val head = sep.split(n, limit = 2).first().trim()
            if (head != n && head.lowercase() in known) canonical[head.lowercase()] ?: head else n
        }
    }
}
