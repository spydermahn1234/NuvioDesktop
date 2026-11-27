package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// fork:shelf-home
class ShelfLogoStripRulesTest {
    private val r = ShelfLogoStripRules
    private val tol = 0.01f

    private fun meta(name: String = "Severance", logo: String? = null) = ShelfCard.Meta(
        MetaPreview(id = "m", type = "series", name = name, logo = logo),
    )

    private fun progress(logo: String? = null, position: Long = 0L, duration: Long = 0L, fraction: Float = 0f) =
        ShelfCard.Progress(
            ContinueWatchingItem(
                parentMetaId = "p",
                parentMetaType = "series",
                videoId = "v",
                title = "Show",
                subtitle = "",
                imageUrl = null,
                logo = logo,
                resumePositionMs = position,
                durationMs = duration,
                progressFraction = fraction,
            ),
        )

    private fun folder(logo: String? = null) =
        ShelfCard.Folder("c", CollectionFolder(id = "f", title = "Marvel", titleLogoUrl = logo))

    @Test
    fun sizesFollowTheMockup() {
        assertEquals(110f, r.ROW_H, tol)
        assertEquals(64f, r.GAP, tol)
        assertEquals(3f, r.BAR_H, tol)
        assertEquals(0.6f, r.DIM_ALPHA, tol)
        assertEquals(170f, r.TITLE_H, tol)
        assertEquals(900f, r.TITLE_MAX_W, tol)
        // The focused logo with its bar fits inside the row.
        assertTrue(r.FOCUSED_LOGO_H + r.BAR_GAP + r.BAR_H <= r.ROW_H)
        assertTrue(r.LOGO_H < r.FOCUSED_LOGO_H)
    }

    @Test
    fun clampKeepsTheIndexInTheRow() {
        assertEquals(2, r.clamp(7, 3))
        assertEquals(0, r.clamp(-1, 3))
        assertEquals(1, r.clamp(1, 3))
        assertEquals(0, r.clamp(4, 0))
    }

    @Test
    fun aspectIsBoundedAndDefaultsWhenUnknown() {
        assertEquals(2f, r.aspect(800, 400), tol)
        assertEquals(r.DEFAULT_ASPECT, r.aspect(0, 400), tol)
        assertEquals(r.DEFAULT_ASPECT, r.aspect(800, 0), tol)
        assertEquals(r.MAX_ASPECT, r.aspect(5000, 100), tol)
        assertEquals(r.MIN_ASPECT, r.aspect(100, 1000), tol)
    }

    @Test
    fun logoWidthFollowsHeightAndShape() {
        assertEquals(140f, r.logoWidth(70f, 2f), tol)
        assertEquals(96f, r.logoWidth(48f, 2f), tol)
        assertEquals(70f * r.DEFAULT_ASPECT, r.logoWidth(70f, null), tol)
        assertEquals(70f * r.MAX_ASPECT, r.logoWidth(70f, 50f), tol)
    }

    @Test
    fun farFocusChangesJumpAndStepsScroll() {
        assertFalse(r.jumps(0, 1))
        assertFalse(r.jumps(5, 0))
        assertFalse(r.jumps(0, r.JUMP_AFTER))
        assertTrue(r.jumps(0, r.JUMP_AFTER + 1))
        assertTrue(r.jumps(30, 2))
    }

    @Test
    fun logoComesFromEachKindOfCard() {
        assertEquals("a.png", r.logoUrl(meta(logo = "a.png")))
        assertNull(r.logoUrl(meta(logo = "  ")))
        assertNull(r.logoUrl(meta()))
        assertEquals("b.png", r.logoUrl(progress(logo = "b.png")))
        assertEquals("c.png", r.logoUrl(folder(logo = "c.png")))
        assertNull(r.logoUrl(folder()))
    }

    @Test
    fun nameIsCapitals() {
        assertEquals("THE BEAR", r.nameLabel(meta(name = " The Bear ")))
        assertEquals("MARVEL", r.nameLabel(folder()))
    }

    @Test
    fun logosAreSizedByArea() {
        // The default shape keeps the base height; unknown shape too.
        assertEquals(48f, r.logoHeight(48f, r.DEFAULT_ASPECT), tol)
        assertEquals(48f, r.logoHeight(48f, null), tol)
        // A wide wordmark gets shorter, a squarer badge taller: same area either way.
        val wide = r.logoHeight(48f, 4f)
        val square = r.logoHeight(48f, 1.5f)
        assertTrue(wide < 48f)
        assertTrue(square > 48f)
        assertEquals(48f * 48f * r.DEFAULT_ASPECT, wide * wide * 4f, 1f)
        // Never outside 60%..135% of the base.
        assertTrue(r.logoHeight(48f, r.MAX_ASPECT) >= 48f * r.MIN_H_SCALE - tol)
        assertEquals(48f * r.MAX_H_SCALE, r.logoHeight(48f, r.MIN_ASPECT), tol)
    }

    @Test
    fun progressBarOnlyForContinueWatching() {
        assertEquals(0.25f, r.progress(progress(position = 25L, duration = 100L))!!, tol)
        assertEquals(0f, r.progress(progress())!!, tol)
        assertEquals(0.4f, r.progress(progress(fraction = 0.4f))!!, tol)
        assertEquals(1f, r.progress(progress(position = 150L, duration = 100L))!!, tol)
        assertNull(r.progress(meta()))
        assertNull(r.progress(folder()))
    }
}
