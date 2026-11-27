package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaPerson
import com.nuvio.app.features.details.PersonDetail
import com.nuvio.app.features.home.HeroCastMember
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.watchedItemKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShelfInfoPeopleRulesTest {
    private fun film(id: String, name: String, year: String? = null, popularity: Double? = null, type: String = "movie") =
        MetaPreview(id = id, type = type, name = name, releaseInfo = year, popularity = popularity)

    private val rings = listOf(
        film("tmdb:120", "The Fellowship of the Ring", "2001"),
        film("tmdb:121", "The Two Towers", "2002"),
        film("tmdb:122", "The Return of the King", "2003"),
    )

    private fun towers(cast: List<MetaPerson> = emptyList(), director: List<String> = emptyList()) = MetaDetails(
        id = "tt0167261",
        type = "movie",
        name = "The Two Towers",
        tmdbId = 121,
        cast = cast,
        director = director,
        collectionName = "The Lord of the Rings Collection",
        collectionItems = rings,
    )

    @Test
    fun castDropsCrewAndRepeatsAndKeepsSix() {
        val people = listOf(
            MetaPerson("Peter Jackson", role = "Director"),
            MetaPerson("Elijah Wood", role = "Frodo", photo = "ew.jpg", tmdbId = 109),
            MetaPerson("Fran Walsh"),
            MetaPerson("Ian McKellen", role = "Gandalf"),
            MetaPerson("ian mckellen", role = "Gandalf"),
            MetaPerson("Viggo Mortensen", role = "Aragorn"),
            MetaPerson("Sean Astin", role = "Sam"),
            MetaPerson("Christopher Lee", role = "Saruman"),
            MetaPerson("Andy Serkis", role = "Gollum"),
            MetaPerson("Bernard Hill", role = "Théoden"),
        )
        val cast = ShelfInfoPeopleRules.cast(towers(people, director = listOf("Peter Jackson", "Fran Walsh")), emptyList())
        assertEquals(
            listOf("Elijah Wood", "Ian McKellen", "Viggo Mortensen", "Sean Astin", "Christopher Lee", "Andy Serkis"),
            cast.map { it.name },
        )
        assertEquals(ShelfPerson("Elijah Wood", "Frodo", "ew.jpg", 109), cast.first())
    }

    @Test
    fun castFallsBackToTheCardsOwn() {
        val cast = ShelfInfoPeopleRules.cast(null, listOf(HeroCastMember("Eli Marienthal", role = "Hogarth"), HeroCastMember(" ")))
        assertEquals(listOf(ShelfPerson("Eli Marienthal", "Hogarth")), cast)
    }

    @Test
    fun initials() {
        assertEquals("IM", ShelfInfoPeopleRules.initials("Ian McKellen"))
        assertEquals("C", ShelfInfoPeopleRules.initials("Cher"))
        assertEquals("SJ", ShelfInfoPeopleRules.initials("Samuel L. Jackson"))
        assertEquals("", ShelfInfoPeopleRules.initials("  "))
    }

    @Test
    fun moreWithLabel() {
        assertEquals("MORE WITH IAN MCKELLEN · OK OPENS THE ROW", ShelfInfoPeopleRules.moreWithLabel("Ian McKellen"))
    }

    @Test
    fun worksMostPopularFirstWithoutThisFilm() {
        val detail = PersonDetail(
            tmdbId = 1327,
            name = "Ian McKellen",
            biography = null,
            birthday = null,
            deathday = null,
            placeOfBirth = null,
            profilePhoto = null,
            knownFor = "Acting",
            movieCredits = listOf(
                film("tmdb:121", "The Two Towers", popularity = 90.0),
                film("tmdb:36657", "X-Men", popularity = 50.0),
                film("tmdb:1", "Gods and Monsters"),
                film("tmdb:36657", "X-Men", popularity = 50.0),
            ),
            tvCredits = listOf(film("tmdb:9", "Vicious", popularity = 60.0, type = "series")),
        )
        val works = ShelfInfoPeopleRules.works(detail, excludeTmdbId = 121, excludeName = "The Two Towers")
        assertEquals(listOf("Vicious", "X-Men", "Gods and Monsters"), works.map { it.name })
        assertEquals(2, ShelfInfoPeopleRules.works(detail, null, null, limit = 2).size)
        assertEquals(emptyList<MetaPreview>(), ShelfInfoPeopleRules.works(null, null, null))
    }

    @Test
    fun collectionFindsThisPart() {
        val c = ShelfInfoPeopleRules.collection(towers(), "The Two Towers")!!
        assertEquals("The Lord of the Rings", c.name)
        assertEquals(1, c.current)
        assertEquals("PART 2 OF 3 · THE LORD OF THE RINGS", ShelfInfoPeopleRules.collectionHeader(c))
        // By name when the TMDB id is not known.
        assertEquals(1, ShelfInfoPeopleRules.collection(towers().copy(tmdbId = null), "the two towers")!!.current)
        val lost = ShelfInfoPeopleRules.collection(towers().copy(tmdbId = null), "Something Else")!!
        assertEquals("3 FILMS · THE LORD OF THE RINGS", ShelfInfoPeopleRules.collectionHeader(lost))
    }

    @Test
    fun noCollectionWithoutTwoParts() {
        assertNull(ShelfInfoPeopleRules.collection(null, "X"))
        assertNull(ShelfInfoPeopleRules.collection(towers().copy(collectionName = null), "The Two Towers"))
        assertNull(ShelfInfoPeopleRules.collection(towers().copy(collectionItems = rings.take(1)), "The Two Towers"))
    }

    @Test
    fun partsWindow() {
        assertEquals(0, ShelfInfoPeopleRules.partsWindowStart(3, 2))
        assertEquals(0, ShelfInfoPeopleRules.partsWindowStart(8, 0))
        assertEquals(3, ShelfInfoPeopleRules.partsWindowStart(8, 4))
        assertEquals(5, ShelfInfoPeopleRules.partsWindowStart(8, 7))
    }

    @Test
    fun watchedPartsByIdOrByNameAndYear() {
        val titles = ShelfInfoPeopleRules.watchedTitles(
            listOf(
                WatchedItem(id = "tt0120737", type = "movie", name = "The Fellowship of the Ring", releaseInfo = "2001", markedAtEpochMs = 1L),
                // Same name, another year: a different film.
                WatchedItem(id = "tt9", type = "movie", name = "The Two Towers", releaseInfo = "1999", markedAtEpochMs = 1L),
                // An episode is not a film.
                WatchedItem(id = "tt8", type = "series", name = "The Return of the King", season = 1, episode = 1, markedAtEpochMs = 1L),
            ),
            emptyList(),
        )
        assertEquals(setOf(0), ShelfInfoPeopleRules.watchedParts(rings, emptySet(), titles))
        assertEquals(setOf(0, 2), ShelfInfoPeopleRules.watchedParts(rings, setOf(watchedItemKey("movie", "tmdb:122")), titles))
    }

    @Test
    fun collectionLine() {
        val c = ShelfInfoPeopleRules.collection(towers(), "The Two Towers")!!
        assertEquals("Watched part 1 · 3h 21m for part 3", ShelfInfoPeopleRules.collectionLine(c, setOf(0), 201))
        assertEquals("Watched part 1 · Part 3 next", ShelfInfoPeopleRules.collectionLine(c, setOf(0), null))
        assertEquals("1h 2m left of part 3", ShelfInfoPeopleRules.collectionLine(c, emptySet(), 201, nextLeftMinutes = 62))
        assertEquals("Watched parts 1 and 3", ShelfInfoPeopleRules.collectionLine(c, setOf(0, 2), 201))
        assertEquals("Watched them all", ShelfInfoPeopleRules.collectionLine(c, setOf(0, 1, 2), 201))
        assertEquals(2, ShelfInfoPeopleRules.nextPart(c, setOf(0)))
        assertNull(ShelfInfoPeopleRules.nextPart(c, setOf(2)))
        val last = c.copy(current = 2)
        assertEquals("", ShelfInfoPeopleRules.collectionLine(last, emptySet(), null))
    }

    @Test
    fun zones() {
        val b = ShelfInfoZone.Buttons
        val p = ShelfInfoZone.Parts
        val c = ShelfInfoZone.Cast
        assertEquals(p, ShelfInfoPeopleRules.up(b, hasParts = true, hasCast = true))
        assertEquals(c, ShelfInfoPeopleRules.up(p, hasParts = true, hasCast = true))
        assertEquals(c, ShelfInfoPeopleRules.up(c, hasParts = true, hasCast = true))
        assertEquals(c, ShelfInfoPeopleRules.up(b, hasParts = false, hasCast = true))
        assertEquals(b, ShelfInfoPeopleRules.up(b, hasParts = false, hasCast = false))
        assertEquals(p, ShelfInfoPeopleRules.down(c, hasParts = true))
        assertEquals(b, ShelfInfoPeopleRules.down(c, hasParts = false))
        assertEquals(b, ShelfInfoPeopleRules.down(p, hasParts = true))
        assertEquals(p, ShelfInfoPeopleRules.tab(b, hasParts = true, hasCast = true))
        assertEquals(b, ShelfInfoPeopleRules.tab(c, hasParts = true, hasCast = true))
        assertEquals(c, ShelfInfoPeopleRules.tab(b, hasParts = false, hasCast = true))
        assertEquals(b, ShelfInfoPeopleRules.settle(c, hasParts = true, hasCast = false))
        assertEquals(p, ShelfInfoPeopleRules.settle(p, hasParts = true, hasCast = false))
    }

    @Test
    fun hintAndScores() {
        assertEquals(
            "UP FROM THE BUTTONS: THE SERIES, THEN THE CAST",
            ShelfInfoPeopleRules.hint(ShelfInfoZone.Buttons, hasParts = true, hasCast = true),
        )
        assertEquals("UP FROM THE BUTTONS: THE CAST", ShelfInfoPeopleRules.hint(ShelfInfoZone.Buttons, false, true))
        assertEquals("BACK RETURNS TO THE ROW", ShelfInfoPeopleRules.hint(ShelfInfoZone.Buttons, false, false))
        assertEquals(
            "IMDB 8.1 · ENDS 23:14",
            ShelfInfoPeopleRules.scoresLine(listOf(ShelfInfoScore("IMDB", "8.1"), ShelfInfoScore("ENDS", "23:14"))),
        )
        assertEquals("", ShelfInfoPeopleRules.scoresLine(emptyList()))
    }
}
