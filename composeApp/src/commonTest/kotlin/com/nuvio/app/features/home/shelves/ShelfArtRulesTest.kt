// fork:shelf-home
package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfArtRulesTest {
    @Test
    fun labelCountsFromOneAndHidesWithOnePicture() {
        assertEquals("ART 2 OF 5", ShelfArtRules.label(1, 5))
        assertEquals("ART 1 OF 2", ShelfArtRules.label(0, 2))
        assertEquals("ART 5 OF 5", ShelfArtRules.label(9, 5))
        assertNull(ShelfArtRules.label(0, 1))
        assertNull(ShelfArtRules.label(0, 0))
    }

    @Test
    fun nextWrapsToTheFirst() {
        assertEquals(1, ShelfArtRules.next(0, 3))
        assertEquals(0, ShelfArtRules.next(2, 3))
        assertEquals(0, ShelfArtRules.next(0, 1))
        assertEquals(0, ShelfArtRules.next(4, 0))
    }

    @Test
    fun holdIsEightSeconds() {
        assertEquals(8_000L, ShelfArtRules.HOLD_MS)
    }

    @Test
    fun textlessComeFirstThenTheCardsOwnBackdrop() {
        val set = ShelfArtRules.merge(
            textless = listOf("t1", " t2 ", "", "t1"),
            fanart = "own",
            wide = "wide",
            poster = "poster",
            textlessFirst = true,
        )
        assertEquals(listOf("t1", "t2", "own"), set.urls)
        assertFalse(set.fallback)
    }

    @Test
    fun theCardsOwnBackdropIsNotRepeatedWhenTmdbHasIt() {
        val set = ShelfArtRules.merge(listOf("a", "own"), "own", null, null, textlessFirst = true)
        assertEquals(listOf("a", "own"), set.urls)
    }

    @Test
    fun offMeansTheCardsOwnBackdropOnly() {
        val set = ShelfArtRules.merge(listOf("t1", "t2"), "own", "wide", "poster", textlessFirst = false)
        assertEquals(listOf("own"), set.urls)
    }

    @Test
    fun atMostTenPictures() {
        val set = ShelfArtRules.merge((1..14).map { "t$it" }, "own", null, null, textlessFirst = true)
        assertEquals(ShelfArtRules.MAX, set.urls.size)
        assertEquals("t1", set.urls.first())
    }

    @Test
    fun noBackdropFallsBackToTheWidePictureThenThePoster() {
        val wide = ShelfArtRules.merge(emptyList(), null, "wide", "poster", textlessFirst = true)
        assertEquals(listOf("wide"), wide.urls)
        assertTrue(wide.fallback)
        val poster = ShelfArtRules.merge(emptyList(), " ", null, "poster", textlessFirst = true)
        assertEquals(listOf("poster"), poster.urls)
        assertTrue(poster.fallback)
        val none = ShelfArtRules.merge(emptyList(), null, null, null, textlessFirst = true)
        assertTrue(none.urls.isEmpty())
        assertFalse(none.fallback)
    }

    @Test
    fun aQuickSetStartsOnTheFirstTextlessPicture() {
        assertEquals(0, ShelfArtRules.indexAfterArrival("own", listOf("t1", "t2", "own"), 400))
    }

    @Test
    fun aLateSetKeepsThePictureOnScreen() {
        assertEquals(2, ShelfArtRules.indexAfterArrival("own", listOf("t1", "t2", "own"), 4_000))
        assertEquals(0, ShelfArtRules.indexAfterArrival("gone", listOf("t1", "t2"), 4_000))
        assertEquals(0, ShelfArtRules.indexAfterArrival(null, listOf("t1"), 4_000))
        assertEquals(0, ShelfArtRules.indexAfterArrival("own", emptyList(), 4_000))
    }
}
