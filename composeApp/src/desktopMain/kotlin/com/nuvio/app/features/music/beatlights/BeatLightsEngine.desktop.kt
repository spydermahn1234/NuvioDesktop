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
    private val goveeSent = AtomicInteger()
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
        Thread({ remote() }, "beat-lights-remote").apply { isDaemon = true }.start()
    }

    /**
     * Switchboard on the phone switches beat lights on and off (see [BeatLightsRules.REMOTE_PORT]). Only lines from the
     * home network (private addresses) are taken.
     */
    private fun remote() {
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(BeatLightsRules.REMOTE_PORT))
            }
        }.getOrElse {
            log.w { "Switchboard control unavailable: port ${BeatLightsRules.REMOTE_PORT} is taken (${it.message})" }
            return
        }
        val pcName = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("PC")
        log.i { "Switchboard control listening on UDP ${BeatLightsRules.REMOTE_PORT}" }
        val buffer = ByteArray(512)
        while (true) {
            runCatching {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val from = packet.address
                if (!(from.isSiteLocalAddress || from.isLoopbackAddress || from.isLinkLocalAddress)) return@runCatching
                val ask = BeatLightsRules.remoteAsk(String(packet.data, 0, packet.length)) ?: return@runCatching
                if (ask.on != null) {
                    val was = BeatLightsSettingsRepository.state.value.enabled
                    BeatLightsSettingsRepository.setEnabled(ask.on)
                    if (was != ask.on) log.i { "Switchboard (${from.hostAddress}) turned beat lights ${if (ask.on) "on" else "off"}" }
                }
                val reply = BeatLightsRules.remoteStatus(BeatLightsSettingsRepository.state.value, pcName).toByteArray()
                socket.send(DatagramPacket(reply, reply.size, packet.socketAddress))
            }.onFailure { log.w { "Switchboard line failed: ${it.message}" } }
        }
    }

    private fun loop() {
        // Off the UI thread: a store's first open can block for seconds on this PC (see DesktopStorage.preload).
        val store = DesktopStorage.store("nuvio_beat_lights")
        BeatLightsSettingsRepository.load(store.getString("settings"))
        BeatLightsSettingsRepository.persist = { store.putString("settings", it) }
        BeatLightsSettingsRepository.scanner = { Thread({ scanGovee() }, "beat-lights-govee-scan").apply { isDaemon = true }.start() }
        while (true) {
            val slept = runCatching { look() }.onFailure { log.w(it) { "look failed" } }.getOrDefault(IDLE_MS)
            Thread.sleep(slept)
        }
    }

    /** One look; returns how long to sleep before the next. */
    private fun look(): Long {
        val own = BeatLightsSettingsRepository.state.value
        // fork:lights-per-content - Lights out's "Music: beat lights" switches them on for music whatever their own switch says.
        val settings = if (com.nuvio.app.features.lights.LightsSettingsRepository.uiState.value.musicTurnsOnBeatLights) own.copy(enabled = true) else own
        if (!settings.ready) {
            cursor.reset()
            releaseGovee()
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
            releaseGovee()
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
        holdGovee(settings)
        keepGovee(now)
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
            fired = 0; sent.set(0); answered.set(0); goveeSent.set(0)
            return
        }
        if ((now - reportFromNanos) / 1_000_000L < REPORT_EVERY_MS) return
        val kicks = BeatLightsRules.hitsBetween(map, reportFromTrackMs.toDouble(), playhead.toDouble(), BeatLightsOn.Kick).size
        val beats = map.beat?.beatsMs?.count { it > reportFromTrackMs && it <= playhead }
        log.i {
            "track ${reportFromTrackMs / 1000}-${playhead / 1000}s on=${settings.on} style=${settings.style}: fired $fired flashes; map has $kicks kicks" +
                " and ${beats ?: "no"} beats there (analysed to ${map.analysedMs / 1000}s); WiZ: sent ${sent.get()}, answered ${answered.get()}; Govee: sent ${goveeSent.get()} to ${settings.goveeLights.size} lights; playhead jumps ${cursor.jumps}"
        }
        reportFromTrackMs = playhead
        reportFromNanos = now
        fired = 0; sent.set(0); answered.set(0); goveeSent.set(0); cursor.jumps = 0
    }

    // Swap: whether the next hit turns the light on.
    private var swapOn = true

    /** One hit on the bulb, in the chosen style. Fire and forget: a lost packet is one missed beat. */
    private fun flash(settings: BeatLightsSettings) {
        val address = settings.wizAddress.takeIf { settings.hasWiz }
        val on = BeatLightsRules.wizBrightness(settings.flashPercent)
        val govee = settings.goveeLights
        val goveeOn = BeatLightsRules.goveeWhite(255 * settings.flashPercent / 100)
        val goveeRest = BeatLightsRules.goveeWhite(goveeRestLevel(settings))
        fun wiz(body: String) { if (address != null) send(address, body) }
        fun gov(packet: ByteArray) {
            goveeFrame = packet
            govee.forEach { sendGovee(it.ip, BeatLightsRules.goveeRazerJson(packet)) }
            goveeSentNanos = System.nanoTime()
        }
        when (settings.style) {
            BeatLightsStyle.Swap -> {
                wiz(if (swapOn) on else BeatLightsRules.WIZ_OFF)
                gov(if (swapOn) goveeOn else goveeRest)
                swapOn = !swapOn
            }
            BeatLightsStyle.Strobe -> {
                wiz(on); gov(goveeOn)
                later(settings.flashMs) { wiz(BeatLightsRules.WIZ_OFF); gov(goveeRest) }
            }
            BeatLightsStyle.Dim -> {
                wiz(on); gov(goveeOn)
                later(settings.flashMs) { wiz(BeatLightsRules.wizBrightness(settings.restPercent)); gov(goveeRest) }
            }
        }
    }

    // ---- Govee over the LAN, in real-time mode ----

    private const val GOVEE_REFRESH_MS = 5_000L
    // The lights in real-time mode now (by address), and when it was last asked for: asked again every few seconds, so
    // a light that was busy (DreamView, or switched off by Switchboard's hand-over) is taken as soon as it is free.
    private val goveeLive = mutableSetOf<String>()
    private var goveeAskedNanos = 0L

    // The colour the Govee lights should show now, sent again every [GOVEE_FRAME_MS]: a floor lamp (H6076) leaves
    // real-time mode within a moment of its last frame and fades back to its own white (Nov 22 video), so between hits
    // it was warm white instead of dark. Streaming frames, as Govee's own desktop app does, holds it.
    private const val GOVEE_FRAME_MS = 80L
    @Volatile private var goveeFrame: ByteArray? = null
    @Volatile private var goveeSentNanos = 0L

    private fun keepGovee(now: Long) {
        val frame = goveeFrame ?: return
        if (goveeLive.isEmpty() || (now - goveeSentNanos) / 1_000_000L < GOVEE_FRAME_MS) return
        val json = BeatLightsRules.goveeRazerJson(frame)
        for (ip in goveeLive) sendGovee(ip, json)
        goveeSentNanos = now
    }

    private fun goveeRestLevel(settings: BeatLightsSettings): Int =
        if (settings.style == BeatLightsStyle.Dim) 255 * settings.restPercent / 100 else 0

    /** Puts the picked Govee lights into real-time mode (on, at the resting colour), and lets go of any unpicked. */
    private fun holdGovee(settings: BeatLightsSettings) {
        val want = settings.goveeLights.map { it.ip }.toSet()
        val now = System.nanoTime()
        val refresh = (now - goveeAskedNanos) / 1_000_000L > GOVEE_REFRESH_MS
        for (ip in goveeLive - want) sendGovee(ip, BeatLightsRules.goveeRazerJson(BeatLightsRules.goveeRealtime(false)))
        goveeLive.retainAll(want)
        val start = if (refresh) want else want - goveeLive
        if (start.isEmpty()) return
        for (ip in start) {
            // Every refresh too: Switchboard hands the lights over by switching them off through Govee's cloud, which
            // can land after this; the next refresh switches them back on (Nov 22).
            sendGovee(ip, BeatLightsRules.GOVEE_TURN_ON)
            sendGovee(ip, BeatLightsRules.goveeRazerJson(BeatLightsRules.goveeRealtime(true)))
            if (ip !in goveeLive) {
                goveeFrame = BeatLightsRules.goveeWhite(goveeRestLevel(settings))
                sendGovee(ip, BeatLightsRules.goveeRazerJson(goveeFrame!!))
            }
        }
        if (start.any { it !in goveeLive }) log.i { "Govee real-time on for ${start - goveeLive}" }
        goveeLive += start
        goveeAskedNanos = now
    }

    /** Hands the Govee lights back to what they were doing (music paused, beat lights off). */
    private fun releaseGovee() {
        if (goveeLive.isEmpty()) return
        for (ip in goveeLive) sendGovee(ip, BeatLightsRules.goveeRazerJson(BeatLightsRules.goveeRealtime(false)))
        log.i { "Govee real-time off for $goveeLive" }
        goveeLive.clear()
        goveeFrame = null
    }

    private fun sendGovee(ip: String, body: String) = send(ip, body, BeatLightsRules.GOVEE_COMMAND_PORT)

    /** Asks the network for Govee lights with LAN Control on; answers come back to port 4002 for about two seconds. */
    private fun scanGovee() {
        val found = linkedMapOf<String, GoveeLanLight>()
        val result = runCatching {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(BeatLightsRules.GOVEE_REPLY_PORT))
                socket.soTimeout = 300
                val ask = BeatLightsRules.GOVEE_SCAN.toByteArray()
                val group = InetAddress.getByName(BeatLightsRules.GOVEE_SCAN_ADDRESS)
                val until = System.nanoTime() + 2_500_000_000L
                var asked = 0
                val buffer = ByteArray(2048)
                while (System.nanoTime() < until) {
                    if (asked < 3) {
                        // On every network the PC is on: a plain send goes out of one adapter only, and on a PC with
                        // VMware's adapters (192.168.87.x, 192.168.160.x) that was not the home Wi-Fi (Nov 22).
                        for (nic in scanAdapters()) {
                            runCatching {
                                java.net.MulticastSocket().use { out ->
                                    out.networkInterface = nic
                                    out.timeToLive = 2
                                    out.send(DatagramPacket(ask, ask.size, group, BeatLightsRules.GOVEE_SCAN_PORT))
                                }
                            }.onFailure { if (asked == 0) log.w { "Govee scan could not ask on ${nic.displayName}: ${it.message}" } }
                        }
                        asked++
                    }
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        BeatLightsRules.goveeScanReply(text)?.let { found[it.id] = it }
                    } catch (_: java.net.SocketTimeoutException) {
                    }
                }
            }
        }
        result.onFailure { log.w(it) { "Govee scan failed" } }
        log.i { "Govee scan found ${found.size}: ${found.values.joinToString { "${it.sku} ${it.ip}" }}" }
        if (result.isFailure) {
            BeatLightsSettingsRepository.scanDone(null, result.exceptionOrNull()?.message)
        } else {
            BeatLightsSettingsRepository.scanDone(found.values.toList())
        }
    }

    /** The adapters that are up, can multicast and have an IPv4 address (loopback left out). */
    private fun scanAdapters(): List<java.net.NetworkInterface> =
        java.net.NetworkInterface.networkInterfaces().toList().filter { nic ->
            runCatching {
                nic.isUp && !nic.isLoopback && nic.supportsMulticast() &&
                    nic.inetAddresses().toList().any { it is java.net.Inet4Address }
            }.getOrDefault(false)
        }.also { list -> log.i { "Govee scan asks on: ${list.joinToString { it.displayName }}" } }

    private fun later(ms: Int, action: () -> Unit) {
        val drop = dropper ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "beat-lights-drop").apply { isDaemon = true }
        }.also { dropper = it }
        drop.schedule(Runnable { action() }, ms.toLong(), TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun send(address: String, body: String, port: Int = BeatLightsRules.WIZ_PORT) {
        runCatching {
            val s = socket ?: DatagramSocket().also { socket = it; listen(it) }
            val bytes = body.toByteArray()
            s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(address), port))
            if (port == BeatLightsRules.WIZ_PORT) sent.incrementAndGet() else goveeSent.incrementAndGet()
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
