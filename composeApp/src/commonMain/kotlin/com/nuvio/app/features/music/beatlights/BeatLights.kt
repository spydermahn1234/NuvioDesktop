package com.nuvio.app.features.music.beatlights
// FORK (Spyder): Nick's beat lights, not in UmbraProjects. See CLAUDE.md.

import com.nuvio.app.features.music.visualiser.MAP_FRAME_MS
import com.nuvio.app.features.music.visualiser.Onset
import com.nuvio.app.features.music.visualiser.TrackMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor

/*
 * fork:beat-lights - flash a light on the music's kick drum (or every beat, or only beat one of each bar). Nothing here
 * listens to the room: the Now Playing visualiser's track map (TrackMap.kt) already holds every kick and the song's beat
 * grid, analysed from the decoded track ahead of the playhead. So a flash is planned, not detected: the engine
 * (BeatLightsEngine.desktop.kt) fires each hit a little early ([BeatLightsSettings.earlyMs]) to cover the HDMI amp and the
 * bulb's own delay, and the light lands on the beat instead of after it.
 *
 * Step one of the plan: one WiZ bulb, straight over the home Wi-Fi (UDP 38899). Switchboard on the phone is to drive it later.
 */

/** Which hits flash the light. */
@Serializable
enum class BeatLightsOn {
    /** Every kick drum (the track map's kick row: struck low end, not the bass line). */
    Kick,

    /** Every beat of the song's beat grid. */
    Beat,

    /** Only beat one of each bar (the downbeat). */
    Downbeat,
}

/** How a hit looks on the bulb. WiZ bulbs fade every change a little by themselves, so a short dip can blur away. */
@Serializable
enum class BeatLightsStyle {
    /** Hard on, then off after the flash length: a strobe. */
    Strobe,

    /** Up to full, back to the resting brightness after the flash length. */
    Dim,

    /** Each hit flips the light (on, off, on...): one command per hit, so a bulb that can't keep up with two still follows. */
    Swap,
}

@Serializable
data class BeatLightsSettings(
    val enabled: Boolean = false,
    /** The WiZ bulb's address on the home network, e.g. 192.168.1.40. */
    val wizAddress: String = "",
    val on: BeatLightsOn = BeatLightsOn.Kick,
    val style: BeatLightsStyle = BeatLightsStyle.Strobe,
    /** How much earlier than the hit the command is sent: the amp's delay plus the bulb's. */
    val earlyMs: Int = DEFAULT_EARLY_MS,
    /** How long the light stays up before it drops back. */
    val flashMs: Int = DEFAULT_FLASH_MS,
    /** Brightness between flashes, 10..100 (WiZ's lowest is 10). */
    val restPercent: Int = 10,
    /** Brightness on a flash, 10..100. */
    val flashPercent: Int = 100,
) {
    val ready: Boolean get() = enabled && BeatLightsRules.validAddress(wizAddress)

    companion object {
        const val DEFAULT_EARLY_MS = 80
        const val DEFAULT_FLASH_MS = 120
        const val MAX_EARLY_MS = 400
    }
}

internal object BeatLightsRules {
    /** WiZ's port for local commands. */
    const val WIZ_PORT = 38899

    /** A playhead that moves further than this between two looks was a seek: start again from there, firing nothing. */
    const val JUMP_MS = 600L

