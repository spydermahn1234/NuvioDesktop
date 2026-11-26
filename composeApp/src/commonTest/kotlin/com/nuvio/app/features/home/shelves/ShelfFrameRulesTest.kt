// fork:shelf-home
package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfFrameRulesTest {
    private val clock: (Int) -> String = { m -> "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}" }

    private val ironGiant = ShelfCard.Meta(
        MetaPreview(
            id = "tt0129167",
            type = "movie",
            name = "The Iron Giant",
            releaseInfo = "1999",
            runtime = "86 min",
            genres = listOf("Animation", "Family", "Adventure"),
            imdbRating = "8.1",
            description = "A giant metal machine befriends a boy.",
        ),
    )

    private fun progress(position: Long, season: Int? = 4, episode: Int? = 1) = ShelfCard.Progress(
        ContinueWatchingItem(
            parentMetaId = "tt2085059",
            parentMetaType = "series",
            videoId = "tt2085059:4:1",
            title = "Black Mirror",
            subtitle = "S4 E1",
            imageUrl = null,
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = "USS Callister",
            resumePositionMs = position,
            durationMs = 76L * 60_000L + position,
            progressFraction = 0.2f,
        ),
    )

    @Test
    fun factsForATitle() {
        assertEquals("1999 · ANIMATION / FAMILY · 1H 26M · IMDB 8.1", ShelfFrameRules.facts(ironGiant))
        assertEquals("", ShelfFrameRules.facts(ShelfCard.Meta(MetaPreview(id = "x", type = "movie", name = "X"))))
    }

    @Test
    fun factsForContinueWatching() {
        assertEquals("S4 E1 · USS CALLISTER · 1H 16M LEFT", ShelfFrameRules.facts(progress(10 * 60_000L)))
        // Not started (Up next): the length, not "left".
        assertEquals("S4 E1 · USS CALLISTER · 1H 16M", ShelfFrameRules.facts(progress(0L)))
    }

    @Test
    fun endsLines() {
        assertEquals("Starts now, ends" to "21:23", ShelfFrameRules.ends(ironGiant, 19 * 60 + 57, clock))
        assertEquals("Finish by" to "21:13", ShelfFrameRules.ends(progress(10 * 60_000L), 19 * 60 + 57, clock))
        assertNull(ShelfFrameRules.ends(ShelfCard.Meta(MetaPreview(id = "x", type = "movie", name = "X")), 0, clock))
    }

    @Test
    fun galleryLine() {
        assertEquals("1999 · 1H 26M", ShelfFrameRules.yearLength(ironGiant))
        assertEquals("S4 E1 · 1H 16M LEFT", ShelfFrameRules.yearLength(progress(10 * 60_000L)))
    }

    @Test
    fun positionAndCount() {
        assertEquals("9 OF 50 · ROW 7 OF 85", ShelfFrameRules.position(8, 50, 6, 85))
        assertEquals("ROW 1 OF 3", ShelfFrameRules.position(0, 0, 0, 3))
        assertEquals("1 OF 50", ShelfFrameRules.count(0, 50))
        assertEquals("", ShelfFrameRules.count(0, 0))
    }

    @Test
    fun movingStaysInsideTheRow() {
        assertEquals(0, ShelfFrameRules.move(0, -1, 10))
        assertEquals(9, ShelfFrameRules.move(9, 1, 10))
        assertEquals(4, ShelfFrameRules.move(3, 1, 10))
        assertEquals(0, ShelfFrameRules.move(5, 1, 0))
    }

    @Test
    fun galleryWrapsAtTheEnd() {
        assertEquals(1, ShelfFrameRules.galleryNext(0, 3))
        assertEquals(0, ShelfFrameRules.galleryNext(2, 3))
        assertEquals(0, ShelfFrameRules.galleryNext(0, 0))
    }

    @Test
    fun pagesWithinSixOfTheEnd() {
        assertFalse(ShelfFrameRules.nearEnd(13, 20))
        assertTrue(ShelfFrameRules.nearEnd(14, 20))
        assertFalse(ShelfFrameRules.nearEnd(0, 0))
    }

    @Test
    fun yearFromReleaseLines() {
        assertEquals("2019", ShelfFrameRules.year("2019-2023"))
        assertEquals("2019", ShelfFrameRules.year("2019-05-01"))
        assertNull(ShelfFrameRules.year("soon"))
    }
}
