package com.amosley.signal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

class PastedLyricsTest {
    @Test fun cleansSunoSectionLabels() {
        val text = "[Verse 1]\nHeadlights spill across the lane\n\n[Chorus]\nChrome hearts (don't break)\n(Instrumental break)\n  Last line  "
        assertEquals(listOf("Headlights spill across the lane", "Chrome hearts (don't break)", "Last line"), PastedLyrics.clean(text))
    }

    @Test fun lrcRoundTrip() {
        val lines = listOf(LyricLine(1.0, "a"), LyricLine(65.25, "b"))
        val lrc = PastedLyrics.toLrc(lines, synced = true)
        assertEquals("[00:01.00]a\n[01:05.25]b", lrc)
        assertTrue(PastedLyrics.isLrc(lrc))
        assertEquals(lines.map { it.t }, Lrc.parse(lrc).lines.map { it.t })
    }

    @Test fun spreadCoversSong() {
        val s = PastedLyrics.spread(listOf("a", "b", "c"), 120.0)
        assertEquals(3, s.size)
        assertTrue(s.last().t < 120.0)
    }
}

class SortingTest {
    private fun t(id: String, title: String, artist: String?, added: Long, year: Int? = null) =
        Track(id, Origin.PC, title, artist = artist, addedAt = added, year = year)

    @Test fun songsByTitleIgnoresLeadingThe() {
        val r = Sorting.songs(listOf(t("1", "The Zebra", null, 0), t("2", "Apple", null, 0), t("3", "banana", null, 0)), SortPref(SortKey.TITLE, false))
        assertEquals(listOf("2", "3", "1"), r.map { it.id })
    }

    @Test fun dateAddedNewestFirstAndMissingYearsLast() {
        val list = listOf(t("a", "A", null, 10, 2020), t("b", "B", null, 30), t("c", "C", null, 20, 2024))
        assertEquals(listOf("b", "c", "a"), Sorting.songs(list, SortPref(SortKey.ADDED, true)).map { it.id })
        assertEquals(listOf("c", "a", "b"), Sorting.songs(list, SortPref(SortKey.YEAR, true)).map { it.id })
        assertEquals(listOf("a", "c", "b"), Sorting.songs(list, SortPref(SortKey.YEAR, false)).map { it.id })
    }

    @Test fun artistsBySongCount() {
        val r = Sorting.artists(listOf("X" to 2, "Y" to 5, "Z" to 1), emptyMap(), SortPref(SortKey.SONGS, true))
        assertEquals(listOf("Y", "X", "Z"), r.map { it.first })
    }
}

class WatchingTest {
    private fun ep(id: String, s: Int, e: Int, watched: Boolean = false, offset: Long = 0, viewed: Long? = null, added: Long = 1) =
        Episode(id, s, e, "E$e", durationMs = 3_000_000, viewOffsetMs = offset, watched = watched, lastViewedAt = viewed, addedAt = added)
    private fun show(id: String, vararg eps: Episode) = Show(id, id, seasons = eps.groupBy { it.season }.map { (n, l) -> Season(n, l) })

    @Test fun upNextIsEpisodeAfterLastWatched() {
        val sh = show("a", ep("1", 1, 1, watched = true, viewed = 10), ep("2", 1, 2, watched = true, viewed = 20), ep("3", 1, 3), ep("4", 2, 1))
        assertEquals("3", Watching.upNext(listOf(sh), now = 100).single().episode!!.id)
    }

    @Test fun inProgressGoesToContinueWatchingNotUpNext() {
        val sh = show("a", ep("1", 1, 1, watched = true, viewed = 10), ep("2", 1, 2, offset = 600_000, viewed = 30))
        assertEquals(emptyList<WatchItem>(), Watching.upNext(listOf(sh), now = 100))
        assertEquals("2", Watching.continueWatching(emptyList(), listOf(sh), now = 100).single().episode!!.id)
    }

    @Test fun newEpisodeAfterCatchingUpAppearsInUpNext() {
        val caughtUp = show("a", ep("1", 1, 1, watched = true, viewed = 10), ep("2", 1, 2, watched = true, viewed = 20))
        assertEquals(emptyList<WatchItem>(), Watching.upNext(listOf(caughtUp), now = 100))
        val withNew = show("a", ep("1", 1, 1, watched = true, viewed = 10), ep("2", 1, 2, watched = true, viewed = 20), ep("3", 1, 3, added = 99))
        val item = Watching.upNext(listOf(withNew), now = 100).single()
        assertEquals("3", item.episode!!.id)
        assertEquals(99L, item.sortKey) // newly added episode bumps the show to the front
    }

