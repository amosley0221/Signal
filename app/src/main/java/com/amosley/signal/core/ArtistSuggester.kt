package com.amosley.signal.core

/** A suggested fictional artist name plus the reason shown under it. */
data class ArtistSuggestion(val name: String, val why: String, val kind: String)

/**
 * Port of `suggestArtists()` from the design prototype: deterministic (hash of the title) picks from
 * word banks chosen by the track's mood tags, plus an existing-artist match, style-prompt word,
 * title word, lyric word and a solo-artist style name.
 */
object ArtistSuggester {
    private class Bank(val adj: List<String>, val noun: List<String>, val first: List<String>, val last: List<String>)

    private val banks = mapOf(
        "bolero" to Bank(listOf("Faro", "Luna", "Sal", "Marea", "Noche"), listOf("Azul", "de Plata", "del Puerto", "Amarga", "Sur"), listOf("Lucía", "Inés", "Mar", "Rocío"), listOf("Serrano", "Valdés", "Ferrer", "del Mar")),
        "dream pop" to Bank(listOf("Glass", "Pale", "Velvet", "Hollow", "Lunar", "Soft"), listOf("Orchard", "Meridian", "Lantern", "Halo", "Bloom", "Weather"), listOf("Ilse", "Marin", "Ophelia", "Wren"), listOf("Vane", "Kessler", "Rook", "Ashby")),
        "synthwave" to Bank(listOf("Neon", "Chrome", "Analog", "Night", "Sodium", "Vector"), listOf("Cathedral", "Transit", "Arcade", "Horizon", "Signal", "Motorway"), listOf("Rex", "Dana", "Kai", "Vera"), listOf("Voltage", "Nakamura", "Halberd", "Cross")),
        "desert rock" to Bank(listOf("Dust", "Copper", "Iron", "Sun", "Red"), listOf("Coyote", "Highway", "Mirage", "Saints", "Canyon", "Static"), listOf("Cal", "Rosa", "Jude", "Mae"), listOf("Reyes", "Holloway", "Buckner", "Stone")),
        "dark cabaret" to Bank(listOf("Ghost", "Velvet", "Midnight", "Gilded", "Crooked"), listOf("Parlour", "Waltz", "Carousel", "Marionette", "Radio", "Orchestra"), listOf("Madame", "Lucien", "Odette", "Balthazar"), listOf("Noir", "Grimm", "Valois", "Pike")),
        "indie folk" to Bank(listOf("Paper", "Harbor", "Cedar", "Salt", "Quiet"), listOf("Compass", "Lights", "Letters", "Fathom", "Almanac"), listOf("Marlow", "June", "Elias", "Tamsin"), listOf("Vane", "Harrow", "Whitlock", "Beck")),
        "industrial" to Bank(listOf("Ninefold", "Failsafe", "Concrete", "Null", "Ballistic"), listOf("Array", "Protocol", "Sector", "Engine", "Method"), listOf("Unit", "Axl", "Sable", "Kron"), listOf("Zero", "Voss", "Mach", "Theta")),
    )
    private val defaultBank = Bank(listOf("Hollow", "Golden", "Silver", "Northern", "Wild"), listOf("Static", "Meridian", "Signal", "Orchard", "Company"), listOf("Ada", "Miles", "Nova", "Sol"), listOf("Vane", "Rook", "Kessler", "Ashby"))
    private val stop = setOf("the", "a", "an", "of", "over", "don’t", "and", "in", "on", "my", "your", "at", "to", "for")

    /** Same as the JS `hash`: h = h*31 + charCode, as unsigned 32-bit, iterating code points. */
    fun hash(s: String): Long {
        var h = 7L
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            // JS `for (const c of s)` iterates code points but charCodeAt(0) returns the high surrogate.
            val code = if (cp > 0xFFFF) s[i].code else cp
            h = (h * 31 + code) and 0xFFFFFFFFL
            i += Character.charCount(cp)
        }
        return h
    }

    private fun <T> pick(arr: List<T>, h: Long, k: Int): T = arr[((h ushr (k * 3)) % arr.size).toInt()]

    private fun capitalize(w: String) = w.substring(0, 1).uppercase() + w.substring(1).lowercase()

    fun suggest(track: Track, allTracks: List<Track>, lyrics: List<LyricLine>? = null, stylePrompt: String = ""): List<ArtistSuggestion> {
        val tags = track.tags
        val bank = tags.firstOrNull { banks.containsKey(it) }?.let { banks[it] } ?: defaultBank
        val h = hash(track.title)
        val words = track.title.split(Regex("\\s+")).filter { it.isNotEmpty() && it.lowercase() !in stop }
        val titleWord = if (words.isEmpty()) "Echo" else words[((h ushr 2) % words.size).toInt()]
        val lyricLine = lyrics?.takeIf { it.isNotEmpty() }?.let { it[((h ushr 4) % it.size).toInt()].text }
        val out = mutableListOf<ArtistSuggestion>()

        // Consistency: an existing artist whose songs share a tag with this one.
        val byArtist = linkedMapOf<String, Int>()
        allTracks.forEach { t ->
            val a = t.artist
            if (a != null && t.id != track.id && t.tags.any { it in tags }) byArtist[a] = (byArtist[a] ?: 0) + 1
        }
        val existing = byArtist.entries.sortedByDescending { it.value }.firstOrNull()
        if (existing != null) {
            val shared = tags.firstOrNull { g -> allTracks.any { it.artist == existing.key && g in it.tags } }
            out += ArtistSuggestion(existing.key, "Existing artist · ${existing.value} song${if (existing.value > 1) "s" else ""} share tag “$shared”", "existing")
        }
        val promptWords = stylePrompt.split(Regex("\\s+")).map { it.replace(Regex("[^a-zA-Z]"), "") }
            .filter { it.length > 3 && it.lowercase() !in stop }
        if (promptWords.isNotEmpty()) {
            val pw = promptWords[(h % promptWords.size).toInt()]
            out += ArtistSuggestion("${capitalize(pw)} ${pick(bank.noun, h, 5)}", "From your style prompt · “$pw”", "prompt")
        }
        out += ArtistSuggestion("${pick(bank.adj, h, 0)} ${pick(bank.noun, h, 1)}", "Mood · ${tags.take(2).joinToString(", ").ifEmpty { "untagged" }}", "mood")
        out += ArtistSuggestion("$titleWord ${pick(bank.noun, h, 2)}", "Title word · “$titleWord”", "title")
        if (lyricLine != null) {
            val lw = lyricLine.split(Regex("\\s+")).filter { it.length > 4 && it.lowercase() !in stop }
            val w = if (lw.isNotEmpty()) lw[((h ushr 6) % lw.size).toInt()] else titleWord
            val cleaned = w.substring(1).replace(Regex("[^a-zA-Z]"), "")
            val base = w.substring(0, 1).uppercase() + cleaned
            out += ArtistSuggestion("The $base${if (base.endsWith("s")) "" else "s"}", "Lyric · “$lyricLine”", "lyric")
        }
        val vocal = when {
            "female vocal" in tags -> "female vocal"
            "male vocal" in tags -> "male vocal"
            else -> "fits the album grouping"
        }
        out += ArtistSuggestion("${pick(bank.first, h, 3)} ${pick(bank.last, h, 4)}", "Solo-artist style · $vocal", "person")
        val seen = HashSet<String>()
        return out.filter { seen.add(it.name) }.take(5)
    }
}
