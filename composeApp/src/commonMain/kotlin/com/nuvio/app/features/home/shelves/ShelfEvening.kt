// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tonight.TonightClock
import com.nuvio.app.features.tonight.TonightSettingsRepository
import com.nuvio.app.features.tonight.clockLabel
import com.nuvio.app.features.tonight.tonightMinutesUntilBed
import kotlinx.coroutines.delay

/*
 * fork:shelf-home - v3 phase 4, the evening home (design-notes/home-shelves-v3.md, board V3Evening).
 *
 * From the user's start time (ShelfExtrasSettings.eveningStartMinute, 21:00 by default) until Tonight's bedtime, a
 * "Fits before bed" shelf leads Home: titles from the Home rows, merged round-robin like a mix (ShelfMixRules does the
 * merge, de-duplication, watched filter and paging), kept only when their known runtime, started now, ends by bedtime.
 * Each poster shows "ENDS 22:53", the title block adds "37 min to spare", "BED AT 23:30 · 2H 20M" sits under the clock
 * and an amber wash warms the art (6% at the start, rising to 12% for the last hour).
 *
 * [ShelfEveningRules] is pure and tested (ShelfEveningRulesTest); the labels take the clock formatter so tests need no
 * settings (the screen passes Tonight's clockLabel, so the 12-hour setting carries over).
 */
internal object ShelfEveningRules {
    private const val DAY = 24 * 60

    /** The wash at the start of the evening, and in the last hour before bed. */
    /** On Fits before bed the row and the title block sit this much higher, to fit the ENDS lines (V3Evening). */
    const val ROW_LIFT = 50

    const val WARMTH_START = 0.06f
    const val WARMTH_LAST_HOUR = 0.12f
    const val LAST_HOUR = 60

    /** The evening shelf asks its rows for more pages until it shows this many... */
    const val TARGET = ShelfMixRules.TARGET

    /** ...asking at most this many rows at once (Home may have dozens of rows)... */
    const val MAX_SOURCES_AT_ONCE = 8

    /** ...and each row at most this many times on its own (scrolling may ask for more). */
    const val MAX_AUTO_PAGES = 2

    private fun mod(m: Int): Int = ((m % DAY) + DAY) % DAY

    /**
     * Whether [nowMinute] is in the evening: from [startMinute] (inclusive) until [bedMinute] (exclusive), across
     * midnight when bed is earlier in the day than the start (21:00 to 00:30). Never when start equals bed.
     */
    fun isEvening(nowMinute: Int, startMinute: Int, bedMinute: Int): Boolean {
        val now = mod(nowMinute)
        val start = mod(startMinute)
        val bed = mod(bedMinute)
        return when {
            start == bed -> false
            start < bed -> now in start until bed
            else -> now >= start || now < bed
        }
    }

    /** Minutes from now to bedtime, read as Tonight reads it (a bedtime after midnight is tonight's; just past is negative). */
    fun minutesToBed(nowMinute: Int, bedMinute: Int): Int = tonightMinutesUntilBed(mod(nowMinute), mod(bedMinute))

    /** Whether something [runtimeMinutes] long, started now, ends by bedtime. An unknown runtime never fits. */
    fun fits(runtimeMinutes: Int?, nowMinute: Int, bedMinute: Int): Boolean =
        runtimeMinutes != null && runtimeMinutes > 0 && runtimeMinutes <= minutesToBed(nowMinute, bedMinute)

    /** Minutes left before bed after watching it now; null when it does not fit (or its length is unknown). */
    fun spare(runtimeMinutes: Int?, nowMinute: Int, bedMinute: Int): Int? =
        if (fits(runtimeMinutes, nowMinute, bedMinute)) minutesToBed(nowMinute, bedMinute) - runtimeMinutes!! else null

    /** A title's runtime in minutes as the shelf reads it (a series' is one episode's). */
    fun runtimeOf(item: MetaPreview): Int? = ShelfTime.runtimeMinutes(item.runtime)

    /** The minute of the day [item] would end if started now; null without a runtime. */
    fun endsAt(item: MetaPreview, nowMinute: Int): Int? = runtimeOf(item)?.let { ShelfTime.endsAt(mod(nowMinute), it) }

    /** The evening shelf as a mix: the Home rows, watched titles hidden, bedtime filter on (refined in [build]). */
    private fun asMix(homeSectionKeys: List<String>) = ShelfMix(
        id = ShelfHomeRules.EVENING_KEY,
        title = "",
        sourceKeys = homeSectionKeys,
        hideWatched = true,
        fitsBeforeBed = true,
    )

    /**
     * The shelf's titles: [homeSectionKeys]' loaded items round-robin (as [ShelfMixRules.build]), de-duplicated,
     * watched ones left out, and only those with a known runtime that, started at [nowMinute], end by [bedMinute].
     * At most [limit].
     */
    fun build(
        sections: Map<String, HomeCatalogSection>,
        homeSectionKeys: List<String>,
        isWatched: (MetaPreview) -> Boolean,
        nowMinute: Int,
        bedMinute: Int,
        limit: Int = 40,
    ): List<MetaPreview> =
        ShelfMixRules.build(asMix(homeSectionKeys), sections, isWatched, mod(nowMinute), mod(bedMinute))
            .asSequence()
            .filter { fits(runtimeOf(it), nowMinute, bedMinute) }
            .take(limit.coerceAtLeast(0))
            .toList()

