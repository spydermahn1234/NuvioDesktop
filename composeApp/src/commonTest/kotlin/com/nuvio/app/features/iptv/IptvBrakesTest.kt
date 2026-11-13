package com.nuvio.app.features.iptv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IptvBrakesTest {
    private val min = 60_000L

    @Test
    fun fiveChangesInTwoMinutesIsOverBudget() {
        var times = emptyList<Long>()
        for (i in 0 until 4) times = IptvBrakeRules.zapped(times, 1_000_000L + i * 20_000L)
        assertFalse(IptvBrakeRules.overBudget(times, null, 5))
        times = IptvBrakeRules.zapped(times, 1_000_000L + 4 * 20_000L)
        assertTrue(IptvBrakeRules.overBudget(times, null, 5))
    }

    @Test
    fun slowChangesNeverAddUp() {
        var times = emptyList<Long>()
        for (i in 0 until 10) times = IptvBrakeRules.zapped(times, i * 3 * min)
        assertEquals(1, times.size)
        assertFalse(IptvBrakeRules.overBudget(times, null, 5))
    }

    @Test
    fun offeredOncePerNight() {
        val times = (0 until 5).map { it * 1_000L }
        assertTrue(IptvBrakeRules.overBudget(times, 4L, 5))
        assertFalse(IptvBrakeRules.overBudget(times, 5L, 5))
    }

    @Test
    fun endCardNeedsStayingAndTheEndNearby() {
        val end = 100 * min
        assertTrue(IptvBrakeRules.endCardDue(end, end - 20_000L, 30 * min, null))
        assertFalse(IptvBrakeRules.endCardDue(end, end - 20_000L, 10 * min, null))   // only flicked past
        assertFalse(IptvBrakeRules.endCardDue(end, end - 5 * min, 30 * min, null))   // too early
        assertFalse(IptvBrakeRules.endCardDue(end, end + 5 * min, 30 * min, null))   // too late
        assertFalse(IptvBrakeRules.endCardDue(end, end - 20_000L, 30 * min, end))    // already shown
    }

    @Test
    fun tallyAddsAndPrunes() {
        var tally = emptyMap<String, IptvNight>()
        tally = IptvBrakeRules.add(tally, 1000, { it.copy(minutes = it.minutes + 30) })
        tally = IptvBrakeRules.add(tally, 1000, { it.copy(changes = it.changes + 3) })
        assertEquals(IptvNight(minutes = 30, changes = 3), tally["1000"])
        tally = IptvBrakeRules.add(tally, 1000 + 200, { it.copy(minutes = 5) })
        assertEquals(setOf("1200"), tally.keys)   // 1000 is more than 120 nights old
    }

    @Test
    fun weekSumsSevenNights() {
        val tally = mapOf(
            "100" to IptvNight(60, 4, 1, 0),
            "97" to IptvNight(90, 10, 2, 1),
            "93" to IptvNight(500, 99, 9, 9),   // eight nights back: not in the week
        )
        assertEquals(IptvNight(150, 14, 3, 1), IptvBrakeRules.week(tally, 100))
    }

    @Test
    fun linesRead() {
        assertEquals("45 min", IptvBrakeRules.hoursText(45))
        assertEquals("2 h", IptvBrakeRules.hoursText(120))
        assertEquals("1 h 5 min", IptvBrakeRules.hoursText(65))
        assertEquals("1 h 5 min watching, 14 channel changes, 3 programmes seen to the end", IptvBrakeRules.weekLine(IptvNight(65, 14, 3, 0)))
    }
}
