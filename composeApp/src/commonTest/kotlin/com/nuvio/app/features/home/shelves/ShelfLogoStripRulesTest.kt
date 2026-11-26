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

    private fun meta(
        name: String = "Severance",
        logo: String? = null,
        releaseInfo: String? = null,
        genres: List<String> = emptyList(),
    ) = ShelfCard.Meta(
        MetaPreview(id = "m", type = "series", name = name, logo = logo, releaseInfo = releaseInfo, genres = genres),
    )

    private fun progress(season: Int? = null, episode: Int? = null, released: String? = null, logo: String? = null) =
        ShelfCard.Progress(
            ContinueWatchingItem(
                parentMetaId = "p",
                parentMetaType = "series",
                videoId = "v",
                title = "Show",
                subtitle = "",
                imageUrl = null,
                logo = logo,
                seasonNumber = season,
                episodeNumber = episode,
                released = released,
                resumePositionMs = 0L,
                durationMs = 0L,
                progressFraction = 0f,
            ),
        )

    private fun folder(logo: String? = null) =
        ShelfCard.Folder("c", CollectionFolder(id = "f", title = "Marvel", titleLogoUrl = logo))

    @Test
    fun focusedIsFullSizeAndNeighboursSmallAndFaded() {
        assertEquals(1f, r.scaleFor(0f), tol)
        assertEquals(0.45f, r.scaleFor(1f), tol)
        assertEquals(0.45f, r.scaleFor(-4f), tol)
        assertEquals(1f, r.alphaFor(0f), tol)
        assertEquals(0.35f, r.alphaFor(1f), tol)
        assertEquals(0.35f, r.alphaFor(-2f), tol)
    }

    @Test
    fun centresKeepTheGap() {
        assertEquals(0f, r.centreOffset(0f), tol)
        // 260 (half the focused box) + 80 gap + 117 (half a 234 dp neighbour)
        assertEquals(457f, r.centreOffset(1f), tol)
        assertEquals(-457f, r.centreOffset(-1f), tol)
        assertEquals(457f + 314f, r.centreOffset(2f), tol)
    }

    @Test
    fun oneNeighbourLeftAndTwoRightInA1300dpView() {
        assertEquals(574f, r.ANCHOR, tol)
        assertEquals(1, r.slotsFitting(r.ANCHOR))
        assertEquals(2, r.slotsFitting(1300f - r.ANCHOR))
    }

    @Test
    fun visibleRangeStaysInBounds() {
        assertEquals(0..3, r.visibleRange(focus = 0, count = 20, before = 1, after = 2))
        assertEquals(3..8, r.visibleRange(focus = 5, count = 20, before = 1, after = 2))
        assertEquals(17..19, r.visibleRange(focus = 19, count = 20, before = 1, after = 2))
        assertTrue(r.visibleRange(focus = 0, count = 0, before = 1, after = 2).isEmpty())
    }

    @Test
    fun arrowsStopAtTheEndsAndRightAtTheLastLetsGo() {
        assertEquals(1, r.move(0, 1, 3))
        assertNull(r.move(0, -1, 3))
        assertNull(r.move(2, 1, 3))
        assertTrue(r.consumes(0, -1, 3))
        assertFalse(r.consumes(2, 1, 3))
        assertTrue(r.consumes(1, 1, 3))
        assertEquals(2, r.clamp(7, 3))
        assertEquals(0, r.clamp(-1, 3))
    }

    @Test
    fun logoComesFromEachKindOfCard() {
        assertEquals("a.png", r.logoUrl(meta(logo = "a.png")))
        assertNull(r.logoUrl(meta(logo = "  ")))
        assertEquals("b.png", r.logoUrl(progress(logo = "b.png")))
        assertEquals("c.png", r.logoUrl(folder(logo = "c.png")))
        assertNull(r.logoUrl(folder()))
    }

    @Test
    fun nameIsCapitals() {
        assertEquals("THE BEAR", r.nameLabel(meta(name = " The Bear ")))
    }

    @Test
    fun infoLineShowsYearAndGenres() {
        assertEquals(
            "2022 · Drama, Mystery, Sci-Fi",
            r.infoLine(meta(releaseInfo = "2022-", genres = listOf("Drama", "Mystery", "Sci-Fi", "Thriller"))),
        )
        assertEquals("2022", r.infoLine(meta(releaseInfo = "2022")))
        assertEquals("Drama", r.infoLine(meta(genres = listOf("Drama", " "))))
        assertEquals("", r.infoLine(meta()))
    }

    @Test
    fun infoLineShowsEpisodeForContinueWatchingAndNothingForFolders() {
        assertEquals("S4 E1", r.infoLine(progress(season = 4, episode = 1)))
        assertEquals("2021", r.infoLine(progress(released = "2021-03-01")))
        assertEquals("", r.infoLine(folder()))
    }
}
