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
        if (!playing || map == null) {
            cursor.reset()
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
        if (hits.isNotEmpty()) flash(settings)
        return LOOK_EVERY_MS
    }

    /** Light up now, drop back after the flash time. Fire and forget: a lost packet is one missed beat. */
    private fun flash(settings: BeatLightsSettings) {
        val address = settings.wizAddress
        send(address, BeatLightsRules.wizBrightness(settings.flashPercent))
        val drop = dropper ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "beat-lights-drop").apply { isDaemon = true }
        }.also { dropper = it }
        drop.schedule({ send(address, BeatLightsRules.wizBrightness(settings.restPercent)) }, settings.flashMs.toLong(), TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun send(address: String, body: String) {
        runCatching {
            val s = socket ?: DatagramSocket().also { socket = it }
            val bytes = body.toByteArray()
            s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(address), BeatLightsRules.WIZ_PORT))
        }.onFailure { log.w { "send to $address failed: ${it.message}" } }
    }
}
