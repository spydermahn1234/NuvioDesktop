package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// fork:shelf-home
class ShelfWallRulesTest {
    private val rules = ShelfWallRules

    @Test
    fun aTvWidthGivesEightPostersAboutOneFiftyWide() {
        val columns = rules.columns(1300f)
        assertEquals(8, columns)
        val w = rules.posterWidth(1300f, columns)
        assertTrue(w in 140f..155f, "poster width $w")
    }

    @Test
    fun postersAreNeverNarrowerThanTheMinimum() {
        for (width in listOf(140f, 200f, 300f, 640f, 960f, 1300f, 1700f, 2400f)) {
            val c = rules.columns(width)
            assertTrue(rules.posterWidth(width, c) >= rules.MIN_POSTER_W, "width $width")
            // One more column would have been too narrow.
            assertTrue(rules.posterWidth(width, c + 1) < rules.MIN_POSTER_W, "width $width")
        }
    }

    @Test
    fun columnsAndGapsFillTheWidthExactly() {
        val c = rules.columns(1300f)
        val w = rules.posterWidth(1300f, c)
        assertEquals(1300f, w * c + rules.GAP * (c - 1), 0.01f)
    }

    @Test
    fun tinyOrEmptyWidthsStillGiveOneColumn() {
        assertEquals(1, rules.columns(0f))
        assertEquals(1, rules.columns(-5f))
        assertEquals(1, rules.columns(60f))
        assertEquals(0f, rules.posterWidth(0f, 1))
    }

    @Test
    fun postersAreTwoByThree() {
        assertEquals(225f, rules.posterHeight(150f), 0.01f)
        assertEquals(225f + rules.TITLE_GAP + rules.TITLE_H, rules.cellHeight(150f), 0.01f)
    }

    @Test
    fun theShelfAreaShowsTwoFullRowsAndPartOfAThird() {
        val w = rules.posterWidth(1300f, rules.columns(1300f))
        assertEquals(3, rules.rowsVisible(540f, w))
        assertEquals(0, rules.rowsVisible(0f, w))
    }

    @Test
    fun rankLabelOnlyOnRankedShelves() {
        assertEquals("1", rules.rankLabel(0, ranked = true))
        assertEquals("10", rules.rankLabel(9, ranked = true))
        assertEquals("", rules.rankLabel(0, ranked = false))
        assertEquals("", rules.rankLabel(-1, ranked = true))
    }
}
