// fork:shelf-home v3
package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfTrailerRulesTest {
    private fun meta(type: String = "movie", id: String = "tt0469494") =
        ShelfCard.Meta(MetaPreview(id = id, type = type, name = "There Will Be Blood"))

    private fun progress() = ShelfCard.Progress(
        ContinueWatchingItem(
            parentMetaId = "tt2085059",
            parentMetaType = "series",
            videoId = "tt2085059:4:1",
            title = "Black Mirror",
            subtitle = "S4 E1",
            imageUrl = null,
            resumePositionMs = 0L,
            durationMs = 60L * 60_000L,
            progressFraction = 0f,
        ),
    )

    @Test
    fun aFilmAsksAboutItself() {
        assertEquals(ShelfTrailerSubject("movie", "tt0469494"), ShelfTrailerRules.subject(meta()))
        assertEquals("movie:tt0469494", ShelfTrailerRules.subject(meta())?.key)
    }

    @Test
    fun anEpisodeAsksAboutItsShow() {
        assertEquals(ShelfTrailerSubject("series", "tt2085059"), ShelfTrailerRules.subject(progress()))
    }

    @Test
    fun cardsWithoutATrailerAskNothing() {
        assertNull(ShelfTrailerRules.subject(null))
        assertNull(ShelfTrailerRules.subject(ShelfCard.Folder("x", CollectionFolder(id = "a", title = "A"))))
        assertNull(ShelfTrailerRules.subject(meta(type = "collection")))
        assertNull(ShelfTrailerRules.subject(meta(type = "music-album")))
        assertNull(ShelfTrailerRules.subject(meta(type = "live_tv_channel")))
        assertNull(ShelfTrailerRules.subject(meta(id = "")))
    }

    @Test
    fun armsOnlyWhenBrowsingATitleNotAlreadySpent() {
        assertTrue(ShelfTrailerRules.shouldArm(active = true, key = "movie:a", spentKey = null, hasWindow = true))
        assertFalse(ShelfTrailerRules.shouldArm(active = false, key = "movie:a", spentKey = null, hasWindow = true))
        assertFalse(ShelfTrailerRules.shouldArm(active = true, key = null, spentKey = null, hasWindow = true))
        assertFalse(ShelfTrailerRules.shouldArm(active = true, key = "movie:a", spentKey = "movie:a", hasWindow = true))
        assertTrue(ShelfTrailerRules.shouldArm(active = true, key = "movie:b", spentKey = "movie:a", hasWindow = true))
        assertFalse(ShelfTrailerRules.shouldArm(active = true, key = "movie:a", spentKey = null, hasWindow = false))
    }

    @Test
    fun progressIsPositionOverDuration() {
        assertEquals(0f, ShelfTrailerRules.progress(5_000L, 0L))
        assertEquals(0.5f, ShelfTrailerRules.progress(60_000L, 120_000L))
        assertEquals(1f, ShelfTrailerRules.progress(130_000L, 120_000L))
        assertEquals(0f, ShelfTrailerRules.progress(-10L, 120_000L))
    }

    @Test
    fun theWindowSitsInTheOpenArtAt1080p() {
        val w = assertNotNull(ShelfTrailerRules.window(1920f, 1080f))
        // Right edge lines up with the clock, 96 in.
        assertEquals(1824f, w.left + w.width, 0.01f)
        // Clear of the title block (x 96..1096) and of the status lines under the clock.
        assertTrue(w.left >= 1096f)
        assertEquals(236f, w.top)
        // Above the row's header (y 790) with room to spare, and 16:9.
        assertTrue(w.top + w.height <= 750f)
        assertEquals(16f / 9f, w.width / w.height, 0.01f)
    }

    @Test
    fun theWindowFollowsTheTopShiftAndShrinksToFit() {
        val shifted = assertNotNull(ShelfTrailerRules.window(1920f, 1080f, topShiftDp = 40f))
        assertEquals(276f, shifted.top)
        // A short screen: the height is capped and the width follows, still right-aligned.
        val short = assertNotNull(ShelfTrailerRules.window(2400f, 900f))
        assertTrue(short.top + short.height <= 900f - 290f - 40f + 0.01f)
        assertEquals(2304f, short.left + short.width, 0.01f)
        assertEquals(16f / 9f, short.width / short.height, 0.01f)
    }

    @Test
    fun noWindowWhenTheHomeIsTooSmall() {
        assertNull(ShelfTrailerRules.window(1400f, 1080f))
        assertNull(ShelfTrailerRules.window(1920f, 700f))
    }
}