    @Test fun oldProgressDropsOffAfter16Weeks() {
        val week = 7L * 24 * 3600_000
        val sh = show("a", ep("1", 1, 1, offset = 600_000, viewed = 1))
        assertEquals(1, Watching.continueWatching(emptyList(), listOf(sh), now = 10 * week).size)
        assertEquals(0, Watching.continueWatching(emptyList(), listOf(sh), now = 17 * week).size)
        assertEquals(1, Watching.continueWatching(emptyList(), listOf(sh), now = 17 * week, maxAge = Long.MAX_VALUE).size)
    }

    @Test fun onlyWhatYouWatchedInSignal() {
        val now = 100L * 24 * 3600_000
        val movies = listOf(
            Movie(id = "mine", title = "A", viewOffsetMs = 600_000, lastViewedAt = now - 1000),
            Movie(id = "plexOnly", title = "B", viewOffsetMs = 600_000, lastViewedAt = now - 1000),
        )
        assertEquals(listOf("mine"), Watching.continueWatching(movies, emptyList(), now = now, mine = setOf("mine")).map { it.id })
        // "Include Plex" adds Plex's own row on top.
        assertEquals(setOf("mine", "plexOnly"), Watching.continueWatching(movies, emptyList(), plexIds = setOf("plexOnly"), now = now, mine = setOf("mine")).map { it.id }.toSet())
        val sh = show("s", ep("1", 1, 1, watched = true, viewed = now - 1000), ep("2", 1, 2))
        assertEquals(0, Watching.upNext(listOf(sh), now = now, mine = emptySet()).size)
        assertEquals("2", Watching.upNext(listOf(sh), now = now, mine = setOf("1")).single().id)
    }

    @Test fun plexListDecidesForPcItems() {
        val sh = show("a", ep("1", 1, 1, offset = 600_000, viewed = 50), ep("2", 1, 2, offset = 600_000, viewed = 60))
        val now = 100L * 24 * 3600_000
        // Nothing watched in Signal; with "include Plex" on, Plex's row (episode 1) still shows.
        assertEquals(listOf("1"), Watching.continueWatching(emptyList(), listOf(sh), plexIds = setOf("1"), now = now, mine = emptySet()).map { it.id })
    }

    @Test fun watchedAtCredits() {
        assertTrue(Watching.isWatched(2_700_000, 3_000_000))
        assertEquals(false, Watching.isWatched(2_000_000, 3_000_000))
    }

    @Test fun localProgressWinsWhenNewer() {
        val e = ep("1", 1, 1, offset = 100_000, viewed = 10)
        assertEquals(500_000L, Watching.applyLocal(e, WatchProgress(500_000, 3_000_000, false, 20)).viewOffsetMs)
        assertEquals(100_000L, Watching.applyLocal(e, WatchProgress(500_000, 3_000_000, false, 5)).viewOffsetMs)
    }
}

class VideoNamesTest {
    @Test fun releaseNames() {
        assertEquals(VideoNames.Parsed("Minions and Monsters", 2026), VideoNames.parse("Minions.and.Monsters.2026.2160p.iT.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-BYNDR.mkv"))
        assertEquals(VideoNames.Parsed("Low Orbit", 2025), VideoNames.parse("Low Orbit (2025).mp4"))
        assertEquals(VideoNames.Parsed("2012", 2009), VideoNames.parse("2012.2009.1080p.BluRay.x264.mkv"))
        assertEquals("Home Video", VideoNames.parse("Home_Video.mp4").title)
        assertEquals(Triple("The Ballast", 1, 4), VideoNames.parseEpisode("The.Ballast.S01E04.1080p.WEB.mkv"))
    }

    @Test fun matchesPlexTitleWithAmpersand() {
        val pc = listOf(Movie("m1", title = "Minions & Monsters", year = 2026), Movie("m2", title = "Other", year = 2026))
        assertEquals("m1", VideoNames.matchMovie(VideoNames.parse("Minions.and.Monsters.2026.2160p.mkv"), pc)?.id)
        assertEquals(null, VideoNames.matchMovie(VideoNames.Parsed("Minions and Monsters", 2010), pc))
    }
}

class LanUrlTest {
    @Test fun followsThePcToItsNewHomeAddress() {
        val pc = listOf("192.168.1.37", "172.20.0.1", "100.101.5.6")
        assertEquals("http://192.168.1.37:8765", LanUrl.refreshed("http://192.168.1.20:8765", pc, "192.168.1.50"))
        assertNull(LanUrl.refreshed("http://192.168.1.37:8765", pc, "192.168.1.50")) // unchanged
        assertNull(LanUrl.refreshed("http://192.168.1.20:8765", pc, null)) // phone not on Wi-Fi
        assertNull(LanUrl.refreshed("http://192.168.1.20:8765", pc, "10.0.0.5")) // phone on another network
        assertNull(LanUrl.refreshed("http://tone-pc2:8765", pc, "192.168.1.50")) // a name, not an address
    }
}
