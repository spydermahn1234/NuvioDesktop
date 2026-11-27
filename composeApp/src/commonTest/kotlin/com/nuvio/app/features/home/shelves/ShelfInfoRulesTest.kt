package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.details.MetaPerson
import com.nuvio.app.features.home.HeroCastMember
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfInfoRulesTest {
    // A plain 24-hour clock, so the tests do not depend on the 12-hour setting.
    private val clock: (Int) -> String = { m -> "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}" }

    private val ironGiant = MetaPreview(
        id = "tt0129167",
        type = "movie",
        name = "The Iron Giant",
        description = "A giant metal machine befriends a nine-year-old boy.",
        releaseInfo = "1999",
        imdbRating = "8.1",
        ageRating = "PG",
        runtime = "86 min",
        genres = listOf("Animation", "Family", "Sci-Fi"),
    )

    private fun details(
        director: List<String> = listOf("Brad Bird"),
        cast: List<String> = listOf("Eli Marienthal", "Jennifer Aniston", "Harry Connick Jr.", "Vin Diesel", "Christopher McDonald"),
        ratings: List<MetaExternalRating> = emptyList(),
        description: String? = null,
    ) = MetaDetails(
        id = "tt0129167",
        type = "movie",
        name = "The Iron Giant",
        director = director,
        cast = cast.map { MetaPerson(name = it) },
        externalRatings = ratings,
        description = description,
    )

    private fun progress(
        position: Long = 24 * 60_000L,
        duration: Long = 100 * 60_000L,
        fraction: Float = 0.24f,
        season: Int? = 4,
        episode: Int? = 1,
        episodeTitle: String? = "USS Callister",
    ) = ShelfCard.Progress(
        ContinueWatchingItem(
            parentMetaId = "tt2085059",
            parentMetaType = "series",
            videoId = "tt2085059:4:1",
            title = "Black Mirror",
            subtitle = "",
            imageUrl = null,
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = episodeTitle,
            pauseDescription = "A programmer traps his co-workers in a starship game.",
            resumePositionMs = position,
            durationMs = duration,
            progressFraction = fraction,
        ),
    )

    @Test
    fun creditLineIsTheMockupsOrder() {
        assertEquals(
            "BRAD BIRD · 1999 · ANIMATION / FAMILY · 1H 26M · PG",
            ShelfInfoRules.credit(listOf("Brad Bird"), "1999", listOf("Animation", "Family", "Sci-Fi"), 86, "PG"),
        )
        // Only the parts it has.
        assertEquals("1999 · 2H", ShelfInfoRules.credit(emptyList(), "1999", emptyList(), 120, " "))
        assertEquals("", ShelfInfoRules.credit(emptyList(), null, emptyList(), null, null))
    }

    @Test
    fun aFilmWithoutItsRecordStillHasItsCardsWords() {
        val text = ShelfInfoRules.text(ShelfCard.Meta(ironGiant), details = null, nowMinute = 19 * 60 + 59, clock = clock)
        assertEquals("The Iron Giant", text.title)
        assertEquals("1999 · ANIMATION / FAMILY · 1H 26M · PG", text.credit)
        assertEquals(ironGiant.description, text.description)
        assertEquals(listOf(ShelfInfoScore("IMDB", "8.1"), ShelfInfoScore("ENDS", "21:25")), text.scores)
        assertEquals("", text.cast)
    }

    @Test
    fun theRecordAddsTheDirectorCastAndScores() {
        val ratings = listOf(
            MetaExternalRating(source = "tomatoes", value = 96.0),
            MetaExternalRating(source = "audience", value = 90.6),
        )
        val text = ShelfInfoRules.text(ShelfCard.Meta(ironGiant), details(ratings = ratings), nowMinute = 19 * 60 + 59, clock = clock)
        assertEquals("BRAD BIRD · 1999 · ANIMATION / FAMILY · 1H 26M · PG", text.credit)
        assertEquals(
            listOf(
                ShelfInfoScore("IMDB", "8.1"),
                ShelfInfoScore("CRITICS", "96%"),
                ShelfInfoScore("AUDIENCE", "91%"),
                ShelfInfoScore("ENDS", "21:25"),
            ),
            text.scores,
        )
        // Four names at most.
        assertEquals("Eli Marienthal · Jennifer Aniston · Harry Connick Jr. · Vin Diesel", text.cast)
    }

    @Test
    fun theCardsOwnCastComesFirst() {
        val card = ShelfCard.Meta(ironGiant.copy(cast = listOf(HeroCastMember("Vin Diesel"), HeroCastMember(" "))))
        assertEquals("Vin Diesel", ShelfInfoRules.text(card, details(), 0, clock).cast)
    }

    @Test
    fun aShowHasNoEndsTimeOrLength() {
        val show = ironGiant.copy(type = "series", name = "Severance", runtime = "50 min", releaseInfo = "2022–")
        val text = ShelfInfoRules.text(ShelfCard.Meta(show), details(director = emptyList()), 0, clock)
        assertEquals("2022 · ANIMATION / FAMILY · PG", text.credit)
        assertTrue(text.scores.none { it.label == "ENDS" })
    }

    @Test
    fun imdbFallsBackToTheRecordThenMdbList() {
        assertEquals("8.0", ShelfInfoRules.imdb("8", null))
        assertEquals("7.4", ShelfInfoRules.imdb(null, details().copy(imdbRating = "7.4")))
        assertEquals("6.9", ShelfInfoRules.imdb(" ", details(ratings = listOf(MetaExternalRating("imdb", 6.94)))))
        assertNull(ShelfInfoRules.imdb(null, null))
        assertNull(ShelfInfoRules.imdb("0", null))
    }

    @Test
    fun aPartWatchedEpisodeShowsWhatIsLeftAndFinishBy() {
        val text = ShelfInfoRules.text(progress(), details = null, nowMinute = 20 * 60, clock = clock)
        assertEquals("Black Mirror", text.title)
        assertEquals("S4 E1 · USS CALLISTER", text.credit)
        assertEquals("A programmer traps his co-workers in a starship game.", text.description)
        assertEquals(listOf(ShelfInfoScore("LEFT", "1h 16m"), ShelfInfoScore("FINISH BY", "21:16")), text.scores)
    }

    @Test
    fun anEpisodeNotStartedEndsAfterItsLength() {
        val text = ShelfInfoRules.text(progress(position = 0L, fraction = 0f), details = null, nowMinute = 23 * 60, clock = clock)
        assertEquals(listOf(ShelfInfoScore("ENDS", "00:40")), text.scores)
    }

    @Test
    fun episodeLineNeedsBothNumbers() {
        assertEquals("S4 E1 · USS CALLISTER", ShelfInfoRules.episodeLine(4, 1, "USS Callister"))
        assertEquals("S4 E1", ShelfInfoRules.episodeLine(4, 1, " "))
        assertEquals("", ShelfInfoRules.episodeLine(null, 1, "USS Callister"))
    }

    @Test
    fun aFilmInContinueWatchingGetsTheFilmsCredit() {
        val text = ShelfInfoRules.text(
            progress(season = null, episode = null, episodeTitle = null, duration = 86 * 60_000L),
            details().copy(releaseInfo = "1999", genres = listOf("Animation")),
            0,
            clock,
        )
        assertEquals("BRAD BIRD · 1999 · ANIMATION · 1H 26M", text.credit)
    }

    @Test
    fun buttonsNeedAnActionAndTrailerAndListNeedATitle() {
        val all = ShelfInfoRules.buttons(ShelfCard.Meta(ironGiant), play = true, trailer = true, moreLikeThis = true, addToList = true)
        assertEquals(
            listOf(ShelfInfoButton.Play, ShelfInfoButton.Trailer, ShelfInfoButton.MoreLikeThis, ShelfInfoButton.AddToList),
            all,
        )
        assertEquals(
            listOf(ShelfInfoButton.Play, ShelfInfoButton.AddToList),
            ShelfInfoRules.buttons(ShelfCard.Meta(ironGiant), play = true, trailer = false, moreLikeThis = false, addToList = true),
        )
        val folder = ShelfCard.Folder("c", CollectionFolder(id = "f", title = "Pixar"))
        assertEquals(listOf(ShelfInfoButton.Play), ShelfInfoRules.buttons(folder, true, true, true, true))
        val channel = ShelfCard.Meta(ironGiant.copy(type = "live_tv_channel"))
        assertEquals(listOf(ShelfInfoButton.Play), ShelfInfoRules.buttons(channel, true, true, true, true))
    }

    @Test
    fun sourceShortcutSitsBesidePlayOnContinueWatchingOnly() {
        assertEquals(
            listOf(ShelfInfoButton.Play, ShelfInfoButton.SourceShortcut, ShelfInfoButton.Trailer),
            ShelfInfoRules.buttons(progress(), play = true, trailer = true, moreLikeThis = false, addToList = false, sourceShortcut = true),
        )
        // A plain title never offers it, even when asked.
        assertTrue(
            ShelfInfoButton.SourceShortcut !in
                ShelfInfoRules.buttons(ShelfCard.Meta(ironGiant), true, true, true, true, sourceShortcut = true),
        )
        assertTrue(ShelfInfoButton.SourceShortcut !in ShelfInfoRules.buttons(progress(), true, true, true, true))
    }

    @Test
    fun sourceShortcutIsNamedForWhatItDoes() {
        assertEquals(
            "Choose a source",
            ShelfInfoRules.sourceShortcutLabel(com.nuvio.app.features.home.components.ContinueWatchingSourceShortcutKind.OpenSourcePicker),
        )
        assertEquals(
            "Play last source",
            ShelfInfoRules.sourceShortcutLabel(com.nuvio.app.features.home.components.ContinueWatchingSourceShortcutKind.ReuseLastLink),
        )
        assertNull(ShelfInfoRules.sourceShortcutLabel(null))
        assertEquals("Play last source", ShelfInfoRules.buttonLabel(ShelfInfoButton.SourceShortcut, false, "Play last source"))
    }

    @Test
    fun addToListSaysWhenItIsAlreadyThere() {
        assertEquals("Add to list", ShelfInfoRules.buttonLabel(ShelfInfoButton.AddToList, inList = false))
        assertEquals("In your list", ShelfInfoRules.buttonLabel(ShelfInfoButton.AddToList, inList = true))
        assertEquals("Play", ShelfInfoRules.buttonLabel(ShelfInfoButton.Play, inList = true))
    }

    @Test
    fun aShowsPlayNamesItsEpisodeAndAFilmsStaysPlay() {
        assertEquals("Resume S4 E2", ShelfInfoRules.buttonLabel(ShelfInfoButton.Play, false, null, "Resume S4 E2"))
        assertEquals("Play", ShelfInfoRules.buttonLabel(ShelfInfoButton.Play, false, null, null))
        // The episode's words are Play's only.
        assertEquals("Trailer", ShelfInfoRules.buttonLabel(ShelfInfoButton.Trailer, false, null, "Play S4 E3"))
        // Films keep the V2 Info: no season list.
        assertNull(ShelfInfoSeasonsRules.showKey(ShelfCard.Meta(ironGiant)))
        assertEquals("series" to "tt2085059", ShelfInfoSeasonsRules.showKey(progress()))
        // A show's Play falls back to the old action until an episode can be played.
        assertNull(ShelfInfoActions().playEpisode)
    }

    @Test
    fun detailsKeyIsTheShowForAnEpisode() {
        assertEquals("series" to "tt2085059", ShelfInfoRules.detailsKey(progress()))
        assertEquals("movie" to "tt0129167", ShelfInfoRules.detailsKey(ShelfCard.Meta(ironGiant)))
    }

    @Test
    fun trailerKeysBecomeYouTubeIds() {
        assertEquals("abc123XYZ_-", youTubeVideoId("abc123XYZ_-"))
        assertEquals("dQw4w9WgXcQ", youTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3"))
        assertEquals("dQw4w9WgXcQ", youTubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertNull(youTubeVideoId("https://vimeo.com/123"))
        assertNull(youTubeVideoId(" "))
    }
}
