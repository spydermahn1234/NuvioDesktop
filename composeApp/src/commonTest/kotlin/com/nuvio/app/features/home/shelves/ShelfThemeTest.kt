// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShelfThemeTest {
    private val gold = Color(0xFFFFD700)
    private val accents = listOf(gold, Color.White, Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFF7C4DFF), Color(0xFF1A73E8))

    @Test
    fun darkIsExactlyTheLookTheShelfHomeHadBefore() {
        val p = shelfPalette(ShelfThemeMode.Dark, accent = gold, onAccent = Color.Black)
        assertEquals(Color.Black, p.page)
        assertEquals(1f, p.backdropAlpha)
        assertEquals(Color.Black, p.scrim)
        assertEquals(Color.White, p.ink)
        assertEquals(Color.White.copy(alpha = 0.78f), p.inkSoft)
        assertEquals(Color.White.copy(alpha = 0.55f), p.inkDim)
        assertEquals(Color.Black.copy(alpha = 0.72f), p.panel)
        assertEquals(Color.White.copy(alpha = 0.10f), p.divider)
        assertEquals(gold, p.focusFill)
        assertEquals(Color.Black, p.focusText)
        assertEquals(gold, p.accent)
        assertEquals(gold, p.accentInk)
        assertEquals(Color.White.copy(alpha = 0.06f), p.rowFocus)
        assertEquals(Color.White.copy(alpha = 0.08f), p.placeholder)
        assertEquals(Color.White.copy(alpha = 0.85f), p.placeholderInk)
        assertEquals(Color(0xFF1B1B1F), p.tile)
        assertEquals(Color.Black.copy(alpha = 0.18f), p.posterDim)
        assertEquals(Color.Black.copy(alpha = 0.72f), p.pickerScrim)
        assertEquals(Color(0xFF17171C), p.pickerFill)
        assertEquals(Color.White.copy(alpha = 0.14f), p.pickerBorder)
        assertEquals(Color.White.copy(alpha = 0.05f), p.pickerRow)
        assertEquals(Color.White.copy(alpha = 0.10f), p.pickerRowHover)
        assertEquals(Color.White.copy(alpha = 0.10f), p.pickerRowBorder)
    }

    @Test
    fun darkPickerKeepsTheAppThemesTextColours() {
        val primary = Color(0xFFEEEEEE)
        val muted = Color(0xFF999999)
        val p = shelfPalette(ShelfThemeMode.Dark, gold, Color.Black, textPrimary = primary, textMuted = muted)
        assertEquals(primary, p.pickerTitle)
        assertEquals(muted, p.pickerMuted)
    }

    @Test
    fun paperHasNoBackdropAndKeepsTheAccentForFills() {
        val p = shelfPalette(ShelfThemeMode.Paper, gold, Color.Black)
        assertEquals(0f, p.backdropAlpha)
        assertEquals(Color(0xFFF4F1EA), p.page)
        assertEquals(Color(0xFF1A1A1A), p.ink)
        assertEquals(gold, p.accent)
        assertEquals(gold, p.focusFill)
        assertEquals(Color.Black, p.focusText)
    }

    @Test
    fun paperTextReadsAtFourAndAHalfToOne() {
        val p = shelfPalette(ShelfThemeMode.Paper, gold, Color.Black)
        val grounds = listOf(p.page, p.panel, p.placeholder)
        for (ground in grounds) {
            for (text in listOf(p.ink, p.inkSoft, p.inkDim, p.placeholderInk)) {
                assertReadable(text, ground)
            }
        }
        for (text in listOf(p.pickerTitle, p.pickerMuted)) assertReadable(text, p.pickerFill)
    }

    @Test
    fun paperTextAlsoReadsOnTheOtherPaperShades() {
        val p = shelfPalette(ShelfThemeMode.Paper, gold, Color.Black)
        for (ground in listOf(ShelfPaper.PAGE, ShelfPaper.WHITE, ShelfPaper.STONE)) {
            for (text in listOf(p.ink, p.inkSoft, p.inkDim)) assertReadable(text, ground)
        }
    }

    @Test
    fun paperAccentTextIsReadableWhateverTheAccent() {
        for (accent in accents) {
            val p = shelfPalette(ShelfThemeMode.Paper, accent, Color.Black)
            assertReadable(p.accentInk, p.page)
            assertReadable(p.accentInk, p.panel)
        }
    }

    @Test
    fun aReadableAccentIsLeftAlone() {
        val deepBlue = Color(0xFF1A3A8A)
        assertEquals(deepBlue, shelfReadableOn(deepBlue, ShelfPaper.STONE, ShelfPaper.INK))
    }

    @Test
    fun contrastMatchesTheKnownValues() {
        assertEquals(21f, shelfContrast(Color.Black, Color.White), 0.05f)
        assertEquals(1f, shelfContrast(ShelfPaper.PAGE, ShelfPaper.PAGE), 0.001f)
        // The mockup's muted ink is why inkDim is darker: it does not reach 4.5:1 on the paper.
        assertTrue(shelfContrast(ShelfPaper.RULE, ShelfPaper.PAGE) < 4.5f)
    }

    private fun assertReadable(text: Color, ground: Color) {
        val ratio = shelfContrast(text, ground)
        assertTrue(ratio >= 4.5f, "contrast $ratio < 4.5 for $text on $ground")
    }
}
