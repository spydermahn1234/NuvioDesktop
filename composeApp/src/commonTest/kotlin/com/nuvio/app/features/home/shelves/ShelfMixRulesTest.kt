// fork:shelf-home
package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfMixRulesTest {
    private fun m(
        id: String,
        type: String = "movie",
        runtime: String? = null,
        genres: List<String> = emptyList(),
    ) = MetaPreview(id = id, type = type, name = id, runtime = runtime, genres = genres)

    private fun section(
        key: String,
        vararg items: MetaPreview,
        nextSkip: Int? = null,
        loadingMore: Boolean = false,
    ) = HomeCatalogSection(
        key = key,
        title = key,
        subtitle = "",
        addonName = "a",
        items = items.toList(),
        nextSkip = nextSkip,
        isLoadingMore = loadingMore,
    )

    private fun mix(
        vararg sources: String,
        maxRuntime: Int? = null,
        hideWatched: Boolean = false,
        fitsBeforeBed: Boolean = false,
        genres: List<String> = emptyList(),
    ) = ShelfMix("m1", "The Hang", sources.toList(), maxRuntime, hideWatched, fitsBeforeBed, genres)

    private fun build(
        mix: ShelfMix,
        sections: List<HomeCatalogSection>,
        watched: Set<String> = emptySet(),
        now: Int = 21 * 60,
        bed: Int = 23 * 60 + 30,
    ) = ShelfMixRules.build(mix, sections.associateBy { it.key }, { it.id in watched }, now, bed).map { it.id }

    @Test
    fun mergesRoundRobinInSourceOrderAndSkipsMissingSections() {
        val a = section("a", m("a1"), m("a2"), m("a3"))
        val b = section("b", m("b1"))
        assertEquals(listOf("b1", "a1", "a2", "a3"), build(mix("b", "gone", "a"), listOf(a, b)))
    }

    @Test
    fun deduplicatesByTypeAndId() {
        val a = section("a", m("x"), m("y"))
        val b = section("b", m("x"), m("y", type = "series"))
        assertEquals(listOf("x", "y", "y"), build(mix("a", "b"), listOf(a, b)))
        // The same source twice is merged once.
        assertEquals(listOf("x", "y"), build(mix("a", "a"), listOf(a)))
    }

    @Test
    fun maxRuntimeDropsKnownLongTitlesAndKeepsUnknown() {
        val a = section("a", m("short", runtime = "95 min"), m("long", runtime = "2h 9m"), m("unknown"), m("edge", runtime = "100"))
        assertEquals(listOf("short", "unknown", "edge"), build(mix("a", maxRuntime = 100), listOf(a)))
    }

    @Test
    fun hideWatchedUsesThePredicate() {
        val a = section("a", m("seen"), m("fresh"))
        assertEquals(listOf("fresh"), build(mix("a", hideWatched = true), listOf(a), watched = setOf("seen")))
        assertEquals(listOf("seen", "fresh"), build(mix("a"), listOf(a), watched = setOf("seen")))
    }

    @Test
    fun fitsBeforeBedAllowsTenMinutesGraceAndUnknownRuntimes() {
        // 21:00, bed 23:30: 150 minutes, 160 with the grace.
        val a = section("a", m("fits", runtime = "160"), m("late", runtime = "161"), m("unknown"))
        assertEquals(listOf("fits", "unknown"), build(mix("a", fitsBeforeBed = true), listOf(a)))
        // Off: everything stays.
        assertEquals(listOf("fits", "late", "unknown"), build(mix("a"), listOf(a)))
    }

    @Test
    fun bedtimeAfterMidnightIsTonights() {
        // 22:00, bed 00:30: 150 minutes.
        val a = section("a", m("fits", runtime = "155"), m("late", runtime = "170"))
        assertEquals(listOf("fits"), build(mix("a", fitsBeforeBed = true), listOf(a), now = 22 * 60, bed = 30))
    }

    @Test
    fun pastBedtimeOnlyUnknownRuntimesPass() {
        // 23:45 with bed at 23:30 is 15 minutes past: nothing with a known length fits.
        val a = section("a", m("tiny", runtime = "5"), m("unknown"))
        assertEquals(listOf("unknown"), build(mix("a", fitsBeforeBed = true), listOf(a), now = 23 * 60 + 45))
    }

    @Test
    fun genresMatchAnyIgnoringCaseAndLeaveOutUnknown() {
        val a = section(
            "a",
            m("comedy", genres = listOf("Comedy")),
            m("drama", genres = listOf("Drama")),
            m("none"),
            m("both", genres = listOf("drama", " ANIMATION ")),
        )
        assertEquals(listOf("comedy", "both"), build(mix("a", genres = listOf("comedy", "Animation")), listOf(a)))
        assertEquals(listOf("comedy", "drama", "none", "both"), build(mix("a"), listOf(a)))
    }

    @Test
    fun needsMorePagesSourcesUntilTheTarget() {
        val sections = listOf(
            section("a", m("a1"), nextSkip = 20),
            section("b", m("b1"), nextSkip = 20, loadingMore = true),
            section("c", m("c1")),
        ).associateBy { it.key }
        val built = listOf(m("a1"))
        assertEquals(listOf("a"), ShelfMixRules.needsMore(mix("a", "b", "c", "gone"), built, sections))
        // Enough: nothing.
        val full = List(ShelfMixRules.TARGET) { m("x$it") }
        assertEquals(emptyList(), ShelfMixRules.needsMore(mix("a"), full, sections))
    }

    @Test
    fun needsMoreStopsAtThePageCap() {
        val sections = mapOf("a" to section("a", m("a1"), nextSkip = 20))
        val built = emptyList<MetaPreview>()
        assertEquals(listOf("a"), ShelfMixRules.needsMore(mix("a"), built, sections, pagesAsked = mapOf("a" to 4)))
        assertEquals(emptyList(), ShelfMixRules.needsMore(mix("a"), built, sections, pagesAsked = mapOf("a" to ShelfMixRules.MAX_AUTO_PAGES)))
        // A larger target and cap (the user scrolling) can ask again.
        assertEquals(listOf("a"), ShelfMixRules.needsMore(mix("a"), built, sections, pagesAsked = mapOf("a" to 5), target = 60, maxPages = 10))
    }

    @Test
    fun keysAndIds() {
        assertEquals("mix:m3", ShelfMixRules.shelfKey("m3"))
        assertEquals("m3", ShelfMixRules.idOf("mix:m3"))
        assertNull(ShelfMixRules.idOf("mix:"))
        assertNull(ShelfMixRules.idOf("continueWatching"))
        assertEquals("m1", ShelfMixRules.newId(emptyList()))
        assertEquals("m3", ShelfMixRules.newId(listOf("m1", "m2", "m4")))
    }

    @Test
    fun tidyAndCanSave() {
        val raw = ShelfMix("m1", "  The Hang ", listOf("a", "", "a", "b"), 0, false, false, listOf(" Comedy", "comedy", ""))
        val tidy = ShelfMixRules.tidy(raw)
        assertEquals("The Hang", tidy.title)
        assertEquals(listOf("a", "b"), tidy.sourceKeys)
        assertNull(tidy.maxRuntime)
        assertEquals(listOf("Comedy"), tidy.genres)
        assertTrue(ShelfMixRules.canSave(tidy))
        assertFalse(ShelfMixRules.canSave(tidy.copy(title = "  ")))
        assertFalse(ShelfMixRules.canSave(tidy.copy(sourceKeys = emptyList())))
    }

    @Test
    fun codecRoundTripsAwkwardText() {
        val mixes = listOf(
            ShelfMix("m1", "The Hang", listOf("addon:movie:top", "addon:series:pop:genre=Comedy"), 100, true, true, listOf("Comedy", "Sci-Fi")),
            ShelfMix("m2", "Tabs\tand\nnew\\lines", listOf("we\tird\nkey\\x", "collection_a/b"), null, false, false, emptyList()),
            ShelfMix("m3", "", emptyList(), 90, false, true, listOf("a\nb")),
        )
        assertEquals(mixes, ShelfMixCodec.decode(ShelfMixCodec.encode(mixes)))
    }

    @Test
    fun codecSkipsBadLinesAndRepeatedIds() {
        val good = ShelfMixCodec.encode(listOf(mix("a")))
        val text = listOf(
            "rubbish",
            "mix9\tm5\tFuture\ta\t\t0\t0\t",
            "mix1\tm6\tBad runtime\ta\tlots\t0\t0\t",
            "mix1\t\tNo id\ta\t\t0\t0\t",
            "mix1\tm7\tBad escape\\q\ta\t\t0\t0\t",
            good,
            good,
        ).joinToString("\n")
        val decoded = ShelfMixCodec.decode(text)
        assertEquals(listOf(mix("a")), decoded)
        assertEquals(emptyList(), ShelfMixCodec.decode(null))
        assertEquals(emptyList(), ShelfMixCodec.decode(""))
    }

    @Test
    fun codecIgnoresExtraTrailingFields() {
        val line = ShelfMixCodec.encode(listOf(mix("a", maxRuntime = 120))) + "\tsomething new"
        assertEquals(listOf(mix("a", maxRuntime = 120)), ShelfMixCodec.decode(line))
    }
}