    /** How long the position may run on the clock after the player's last report (the stage's smoothing does the same). */
    const val CARRY_MS = 1_500L

    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun validAddress(text: String): Boolean {
        val m = ipv4.matchEntire(text.trim()) ?: return false
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    /**
     * The hit times (ms into the track) in (fromMs, toMs], in order. Downbeat and Beat need the beat grid; a track without
     * one falls back to its kicks, so the light still moves with the music.
     */
    fun hitsBetween(map: TrackMap, fromMs: Double, toMs: Double, on: BeatLightsOn): List<Double> {
        if (toMs <= fromMs) return emptyList()
        val grid = map.beat
        if (on == BeatLightsOn.Kick || grid == null) {
            // Frame f covers [f*25, f*25+25); its hit counts at the frame's start.
            val first = maxOf(0, floor(fromMs / MAP_FRAME_MS).toInt() + 1)
            val last = minOf(map.frames - 1, floor(toMs / MAP_FRAME_MS).toInt())
            val out = ArrayList<Double>()
            for (f in first..last) {
                val at = f.toDouble() * MAP_FRAME_MS
                if (at > fromMs && at <= toMs && map.hits(f, Onset.KICK)) out += at
            }
            // A kick can be flagged in two frames in a row; one flash is enough.
            return out.filterIndexed { i, at -> i == 0 || at - out[i - 1] > MAP_FRAME_MS }
        }
        val out = ArrayList<Double>()
        for (i in grid.beatsMs.indices) {
            val at = grid.beatsMs[i]
            if (at <= fromMs) continue
            if (at > toMs) break
            if (on == BeatLightsOn.Downbeat && (grid.barPos[i] + grid.barShift).mod(grid.beatsPerBar) != 0) continue
            out += at
        }
        return out
    }

    /** Where the song is now: the last reported position carried on by the clock while it plays (at most [CARRY_MS]). */
    fun playheadMs(reportedMs: Long, msSinceReport: Long, playing: Boolean): Long =
        if (playing) reportedMs + msSinceReport.coerceIn(0L, CARRY_MS) else reportedMs

    /** The WiZ command that sets the bulb on at [percent] brightness. */
    fun wizBrightness(percent: Int): String =
        """{"method":"setPilot","params":{"state":true,"dimming":${percent.coerceIn(10, 100)}}}"""

    /** The WiZ command that switches the bulb off. */
    const val WIZ_OFF = """{"method":"setPilot","params":{"state":false}}"""
}

/**
 * Tracks the last look's target so each hit fires once. [advance] is given the playhead plus the early time and returns
 * the hits passed since the last call; a seek (or the first look) fires nothing and starts again from there.
 */
internal class BeatLightsCursor {
    private var lastTarget = Double.NaN
    private var lastLoadId = Long.MIN_VALUE

    // The last hit fired: a kick flagged in two frames in a row can land either side of a look, and is one flash.
    private var lastFired = Double.NEGATIVE_INFINITY

    /** Times the playhead moved more than [BeatLightsRules.JUMP_MS] between two looks (a seek, or a position report that jumped). */
    var jumps = 0

    fun reset() {
        lastTarget = Double.NaN
        lastFired = Double.NEGATIVE_INFINITY
    }

    fun advance(loadId: Long, map: TrackMap, targetMs: Double, on: BeatLightsOn): List<Double> {
        val from = lastTarget
        val sameTrack = loadId == lastLoadId
        lastLoadId = loadId
        if (!sameTrack || from.isNaN()) {
            reset()
            lastTarget = targetMs
            return emptyList()
        }
        if (targetMs < from) {
            // A fresh position report a little behind the clock's guess: hold, so nothing already fired fires again.
            // A real seek back starts again from there.
            if (from - targetMs > BeatLightsRules.JUMP_MS) {
                jumps++
                reset()
                lastTarget = targetMs
            }
            return emptyList()
        }
        lastTarget = targetMs
        if (targetMs - from > BeatLightsRules.JUMP_MS) {
            jumps++
            return emptyList()
        }
        // Only what the map has analysed so far can flash.
        val upTo = minOf(targetMs, map.analysedMs.toDouble())
        val hits = BeatLightsRules.hitsBetween(map, from, upTo, on).filter { it - lastFired > MAP_FRAME_MS }
        if (hits.isNotEmpty()) lastFired = hits.last()
        return hits
    }
}

/** The settings, shared by the settings page and the desktop engine (which loads and saves them). */
object BeatLightsSettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(BeatLightsSettings())
    val state: StateFlow<BeatLightsSettings> = _state.asStateFlow()

    /** Set by the desktop side: where the settings are kept. */
    internal var persist: ((String) -> Unit)? = null

    internal fun load(payload: String?) {
        if (payload.isNullOrBlank()) return
        runCatching { json.decodeFromString(BeatLightsSettings.serializer(), payload) }.onSuccess { _state.value = it }
    }

    private fun change(edit: (BeatLightsSettings) -> BeatLightsSettings) {
        _state.update(edit)
        persist?.invoke(json.encodeToString(BeatLightsSettings.serializer(), _state.value))
    }

    fun setEnabled(value: Boolean) = change { it.copy(enabled = value) }
    fun setWizAddress(value: String) = change { it.copy(wizAddress = value.trim()) }
    fun setOn(value: BeatLightsOn) = change { it.copy(on = value) }
    fun setStyle(value: BeatLightsStyle) = change { it.copy(style = value) }
    fun setEarlyMs(value: Int) = change { it.copy(earlyMs = value.coerceIn(0, BeatLightsSettings.MAX_EARLY_MS)) }
    fun setFlashMs(value: Int) = change { it.copy(flashMs = value.coerceIn(40, 400)) }
    fun setRestPercent(value: Int) = change { it.copy(restPercent = value.coerceIn(10, 100)) }
}

