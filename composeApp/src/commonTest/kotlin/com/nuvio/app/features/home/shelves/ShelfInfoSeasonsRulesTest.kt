package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShelfInfoSeasonsRulesTest {
    private val show = "tt2085059"

    private fun ep(season: Int, number: Int, title: String = "E$number", minutes: Int? = 50, released: String? = null) =
        ShelfEpisode(season, number, title, minutes, "$show:$season:$number", released = released)

    /** Four seasons of three, plus two specials. */
    private val all = buildList {
        for (s in 1..4) for (e in 1..3) add(ep(s, e))
        add(ep(0, 1))
        add(ep(0, 2))
    }.let { list -> ShelfInfoSeasonsRules.seasons(list).flatMap { s -> list.filter { it.season == s } } }

    private fun entry(
        season: Int,
        number: Int,
        position: Long,
        duration: Long = 50 * 60_000L,
        completed: Boolean = false,
        updated: Long = 1L,
    ) = WatchProgressEntry(
        contentType = "series",
        parentMetaId = show,
        parentMetaType = "series",
        videoId = "$show:$season:$number",
        title = "Black Mirror",
        seasonNumber = season,
        episodeNumber = number,
        lastPositionMs = position,
        durationMs = duration,
        lastUpdatedEpochMs = updated,
        isCompleted = completed,
    )

    @Test
    fun onlyShowsGetTheSeasonList() {
        val series = ShelfCard.Meta(MetaPreview(id = show, type = "series", name = "Black Mirror"))
        val film = ShelfCard.Meta(MetaPreview(id = "tt0129167", type = "movie", name = "The Iron Giant"))
        assertEquals("series" to show, ShelfInfoSeasonsRules.showKey(series))
        assertNull(ShelfInfoSeasonsRules.showKey(film))
        val episodeCard = ShelfCard.Progress(
            ContinueWatchingItem(
                parentMetaId = show, parentMetaType = "series", videoId = "$show:4:2", title = "Black Mirror",
                subtitle = "", imageUrl = null, resumePositionMs = 0L, durationMs = 0L, progressFraction = 0f,
            ),
        )
        assertEquals("series" to show, ShelfInfoSeasonsRules.showKey(episodeCard))
    }

    @Test
    fun episodesAreOrderedWithSpecialsLast() {
        val videos = listOf(
            MetaVideo(id = "$show:0:1", title = "Special", season = 0, episode = 1),
            MetaVideo(id = "$show:2:1", title = "Be Right Back", season = 2, episode = 1, runtime = 48),
            MetaVideo(id = "$show:1:2", title = "Fifteen Million Merits", season = 1, episode = 2),
            MetaVideo(id = "$show:1:1", title = "The National Anthem", season = 1, episode = 1),
            MetaVideo(id = "$show:1:1", title = "Duplicate", season = 1, episode = 1),
            MetaVideo(id = "", title = "No id", season = 2, episode = 2),
            MetaVideo(id = "trailer", title = "No numbers"),
        )
        val list = ShelfInfoSeasonsRules.episodes(show, videos)
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2, 0 to 1), list.map { it.season to it.number })
        assertEquals("The National Anthem", list.first().title)
        assertEquals(48, list[2].minutes)
        // Without an id of its own an episode plays by the show's id and its numbers.
        assertEquals("$show:2:2", list[3].videoId)
        assertEquals(listOf(1, 2, 0), ShelfInfoSeasonsRules.seasons(list))
        assertEquals(2, ShelfInfoSeasonsRules.seasonCount(list))
    }

    @Test
    fun seasonTabsReadLikeTheBoard() {
        assertEquals("SEASON 1", ShelfInfoSeasonsRules.seasonLabel(1, first = true))
        assertEquals("4", ShelfInfoSeasonsRules.seasonLabel(4, first = false))
        assertEquals("SPECIALS", ShelfInfoSeasonsRules.seasonLabel(0, first = false))
    }

    @Test
    fun statesComeFromProgressAndWatchedMarks() {
        val states = ShelfInfoSeasonsRules.states(
            all,
            "series",
            show,
            listOf(entry(4, 1, position = 50 * 60_000L, completed = true), entry(4, 2, position = 26 * 60_000L)),
            setOf(watchedItemKey("series", show, 3, 3)),
        )
        assertEquals(true, states[4 to 1]?.watched)
        assertEquals(true, states[4 to 2]?.partWatched)
        assertEquals(true, states[3 to 3]?.watched)
        assertEquals(false, states[4 to 3]?.watched)
        assertEquals(false, states[4 to 3]?.partWatched)
    }

    @Test
    fun opensOnTheContinueWatchingEpisode() {
        val open = ShelfInfoSeasonsRules.openOn(all, emptyMap(), cardSeason = 4, cardEpisode = 2)
        assertEquals(4 to 2, open?.let { it.season to it.number })
    }

    @Test
    fun opensOnThePartWatchedEpisodeTouchedLast() {
        val states = mapOf(
            (2 to 1) to ShelfEpisodeState(positionMs = 1_000L, durationMs = 10_000L, updatedMs = 5L),
            (3 to 2) to ShelfEpisodeState(positionMs = 1_000L, durationMs = 10_000L, updatedMs = 9L),
        )
        assertEquals(3 to 2, ShelfInfoSeasonsRules.openOn(all, states)?.let { it.season to it.number })
    }

    @Test
    fun opensAfterTheFurthestWatchedEpisode() {
        val states = mapOf(
            (1 to 1) to ShelfEpisodeState(watched = true),
            (3 to 3) to ShelfEpisodeState(watched = true),
            (0 to 2) to ShelfEpisodeState(watched = true),
        )
        // Across the season break, and a special watched does not count.
        assertEquals(4 to 1, ShelfInfoSeasonsRules.openOn(all, states)?.let { it.season to it.number })
    }

    @Test
    fun aNotYetAiredEpisodeIsSkippedAndAFinishedShowStartsAgain() {
        val list = listOf(ep(1, 1), ep(1, 2), ep(1, 3, released = "2027-01-05T00:00:00.000Z"))
        val states = mapOf((1 to 2) to ShelfEpisodeState(watched = true))
        assertEquals(1 to 1, ShelfInfoSeasonsRules.openOn(list, states, today = "2026-11-27")?.let { it.season to it.number })
        assertEquals(1 to 3, ShelfInfoSeasonsRules.openOn(list, states, today = "2027-02-01")?.let { it.season to it.number })
        val allWatched = list.associate { (it.season to it.number) to ShelfEpisodeState(watched = true) }
        assertEquals(1 to 1, ShelfInfoSeasonsRules.openOn(list, allWatched)?.let { it.season to it.number })
    }

    @Test
    fun noWatchingOpensOnSeasonOneEpisodeOne() {
        assertEquals(1 to 1, ShelfInfoSeasonsRules.openOn(all, emptyMap())?.let { it.season to it.number })
        assertNull(ShelfInfoSeasonsRules.openOn(emptyList(), emptyMap()))
    }

    @Test
    fun changingSeasonLandsWhereYouAreInIt() {
        val states = mapOf(
            (2 to 1) to ShelfEpisodeState(watched = true),
            (3 to 2) to ShelfEpisodeState(positionMs = 5L, durationMs = 10L),
        )
        assertEquals(2, ShelfInfoSeasonsRules.entryFor(all, 2, states)?.number)
        assertEquals(2, ShelfInfoSeasonsRules.entryFor(all, 3, states)?.number)
        assertEquals(1, ShelfInfoSeasonsRules.entryFor(all, 4, states)?.number)
        assertNull(ShelfInfoSeasonsRules.entryFor(all, 9, states))
    }

    @Test
    fun statusAndProgressLine() {
        val e = ep(4, 2, minutes = 52)
        assertEquals("watched", ShelfInfoSeasonsRules.status(e, ShelfEpisodeState(watched = true)))
        assertEquals(1f, ShelfInfoSeasonsRules.progress(ShelfEpisodeState(watched = true)))
        val part = ShelfEpisodeState(positionMs = 28 * 60_000L, durationMs = 52 * 60_000L)
        assertEquals("24m left", ShelfInfoSeasonsRules.status(e, part))
        assertEquals(28f / 52f, ShelfInfoSeasonsRules.progress(part))
        // No length in the record: the episode's own length and the fraction.
        assertEquals("26m left", ShelfInfoSeasonsRules.status(e, ShelfEpisodeState(fraction = 0.5f)))
        assertEquals("", ShelfInfoSeasonsRules.status(e, ShelfEpisodeState()))
        assertEquals("", ShelfInfoSeasonsRules.status(e, null))
        assertEquals(0f, ShelfInfoSeasonsRules.progress(null))
    }

    @Test
    fun playSaysResumeOrPlayAndWhichEpisode() {
        val part = ShelfEpisodeState(positionMs = 1L, durationMs = 10L)
        assertEquals("Resume S4 E2", ShelfInfoSeasonsRules.playLabel(ep(4, 2), part))
        assertEquals("Play S4 E3", ShelfInfoSeasonsRules.playLabel(ep(4, 3), null))
        assertEquals("Play S4 E1", ShelfInfoSeasonsRules.playLabel(ep(4, 1), ShelfEpisodeState(watched = true)))
        assertEquals("Play Special 2", ShelfInfoSeasonsRules.playLabel(ep(0, 2), null))
        assertEquals("E2", ShelfInfoSeasonsRules.episodeCode(ep(4, 2)))
        assertEquals("1h 16m", ShelfInfoSeasonsRules.length(ep(4, 1, minutes = 76)))
        assertEquals("", ShelfInfoSeasonsRules.length(ep(4, 1, minutes = null)))
    }

    @Test
    fun theListScrollsOnlyToKeepTheFocusedRowInView() {
        // Ten rows, six shown.
        assertEquals(0, ShelfInfoSeasonsRules.windowStart(0, 3, 10))
        assertEquals(1, ShelfInfoSeasonsRules.windowStart(0, 5, 10))
        assertEquals(4, ShelfInfoSeasonsRules.windowStart(4, 7, 10))
        assertEquals(4, ShelfInfoSeasonsRules.windowStart(4, 9, 10))
        assertEquals(2, ShelfInfoSeasonsRules.windowStart(4, 3, 10))
        assertEquals(0, ShelfInfoSeasonsRules.windowStart(4, 0, 10))
        // Fewer rows than places: never scrolls.
        assertEquals(0, ShelfInfoSeasonsRules.windowStart(3, 4, 5))
    }

    @Test
    fun aLongStripShowsTheSeasonsAroundTheCurrentOne() {
        val seasons = (1..30).toList()
        assertEquals((1..14).toList(), ShelfInfoSeasonsRules.seasonWindow(seasons, 3))
        assertEquals((13..26).toList(), ShelfInfoSeasonsRules.seasonWindow(seasons, 20))
        assertEquals((17..30).toList(), ShelfInfoSeasonsRules.seasonWindow(seasons, 30))
        assertEquals(listOf(1, 2, 0), ShelfInfoSeasonsRules.seasonWindow(listOf(1, 2, 0), 0))
    }

    @Test
    fun yearsShowWhetherTheShowIsStillRunning() {
        assertEquals("2011 –", ShelfInfoSeasonsRules.years("2011–"))
        assertEquals("2011 – 2019", ShelfInfoSeasonsRules.years("2011–2019"))
        assertEquals("2011 –", ShelfInfoSeasonsRules.years("2011", status = "Returning Series"))
        assertEquals("2011", ShelfInfoSeasonsRules.years("2011", status = "Ended"))
        assertNull(ShelfInfoSeasonsRules.years(null))
    }

    @Test
    fun theShowsCreditLineIsTheBoards() {
        assertEquals(
            "CHARLIE BROOKER · 2011 – · DRAMA / SCI-FI · 7 SEASONS",
            ShelfInfoSeasonsRules.credit(listOf("Charlie Brooker", "Annabel Jones"), "2011 –", listOf("Drama", "Sci-Fi", "Thriller"), 7),
        )
        assertEquals("2020 · 1 SEASON", ShelfInfoSeasonsRules.credit(emptyList(), "2020", emptyList(), 1))
        assertEquals("", ShelfInfoSeasonsRules.credit(emptyList(), null, emptyList(), 0))
    }

    @Test
    fun aContinueWatchingCardShowsTheShowsLinesNotTheEpisodes() {
        val card = ShelfCard.Progress(
            ContinueWatchingItem(
                parentMetaId = show, parentMetaType = "series", videoId = "$show:4:2", title = "Black Mirror",
                subtitle = "", imageUrl = null, seasonNumber = 4, episodeNumber = 2, episodeTitle = "Arkangel",
                pauseDescription = "A mother tracks her daughter.",
                resumePositionMs = 0L, durationMs = 0L, progressFraction = 0f,
            ),
        )
        val details = MetaDetails(
            id = show,
            type = "series",
            name = "Black Mirror",
            creator = listOf("Charlie Brooker"),
            releaseInfo = "2011–",
            genres = listOf("Drama", "Sci-Fi"),
            description = "Stand-alone stories about technology and the people who cannot put it down.",
        )
        val text = ShelfInfoSeasonsRules.text(card, details, seasonCount = 7)
        assertEquals("Black Mirror", text.title)
        assertEquals("CHARLIE BROOKER · 2011 – · DRAMA / SCI-FI · 7 SEASONS", text.credit)
        assertEquals("Stand-alone stories about technology and the people who cannot put it down.", text.description)
        // Before the record arrives, the episode's words stand in.
        assertEquals("A mother tracks her daughter.", ShelfInfoSeasonsRules.text(card, null, 0).description)
    }
}