    /** Whether anything on the loaded rows fits now (watched titles counted): HomeScreen's test for showing the shelf. */
    fun anyFits(
        sections: Map<String, HomeCatalogSection>,
        homeSectionKeys: List<String>,
        nowMinute: Int,
        bedMinute: Int,
    ): Boolean = homeSectionKeys.asSequence().mapNotNull { sections[it] }.flatMap { it.items.asSequence() }
        .any { fits(runtimeOf(it), nowMinute, bedMinute) }

    /**
     * The rows worth asking for another page (as [ShelfMixRules.needsMore]): while [built] is short of [target], rows
     * with a next page, not already loading, asked fewer than [maxPages] times ([pagesAsked], kept by the caller);
     * at most [maxSources] of them, in Home order.
     */
    fun needsMore(
        built: List<MetaPreview>,
        sections: Map<String, HomeCatalogSection>,
        homeSectionKeys: List<String>,
        pagesAsked: Map<String, Int> = emptyMap(),
        target: Int = TARGET,
        maxPages: Int = MAX_AUTO_PAGES,
        maxSources: Int = MAX_SOURCES_AT_ONCE,
    ): List<String> = ShelfMixRules.needsMore(asMix(homeSectionKeys), built, sections, pagesAsked, target, maxPages)
        .take(maxSources.coerceAtLeast(0))

    /** "ENDS 22:53" under a poster, for a title ending at [endMinute]. */
    fun endsLabel(endMinute: Int, clock: (Int) -> String = { clockLabel(it) }): String = "ENDS ${clock(mod(endMinute))}".uppercase()

    /** "37 min to spare"; "1h 5m to spare" from an hour; "Ends right at bedtime" at 0. */
    fun spareLabel(minutes: Int): String = when {
        minutes <= 0 -> "ends right at bedtime"
        minutes < 60 -> "$minutes min to spare"
        else -> "${ShelfTime.length(minutes)} to spare"
    }

    /** "BED AT 23:30 · 2H 20M" under the clock; just "BED AT 23:30" once bedtime has come. */
    fun bedLine(bedMinute: Int, nowMinute: Int, clock: (Int) -> String = { clockLabel(it) }): String {
        val left = minutesToBed(nowMinute, bedMinute)
        val head = "BED AT ${clock(mod(bedMinute))}"
        return (if (left > 0) "$head · ${ShelfTime.length(left)}" else head).uppercase()
    }

    /**
     * The amber wash's strength: 0 outside the evening; [WARMTH_START] at the start, rising evenly to
     * [WARMTH_LAST_HOUR] when the last hour before bed begins, and held there until bed. An evening an hour long or
     * shorter is all last hour.
     */
    fun warmth(nowMinute: Int, startMinute: Int, bedMinute: Int): Float {
        if (!isEvening(nowMinute, startMinute, bedMinute)) return 0f
        val length = mod(bedMinute - startMinute)
        val elapsed = mod(nowMinute - startMinute)
        val rise = length - LAST_HOUR
        if (rise <= 0 || elapsed >= rise) return WARMTH_LAST_HOUR
        return WARMTH_START + (WARMTH_LAST_HOUR - WARMTH_START) * (elapsed.toFloat() / rise)
    }
}

/** The wash's colour, rgba(255,140,60). */
private val EveningAmber = Color(0xFFFF8C3C)

/** The amber wash over the art and its scrims, under the words. [warmth] is its alpha (0 draws nothing). */
@Composable
internal fun ShelfEveningWash(warmth: Float, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(warmth.coerceIn(0f, 1f), tween(2_000), label = "eveningWash")
    if (alpha <= 0f) return
    Box(modifier.fillMaxSize().background(EveningAmber.copy(alpha = alpha)))
}

/**
 * For HomeScreen: whether the "Fits before bed" shelf should lead Home now. True only with the Evening home switch
 * on, inside the evening, and with something on the loaded rows that fits. The clock is read every 20 s inside an
 * effect and the result is a state that changes only when the evening starts or ends (or the last title stops
 * fitting), so HomeScreen is not recomposed by the tick and the model it keys is rebuilt only then.
 */
@Composable
internal fun rememberShelfEveningOn(
    sections: Map<String, HomeCatalogSection>,
    homeSectionKeys: List<String>,
): Boolean {
    remember { ShelfExtrasRepository.ensureLoaded() }
    remember { TonightSettingsRepository.ensureLoaded() }
    val extras by ShelfExtrasRepository.state.collectAsStateWithLifecycle()
    val tonight by TonightSettingsRepository.state.collectAsStateWithLifecycle()
    val on = remember { mutableStateOf(false) }
    LaunchedEffect(extras.eveningHome, extras.eveningStartMinute, tonight.bedMinuteOfDay, sections, homeSectionKeys) {
        while (true) {
            val now = TonightClock.now().minuteOfDay
            // Writing an equal value does not recompose anyone.
            on.value = extras.eveningHome &&
                ShelfEveningRules.isEvening(now, extras.eveningStartMinute, tonight.bedMinuteOfDay) &&
                ShelfEveningRules.anyFits(sections, homeSectionKeys, now, tonight.bedMinuteOfDay)
            delay(20_000)
        }
    }
    return on.value
}
