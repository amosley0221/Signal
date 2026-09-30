package com.amosley.signal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNamesTest {
    private fun t(id: String, artist: String?, albumArtist: String? = null) = Track(id = id, title = id, artist = artist, albumArtist = albumArtist)

    @Test fun featuredArtistsGroupUnderTheMainOne() {
        val m = ArtistNames.primary(listOf(
            t("a", "21 Savage"), t("b", "21 Savage & Doja Cat"), t("c", "21 Savage, Burna Boy & Metro Boomin"),
            t("d", "21 Savage feat. Drake"), t("e", "21 Savage (ft. Future)"), t("f", "Doja Cat"),
        ))
        assertEquals(setOf("21 Savage"), setOf(m["a"], m["b"], m["c"], m["d"], m["e"]))
        assertEquals("Doja Cat", m["f"])
    }

    @Test fun bandNamesWithSeparatorsStayWhole() {
        val m = ArtistNames.primary(listOf(t("a", "Earth, Wind & Fire"), t("b", "Simon & Garfunkel")))
        assertEquals("Earth, Wind & Fire", m["a"])
        assertEquals("Simon & Garfunkel", m["b"])
    }

    @Test fun albumArtistWins() {
        val m = ArtistNames.primary(listOf(t("a", "Metro Boomin & 21 Savage", albumArtist = "21 Savage")))
        assertEquals("21 Savage", m["a"])
    }

    @Test fun compilationAlbumArtistDoesNotHideTheRealArtists() {
        val m = ArtistNames.primary(listOf(
            t("a", "Buck Tillery", albumArtist = "Various Artists"), t("b", "June Marlowe & The Night Shift", albumArtist = "Various Artists"),
        ))
        assertEquals("Buck Tillery", m["a"])
        assertEquals("June Marlowe & The Night Shift", m["b"])
    }
}
