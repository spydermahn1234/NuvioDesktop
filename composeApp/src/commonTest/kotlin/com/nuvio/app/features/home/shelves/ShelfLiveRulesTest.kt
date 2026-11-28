// fork:shelf-home live-tv
package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.home.LIVE_TV_HOME_ITEM_TYPE
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.IptvProgrammeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfLiveRulesTest {
    private val channel = ShelfCard.Meta(
        MetaPreview(id = "p1:skyaction", type = LIVE_TV_HOME_ITEM_TYPE, name = "Sky Cinema Action", logo = "https://logo/sky.png"),
    )
    private val film = ShelfCard.Meta(MetaPreview(id = "tt0469494", type = "movie", name = "There Will Be Blood"))
    private val clock: (Long) -> String = { ms -> "T${ms / 60_000L}" }

    @Test
    fun onlyLiveFavouritesAreLive() {
        assertTrue(ShelfLiveRules.isLive(channel))
        assertFalse(ShelfLiveRules.isLive(film))
        assertFalse(ShelfLiveRules.isLive(null))
        assertEquals("p1:skyaction", ShelfLiveRules.ref(channel))
        assertNull(ShelfLiveRules.ref(film))
        assertEquals("https://logo/sky.png", ShelfLiveRules.logo(channel))
    }

    @Test
    fun liveCardsAskTmdbForNothingAndPlayNoTrailer() {
        assertNull(ShelfArtRules.lookup(channel))
        assertNull(ShelfTrailerRules.subject(channel))
        assertEquals("tt0469494" to "movie", ShelfArtRules.lookup(film))
    }

    @Test
    fun theSkyFollowsTheClockAndWraps() {
        assertEquals(ShelfLiveRules.sky(0), ShelfLiveRules.sky(24 * 60))
        assertEquals(ShelfLiveRules.sky(-60), ShelfLiveRules.sky(23 * 60))
        // At a stop the sky is that stop's colours.
        val noon = ShelfLiveRules.SKY.first { it.minute == 12 * 60 }
        val expected = androidx.compose.ui.graphics.Color(noon.top)
        val got = ShelfLiveRules.sky(12 * 60).first
        assertTrue(kotlin.math.abs(expected.red - got.red) < 0.02f && kotlin.math.abs(expected.blue - got.blue) < 0.02f)
        // Dusk is warmer (more red than blue) than noon.
        val dusk = ShelfLiveRules.sky(18 * 60 + 30).first
        assertTrue(dusk.red > dusk.blue)
        val day = ShelfLiveRules.sky(12 * 60).first
        assertTrue(day.blue > day.red)
    }

    @Test
    fun factsSayWhatIsOn() {
        assertEquals("LIVE TV", ShelfLiveRules.facts(null))
        val f = IptvEpgEntry("John Wick", 0L, 60_000L, kind = IptvProgrammeKind.FILM, year = 2014)
        assertEquals("ON NOW · FILM · 2014", ShelfLiveRules.facts(f))
        val ep = IptvEpgEntry("New: Taskmaster S2E5", 0L, 60_000L)
        assertEquals("ON NOW · S2 E5 · NEW", ShelfLiveRules.facts(ep))
        assertEquals("Taskmaster", ShelfLiveRules.title(ep))
    }

    @Test
    fun untilNamesTheEndAndWhatIsNext() {
        val now = IptvEpgEntry("John Wick", 0L, 120 * 60_000L)
        val next = IptvEpgEntry("Heat", 120 * 60_000L, 300 * 60_000L)
        assertEquals("Until T120 · Next: Heat", ShelfLiveRules.until(now, next, clock))
        val gap = IptvEpgEntry("Heat", 130 * 60_000L, 300 * 60_000L)
        assertEquals("Until T120 · Next: Heat at T130", ShelfLiveRules.until(now, gap, clock))
        assertEquals("Until T120", ShelfLiveRules.until(now, null, clock))
        assertNull(ShelfLiveRules.until(null, next, clock))
    }

    @Test
    fun filmChannelsCountAsFilmsWhenTheGuideSaysNothing() {
        val untagged = IptvEpgEntry(title = "Spider-Man", startMs = 0L, endMs = 1L)
        assertTrue(ShelfLiveRules.isFilm(untagged, "Sky Cinema Hits"))
        assertTrue(ShelfLiveRules.isFilm(untagged, "UK: Film4 HD"))
        assertTrue(ShelfLiveRules.isFilm(untagged.copy(kind = IptvProgrammeKind.FILM), "BBC One"))
        assertFalse(ShelfLiveRules.isFilm(untagged, "BBC One London"))
        assertFalse(ShelfLiveRules.isFilm(untagged.copy(kind = IptvProgrammeKind.NEWS), "Sky Cinema Hits"))
        assertFalse(ShelfLiveRules.isFilm(null, "Sky Cinema Hits"))
    }

    @Test
    fun theHeroAsksForTheSharperBackdrop() {
        assertEquals("https://image.tmdb.org/t/p/w1280/a.jpg", ShelfLiveRules.sharper("https://image.tmdb.org/t/p/w780/a.jpg"))
        assertNull(ShelfLiveRules.sharper(null))
    }
}
