package com.amosley.signal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesTest {
    private fun t(id: String, o: Origin, title: String, dur: Long = 159_000, album: String? = "The Real Me", artist: String? = "Future") =
        Track(id = id, origin = o, title = title, artist = artist, album = album, durationMs = dur)

    private val list = listOf(
        t("pc1", Origin.PC, "One Two"), t("ph1", Origin.PHONE, "one two"),
        t("pc2", Origin.PC, "No Misery"),
        t("ph3", Origin.PHONE, "Only On Phone"),
        t("pc4", Origin.PC, "Intro", dur = 60_000), t("ph4", Origin.PHONE, "Intro", dur = 200_000),
    )

    @Test fun keepsPhoneCopy() =
        assertEquals(listOf("pc2", "ph1", "ph3", "pc4", "ph4").sorted(), Duplicates.merge(list, DuplicateMode.PHONE).map { it.id }.sorted())

    @Test fun keepsPcCopy() =
        assertEquals(listOf("pc1", "pc2", "ph3", "pc4", "ph4").sorted(), Duplicates.merge(list, DuplicateMode.PC).map { it.id }.sorted())

    @Test fun bothShowsEverything() = assertEquals(list, Duplicates.merge(list, DuplicateMode.BOTH))

    @Test fun differentAlbumsAreNotDuplicates() {
        val l = listOf(t("a", Origin.PC, "Song", album = "Live"), t("b", Origin.PHONE, "Song", album = "Studio"))
        assertEquals(2, Duplicates.merge(l, DuplicateMode.PHONE).size)
    }
}
