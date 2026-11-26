package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// fork:shelf-home
class ShelfLedgerRulesTest {
    private val clock: (Int) -> String = { m -> "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}" }
    private val now = 19 * 60 + 27 // 19:27

    private fun meta(runtime: String? = null, releaseInfo: String? = null) =
        ShelfCard.Meta(MetaPreview(id = "m", type = "movie", name = "Film", runtime = runtime, releaseInfo = releaseInfo))

    private fun progress(
        positionMs: Long,
        durationMs: Long,
        season: Int? = null,
        episode: Int? = null,
        released: String? = null,
    ) = ShelfCard.Progress(
        ContinueWatchingItem(
            parentMetaId = "p",
            parentMetaType = "series",
            videoId = "v",
            title = "Show",
            subtitle = "",
            imageUrl = null,
            seasonNumber = season,
            episodeNumber = episode,
            released = released,
            resumePositionMs = positionMs,
            durationMs = durationMs,
            progressFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
        ),
    )

    @Test
    fun ranksOnlyRankedShelves() {
        assertEquals("1", ShelfLedgerRules.rankLabel(0, ranked = true))
        assertEquals("10", ShelfLedgerRules.rankLabel(9, ranked = true))
        assertEquals("", ShelfLedgerRules.rankLabel(0, ranked = false))
    }

    @Test
    fun filmShowsLengthAndEndTime() {
        assertEquals("1h 49m · 21:16", ShelfLedgerRules.rightLabel(meta(runtime = "109 min"), now, clock))
    }

    @Test
    fun filmWithoutLengthShowsYear() {
        assertEquals("2019", ShelfLedgerRules.rightLabel(meta(releaseInfo = "2019-2023"), now, clock))
        assertEquals("", ShelfLedgerRules.rightLabel(meta(), now, clock))
    }

    @Test
    fun partWatchedShowsTimeLeftAndEnd() {
        val card = progress(positionMs = 10 * 60_000L, durationMs = 86 * 60_000L)
        assertEquals("1h 16m left · 20:43", ShelfLedgerRules.rightLabel(card, now, clock))
    }

    @Test
    fun notStartedShowsLengthWithoutLeft() {
        val card = progress(positionMs = 0L, durationMs = 52 * 60_000L)
        assertEquals("52m · 20:19", ShelfLedgerRules.rightLabel(card, now, clock))
    }

    @Test
    fun progressWithoutLengthFallsBackToEpisodeThenYear() {
        assertEquals("S4 E1", ShelfLedgerRules.rightLabel(progress(0L, 0L, season = 4, episode = 1), now, clock))
        assertEquals("2017", ShelfLedgerRules.rightLabel(progress(0L, 0L, released = "2017-12-29"), now, clock))
    }

    @Test
    fun endTimeWrapsPastMidnight() {
        assertEquals("2h · 01:00", ShelfLedgerRules.rightLabel(meta(runtime = "2h"), 23 * 60, clock))
    }

    @Test
    fun folderHasNoLabel() {
        val folder = ShelfCard.Folder("c", CollectionFolder(id = "f", title = "Marvel"))
        assertEquals("", ShelfLedgerRules.rightLabel(folder, now, clock))
    }

    @Test
    fun readsYears() {
        assertEquals("1999", ShelfLedgerRules.year("1999"))
        assertNull(ShelfLedgerRules.year("soon"))
        assertNull(ShelfLedgerRules.year(null))
    }
}
