package com.amosley.signal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlphaIndexTest {
    @Test fun lettersIgnoreArticlesAccentsAndCase() {
        assertEquals('W', AlphaIndex.letterOf("The Weeknd"))
        assertEquals('E', AlphaIndex.letterOf("élan"))
        assertEquals('#', AlphaIndex.letterOf("2Pac"))
        assertEquals('#', AlphaIndex.letterOf(null))
        assertEquals('B', AlphaIndex.letterOf("a bird"))
    }

    @Test fun firstPositionPerLetterAndNearestFallback() {
        val idx = AlphaIndex.build(listOf("1999", "Apple", "Avocado", "Cherry", "Zebra"))
        assertEquals(mapOf('#' to 0, 'A' to 1, 'C' to 3, 'Z' to 4), idx)
        assertEquals(3, AlphaIndex.positionFor(idx, 'B'))
        assertEquals(4, AlphaIndex.positionFor(idx, 'M'))
        assertEquals(1, AlphaIndex.positionFor(AlphaIndex.build(listOf("Apple", "Zebra")), 'Z'))
        assertNull(AlphaIndex.positionFor(emptyMap(), 'A'))
    }

    @Test fun descendingListStillMapsEachLetterToItsFirstRow() {
        val idx = AlphaIndex.build(listOf("Zebra", "Mango", "Apple"))
        assertEquals(0, AlphaIndex.positionFor(idx, 'Z'))
        assertEquals(2, AlphaIndex.positionFor(idx, 'A'))
        // missing letter falls to the next one in rail order
        assertEquals(0, AlphaIndex.positionFor(idx, 'P'))
    }
}
