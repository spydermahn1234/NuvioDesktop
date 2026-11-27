// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShelfAccentRulesTest {
    private val scrim = Color(0xFF06090A)

    private fun close(a: Color, b: Color, tolerance: Float = 0.01f) =
        abs(a.red - b.red) <= tolerance && abs(a.green - b.green) <= tolerance && abs(a.blue - b.blue) <= tolerance

    @Test
    fun aReadableColourComesBackAsItIs() {
        // The board's swatches: desert gold, sea glass, neon orange, spring lime.
        listOf(Color(0xFFE8B86A), Color(0xFF9FD0D8), Color(0xFFF0A040), Color(0xFFD8E090)).forEach { c ->
            assertEquals(c, ShelfAccentRules.readable(c))
        }
    }

    @Test
    fun aDarkColourIsLiftedUntilItReadsOnTheScrim() {
        listOf(Color(0xFF1F3238), Color(0xFF2B1D3A), Color(0xFF0A1A40), Color(0xFF5A4632), Color(0xFF26304A)).forEach { c ->
            val out = ShelfAccentRules.readable(c)
            assertTrue(shelfContrast(out, scrim) >= ShelfAccentRules.MIN_CONTRAST, "contrast of $out")
            // Lifted only just enough: not run up to white.
            assertTrue(shelfContrast(out, scrim) < 4.5f, "lifted too far: $out")
        }
    }

    @Test
    fun liftingKeepsTheHue() {
        val c = Color(0xFF1F3238)
        val (h0, s0, _) = ShelfAccentRules.toHsl(c)
        val (h1, s1, l1) = ShelfAccentRules.toHsl(ShelfAccentRules.readable(c))
        assertTrue(abs(h0 - h1) < 3f, "hue $h0 -> $h1")
        assertTrue(abs(s0 - s1) < 0.05f, "saturation $s0 -> $s1")
        assertTrue(l1 > ShelfAccentRules.toHsl(c).third)
    }

    @Test
    fun blackBecomesAReadableGrey() {
        val out = ShelfAccentRules.readable(Color.Black)
        assertTrue(shelfContrast(out, scrim) >= 3f)
        assertTrue(abs(out.red - out.green) < 0.01f && abs(out.green - out.blue) < 0.01f)
    }

    @Test
    fun theResultIsOpaque() {
        assertEquals(1f, ShelfAccentRules.readable(Color(0x801F3238)).alpha)
        assertEquals(1f, ShelfAccentRules.readable(Color(0x80E8B86A)).alpha)
    }

    @Test
    fun hslRoundTrips() {
        listOf(Color(0xFFE8B86A), Color(0xFF1F3238), Color(0xFFB18CFF), Color(0xFF808080), Color.Red, Color.Blue).forEach { c ->
            val (h, s, l) = ShelfAccentRules.toHsl(c)
            assertTrue(close(c, ShelfAccentRules.fromHsl(h, s, l)), "round trip of $c")
        }
    }
}
