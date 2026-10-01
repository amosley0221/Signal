package com.amosley.signal.core

import com.amosley.signal.data.PhoneFolderType
import com.amosley.signal.data.PhoneLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLibraryTest {
    private fun t(id: String, folder: String) = Track(id, Origin.PHONE, id, folder = folder)
    private fun v(id: String, title: String, folder: String) = Movie(id, Origin.PHONE, title, folder = folder)

    @Test fun onlyChosenFoldersAndSubfoldersAreIncluded() {
        val chosen = mapOf("Music/Suno" to PhoneFolderType.MUSIC)
        val tracks = listOf(t("a", "Music/Suno"), t("b", "Music/Suno/Album"), t("c", "Music/Samsung"), t("d", "Ringtones"))
        val out = PhoneLibrary.split(tracks, emptyList(), chosen)
        assertEquals(listOf("a", "b"), out.tracks.map { it.id })
    }

    @Test fun mostSpecificFolderWins() {
        val chosen = mapOf("Movies" to PhoneFolderType.MOVIES, "Movies/Shows" to PhoneFolderType.TV)
        assertEquals(PhoneFolderType.TV, PhoneLibrary.typeOf("Movies/Shows/Ballast", chosen))
        assertEquals(PhoneFolderType.MOVIES, PhoneLibrary.typeOf("Movies/Other", chosen))
        assertEquals(null, PhoneLibrary.typeOf("MoviesX", chosen))
    }

    @Test fun tvEpisodesGroupIntoShowsAndSeasons() {
        val chosen = mapOf("TV" to PhoneFolderType.TV)
        val vids = listOf(
            v("1", "The Ballast - S01E02 - Drift", "TV/The Ballast/Season 1"),
            v("2", "The Ballast - S01E01 - Pilot", "TV/The Ballast/Season 1"),
            v("3", "Episode A", "TV/Other Show"),
        )
        val shows = PhoneLibrary.split(emptyList(), vids, chosen).shows
        assertEquals(listOf("Other Show", "The Ballast"), shows.map { it.title })
        val ballast = shows.last()
        assertEquals(listOf(1, 2), ballast.seasons.single().episodes.map { it.episode })
        assertEquals("Pilot", ballast.seasons.single().episodes.first().title)
    }
}

class OnlineLyricsTest {
    private fun rec(dur: Double, synced: String? = null, plain: String? = null, instrumental: Boolean = false) =
        com.amosley.signal.data.LrclibRecord(duration = dur, syncedLyrics = synced, plainLyrics = plain, instrumental = instrumental)

    @Test fun picksClosestSyncedWithinTolerance() {
        val r = com.amosley.signal.data.OnlineLyrics.pick(
            listOf(rec(200.0, plain = "a"), rec(119.0, synced = "[00:01.00]x"), rec(300.0, synced = "[00:01.00]far")), 120.0,
        )
        assertEquals("[00:01.00]x", r?.syncedLyrics)
    }

    @Test fun convertsSyncedPlainAndInstrumental() {
        val synced = com.amosley.signal.data.OnlineLyrics.toLyrics(rec(1.0, synced = "[00:02.50]Hello\n[00:05.00]World"))!!
        assertEquals(true, synced.synced)
        assertEquals(2.5, synced.lines.first().t, 0.0)
        val plain = com.amosley.signal.data.OnlineLyrics.toLyrics(rec(1.0, plain = "one\ntwo"))!!
        assertEquals(false, plain.synced)
        assertEquals("lrclib-instrumental", com.amosley.signal.data.OnlineLyrics.toLyrics(rec(1.0, instrumental = true))!!.source)
    }
}

class PhoneFolderTreeTest {
    private val all = listOf(
        com.amosley.signal.data.PhoneFolder("Music/Suno", 10, 0),
        com.amosley.signal.data.PhoneFolder("Music/Suno/Album", 5, 0),
        com.amosley.signal.data.PhoneFolder("Movies/Inception (2010)", 0, 1),
        com.amosley.signal.data.PhoneFolder("DCIM/Camera", 0, 8),
    )

    @Test fun rootListsTopFoldersWithTotals() {
        val top = com.amosley.signal.data.PhoneLibrary.children("", all)
        assertEquals(listOf("DCIM", "Movies", "Music"), top.map { it.path })
        assertEquals(15, top.first { it.path == "Music" }.audio)
    }

