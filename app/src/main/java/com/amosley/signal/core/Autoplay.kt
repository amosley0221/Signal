package com.amosley.signal.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Autoplay (like Apple Music's ∞): when the queue runs out, keep going with songs from the library that are
 * like what was just played. "Like" means the same or related artists (artists who appear together on songs),
 * the same genres and a similar era, weighted toward the most recent songs.
 */
object Autoplay {
    /** Each credited artist of a song: its artist and album artist, plus anyone in "(feat. …)" in the title. */
    fun artistsOf(t: Track): Set<String> {
        val feat = Regex("\\((?:feat|ft|featuring|with)\\.?\\s+([^)]*)\\)", RegexOption.IGNORE_CASE).find(t.title)?.groupValues?.get(1)
        return PlaylistImport.artistNames(t.artist) + PlaylistImport.artistNames(t.albumArtist).filterNot { it == "various artists" } +
            PlaylistImport.artistNames(feat)
    }

    /** "Hip-Hop/Rap" → hip, hop, rap: genres compare by their words so close spellings still match. */
    fun genreWords(t: Track): Set<String> = t.genres.flatMap { g ->
        PlaylistImport.artistNames(g).flatMap { it.split(' ') }
    }.filter { it.length > 1 && it !in setOf("and", "music", "the") }.toSet()

    /**
     * Picks [count] songs to follow [seeds] (oldest first, the song playing now last), never from [exclude].
     * A batch has at most two songs per artist and never the same artist twice in a row.
     */
    fun pick(seeds: List<Track>, library: List<Track>, exclude: Set<String>, favorites: Set<String> = emptySet(), count: Int = 10, rnd: Random = Random.Default): List<Track> {
        val pool = library.filter { it.id !in exclude }
        if (pool.isEmpty()) return emptyList()
        if (seeds.isEmpty()) return pool.shuffled(rnd).take(count)

        // What was being listened to, recent songs counting most.
        val artistW = HashMap<String, Double>()
        val genreW = HashMap<String, Double>()
        val albums = HashSet<String>()
        val years = ArrayList<Pair<Int, Double>>()
        seeds.asReversed().forEachIndexed { i, s ->
            val w = 0.85.pow(i)
            artistsOf(s).forEach { artistW.merge(it, w, Double::plus) }
            genreWords(s).forEach { genreW.merge(it, w, Double::plus) }
            s.year?.let { years += it to w }
            if (i < 3) albums += s.albumKey
        }
        // Artists who share songs with those artists (features, collaborations, joint albums).
        val related = HashMap<String, Double>()
        for (t in library) {
            val names = artistsOf(t)
            if (names.size < 2) continue
            val seedHere = names.mapNotNull { artistW[it] }.maxOrNull() ?: continue
            names.filter { it !in artistW }.forEach { related.merge(it, seedHere, Double::plus) }
        }
        fun norm(m: Map<String, Double>) = (m.values.maxOrNull() ?: 0.0).let { top -> if (top <= 0) m else m.mapValues { it.value / top } }
        val a = norm(artistW); val r = norm(related); val g = norm(genreW)
        val year = if (years.isEmpty()) null else years.sumOf { it.first * it.second } / years.sumOf { it.second }

        fun score(t: Track): Double {
            val names = artistsOf(t)
            val same = names.maxOfOrNull { a[it] ?: 0.0 } ?: 0.0
            val rel = names.maxOfOrNull { r[it] ?: 0.0 } ?: 0.0
            val words = genreWords(t)
            val genre = if (words.isEmpty() || g.isEmpty()) 0.0 else words.sumOf { g[it] ?: 0.0 } / words.size
            val era = if (year != null && t.year != null) (1.0 - abs(t.year - year) / 12.0).coerceAtLeast(0.0) else 0.0
            var s = 2.5 * same + 3.0 * rel + 3.0 * genre + 1.0 * era
            if (t.id in favorites) s += 0.5
            if (t.albumKey in albums) s *= 0.5 // the album just played: move on to something else
            return s
        }
        val scored = pool.map { it to score(it) }.filter { it.second > 0.3 }.sortedByDescending { it.second }.take(300)
        val candidates = (if (scored.size >= count) scored else scored + pool.filter { p -> scored.none { it.first.id == p.id } }.shuffled(rnd).map { it to 0.2 })
            .toMutableList()

        val out = ArrayList<Track>()
        val perArtist = HashMap<String, Int>()
        var last: String? = null
        val lead = { t: Track -> PlaylistImport.artistNames(t.artist).firstOrNull() ?: "" }
        while (out.size < count && candidates.isNotEmpty()) {
            // Weighted draw: better matches come up more often, but it isn't the same order every time.
            val ok = candidates.filter { (t, _) -> lead(t) != last && (perArtist[lead(t)] ?: 0) < 2 }.ifEmpty { candidates }
            val total = ok.sumOf { it.second * it.second }
            var x = rnd.nextDouble() * total
            val chosen = ok.firstOrNull { x -= it.second * it.second; x <= 0 } ?: ok.last()
            candidates.remove(chosen)
            if (out.any { it.id == chosen.first.id }) continue
            out += chosen.first
            last = lead(chosen.first)
            perArtist.merge(last, 1, Int::plus)
        }
        return out
    }
}
