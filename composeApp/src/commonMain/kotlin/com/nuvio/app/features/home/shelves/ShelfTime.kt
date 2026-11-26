package com.nuvio.app.features.home.shelves

/*
 * fork:shelf-home - the time-aware lines ("1h 16m left · ends 8:41 PM"). Pure, so the rules are
 * tested; the screen passes in the wall clock and formats the clock times with Tonight's
 * clockLabel (so the 12-hour setting carries over).
 */
object ShelfTime {
    private val hours = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE)
    private val minutes = Regex("""(\d+)\s*m""", RegexOption.IGNORE_CASE)
    private val bare = Regex("""^\s*(\d+)\s*(min|mins|minutes)?\s*$""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""^\s*(\d+):(\d{2})(?::(\d{2}))?\s*$""")

    /**
     * A catalog's runtime in minutes: "129 min", "129", "2h 9m", "2h", "45m", "02:09:00". Null when it
     * cannot be read or is not a sensible film or episode length (1 to 600 minutes).
     */
    fun runtimeMinutes(runtime: String?): Int? {
        val text = runtime?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val result = bare.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: clock.find(text)?.let { m ->
                // "02:09:00" and "2:09" are both hours and minutes.
                m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            }
            ?: run {
                val h = hours.find(text)?.groupValues?.get(1)?.toIntOrNull()
                val mm = minutes.find(text)?.groupValues?.get(1)?.toIntOrNull()
                if (h == null && mm == null) null else (h ?: 0) * 60 + (mm ?: 0)
            }
        return result?.takeIf { it in 1..600 }
    }

    /** Whole minutes left of a part-watched item, rounded up; null without a length. */
    fun minutesLeft(durationMs: Long, positionMs: Long): Int? {
        if (durationMs <= 0L) return null
        val leftMs = (durationMs - positionMs.coerceIn(0L, durationMs)).coerceAtLeast(0L)
        if (leftMs == 0L) return null
        return ((leftMs + 59_999L) / 60_000L).toInt()
    }

    /** "1h 16m", "45m", "2h". */
    fun length(minutes: Int): String = when {
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }

    /** The minute of the day something that starts now and runs [minutes] ends at (wraps past midnight). */
    fun endsAt(nowMinuteOfDay: Int, minutes: Int): Int = (nowMinuteOfDay + minutes) % (24 * 60)
}
