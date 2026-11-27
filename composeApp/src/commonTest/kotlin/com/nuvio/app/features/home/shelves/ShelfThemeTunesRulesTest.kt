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

class ShelfThemeTunesRulesTest {
    private fun meta(type: String, id: String = "tt0141842", name: String = "The Sopranos") =
        ShelfCard.Meta(MetaPreview(id = id, type = type, name = name, releaseInfo = "1999-2007", genres = listOf("Drama")))

    private fun progress(type: String, season: Int? = 3, episode: Int? = 2, id: String = "tt0141842") =
        ShelfCard.Progress(
            ContinueWatchingItem(
                parentMetaId = id,
                parentMetaType = type,
                videoId = "$id:3:2",
                title = "The Sopranos",
                subtitle = "S3 E2",
                imageUrl = null,
                seasonNumber = season,
                episodeNumber = episode,
                released = "2001-03-11",
                resumePositionMs = 0L,
                durationMs = 41L * 60_000L,
                progressFraction = 0f,
            ),
        )

    @Test
    fun aShowGetsATarget() {
        val t = assertNotNull(ShelfThemeTunesRules.target(meta("series")))
        assertEquals("tt0141842", t.key)
        assertEquals("The Sopranos", t.title)
        assertEquals("1999-2007", t.releaseInfo)
        assertEquals("series", t.type)
        assertEquals(listOf("Drama"), t.genres)
    }

    @Test
    fun animeIsAShow() {
        assertNotNull(ShelfThemeTunesRules.target(meta("anime")))
        assertNotNull(ShelfThemeTunesRules.target(meta("Series")))
    }

    @Test
    fun filmsChannelsAndFoldersGetNone() {
        assertNull(ShelfThemeTunesRules.target(meta("movie")))
        assertNull(ShelfThemeTunesRules.target(meta("tv")))
        assertNull(ShelfThemeTunesRules.target(meta("channel")))
        assertNull(ShelfThemeTunesRules.target(null))
        assertNull(ShelfThemeTunesRules.target(ShelfCard.Folder("c", CollectionFolder(id = "f", title = "Folder"))))
    }

    @Test
    fun aCardWithNoNameGetsNone() {
        assertNull(ShelfThemeTunesRules.target(meta("series", name = "  ")))
        assertNull(ShelfThemeTunesRules.target(meta("series", id = "")))
    }

    @Test
    fun continueWatchingOfAShowIsTheSameShow() {
        val fromRow = assertNotNull(ShelfThemeTunesRules.target(progress("series")))
        val fromCatalog = assertNotNull(ShelfThemeTunesRules.target(meta("series")))
        assertEquals(fromCatalog.key, fromRow.key)
        // The episode's air date is not the show's year.
        assertNull(fromRow.releaseInfo)
    }

    @Test
    fun continueWatchingOfAFilmGetsNone() {
        assertNull(ShelfThemeTunesRules.target(progress("movie", season = null, episode = null)))
        assertNull(ShelfThemeTunesRules.target(progress("movie")))
    }

    @Test
    fun anEpisodeNumberMakesItAShowWhateverItsType() {
        val t = assertNotNull(ShelfThemeTunesRules.target(progress("")))
        assertEquals("series", t.type)
        assertNull(ShelfThemeTunesRules.target(progress("", season = null, episode = null)))
    }

    @Test
    fun playsOnlyWhenNothingElseMakesSound() {
        assertTrue(ShelfThemeTunesRules.mayPlay(active = true, covered = false, trailerAudible = false, filmPlaying = false, musicPlaying = false))
        assertFalse(ShelfThemeTunesRules.mayPlay(active = false, covered = false, trailerAudible = false, filmPlaying = false, musicPlaying = false))
        assertFalse(ShelfThemeTunesRules.mayPlay(active = true, covered = true, trailerAudible = false, filmPlaying = false, musicPlaying = false))
        assertFalse(ShelfThemeTunesRules.mayPlay(active = true, covered = false, trailerAudible = true, filmPlaying = false, musicPlaying = false))
        assertFalse(ShelfThemeTunesRules.mayPlay(active = true, covered = false, trailerAudible = false, filmPlaying = true, musicPlaying = false))
        assertFalse(ShelfThemeTunesRules.mayPlay(active = true, covered = false, trailerAudible = false, filmPlaying = false, musicPlaying = true))
    }

    @Test
    fun fadesLinearlyOverOneSecond() {
        assertEquals(1_000L, ShelfThemeTunesRules.FADE_MS)
        assertEquals(1_500L, ShelfThemeTunesRules.WAIT_MS)
        assertEquals(0f, ShelfThemeTunesRules.fade(0f, 1f, 0L))
        assertEquals(0.5f, ShelfThemeTunesRules.fade(0f, 1f, 500L))
        assertEquals(1f, ShelfThemeTunesRules.fade(0f, 1f, 1_000L))
        assertEquals(1f, ShelfThemeTunesRules.fade(0f, 1f, 5_000L))
        // A fade out that starts part way down takes the same second.
        assertEquals(0.3f, ShelfThemeTunesRules.fade(0.6f, 0f, 500L), 0.0001f)
        assertEquals(0f, ShelfThemeTunesRules.fade(0.6f, 0f, 1_000L))
    }

    @Test
    fun playerVolumeFollowsTheSettingAndNeverHitsZero() {
        assertEquals(50, ShelfThemeTunesRules.playerVolume(50, 1f))
        assertEquals(25, ShelfThemeTunesRules.playerVolume(50, 0.5f))
        assertEquals(1, ShelfThemeTunesRules.playerVolume(50, 0f))
        assertEquals(100, ShelfThemeTunesRules.playerVolume(100, 2f))
    }
}