    @Test fun drillsDown() {
        assertEquals(listOf("Music/Suno"), com.amosley.signal.data.PhoneLibrary.children("Music", all).map { it.path })
    }

    @Test fun movieTitleFromItsFolder() {
        val v = Movie("locv:1", Origin.PHONE, "inception", folder = "Movies/Inception (2010)")
        val out = com.amosley.signal.data.PhoneLibrary.split(emptyList(), listOf(v), mapOf("Movies" to com.amosley.signal.data.PhoneFolderType.MOVIES))
        assertEquals("Inception", out.movies.single().title)
        assertEquals(2010, out.movies.single().year)
    }
}

class OnlineVideoInfoTest {
    @Test fun parsesItunesAndPicksByTitleAndYear() {
        val json = """{"resultCount":2,"results":[
          {"trackName":"Minions & Monsters","artistName":"Pierre Coffin","releaseDate":"2026-07-01T07:00:00Z","primaryGenreName":"Kids & Family",
           "contentAdvisoryRating":"PG","longDescription":"This is the rambunctious...","artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Video/x/100x100bb.jpg","trackTimeMillis":5340000},
          {"trackName":"Monsters, Inc.","releaseDate":"2001-11-02T08:00:00Z"}]}"""
        val all = com.amosley.signal.data.OnlineVideoInfo.parseItunes(json)
        assertEquals(2, all.size)
        val m = com.amosley.signal.data.OnlineVideoInfo.pickMovie(all, "Minions and Monsters", 2026)!!
        assertEquals("Pierre Coffin", m.director)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Video/x/600x900bb.jpg", m.posterUrl)
        assertEquals(null, com.amosley.signal.data.OnlineVideoInfo.pickMovie(all, "Minions and Monsters", 2010))
    }

    @Test fun parsesTvmazeShowWithEpisodes() {
        val json = """{"id":1,"name":"The Ballast","premiered":"2024-01-05","summary":"<p>Sea &amp; sky.</p>","genres":["Drama"],
          "rating":{"average":8.1},"image":{"medium":"m.jpg","original":"o.jpg"},
          "_embedded":{"episodes":[{"season":1,"number":1,"name":"Pilot","summary":"<p>Start</p>","runtime":45,"image":{"medium":"e1.jpg"}}]}}"""
        val s = com.amosley.signal.data.OnlineVideoInfo.parseTvmazeShowJson(json)!!
        assertEquals("Sea & sky.", s.synopsis)
        assertEquals(2024, s.year)
        assertEquals("Pilot", s.episodes.single().title)
        assertEquals(45 * 60_000L, s.episodes.single().durationMs)
        val phone = Show("locs:ballast", "The Ballast", seasons = listOf(Season(1, listOf(Episode("locv:9", 1, 1, "the ballast s01e01")))))
        val out = com.amosley.signal.data.OnlineVideoInfo.apply(phone, s)
        assertEquals("Pilot", out.seasons.single().episodes.single().title)
        assertEquals("o.jpg", out.posterUrl)
    }
}

class PhoneMatchTest {
    private fun ep(s: Int, e: Int, id: String) = com.amosley.signal.core.Episode(id = id, season = s, episode = e, title = "t$e")

    @Test fun showsWithTheSameTitleStaySeparate() {
        val dahmer = com.amosley.signal.core.Show(id = "a", title = "Monster", seasons = listOf(com.amosley.signal.core.Season(1, (1..10).map { ep(1, it, "d$it") })))
        val gein = com.amosley.signal.core.Show(id = "b", title = "Monster", seasons = listOf(
            com.amosley.signal.core.Season(1, (1..8).map { ep(1, it, "g$it") }), com.amosley.signal.core.Season(4, (1..8).map { ep(4, it, "l$it") })))
        assertEquals(listOf("a", "b"), com.amosley.signal.data.PhoneMatch.shows(listOf(dahmer, gein), emptyList()).map { it.id })
        // A phone copy goes to the same-titled show that has its episodes.
        val phone = com.amosley.signal.core.Show(id = "locs:monster", title = "Monster", seasons = listOf(com.amosley.signal.core.Season(4, listOf(ep(4, 2, "p").copy(uri = "content://x")))))
        val out = com.amosley.signal.data.PhoneMatch.shows(listOf(dahmer, gein), listOf(phone))
        assertEquals(2, out.size)
        assertEquals("content://x", out[1].seasons[1].episodes[1].uri)
    }
}
