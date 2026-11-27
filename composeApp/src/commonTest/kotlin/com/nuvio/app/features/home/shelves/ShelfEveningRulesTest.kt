// fork:shelf-home
package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfEveningRulesTest {
    private val rules = ShelfEveningRules

    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private val start = at(21)
    private val bed = at(23, 30)

    private fun two(n: Int) = n.toString().padStart(2, '0')
    private val clock24: (Int) -> String = { m -> "${two(m / 60)}:${two(m % 60)}" }
    private val clock12: (Int) -> String = { m -> "${(m / 60 % 12).let { if (it == 0) 12 else it }}:${two(m % 60)} ${if (m < 720) "am" else "pm"}" }

    private fun m(id: String, runtime: String? = null, type: String = "movie") =
        MetaPreview(id = id, type = type, name = id, runtime = runtime)

    private fun section(key: String, vararg items: MetaPreview, nextSkip: Int? = null, loadingMore: Boolean = false) =
        HomeCatalogSection(
            key = key,
            title = key,
            subtitle = "",
            addonName = "a",
            items = items.toList(),
            nextSkip = nextSkip,
            isLoadingMore = loadingMore,
        )

    private fun close(expected: Float, actual: Float) =
        assertTrue(kotlin.math.abs(expected - actual) < 0.0005f, "expected $expected, was $actual")

    @Test
    fun theEveningRunsFromTheStartUntilBed() {
        assertFalse(rules.isEvening(at(20, 59), start, bed))
        assertTrue(rules.isEvening(at(21), start, bed))
        assertTrue(rules.isEvening(at(23, 29), start, bed))
        assertFalse(rules.isEvening(at(23, 30), start, bed))
        assertFalse(rules.isEvening(at(3), start, bed))
    }

    @Test
    fun anEveningCanRunPastMidnight() {
        val lateBed = at(0, 30)
        assertTrue(rules.isEvening(at(23, 50), start, lateBed))
        assertTrue(rules.isEvening(at(0, 10), start, lateBed))
        assertFalse(rules.isEvening(at(0, 30), start, lateBed))
        assertFalse(rules.isEvening(at(12), start, lateBed))
        // A start equal to bed is no evening at all.
        assertFalse(rules.isEvening(at(21), at(21), at(21)))
    }

    @Test
    fun minutesToBedAndWhatFits() {
        val now = at(21, 10)
        assertEquals(140, rules.minutesToBed(now, bed))
        assertEquals(200, rules.minutesToBed(now, at(0, 30)))
        assertTrue(rules.fits(103, now, bed))
        assertTrue(rules.fits(140, now, bed))
        assertFalse(rules.fits(141, now, bed))
        assertFalse(rules.fits(null, now, bed))
        // Past bedtime nothing fits.
        assertFalse(rules.fits(5, at(23, 40), bed))
    }

    @Test
    fun spareIsWhatIsLeftBeforeBed() {
        assertEquals(37, rules.spare(103, at(21, 10), bed))
        assertEquals(0, rules.spare(140, at(21, 10), bed))
        assertNull(rules.spare(150, at(21, 10), bed))
        assertNull(rules.spare(null, at(21, 10), bed))
    }

    @Test
    fun buildMergesTheHomeRowsAndKeepsOnlyWhatEndsBeforeBed() {
        val a = section("a", m("a1", "95 min"), m("a2", "3h"), m("a3"), m("a4", "1h 30m"))
        val b = section("b", m("b1", "100"), m("a1", "95 min"), m("seen", "90 min"), m("b4", "2h"))
        val other = section("other", m("o1", "80 min"))
        val sections = listOf(a, b, other).associateBy { it.key }
        val built = rules.build(sections, listOf("a", "b"), { it.id == "seen" }, at(21, 10), bed).map { it.id }
        // Round-robin a, b; a2 is too long, a3 has no runtime, a1 is a repeat, "seen" is watched, b4 is 120 (fits).
        assertEquals(listOf("a1", "b1", "a4", "b4"), built)
        assertEquals(listOf("a1", "b1"), rules.build(sections, listOf("a", "b"), { false }, at(21, 10), bed, limit = 2).map { it.id })
        // Later in the evening less fits.
        assertEquals(listOf("o1"), rules.build(sections, listOf("other", "a"), { false }, at(22, 10), bed).map { it.id })
    }

    @Test
    fun buildAcrossMidnight() {
        val a = section("a", m("long", "95 min"), m("short", "80 min"))
        assertEquals(
            listOf("short"),
            rules.build(mapOf("a" to a), listOf("a"), { false }, at(23), at(0, 30)).map { it.id },
        )
    }

    @Test
    fun anyFitsLooksAtTheLoadedRows() {
        val a = section("a", m("long", "3h"), m("unknown"))
        val b = section("b", m("short", "90 min"))
        val sections = listOf(a, b).associateBy { it.key }
        assertFalse(rules.anyFits(sections, listOf("a"), at(21, 10), bed))
        assertTrue(rules.anyFits(sections, listOf("a", "b"), at(21, 10), bed))
        assertFalse(rules.anyFits(sections, listOf("a", "b"), at(23, 0), bed))
    }

    @Test
    fun needsMorePagesAFewRowsUntilTheShelfHasEnough() {
        val sections = (1..12).map { section("r$it", m("x$it", "3h"), nextSkip = 20) }.associateBy { it.key } +
            ("busy" to section("busy", nextSkip = 20, loadingMore = true)) +
            ("done" to section("done"))
        val keys = listOf("busy", "done") + (1..12).map { "r$it" }
        val asked = rules.needsMore(emptyList(), sections, keys)
        assertEquals((1..ShelfEveningRules.MAX_SOURCES_AT_ONCE).map { "r$it" }, asked)
        assertEquals(listOf("r2", "r3"), rules.needsMore(emptyList(), sections, keys, pagesAsked = mapOf("r1" to 2), maxSources = 2))
        val plenty = (1..ShelfEveningRules.TARGET).map { m("p$it", "90") }
        assertTrue(rules.needsMore(plenty, sections, keys).isEmpty())
    }

    @Test
    fun labels() {
        assertEquals("ENDS 22:53", rules.endsLabel(at(22, 53), clock24))
        assertEquals("ENDS 10:53 PM", rules.endsLabel(at(22, 53), clock12))
        assertEquals("ENDS 00:20", rules.endsLabel(at(24, 20), clock24))
        assertEquals("37 min to spare", rules.spareLabel(37))
        assertEquals("1h 5m to spare", rules.spareLabel(65))
        assertEquals("ends right at bedtime", rules.spareLabel(0))
        assertEquals("BED AT 23:30 · 2H 20M", rules.bedLine(bed, at(21, 10), clock24))
        assertEquals("BED AT 23:30 · 1H", rules.bedLine(bed, at(22, 30), clock24))
        assertEquals("BED AT 23:30", rules.bedLine(bed, at(23, 30), clock24))
        assertEquals("BED AT 11:30 PM · 20M", rules.bedLine(bed, at(23, 10), clock12))
    }

    @Test
    fun endsAtUsesTheRuntime() {
        assertEquals(at(22, 53), rules.endsAt(m("p", "1h 43m"), at(21, 10)))
        assertEquals(at(0, 10), rules.endsAt(m("p", "70 min"), at(23)))
        assertNull(rules.endsAt(m("p"), at(21)))
    }

    @Test
    fun theWashWarmsAsBedtimeNears() {
        close(0f, rules.warmth(at(20), start, bed))
        close(0.06f, rules.warmth(at(21), start, bed))
        close(0.09f, rules.warmth(at(21, 45), start, bed))
        close(0.12f, rules.warmth(at(22, 30), start, bed))
        close(0.12f, rules.warmth(at(23, 29), start, bed))
        close(0f, rules.warmth(at(23, 30), start, bed))
        // Across midnight: 22:00 to 00:30 rises until 23:30.
        close(0.09f, rules.warmth(at(22, 45), at(22), at(0, 30)))
        close(0.12f, rules.warmth(at(0, 10), at(22), at(0, 30)))
        // An evening of an hour or less is all last hour.
        close(0.12f, rules.warmth(at(23), at(23), bed))
    }
}
