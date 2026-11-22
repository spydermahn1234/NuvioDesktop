package com.nuvio.app.features.music.beatlights
// FORK (Spyder): Nick's beat lights, not in UmbraProjects. See CLAUDE.md.

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.music.MusicPlayback
import com.nuvio.app.features.music.visualiser.TrackMapLive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val log = Logger.withTag("BeatLights")

/**
 * fork:beat-lights - the desktop side: loads the settings, then on its own thread looks at the music's playhead every
 * few milliseconds and flashes the WiZ bulb on each hit the track map holds (see BeatLights.kt). It sleeps while the
 * setting is off, nothing plays, or the track has no map yet.
 */
object BeatLightsEngine {
    private const val LOOK_EVERY_MS = 4L
    private const val IDLE_MS = 250L

    @Volatile
    private var started = false
    private val cursor = BeatLightsCursor()
    private var socket: DatagramSocket? = null
    private var dropper: ScheduledExecutorService? = null

    // What the last ten seconds did, for nuvio.log: tells a missed kick in the map from a command the bulb dropped.
    private const val REPORT_EVERY_MS = 10_000L
    private val sent = AtomicInteger()
    private val answered = AtomicInteger()
    private var fired = 0
    private var reportFromNanos = 0L
    private var reportFromTrackMs = -1L
    private var noMapLoggedFor = Long.MIN_VALUE

    // The player reports its position a few times a second: when the reported value last changed, on this clock.
    private var reportedMs = Long.MIN_VALUE
    private var reportedAtNanos = 0L

    fun start() {
        if (started) return
        started = true
        Thread({ loop() }, "beat-lights").apply { isDaemon = true; priority = Thread.MAX_PRIORITY - 1 }.start()
    }

    private fun loop() {
        // Off the UI thread: a store's first open can block for seconds on this PC (see DesktopStorage.preload).
        val store = DesktopStorage.store("nuvio_beat_lights")
        BeatLightsSettingsRepository.load(store.getString("settings"))
        BeatLightsSettingsRepository.persist = { store.putString("settings", it) }
        while (true) {
            val slept = runCatching { look() }.onFailure { log.w(it) { "look failed" } }.getOrDefault(IDLE_MS)
            Thread.sleep(slept)
        }
    }

    /** One look; returns how long to sleep before the next. */
    private fun look(): Long {
        val settings = BeatLightsSettingsRepository.state.value
        if (!settings.ready) {
            cursor.reset()
            return IDLE_MS
        }
        val player = MusicPlayback.player.state.value
        val live = TrackMapLive.state.value
        val map = live?.takeIf { it.loadId == player.loadId }?.map
        val playing = player.playWhenReady && !player.isLoading && player.track != null
        if (playing && map == null && noMapLoggedFor != player.loadId) {
            noMapLoggedFor = player.loadId
            log.i { "no track map for load ${player.loadId} (map is for ${live?.loadId}, unavailable=${live?.unavailable}): no flashes" }
        }
        if (!playing || map == null) {
            cursor.reset()
            reportFromTrackMs = -1L
            reportedMs = Long.MIN_VALUE
            return if (player.track == null) IDLE_MS else 20L
        }
        val now = System.nanoTime()
        if (player.positionMs != reportedMs) {
            reportedMs = player.positionMs
            reportedAtNanos = now
        }
        val playhead = BeatLightsRules.playheadMs(reportedMs, (now - reportedAtNanos) / 1_000_000L, playing = true)
        val hits = cursor.advance(player.loadId, map, (playhead + settings.earlyMs).toDouble(), settings.on)
        if (hits.isNotEmpty()) {
            fired++
            flash(settings)
        }
        report(now, playhead, map, settings)
        return LOOK_EVERY_MS
    }

    private fun report(now: Long, playhead: Long, map: com.nuvio.app.features.music.visualiser.TrackMap, settings: BeatLightsSettings) {
        if (reportFromTrackMs < 0) {
            reportFromTrackMs = playhead
            reportFromNanos = now
            fired = 0; sent.set(0); answered.set(0)
            return
        }
        if ((now - reportFromNanos) / 1_000_000L < REPORT_EVERY_MS) return
        val kicks = BeatLightsRules.hitsBetween(map, reportFromTrackMs.toDouble(), playhead.toDouble(), BeatLightsOn.Kick).size
        val beats = map.beat?.beatsMs?.count { it > reportFromTrackMs && it <= playhead }
        log.i {
            "track ${reportFromTrackMs / 1000}-${playhead / 1000}s on=${settings.on} style=${settings.style}: fired $fired flashes; map has $kicks kicks" +
                " and ${beats ?: "no"} beats there (analysed to ${map.analysedMs / 1000}s); sent ${sent.get()} commands, bulb answered ${answered.get()}; playhead jumps ${cursor.jumps}"
        }
        reportFromTrackMs = playhead
        reportFromNanos = now
        fired = 0; sent.set(0); answered.set(0); cursor.jumps = 0
    }

    // Swap: whether the next hit turns the light on.
    private var swapOn = true

    /** One hit on the bulb, in the chosen style. Fire and forget: a lost packet is one missed beat. */
    private fun flash(settings: BeatLightsSettings) {
        val address = settings.wizAddress
        val on = BeatLightsRules.wizBrightness(settings.flashPercent)
        when (settings.style) {
            BeatLightsStyle.Swap -> {
                send(address, if (swapOn) on else BeatLightsRules.WIZ_OFF)
                swapOn = !swapOn
            }
            BeatLightsStyle.Strobe -> {
                send(address, on)
                later(settings.flashMs) { send(address, BeatLightsRules.WIZ_OFF) }
            }
            BeatLightsStyle.Dim -> {
                send(address, on)
                later(settings.flashMs) { send(address, BeatLightsRules.wizBrightness(settings.restPercent)) }
            }
        }
    }

    private fun later(ms: Int, action: () -> Unit) {
        val drop = dropper ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "beat-lights-drop").apply { isDaemon = true }
        }.also { dropper = it }
        drop.schedule(Runnable { action() }, ms.toLong(), TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun send(address: String, body: String) {
        runCatching {
            val s = socket ?: DatagramSocket().also { socket = it; listen(it) }
            val bytes = body.toByteArray()
            s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(address), BeatLightsRules.WIZ_PORT))
            sent.incrementAndGet()
        }.onFailure { log.w { "send to $address failed: ${it.message}" } }
    }

    /** Counts the bulb's replies ({"method":"setPilot",...,"result":{"success":true}}) for the ten-second report. */
    private fun listen(socket: DatagramSocket) {
        Thread({
            val buffer = ByteArray(1024)
            while (!socket.isClosed) {
                runCatching {
                    socket.receive(DatagramPacket(buffer, buffer.size))
                    answered.incrementAndGet()
                }
            }
        }, "beat-lights-replies").apply { isDaemon = true }.start()
    }
}
