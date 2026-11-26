package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfSpotlightRulesTest {
    private val r = ShelfSpotlightRules

    @Test
    fun focusedIsFullSizeAndNeighboursHalf() {
        assertEquals(1f, r.scaleFor(0f))
        assertEquals(0.5f, r.scaleFor(1f))
        assertEquals(0.5f, r.scaleFor(-3f))
        assertEquals(0.75f, r.scaleFor(0.5f))
    }

    @Test
    fun centresKeepTheGapBetweenPosters() {
        assertEquals(0f, r.centreOffset(0f))
        // 150 (half the big one) + 54 gap + 75 (half a small one)
        assertEquals(279f, r.centreOffset(1f))
        assertEquals(-279f, r.centreOffset(-1f))
        assertEquals(279f + 204f, r.centreOffset(2f))
        assertEquals(-(279f + 2 * 204f), r.centreOffset(-3f))
    }

    @Test
    fun oneWholeNeighbourFitsLeftOfTheSlot() {
        assertEquals(1, r.slotsFitting(r.ANCHOR))
        // A 1300 dp view leaves 946 dp right of the focused centre: four neighbours show.
        assertEquals(4, r.slotsFitting(1300f - r.ANCHOR))
        assertEquals(0, r.slotsFitting(0f))
    }

    @Test
    fun visibleRangeAddsOneForTheSlideAndStaysInBounds() {
        assertEquals(0..5, r.visibleRange(focus = 0, count = 20, before = 1, after = 4))
        assertEquals(8..15, r.visibleRange(focus = 10, count = 20, before = 1, after = 4))
        assertEquals(17..19, r.visibleRange(focus = 19, count = 20, before = 1, after = 4))
        assertTrue(r.visibleRange(focus = 0, count = 0, before = 1, after = 4).isEmpty())
        assertEquals(0..2, r.visibleRange(focus = 99, count = 3, before = 1, after = 4))
    }

    @Test
    fun clampKeepsTheIndexOnTheShelf() {
        assertEquals(0, r.clamp(5, 0))
        assertEquals(2, r.clamp(5, 3))
        assertEquals(0, r.clamp(-1, 3))
        assertEquals(1, r.clamp(1, 3))
    }

    @Test
    fun movingStopsAtTheEnds() {
        assertEquals(1, r.move(0, 1, 3))
        assertNull(r.move(0, -1, 3))
        assertNull(r.move(2, 1, 3))
        assertNull(r.move(0, 1, 0))
    }

    @Test
    fun leftAtTheFirstStaysRightAtTheLastLetsGo() {
        assertTrue(r.consumes(0, -1, 5))
        assertFalse(r.consumes(4, 1, 5))
        assertTrue(r.consumes(2, 1, 5))
        assertTrue(r.consumes(2, -1, 5))
    }
}
