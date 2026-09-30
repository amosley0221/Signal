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
