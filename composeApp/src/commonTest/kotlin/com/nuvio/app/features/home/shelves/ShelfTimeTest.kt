package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShelfTimeTest {
    @Test
    fun readsTheRuntimeShapesCatalogsUse() {
        assertEquals(129, ShelfTime.runtimeMinutes("129 min"))
        assertEquals(129, ShelfTime.runtimeMinutes("129"))
        assertEquals(129, ShelfTime.runtimeMinutes("2h 9m"))
        assertEquals(120, ShelfTime.runtimeMinutes("2h"))
        assertEquals(45, ShelfTime.runtimeMinutes("45m"))
        assertEquals(129, ShelfTime.runtimeMinutes("02:09:00"))
        assertEquals(30, ShelfTime.runtimeMinutes(" 30 mins "))
    }

    @Test
    fun unreadableOrSillyRuntimesGiveNothing() {
        assertNull(ShelfTime.runtimeMinutes(null))
        assertNull(ShelfTime.runtimeMinutes(""))
        assertNull(ShelfTime.runtimeMinutes("N/A"))
        assertNull(ShelfTime.runtimeMinutes("0"))
        assertNull(ShelfTime.runtimeMinutes("9999"))
    }

    @Test
    fun minutesLeftRoundsUpAndNeedsALength() {
        assertEquals(76, ShelfTime.minutesLeft(durationMs = 100 * 60_000L, positionMs = 24 * 60_000L + 30_000L))
        assertEquals(1, ShelfTime.minutesLeft(durationMs = 60_000L, positionMs = 59_000L))
        assertNull(ShelfTime.minutesLeft(durationMs = 0L, positionMs = 0L))
        assertNull(ShelfTime.minutesLeft(durationMs = 60_000L, positionMs = 60_000L))
    }

    @Test
    fun lengthsAndEndTimes() {
        assertEquals("1h 16m", ShelfTime.length(76))
        assertEquals("2h", ShelfTime.length(120))
        assertEquals("45m", ShelfTime.length(45))
        assertEquals(20 * 60 + 41, ShelfTime.endsAt(19 * 60 + 25, 76))
        assertEquals(30, ShelfTime.endsAt(23 * 60 + 30, 60))
    }
}
