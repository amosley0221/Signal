package com.amosley.signal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AutoplayTest {
    private fun t(id: String, artist: String, album: String, genre: String, year: Int, title: String = id) =
        Track(id = id, title = title, artist = artist, album = album, genres = listOf(genre), year = year)

    private val lib = listOf(
        t("f1", "Future", "Monster", "Hip-Hop/Rap", 2014), t("f2", "Future", "Monster", "Hip-Hop/Rap", 2014),
        t("f3", "Future", "DS2", "Hip-Hop/Rap", 2015), t("f4", "Future", "DS2", "Hip-Hop/Rap", 2015),
        t("s1", "21 Savage", "Savage Mode", "Rap", 2016), t("s2", "21 Savage", "Savage Mode", "Rap", 2016),
        t("x1", "Metro Boomin, Future", "Joint", "Hip-Hop", 2017), t("m1", "Metro Boomin", "Heroes", "Hip-Hop", 2018),
        t("m2", "Metro Boomin", "Heroes", "Hip-Hop", 2018),
        t("k1", "Keith Sweat", "Make It Last", "R&B", 1988), t("k2", "Keith Sweat", "Make It Last", "R&B", 1988),
        t("c1", "Mozart", "Requiem", "Classical", 1791), t("c2", "Bach", "Cello Suites", "Classical", 1720),
        t("j1", "Miles Davis", "Kind of Blue", "Jazz", 1959),
    )

    @Test fun keepsGoingWithSimilarMusic() {
        val seeds = lib.filter { it.album == "Monster" }
        repeat(20) { seed ->
            val out = Autoplay.pick(seeds, lib, seeds.map { it.id }.toSet(), count = 5, rnd = Random(seed))
            assertEquals(5, out.size)
            assertTrue(out.none { it.id in setOf("f1", "f2") })
            // Rap and its relatives, never the classical or jazz albums when there's enough rap.
            assertTrue(out.map { it.id }.toString(), out.none { it.genres.first() in setOf("Classical", "Jazz") })
            // Never the same artist twice in a row, and at most two songs by one artist.
            out.zipWithNext().forEach { (a, b) -> assertTrue(a.artist != b.artist || a.artist == null) }
            assertTrue(out.groupBy { it.artist }.values.all { it.size <= 2 })
        }
    }

    @Test fun relatedArtistsComeFromSharedSongs() {
        val seeds = listOf(lib.first { it.id == "f3" })
        val counts = HashMap<String, Int>()
        repeat(50) { seed -> Autoplay.pick(seeds, lib, setOf("f3"), count = 3, rnd = Random(seed)).forEach { counts.merge(it.artist!!, 1, Int::plus) } }
        // Metro Boomin (who shares a song with Future) comes up more than Keith Sweat.
        assertTrue(counts.toString(), (counts["Metro Boomin"] ?: 0) > (counts["Keith Sweat"] ?: 0))
    }

    @Test fun genreWordsMatchAcrossSpellings() {
        assertEquals(setOf("hip", "hop", "rap"), Autoplay.genreWords(lib[0]))
        assertEquals(setOf("future", "metro boomin"), Autoplay.artistsOf(lib.first { it.id == "x1" }))
    }
}
