// fork:shelf-home paper-logos
package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfPaperLogoRulesTest {
    private val r = ShelfPaperLogoRules

    @Test
    fun pixelsAreSorted() {
        assertEquals(r.PALE, r.classify(1f, 1f, 1f))
        assertEquals(r.PALE, r.classify(0.85f, 0.85f, 0.82f))
        assertEquals(r.COLOUR, r.classify(0.9f, 0.1f, 0.15f)) // Sally4Ever's red
        assertEquals(r.DARK, r.classify(0.1f, 0.1f, 0.1f)) // a drop shadow
        assertEquals(r.OTHER, r.classify(0.5f, 0.5f, 0.5f)) // mid grey
    }

    @Test
    fun aWhiteLogoIsInked() {
        // White letters with a little grey anti-aliasing.
        assertTrue(r.inks(r.sample(pale = 900, colour = 0, dark = 20, other = 80)))
    }

    @Test
    fun aLogoWithColourKeepsIt() {
        // Mostly white, with a red "4".
        assertFalse(r.inks(r.sample(pale = 700, colour = 120, dark = 120, other = 60)))
    }

    @Test
    fun aDarkLogoIsLeftAlone() {
        assertFalse(r.inks(r.sample(pale = 50, colour = 0, dark = 900, other = 50)))
    }

    @Test
    fun nothingVisibleIsNoSample() {
        assertNull(r.sample(0, 0, 0, 0))
        assertFalse(r.inks(null))
    }
}
