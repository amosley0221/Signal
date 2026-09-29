package com.amosley.signal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private fun track(id: String, title: String, artist: String?, genres: List<String>, album: String? = null, n: Int? = null, disc: Int? = null) =
        Track(id = id, origin = Origin.PC, title = title, artist = artist, genres = genres, album = album, track = n, disc = disc)

    @Test fun lrcParsesStampsAndMeta() {
        val p = Lrc.parse("[la:es]\n[ti:Faro]\n[00:10.50]Baja la marea\n[00:00.00][01:00]Coro\n\n")
        assertEquals("es", p.lang)
        assertTrue(p.synced)
        assertEquals(listOf(0.0, 10.5, 60.0), p.lines.map { it.t })
        assertEquals("Coro", p.lines[0].text)
    }

    @Test fun lrcUnsyncedFallback() {
        val p = Lrc.parse("line one\nline two")
        assertEquals(false, p.synced)
        assertEquals(4.0, p.lines[1].t, 0.0)
    }

    @Test fun lrcMergeAndActive() {
        val o = listOf(LyricLine(0.0, "a"), LyricLine(10.0, "b"))
        val m = Lrc.merge(o, listOf(LyricLine(0.0, "A"), LyricLine(10.2, "B")))
        assertEquals("B", m[1].tr)
        assertEquals(-1, Lrc.activeIndex(listOf(LyricLine(5.0, "x")), 1.0))
        assertEquals(1, Lrc.activeIndex(o, 12.0))
    }

    @Test fun suggesterMatchesPrototype() {
        // Expected values computed with the prototype's JS suggestArtists().
        val all = listOf(
            track("t1", "Chrome Hearts Don’t Break", "Velvet Static", listOf("synthwave", "night drive")),
            track("t2", "Midnight Transit", "Velvet Static", listOf("synthwave")),
        )
        val t = track("x", "Glass Orchard", null, listOf("dream pop", "slow", "female vocal"))
        val s = ArtistSuggester.suggest(t, all + t, null, "")
        assertEquals(3, s.size)
        assertEquals("mood", s[0].kind)
        assertTrue(s[0].why.startsWith("Mood · dream pop, slow"))
        assertTrue(s.last().why.endsWith("female vocal"))
        val withExisting = ArtistSuggester.suggest(track("y", "Neon Rain", null, listOf("synthwave")), all, null, "a duo from a rainy port city")
        assertEquals("Velvet Static", withExisting[0].name)
        assertEquals("existing", withExisting[0].kind)
        assertEquals("prompt", withExisting[1].kind)
    }

    @Test fun hashMatchesJs() {
        // JS: hash('Glass Orchard') with h=7; h=(h*31+c)>>>0
        var h = 7L
        "Glass Orchard".forEach { h = (h * 31 + it.code) and 0xFFFFFFFFL }
        assertEquals(h, ArtistSuggester.hash("Glass Orchard"))
    }

    @Test fun qualityDetection() {
        assertEquals(Quality.HIRES, Quality.of("WAV", null, 24, 96000))
        assertEquals(Quality.LOSSLESS, Quality.of("FLAC", null, 16, 44100))
        assertEquals(Quality.LOSSY, Quality.of("MP3", null, null, 44100))
        assertEquals(Quality.LOSSLESS, Quality.of("M4A", "ALAC", 16, 44100))
        assertEquals(Quality.LOSSY, Quality.of("M4A", "AAC", null, 44100))
    }

    @Test fun albumOrderIsDiscThenTrackThenTitle() {
        val ts = listOf(
            track("a", "Zed", "X", emptyList(), "Alb", n = 1, disc = 2),
            track("b", "Beta", "X", emptyList(), "Alb", n = 2, disc = 1),
            track("c", "Alpha", "X", emptyList(), "Alb", n = 1, disc = 1),
        )
        val albums = groupAlbums(ts, emptyList())
        assertEquals(listOf("c", "b", "a"), albums.single().tracks.map { it.id })
    }

    @Test fun searchGroups() {
        val ts = listOf(track("a", "Harbor Lights", "Marlow Vane", emptyList(), "Salt"))
        val r = Search.run("harbor", ts, groupAlbums(ts, emptyList()), emptyList(), emptyList(), emptyList(), emptyList(), mapOf("a" to listOf(LyricLine(0.0, "the harbor"))))
        assertEquals(1, r.songs.size)
        assertEquals(0, r.lyrics.size) // already listed as a song
        assertEquals("2:05", Fmt.dur(125_000))
        assertEquals("2h 04m", Fmt.runtime(124 * 60_000L))
    }
}
