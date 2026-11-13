package com.nuvio.app.features.iptv

import kotlinx.serialization.Serializable

/** fork:live-tv-brakes - one night's honest count: minutes watched, channel changes, programmes seen to the end, brakes taken. */
@Serializable
data class IptvNight(
    val minutes: Int = 0,
    val changes: Int = 0,
    val finished: Int = 0,
    val stopped: Int = 0,
)

/** What the viewer picked on a brake card. */
enum class IptvBrakeAnswer { KeepGoing, TurnOff, Dismissed }

/**
 * fork:live-tv-brakes (Settings -> Live TV -> Brakes): three gentle stops that never nag.
 * - Zap budget: five channel changes inside two minutes offers a choice, once a night.
 * - End card: after a programme you stayed with, "Stop here" is the focused answer.
 * - Your week: the tally, kept as plain numbers for [KEEP_NIGHTS] nights.
 */
object IptvBrakeRules {
    const val ZAP_LIMIT = 5
    const val ZAP_WINDOW_MS = 2 * 60_000L
    const val KEEP_NIGHTS = 120
    const val STAY_MINUTES = 15

    /** Channel changes still inside the window, with [now] added. */
    fun zapped(times: List<Long>, now: Long): List<Long> =
        (times.filter { now - it in 0 until ZAP_WINDOW_MS } + now)

    /** True when the changes in the window hit the budget and tonight has not been offered the brake yet. */
    fun overBudget(times: List<Long>, offeredNight: Long?, night: Long): Boolean =
        times.size >= ZAP_LIMIT && offeredNight != night

    /** The end card shows from half a minute before the programme ends, only after the viewer stayed [STAY_MINUTES] on it. */
    fun endCardDue(endMs: Long, now: Long, stayedMs: Long, shownFor: Long?): Boolean =
        shownFor != endMs && stayedMs >= STAY_MINUTES * 60_000L && now >= endMs - 30_000L && now <= endMs + 2 * 60_000L

    fun nightKey(night: Long): String = night.toString()

    fun add(tally: Map<String, IptvNight>, night: Long, change: (IptvNight) -> IptvNight): Map<String, IptvNight> {
        val key = nightKey(night)
        return prune(tally + (key to change(tally[key] ?: IptvNight())), night)
    }

    /** Drops nights older than [KEEP_NIGHTS]. */
    fun prune(tally: Map<String, IptvNight>, night: Long): Map<String, IptvNight> =
        tally.filterKeys { key -> (key.toLongOrNull()?.let { night - it in 0 until KEEP_NIGHTS } ?: false) }

    /** The last seven nights, tonight included, summed. */
    fun week(tally: Map<String, IptvNight>, night: Long): IptvNight {
        var sum = IptvNight()
        for (day in 0 until 7) {
            val n = tally[nightKey(night - day)] ?: continue
            sum = IptvNight(sum.minutes + n.minutes, sum.changes + n.changes, sum.finished + n.finished, sum.stopped + n.stopped)
        }
        return sum
    }

    fun hoursText(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /** The one honest line for the week. */
    fun weekLine(week: IptvNight): String =
        "${hoursText(week.minutes)} watching, ${week.changes} channel changes, ${week.finished} programmes seen to the end"
}
