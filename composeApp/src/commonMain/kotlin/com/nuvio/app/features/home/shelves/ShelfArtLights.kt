// FORK (Spyder): Nick's shelf home, not in UmbraProjects. See CLAUDE.md.
package com.nuvio.app.features.home.shelves

import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.nuvio.app.features.lights.GoveeClient
import com.nuvio.app.features.lights.GoveeDevice
import com.nuvio.app.features.lights.GoveeDeviceState
import com.nuvio.app.features.lights.GoveeResult
import com.nuvio.app.features.lights.LightsController
import com.nuvio.app.features.lights.LightsSettings
import com.nuvio.app.features.lights.LightsSettingsRepository
import com.nuvio.app.features.lights.isWithinDailyWindow
import com.nuvio.app.features.music.MusicPlayback
import com.nuvio.app.features.music.beatlights.BeatLightsRules
import com.nuvio.app.features.music.beatlights.BeatLightsSettings
import com.nuvio.app.features.music.beatlights.BeatLightsSettingsRepository
import com.nuvio.app.features.tonight.TonightSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * fork:shelf-home v3 phase 7 - lights follow the art (boards V3Lights and V3Tunes; plan in
 * design-notes/home-shelves-v3.md). While the shelf home is browsed (and in its gallery) the chosen lights take the
 * art's accent colour: only once it has been steady for a second, at most every two seconds, a two-second fade, and
 * capped brightness (halved after bedtime). The room goes back to how it was when the home is left, a player starts or
 * the setting goes off. Every change runs in LightsController's queue, so it never overlaps Lights out's scenes.
 *
 * Lights: the beat lights' WiZ bulb and Govee LAN lights (UDP, faded in software), and Lights out's Govee cloud lights
 * (one change per colour, Govee's rate limit).
 */

/** What the bulbs are asked to show: a colour (0..255 each), or white at [kelvin] for a grey picture. */
internal data class ShelfBulbColour(val r: Int, val g: Int, val b: Int, val kelvin: Int? = null) {
    val rgb: Int get() = (r shl 16) or (g shl 8) or b
}

enum class ShelfArtBulbKind { Wiz, GoveeLan, GoveeCloud }

/** One light that can follow the art. [address] is an IP for the LAN kinds and the device id for the cloud. */
data class ShelfArtBulb(
    val id: String,
    val label: String,
    val kind: ShelfArtBulbKind,
    val address: String,
    val sku: String = "",
    val name: String = "",
)

/** What the room shows now, for the status line under the clock. */
data class ShelfArtLit(val name: String, val color: Color)

/** A WiZ bulb's state as `getPilot` reports it. */
internal data class WizPilot(
    val on: Boolean,
    val sceneId: Int = 0,
    val r: Int? = null,
    val g: Int? = null,
    val b: Int? = null,
    val c: Int? = null,
    val w: Int? = null,
    val temp: Int? = null,
    val dimming: Int? = null,
    val speed: Int? = null,
)

/** A Govee LAN light's state as `devStatus` reports it. */
internal data class GoveeLanStatus(
    val on: Boolean,
    val brightness: Int,
    val r: Int,
    val g: Int,
    val b: Int,
    val kelvin: Int,
)

internal object ShelfArtLightsRules {
    /** The colour must hold this long before the room follows (scrolling fast leaves it alone). */
    const val STEADY_MS = 1_000L

    /** Never two changes closer than this. */
    const val MIN_GAP_MS = 2_000L

    /** The fade from one colour to the next, in [FADE_STEPS] sends (WiZ and Govee LAN; the cloud jumps). */
    const val FADE_MS = 2_000L
    const val FADE_STEPS = 8

    /** Colours within this (per channel, 0..255) are the same colour to the room: the accent's easing is not a change. */
    const val SAME_COLOUR = 10

    /** Below this saturation (HSL) a picture is grey: the bulbs go warm white rather than a muddy tint. */
    const val GREY_SATURATION = 0.12f
    const val WARM_WHITE_K = 2_700

    /** A bulb's colour is at least this saturated (HSV), so a pale accent still reads as a colour on the wall. */
    const val MIN_BULB_SATURATION = 0.55f

    /** After bedtime the cap is halved until this minute of the morning (06:00). */
    const val NIGHT_UNTIL_MINUTE = 6 * 60

    /** WiZ's lowest dimming. */
    const val MIN_DIMMING = 10

    const val WIZ_GET_PILOT = """{"method":"getPilot","params":{}}"""
    const val GOVEE_DEV_STATUS = """{"msg":{"cmd":"devStatus","data":{}}}"""

    private val json = Json { ignoreUnknownKeys = true }

    /** Hue (0..360), saturation and lightness (0..1) of [color]. */
    fun hsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        val d = hi - lo
        if (d < 1e-4f) return Triple(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (hi) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Triple((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
    }

    // Hue bands (start degree) with names for light / mid / deep, vivid and muted. A guess at what reads as friendly.
    private class Band(val from: Float, val vivid: List<String>, val muted: List<String>)

    private val bands = listOf(
        Band(0f, listOf("coral", "ember red", "oxblood"), listOf("dusk rose", "clay", "maroon")),
        Band(15f, listOf("peach", "amber", "rust"), listOf("dusk rose", "terracotta", "umber")),
        Band(40f, listOf("honey", "desert gold", "bronze"), listOf("sand", "desert gold", "old brass")),
        Band(65f, listOf("lime", "moss", "olive"), listOf("sage", "moss", "olive")),
        Band(95f, listOf("mint", "emerald", "forest"), listOf("sage", "fern", "forest")),
        Band(150f, listOf("aqua", "teal", "deep teal"), listOf("sea glass", "jade", "deep teal")),
        Band(185f, listOf("sky blue", "lagoon", "petrol"), listOf("mist blue", "slate", "petrol")),
        Band(215f, listOf("cornflower", "cobalt", "midnight blue"), listOf("steel blue", "denim", "midnight")),
        Band(250f, listOf("lavender", "violet", "indigo"), listOf("lilac", "heather", "indigo")),
        Band(285f, listOf("orchid", "magenta", "plum"), listOf("mauve", "heather", "plum")),
        Band(320f, listOf("flamingo", "fuchsia", "berry"), listOf("blush", "dusk rose", "wine")),
        Band(345f, listOf("coral", "ember red", "oxblood"), listOf("dusk rose", "clay", "maroon")),
    )

    /** A short friendly name for [color] ("dusk rose", "desert gold", "sea glass"), lower case. */
    fun colourName(color: Color): String {
        val (h, s, l) = hsl(color)
        if (s < GREY_SATURATION || l > 0.94f || l < 0.06f) {
            return when {
                l >= 0.6f -> "moonlight"
                l >= 0.3f -> "pewter"
                else -> "charcoal"
            }
        }
        val band = bands.last { h >= it.from }
        val tone = when {
            l >= 0.6f -> 0
            l >= 0.35f -> 1
            else -> 2
        }
        return (if (s < 0.45f) band.muted else band.vivid)[tone]
    }

    /** What the bulbs show for [color]: its hue at full value and at least [MIN_BULB_SATURATION]; warm white if grey. */
    fun bulbColour(color: Color): ShelfBulbColour {
        val (_, s, _) = hsl(color)
        if (s < GREY_SATURATION) return ShelfBulbColour(255, 180, 107, kelvin = WARM_WHITE_K)
        val r = color.red
        val g = color.green
        val b = color.blue
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val hue = hsl(color).first
        val sat = (if (hi <= 0f) 0f else (hi - lo) / hi).coerceAtLeast(MIN_BULB_SATURATION).coerceAtMost(1f)
        return hsvToRgb(hue, sat, 1f)
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): ShelfBulbColour {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
        return ShelfBulbColour(ch(r), ch(g), ch(b))
    }

    /** True when the room would not see the difference. */
    fun close(a: ShelfBulbColour, b: ShelfBulbColour): Boolean {
        if (a.kelvin != b.kelvin) return false
        if (a.kelvin != null) return true
        return abs(a.r - b.r) <= SAME_COLOUR && abs(a.g - b.g) <= SAME_COLOUR && abs(a.b - b.b) <= SAME_COLOUR
    }

    /** How long to wait before sending a colour that became the target at [steadySinceMs]. */
    fun waitMs(nowMs: Long, steadySinceMs: Long, lastSentMs: Long): Long =
        (max(steadySinceMs + STEADY_MS, lastSentMs + MIN_GAP_MS) - nowMs).coerceAtLeast(0L)

    /** The colours to send, in order, to fade from [from] to [to]; a jump when either is white or [from] is unknown. */
    fun fade(from: ShelfBulbColour?, to: ShelfBulbColour, steps: Int = FADE_STEPS): List<ShelfBulbColour> {
        if (from == null || from.kelvin != null || to.kelvin != null || steps <= 1) return listOf(to)
        return (1..steps).map { i ->
            val t = i.toFloat() / steps
            fun mix(a: Int, b: Int) = (a + (b - a) * t).roundToInt().coerceIn(0, 255)
            ShelfBulbColour(mix(from.r, to.r), mix(from.g, to.g), mix(from.b, to.b))
        }
    }

    /** The brightness to use: the cap (10..100), halved from bedtime until 06:00. */
    fun brightness(capPercent: Int, minuteOfDay: Int, bedMinuteOfDay: Int): Int {
        val cap = capPercent.coerceIn(MIN_DIMMING, 100)
        val night = bedMinuteOfDay != NIGHT_UNTIL_MINUTE &&
            isWithinDailyWindow(bedMinuteOfDay, NIGHT_UNTIL_MINUTE, minuteOfDay)
        return if (night) (cap / 2).coerceAtLeast(MIN_DIMMING) else cap
    }

    /** Every light already set up that can follow the art, in a stable order (WiZ, Govee LAN, Govee cloud). */
    fun bulbs(beat: BeatLightsSettings, lights: LightsSettings): List<ShelfArtBulb> = buildList {
        if (beat.hasWiz) {
            val ip = beat.wizAddress.trim()
            add(ShelfArtBulb(id = "wiz:$ip", label = "WiZ · $ip", kind = ShelfArtBulbKind.Wiz, address = ip))
        }
        beat.goveeFound.forEach { light ->
            add(
                ShelfArtBulb(
                    id = "goveelan:${light.id}",
                    label = "Govee ${light.sku} · ${light.ip}",
                    kind = ShelfArtBulbKind.GoveeLan,
                    address = light.ip,
                    sku = light.sku,
                ),
            )
        }
        if (lights.hasGoveeKey) {
            lights.goveeDevices.forEach { device ->
                add(
                    ShelfArtBulb(
                        id = "govee:${device.deviceId}",
                        label = "${device.name} (${device.sku}, cloud)",
                        kind = ShelfArtBulbKind.GoveeCloud,
                        address = device.deviceId,
                        sku = device.sku,
                        name = device.name,
                    ),
                )
            }
        }
    }

    // ---- WiZ (UDP 38899) ----

    fun wizColourBody(colour: ShelfBulbColour, dimming: Int): String {
        val d = dimming.coerceIn(MIN_DIMMING, 100)
        return if (colour.kelvin != null) {
            """{"method":"setPilot","params":{"state":true,"temp":${colour.kelvin},"dimming":$d}}"""
        } else {
            """{"method":"setPilot","params":{"state":true,"r":${colour.r},"g":${colour.g},"b":${colour.b},"dimming":$d}}"""
        }
    }

    /** The `result` of a getPilot reply; null when it is not one. */
    fun wizPilotOf(reply: String?): WizPilot? {
        if (reply.isNullOrBlank()) return null
        val result = runCatching { json.parseToJsonElement(reply) as? JsonObject }.getOrNull()
            ?.get("result") as? JsonObject ?: return null
        fun int(key: String) = (result[key] as? JsonPrimitive)?.intOrNull
        val state = (result["state"] as? JsonPrimitive)?.booleanOrNull ?: return null
        return WizPilot(
            on = state,
            sceneId = int("sceneId") ?: 0,
            r = int("r"),
            g = int("g"),
            b = int("b"),
            c = int("c"),
            w = int("w"),
            temp = int("temp")?.takeIf { it > 0 },
            dimming = int("dimming"),
            speed = int("speed"),
        )
    }

    /** The setPilot that puts a bulb back as [pilot] was. */
    fun wizRestoreBody(pilot: WizPilot): String {
        if (!pilot.on) return BeatLightsRules.WIZ_OFF
        val parts = mutableListOf("\"state\":true")
        when {
            pilot.sceneId > 0 -> {
                parts += "\"sceneId\":${pilot.sceneId}"
                pilot.speed?.let { parts += "\"speed\":$it" }
            }
            pilot.temp != null -> parts += "\"temp\":${pilot.temp}"
            pilot.r != null && pilot.g != null && pilot.b != null -> {
                parts += "\"r\":${pilot.r}"
                parts += "\"g\":${pilot.g}"
                parts += "\"b\":${pilot.b}"
                pilot.c?.let { parts += "\"c\":$it" }
                pilot.w?.let { parts += "\"w\":$it" }
            }
        }
        pilot.dimming?.let { parts += "\"dimming\":${it.coerceIn(MIN_DIMMING, 100)}" }
        return """{"method":"setPilot","params":{${parts.joinToString(",")}}}"""
    }

    // ---- Govee LAN (commands to 4003, answers to 4002) ----

    fun goveeLanStatusOf(reply: String?): GoveeLanStatus? {
        if (reply.isNullOrBlank()) return null
        val msg = runCatching { json.parseToJsonElement(reply) as? JsonObject }.getOrNull()
            ?.get("msg") as? JsonObject ?: return null
        if ((msg["cmd"] as? JsonPrimitive)?.content != "devStatus") return null
        val data = msg["data"] as? JsonObject ?: return null
        fun int(obj: JsonObject?, key: String) = (obj?.get(key) as? JsonPrimitive)?.intOrNull
        val color = data["color"] as? JsonObject
        return GoveeLanStatus(
            on = (int(data, "onOff") ?: return null) == 1,
            brightness = (int(data, "brightness") ?: 100).coerceIn(1, 100),
            r = int(color, "r") ?: 255,
            g = int(color, "g") ?: 255,
            b = int(color, "b") ?: 255,
            kelvin = int(data, "colorTemInKelvin") ?: 0,
        )
    }

    fun goveeLanTurn(on: Boolean): String = """{"msg":{"cmd":"turn","data":{"value":${if (on) 1 else 0}}}}"""

    fun goveeLanBrightness(percent: Int): String =
        """{"msg":{"cmd":"brightness","data":{"value":${percent.coerceIn(1, 100)}}}}"""

    fun goveeLanColour(colour: ShelfBulbColour): String {
        val k = colour.kelvin ?: 0
        return """{"msg":{"cmd":"colorwc","data":{"color":{"r":${colour.r},"g":${colour.g},"b":${colour.b}},"colorTemInKelvin":$k}}}"""
    }

    /** The commands that put a Govee LAN light back as [status] was. */
    fun goveeLanRestore(status: GoveeLanStatus): List<String> {
        if (!status.on) return listOf(goveeLanTurn(false))
        val colour = ShelfBulbColour(status.r, status.g, status.b, kelvin = status.kelvin.takeIf { it > 0 })
        return listOf(goveeLanTurn(true), goveeLanColour(colour), goveeLanBrightness(status.brightness))
    }
}

/** fork:shelf-home v3 - UDP to the bulbs (the desktop sends; see ShelfArtLightsWire.desktop.kt). */
internal expect object ShelfArtLightsWire {
    /** One datagram, fire and forget. */
    fun send(address: String, port: Int, body: String)

    /**
     * Sends [body] and waits up to [timeoutMs] for the light's answer: on the sending socket, or on local port [replyPort]
     * when the light answers there (Govee LAN). Null when nothing came. Blocks the caller.
     */
    fun ask(address: String, port: Int, body: String, replyPort: Int?, timeoutMs: Int): String?
}

/**
 * fork:shelf-home v3 phase 7 - the engine. [show] is called by the shelf home with the accent while browsing and in the
 * gallery (null: keep what is lit); [release] when the home is left, covered, or the setting goes off. A player
 * starting releases it too, through [LightsController.onPlaybackStarting], before Lights out captures the room.
 */
object ShelfArtLights {
    private val log = Logger.withTag("ShelfArtLights")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _lit = MutableStateFlow<ShelfArtLit?>(null)

    /** What the room shows now (null: the room is its own), for "● LIGHTS · DUSK ROSE" under the clock. */
    val lit: StateFlow<ShelfArtLit?> = _lit.asStateFlow()

    // Guarded by [lock].
    private var hooked = false
    private var target: ShelfBulbColour? = null
    private var targetColor: Color? = null
    private var targetSince = 0L
    private var lastSent = 0L
    private var sent: ShelfBulbColour? = null
    private var touched = false
    private var pending: Job? = null

    /** Bumped by every release: a fade still running sees it and stops, so the restore is not overwritten. */
    @Volatile
    private var generation = 0

    // Worker-owned: only read or written inside LightsController.room blocks, which run one at a time.
    private val captured = linkedMapOf<String, Capture>()
    private val cloudLevel = mutableMapOf<String, Int>()

    private sealed interface Capture {
        val bulb: ShelfArtBulb

        class Wiz(override val bulb: ShelfArtBulb, val pilot: WizPilot) : Capture
        class Lan(override val bulb: ShelfArtBulb, val status: GoveeLanStatus) : Capture
        class Cloud(override val bulb: ShelfArtBulb, val state: GoveeDeviceState) : Capture
    }

    private const val ASK_TIMEOUT_MS = 800
    private const val CLOUD_GAP_MS = 550L
    private const val LAN_GAP_MS = 60L

    /** The art's colour now; null keeps what is lit (Info, Shelves, art still loading). */
    fun show(color: Color?) {
        hook()
        if (color == null) return
        if (!allowed()) {
            release()
            return
        }
        val next = ShelfArtLightsRules.bulbColour(color)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val aim = target
            // The accent eases over 600 ms: a small step is the same colour still arriving, not a new one.
            val same = aim != null && ShelfArtLightsRules.close(aim, next)
            target = next
            targetColor = color
            if (same && pending?.isActive == true) return // the wait under way sends the latest
            val was = sent
            if (same && was != null && ShelfArtLightsRules.close(was, next)) return
            if (!same) targetSince = now
            pending?.cancel()
            val wait = ShelfArtLightsRules.waitMs(now, targetSince, lastSent)
            pending = scope.launch {
                delay(wait)
                send()
            }
        }
    }

    /** Puts the room back as it was before the art touched it; nothing when it never did. */
    fun release() {
        val wasTouched = synchronized(lock) {
            pending?.cancel()
            pending = null
            target = null
            targetColor = null
            sent = null
            generation++
            touched.also { touched = false }
        }
        _lit.value = null
        if (wasTouched) LightsController.room("art lights: restore") { restoreAll() }
    }

    private fun hook() {
        synchronized(lock) {
            if (hooked) return
            hooked = true
        }
        LightsSettingsRepository.ensureLoaded()
        TonightSettingsRepository.ensureLoaded()
        LightsController.onPlaybackStarting { release() }
    }

    private fun allowed(): Boolean =
        ShelfExtrasRepository.state.value.artLights &&
            !LightsController.playbackUp.value &&
            LightsSettingsRepository.snapshot().artBulbs.isNotEmpty()

    private fun send() {
        val lights = LightsSettingsRepository.snapshot()
        val beat = BeatLightsSettingsRepository.state.value
        val bulbs = ShelfArtLightsRules.bulbs(beat, lights)
            .filter { it.id in lights.artBulbs && !beatHolds(it, beat, lights) }
        class Go(val from: ShelfBulbColour?, val to: ShelfBulbColour, val color: Color, val gen: Int)
        val go = synchronized(lock) {
            val to = target
            val color = targetColor
            val was = sent
            when {
                !allowed() || bulbs.isEmpty() || to == null || color == null -> null
                was != null && ShelfArtLightsRules.close(was, to) -> null
                else -> {
                    sent = to
                    lastSent = System.currentTimeMillis()
                    touched = true
                    Go(was, to, color, generation)
                }
            }
        } ?: return
        val from = go.from
        val to = go.to
        val color = go.color
        val gen = go.gen
        val now = java.time.LocalTime.now()
        val level = ShelfArtLightsRules.brightness(
            capPercent = lights.artCapPercent,
            minuteOfDay = now.hour * 60 + now.minute,
            bedMinuteOfDay = TonightSettingsRepository.state.value.bedMinuteOfDay,
        )
        val name = ShelfArtLightsRules.colourName(color)
        _lit.value = ShelfArtLit(name, color)
        LightsController.room("art lights: $name") { paint(bulbs, from, to, level, gen, lights) }
    }

    /** The beat lights own a light while the music plays (beat lights on, or Lights out's "Music: beat lights"). */
    private fun beatHolds(bulb: ShelfArtBulb, beat: BeatLightsSettings, lights: LightsSettings): Boolean {
        val beatOn = beat.enabled || lights.musicTurnsOnBeatLights
        if (!beatOn) return false
        val owned = when (bulb.kind) {
            ShelfArtBulbKind.Wiz -> beat.hasWiz && beat.wizAddress.trim() == bulb.address
            ShelfArtBulbKind.GoveeLan -> beat.goveeLights.any { it.ip == bulb.address }
            ShelfArtBulbKind.GoveeCloud -> false
        }
        if (!owned) return false
        return runCatching { MusicPlayback.player.state.value.isPlaying }.getOrDefault(false)
    }

    private suspend fun paint(
        bulbs: List<ShelfArtBulb>,
        from: ShelfBulbColour?,
        to: ShelfBulbColour,
        level: Int,
        gen: Int,
        lights: LightsSettings,
    ) {
        if (gen != generation) return
        // First touch of a light: remember how it was. A light that does not answer is left out (and so never changed).
        val fresh = bulbs.filter { it.id !in captured }
        if (fresh.isNotEmpty()) {
            val got = coroutineScope { fresh.map { bulb -> async { capture(bulb, lights) } }.map { it.await() } }
            got.filterNotNull().forEach { captured[it.bulb.id] = it }
            if (gen != generation) return
        }
        val live = bulbs.mapNotNull { captured[it.id] }
        if (live.isEmpty()) return
        val firstTouch = live.associate { it.bulb.id to (it.bulb in fresh) }
        coroutineScope {
            // The cloud jumps once per colour (Govee's 10k a day); brightness only when it changes.
            val cloud = live.filterIsInstance<Capture.Cloud>()
            if (cloud.isNotEmpty()) launch { cloud.forEach { cap -> setCloud(cap, to, level, lights) } }
            val lan = live.filter { it !is Capture.Cloud }
            if (lan.isEmpty()) return@coroutineScope
            // A light seen for the first time jumps to the colour (its old colour is not the art's).
            val steps = ShelfArtLightsRules.fade(from, to)
            val stepMs = if (steps.size > 1) ShelfArtLightsRules.FADE_MS / steps.size else 0L
            steps.forEachIndexed { i, step ->
                if (gen != generation) return@coroutineScope
                lan.forEach { cap ->
                    val colour = if (firstTouch[cap.bulb.id] == true) to else step
                    if (firstTouch[cap.bulb.id] == true && i > 0) return@forEach
                    sendLan(cap, colour, level, withBrightness = i == 0)
                }
                if (stepMs > 0 && i < steps.lastIndex) delay(stepMs)
            }
        }
    }

    private fun sendLan(cap: Capture, colour: ShelfBulbColour, level: Int, withBrightness: Boolean) {
        val bulb = cap.bulb
        runCatching {
            when (cap) {
                is Capture.Wiz -> ShelfArtLightsWire.send(bulb.address, BeatLightsRules.WIZ_PORT, ShelfArtLightsRules.wizColourBody(colour, level))
                is Capture.Lan -> {
                    val port = BeatLightsRules.GOVEE_COMMAND_PORT
                    if (withBrightness) {
                        if (!cap.status.on) ShelfArtLightsWire.send(bulb.address, port, ShelfArtLightsRules.goveeLanTurn(true))
                        ShelfArtLightsWire.send(bulb.address, port, ShelfArtLightsRules.goveeLanBrightness(level))
                    }
                    ShelfArtLightsWire.send(bulb.address, port, ShelfArtLightsRules.goveeLanColour(colour))
                }
                is Capture.Cloud -> Unit
            }
        }.onFailure { log.w { "Could not set ${bulb.label}: ${it.message}" } }
    }

    private suspend fun setCloud(cap: Capture.Cloud, colour: ShelfBulbColour, level: Int, lights: LightsSettings) {
        val apiKey = lights.goveeApiKey
        if (apiKey.isBlank()) return
        val device = GoveeDevice(sku = cap.bulb.sku, deviceId = cap.bulb.address, name = cap.bulb.name)
        if (!cap.state.powerOn && cap.bulb.id !in cloudLevel) {
            GoveeClient.setPower(apiKey, device, on = true).logFailure("on", cap.bulb)
            delay(CLOUD_GAP_MS)
        }
        val kelvin = colour.kelvin
        if (kelvin != null) {
            GoveeClient.setColorTemperature(apiKey, device, kelvin).logFailure("white", cap.bulb)
        } else {
            GoveeClient.setColorRgb(apiKey, device, colour.rgb).logFailure("colour", cap.bulb)
        }
        if (cloudLevel[cap.bulb.id] != level) {
            delay(CLOUD_GAP_MS)
            GoveeClient.setBrightness(apiKey, device, level).logFailure("brightness", cap.bulb)
            cloudLevel[cap.bulb.id] = level
        }
    }

    private suspend fun capture(bulb: ShelfArtBulb, lights: LightsSettings): Capture? = runCatching {
        when (bulb.kind) {
            ShelfArtBulbKind.Wiz -> ShelfArtLightsRules.wizPilotOf(
                ShelfArtLightsWire.ask(bulb.address, BeatLightsRules.WIZ_PORT, ShelfArtLightsRules.WIZ_GET_PILOT, null, ASK_TIMEOUT_MS),
            )?.let { Capture.Wiz(bulb, it) }
            ShelfArtBulbKind.GoveeLan -> ShelfArtLightsRules.goveeLanStatusOf(
                ShelfArtLightsWire.ask(
                    bulb.address,
                    BeatLightsRules.GOVEE_COMMAND_PORT,
                    ShelfArtLightsRules.GOVEE_DEV_STATUS,
                    BeatLightsRules.GOVEE_REPLY_PORT,
                    ASK_TIMEOUT_MS,
                ),
            )?.let { Capture.Lan(bulb, it) }
            ShelfArtBulbKind.GoveeCloud -> {
                if (lights.goveeApiKey.isBlank()) return@runCatching null
                val device = GoveeDevice(sku = bulb.sku, deviceId = bulb.address, name = bulb.name)
                when (val result = GoveeClient.readState(lights.goveeApiKey, device)) {
                    is GoveeResult.Ok -> result.value.takeIf { it.online }?.let { Capture.Cloud(bulb, it) }
                    else -> null
                }
            }
        }
    }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        .also { if (it == null) log.w { "${bulb.label} did not answer; leaving it out" } }

    private suspend fun restoreAll() {
        val all = captured.values.toList()
        captured.clear()
        val levels = cloudLevel.toMap()
        cloudLevel.clear()
        if (all.isEmpty()) return
        log.i { "Handing back ${all.size} light(s)" }
        val apiKey = LightsSettingsRepository.snapshot().goveeApiKey
        coroutineScope {
            all.forEach { cap ->
                launch {
                    runCatching {
                        when (cap) {
                            is Capture.Wiz -> ShelfArtLightsWire.send(
                                cap.bulb.address,
                                BeatLightsRules.WIZ_PORT,
                                ShelfArtLightsRules.wizRestoreBody(cap.pilot),
                            )
                            is Capture.Lan -> ShelfArtLightsRules.goveeLanRestore(cap.status).forEach { body ->
                                ShelfArtLightsWire.send(cap.bulb.address, BeatLightsRules.GOVEE_COMMAND_PORT, body)
                                delay(LAN_GAP_MS)
                            }
                            is Capture.Cloud -> restoreCloud(cap, apiKey, touchedLevel = cap.bulb.id in levels)
                        }
                    }.onFailure { if (it is CancellationException) throw it; log.w { "Could not restore ${cap.bulb.label}: ${it.message}" } }
                }
            }
        }
    }

    private suspend fun restoreCloud(cap: Capture.Cloud, apiKey: String, touchedLevel: Boolean) {
        if (apiKey.isBlank()) return
        val device = GoveeDevice(sku = cap.bulb.sku, deviceId = cap.bulb.address, name = cap.bulb.name)
        val state = cap.state
        if (!state.powerOn) {
            GoveeClient.setPower(apiKey, device, on = false).logFailure("off", cap.bulb)
            return
        }
        val kelvin = state.kelvin
        val rgb = state.colorRgb
        when {
            kelvin != null -> GoveeClient.setColorTemperature(apiKey, device, kelvin).logFailure("white", cap.bulb)
            rgb != null -> GoveeClient.setColorRgb(apiKey, device, rgb).logFailure("colour", cap.bulb)
        }
        val brightness = state.brightness
        if (touchedLevel && brightness != null) {
            delay(CLOUD_GAP_MS)
            GoveeClient.setBrightness(apiKey, device, brightness).logFailure("brightness", cap.bulb)
        }
    }

    private fun GoveeResult<Unit>.logFailure(what: String, bulb: ShelfArtBulb) {
        when (this) {
            is GoveeResult.Ok -> Unit
            is GoveeResult.Unauthorized -> log.w { "Govee rejected the API key ($what ${bulb.label}): $detail" }
            is GoveeResult.Failed -> log.w { "Could not set $what on ${bulb.label}: $detail" }
        }
    }
}
