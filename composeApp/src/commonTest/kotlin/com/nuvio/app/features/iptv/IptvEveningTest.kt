package com.nuvio.app.features.iptv

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IptvEveningTest {
    private val minute = 60_000L
    private val now = 10_000_000L

    private fun merged(uid: Int, name: String = "Channel $uid") = IptvMergedChannel(listOf(IptvChannel(uid = uid, key = "k$uid", num = uid, name = name, categoryId = "c")))
    private fun p(uid: Int, title: String, from: Int, to: Int, kind: String? = null, name: String = "Channel $uid") =
        IptvWallProgramme(merged(uid, name), IptvEpgEntry(title, now + from * minute, now + to * minute, kind))

    @Test
    fun `bedtime is today's, or tomorrow's when it is long past`() {
        val zone = ZoneId.of("UTC")
        val nine = ZonedDateTime.of(2026, 11, 13, 21, 10, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(nine + 140 * minute, IptvRunningOrder.bedtimeMs(nine, 23 * 60 + 30, zone))
        assertEquals(nine + 200 * minute, IptvRunningOrder.bedtimeMs(nine, 30, zone))
    }

    @Test
    fun `the running order chains slots, follows first, and stops before bedtime`() {
        val guide = listOf(
            p(1, "News", -10, 30),
            p(2, "Film A", 0, 100, IptvProgrammeKind.FILM),
            p(3, "Quiz", 30, 60),
            p(4, "Followed Show", 100, 160),
            p(5, "Late Film", 170, 290, IptvProgrammeKind.FILM),
            p(6, "Too Short", 100, 110),
        )
        val order = IptvRunningOrder.build(guide, listOf("Followed Show"), emptySet(), now, now + 200 * minute)
        assertEquals(listOf("Film A", "Followed Show"), order.map { it.entry.title }, "Late Film would pass bedtime, so it is left out")
        // each one starts after the last ends
        assertTrue(order[1].entry.startMs >= order[0].entry.endMs)
    }

    @Test
    fun `a programme nearly over is not the first slot`() {
        val guide = listOf(p(1, "Nearly Done", -50, 10), p(2, "Fresh", 5, 60))
        val order = IptvRunningOrder.build(guide, emptyList(), emptySet(), now, now + 120 * minute)
        assertEquals(listOf("Fresh"), order.map { it.entry.title })
    }

    @Test
    fun `no title twice and nothing after bedtime when there is no room`() {
        val guide = listOf(p(1, "Soap", 0, 30), p(2, "Soap", 30, 60))
        val order = IptvRunningOrder.build(guide, emptyList(), emptySet(), now, now + 45 * minute)
        assertEquals(listOf("Soap"), order.map { it.entry.title })
        assertTrue(IptvRunningOrder.build(guide, emptyList(), emptySet(), now, now - minute).isEmpty())
    }

    @Test
    fun `approved only when every slot has an auto-switch reminder`() {
        val a = p(1, "A", 0, 40)
        val b = p(2, "B", 40, 80)
        fun reminder(x: IptvWallProgramme, auto: Boolean) =
            IptvReminder("pl", x.channel.primary.key, "c", x.entry.title, x.entry.startMs, x.entry.endMs, auto)
        assertFalse(IptvRunningOrder.approved("pl", listOf(a, b), listOf(reminder(a, true))))
        assertFalse(IptvRunningOrder.approved("pl", listOf(a, b), listOf(reminder(a, true), reminder(b, false))))
        assertTrue(IptvRunningOrder.approved("pl", listOf(a, b), listOf(reminder(a, true), reminder(b, true))))
        assertFalse(IptvRunningOrder.approved("pl", emptyList(), emptyList()))
    }

    @Test
    fun `a typed line is read into a time, a kind and words`() {
        assertEquals(30, IptvAsk.parse("something funny, half an hour").minutes)
        assertTrue(IptvAsk.parse("something funny, half an hour").comedy)
        assertEquals(60, IptvAsk.parse("a film for an hour").minutes)
        assertEquals(setOf(IptvProgrammeKind.FILM), IptvAsk.parse("a film for an hour").kinds)
        assertEquals(45, IptvAsk.parse("45 minutes of sport").minutes)
        assertEquals(120, IptvAsk.parse("2 hours").minutes)
        assertEquals(listOf("bond"), IptvAsk.parse("any bond film").words)
        assertNull(IptvAsk.parse("football").minutes)
    }

    @Test
    fun `just ask gives one answer that fits and not that steps on`() {
        val guide = listOf(
            p(1, "Big Film", -5, 120, IptvProgrammeKind.FILM),
            p(2, "Short Comedy Show", -5, 25),
            p(3, "Sitcom Hour", 10, 40),
            p(4, "Weather", -5, 5),
        )
        val first = IptvAsk.answer("something funny, half an hour", guide, emptyList(), emptySet(), now, 0)
        assertNotNull(first.programme)
        assertEquals("Sitcom Hour", first.programme!!.entry.title, "30 minutes long is the closest to half an hour")
        assertEquals(1, first.others)
        val second = IptvAsk.answer("something funny, half an hour", guide, emptyList(), emptySet(), now, 1)
        assertEquals("Short Comedy Show", second.programme!!.entry.title)
        assertEquals(first.programme!!.entry.title, IptvAsk.answer("something funny, half an hour", guide, emptyList(), emptySet(), now, 2).programme!!.entry.title, "it wraps round")
    }

    @Test
    fun `nothing that fits says so`() {
        val answer = IptvAsk.answer("a film, 15 minutes", listOf(p(1, "Epic", -5, 180, IptvProgrammeKind.FILM)), emptyList(), emptySet(), now, 0)
        assertNull(answer.programme)
        assertTrue(answer.why.startsWith("Nothing"))
    }
}
