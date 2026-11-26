// fork:shelf-home
package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfPresentationRulesTest {

    @Test
    fun networkRowsDefaultToLogosAndEverythingElseToPosters() {
        assertEquals(ShelfViewMode.Posters, ShelfPresentationRules.defaultView(ShelfHomeRules.CONTINUE_WATCHING_KEY, "Continue watching"))
        assertEquals(ShelfViewMode.Posters, ShelfPresentationRules.defaultView("tmdb:movie:top", "Top 10 Movies"))
        assertEquals(ShelfViewMode.Posters, ShelfPresentationRules.defaultView("cinemeta:movie:popular", "Popular Movies"))
        assertEquals(ShelfViewMode.Posters, ShelfPresentationRules.defaultView("collection_abc/def", "Netflix"))
        assertEquals(ShelfViewMode.Logos, ShelfPresentationRules.defaultView("org.stremio.netflix:movie:new", "New"))
        assertEquals(ShelfViewMode.Logos, ShelfPresentationRules.defaultView("x:series:y", "BBC iPlayer"))
        assertEquals(ShelfViewMode.Posters, ShelfPresentationRules.defaultView("collection_abc", "My collection"))
    }

    @Test
    fun toggledSwapsPostersAndLogos() {
        assertEquals(ShelfViewMode.Logos, ShelfPresentationRules.toggled(ShelfViewMode.Posters))
        assertEquals(ShelfViewMode.Posters, ShelfPresentationRules.toggled(ShelfViewMode.Logos))
    }

    @Test
    fun isRankedMatchesRankedTitles() {
        assertTrue(ShelfPresentationRules.isRanked("Top 10 Movies"))
        assertTrue(ShelfPresentationRules.isRanked("TOP 100 of all time"))
        assertTrue(ShelfPresentationRules.isRanked("Netflix top10 today"))
        assertTrue(ShelfPresentationRules.isRanked("Best of the decade"))
        assertTrue(ShelfPresentationRules.isRanked("Best this week"))
        assertTrue(ShelfPresentationRules.isRanked("Most-Watched Shows"))
        assertFalse(ShelfPresentationRules.isRanked("Popular Movies"))
        assertFalse(ShelfPresentationRules.isRanked("Stop Motion"))
        assertFalse(ShelfPresentationRules.isRanked("Best Picture Winners"))
        assertFalse(ShelfPresentationRules.isRanked(""))
    }

    @Test
    fun networksAreRecognisedByWordNotByFragment() {
        assertTrue(ShelfPresentationRules.isNetwork("Netflix"))
        assertTrue(ShelfPresentationRules.isNetwork("org.stremio.disneyplus:movie:new"))
        assertTrue(ShelfPresentationRules.isNetwork("BBC iPlayer"))
        assertTrue(ShelfPresentationRules.isNetwork("New on Apple TV+"))
        assertTrue(ShelfPresentationRules.isNetwork("Max Originals"))
        assertFalse(ShelfPresentationRules.isNetwork("Maximum Overdrive"))
        assertFalse(ShelfPresentationRules.isNetwork("Primeval"))
        assertFalse(ShelfPresentationRules.isNetwork("Popular Movies"))
    }

    @Test
    fun availableHoldsOnlyBuiltViews() {
        assertEquals(listOf(ShelfViewMode.Posters, ShelfViewMode.Logos), ShelfPresentationRules.available())
    }

    @Test
    fun codecRoundTripsAwkwardKeys() {
        val views = mapOf(
            "manifest.id:movie:top:genre=Action" to ShelfViewMode.Logos,
            "collection_abc/folder 1" to ShelfViewMode.Posters,
            ShelfHomeRules.CONTINUE_WATCHING_KEY to ShelfViewMode.Posters,
            "odd\tkey\nwith\\slashes\r" to ShelfViewMode.Logos,
        )
        val text = ShelfPresentationCodec.encode(views)
        assertEquals(views.size, text.split('\n').size)
        assertEquals(views, ShelfPresentationCodec.decode(text))
    }

    @Test
    fun codecReadsTheRemovedViewsAsPosters() {
        val text = "Strip\ta\nLedger\tb\nSpotlight\tc\nWall\td\nLogos\te"
        assertEquals(
            mapOf(
                "a" to ShelfViewMode.Posters,
                "b" to ShelfViewMode.Posters,
                "c" to ShelfViewMode.Posters,
                "d" to ShelfViewMode.Posters,
                "e" to ShelfViewMode.Logos,
            ),
            ShelfPresentationCodec.decode(text),
        )
    }

    @Test
    fun codecSkipsBadLinesAndUnknownModes() {
        val text = "Logos\tgood:key\nCarousel\tunknown:mode\nno tab here\n\tno mode\nPosters\tbad\\escape\nPosters\t"
        assertEquals(mapOf("good:key" to ShelfViewMode.Logos), ShelfPresentationCodec.decode(text))
        assertEquals(emptyMap<String, ShelfViewMode>(), ShelfPresentationCodec.decode(null))
        assertEquals(emptyMap<String, ShelfViewMode>(), ShelfPresentationCodec.decode(""))
    }

    @Test
    fun escapeAndUnescapeAreInverse() {
        val key = "a\\b\tc\nd"
        assertEquals(key, ShelfPresentationCodec.unescape(ShelfPresentationCodec.escape(key)))
        assertNull(ShelfPresentationCodec.unescape("trailing\\"))
    }

    @Test
    fun resolveUsesTheChoiceElseTheDefault() {
        val overrides = mapOf("a:movie:x" to ShelfViewMode.Logos)
        assertEquals(ShelfViewMode.Logos, ShelfPresentationRepository.resolve(overrides, "a:movie:x", "Popular"))
        assertEquals(ShelfViewMode.Posters, ShelfPresentationRepository.resolve(overrides, "c:movie:z", "Top 10"))
        assertEquals(ShelfViewMode.Logos, ShelfPresentationRepository.resolve(overrides, "c:movie:z", "Netflix"))
    }
}
