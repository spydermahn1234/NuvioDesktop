// fork:shelf-home
package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfPresentationRulesTest {

    @Test
    fun progressShelvesDefaultToLedger() {
        assertEquals(ShelfViewMode.Ledger, ShelfPresentationRules.defaultView(ShelfHomeRules.CONTINUE_WATCHING_KEY, "Continue watching"))
        assertEquals(ShelfViewMode.Ledger, ShelfPresentationRules.defaultView(ShelfHomeRules.UP_NEXT_KEY, "Up next"))
        assertEquals(ShelfViewMode.Ledger, ShelfPresentationRules.defaultView(ShelfHomeRules.UPCOMING_KEY, "Upcoming"))
    }

    @Test
    fun rankedTitlesDefaultToLedger() {
        assertEquals(ShelfViewMode.Ledger, ShelfPresentationRules.defaultView("tmdb:movie:top", "Top 10 Movies"))
        assertEquals(ShelfViewMode.Ledger, ShelfPresentationRules.defaultView("x:series:best", "Best of 2025"))
    }

    @Test
    fun otherShelvesDefaultToStrip() {
        assertEquals(ShelfViewMode.Strip, ShelfPresentationRules.defaultView("cinemeta:movie:popular", "Popular Movies"))
        assertEquals(ShelfViewMode.Strip, ShelfPresentationRules.defaultView("collection_abc/def", "Marvel"))
        assertEquals(ShelfViewMode.Strip, ShelfPresentationRules.defaultView("org.stremio.netflix:movie:new", "New"))
        assertEquals(ShelfViewMode.Strip, ShelfPresentationRules.defaultView("collection_abc", "My collection"))
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
        assertEquals(
            listOf(ShelfViewMode.Strip, ShelfViewMode.Ledger, ShelfViewMode.Spotlight),
            ShelfPresentationRules.available(),
        )
    }

    @Test
    fun codecRoundTripsAwkwardKeys() {
        val views = mapOf(
            "manifest.id:movie:top:genre=Action" to ShelfViewMode.Ledger,
            "collection_abc/folder 1" to ShelfViewMode.Spotlight,
            ShelfHomeRules.CONTINUE_WATCHING_KEY to ShelfViewMode.Strip,
            "odd\tkey\nwith\\slashes\r" to ShelfViewMode.Wall,
        )
        val text = ShelfPresentationCodec.encode(views)
        assertEquals(views.size, text.split('\n').size)
        assertEquals(views, ShelfPresentationCodec.decode(text))
    }

    @Test
    fun codecSkipsBadLinesAndUnknownModes() {
        val text = "Ledger\tgood:key\nCarousel\tunknown:mode\nno tab here\n\tno mode\nStrip\tbad\\escape\nStrip\t"
        assertEquals(mapOf("good:key" to ShelfViewMode.Ledger), ShelfPresentationCodec.decode(text))
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
    fun resolveFallsBackWhenTheChoiceIsNotBuilt() {
        val overrides = mapOf(
            "a:movie:x" to ShelfViewMode.Spotlight,
            "b:movie:y" to ShelfViewMode.Wall,
        )
        assertEquals(ShelfViewMode.Spotlight, ShelfPresentationRepository.resolve(overrides, "a:movie:x", "Popular"))
        assertEquals(ShelfViewMode.Strip, ShelfPresentationRepository.resolve(overrides, "b:movie:y", "Popular"))
        assertEquals(ShelfViewMode.Ledger, ShelfPresentationRepository.resolve(overrides, "c:movie:z", "Top 10"))
    }
}
