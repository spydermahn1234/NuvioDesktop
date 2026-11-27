package com.nuvio.app.features.lights

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Who is playing: the in-app player, or a process Nuvio handed the stream to. */
enum class LightsPlaybackSource {
    Player,
    External,
}

/**
 * Turns playback state into room events and hands them to the providers, one at a time.
 *
 * Every caller is fire-and-forget: the player and the external-player launcher must never wait on
 * a cloud round-trip. Events queue on a channel and a single worker drains it, so a Start can
 * never overlap the End that follows it and the providers see a consistent order. The worker also
 * collapses a pair that cancels out before it has run — Start then End with the room untouched,
 * Pause then Resume — which is what a user who backs out of the player immediately, or taps
 * pause twice while seeking, produces.
 *
 * The session concept lives here, not in the providers: [sessionActive] is "the scene is applied",
 * and it is what decides whether a `playing` report is a Start (first frame) or a Resume (after a
 * pause). A session that is active when the feature is switched off still gets its End, so the
 * room is never left dark by a settings change.
 */
object LightsController {
    private val log = Logger.withTag("Lights")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val providers: List<LightsProvider> = listOf(GoveeLightsProvider, WebhookLightsProvider)

    private sealed interface Command {
        data class Playback(
            val event: LightsEvent,
            val source: LightsPlaybackSource,
            val kind: LightsContentKind? = null,
            /** fork:bedtime - an End that leaves the room dark instead of bringing it back. */
            val lightsOff: Boolean = false,
        ) : Command
        data object Test : Command

        /**
         * fork:shelf-home v3 - other room work (the shelf home's art lights) run in the same queue, so it never overlaps a
         * playback scene: a restore queued before a Start has finished before the Start captures the room.
         */
        class Room(val label: String, val block: suspend () -> Unit) : Command {
            override fun toString(): String = "Room($label)"
        }
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)

    // Worker-owned; only ever read or written from the drain loop.
    private var sessionActive = false
    private var sessionSource: LightsPlaybackSource? = null
    private var paused = false
    // fork:lights-per-content - the scene this session opened with; every later event re-uses it.
    private var sessionScene: LightsSceneTarget? = null

    private const val TEST_HOLD_MS = 4_000L

    // fork:shelf-home v3 - who is playing right now, as the callers report it (not the worker's session, which only
    // exists when Lights out is on): the art lights never touch the room while anything plays.
    private val playingSources = mutableSetOf<LightsPlaybackSource>()
    private val _playbackUp = MutableStateFlow(false)
    val playbackUp: StateFlow<Boolean> = _playbackUp.asStateFlow()
    private val playbackListeners = mutableListOf<() -> Unit>()

    /**
     * fork:shelf-home v3 - [listener] runs on the caller's thread each time playback reports Playing, before the Start
     * is queued, so anything it queues with [room] runs first (the art lights hand the room back this way).
     */
    internal fun onPlaybackStarting(listener: () -> Unit) {
        synchronized(playbackListeners) { playbackListeners += listener }
    }

    /** fork:shelf-home v3 - queues [block] behind every room change already asked for. */
    internal fun room(label: String, block: suspend () -> Unit) {
        commands.trySend(Command.Room(label, block))
    }

    private fun markPlaying(source: LightsPlaybackSource, up: Boolean) {
        val listeners = synchronized(playingSources) {
            if (up) playingSources += source else playingSources -= source
            _playbackUp.value = playingSources.isNotEmpty()
            if (up) synchronized(playbackListeners) { playbackListeners.toList() } else emptyList()
        }
        listeners.forEach { listener ->
            runCatching { listener() }.onFailure { log.w(it) { "Playback listener failed" } }
        }
    }

    init {
        scope.launch { drain() }
    }

    /** Loads settings and undoes a scene the previous run never got to restore. */
    fun ensureLoaded() {
        LightsSettingsRepository.ensureLoaded()
        scope.launch {
            runCatching { GoveeLightsProvider.restoreAfterCrash(LightsSettingsRepository.snapshot()) }
                .onFailure { if (it is CancellationException) throw it; log.w(it) { "Crash restore failed" } }
        }
    }

    /** Playback is actually running: the first report is the Start, later ones (after a pause) a Resume. */
    fun playing(source: LightsPlaybackSource, kind: LightsContentKind? = null) {
        markPlaying(source, up = true)
        commands.trySend(Command.Playback(LightsEvent.Start, source, kind))
    }

    fun paused(source: LightsPlaybackSource) {
        commands.trySend(Command.Playback(LightsEvent.Pause, source))
    }

    /** Playback finished or the player was left; the room goes back to how it was. */
    fun ended(source: LightsPlaybackSource) {
        markPlaying(source, up = false)
        commands.trySend(Command.Playback(LightsEvent.End, source))
    }

    /**
     * fork:bedtime - playback finished and the evening is over: the session ends with every selected light switched
     * off rather than put back. Works with no session too (outside the lights-out hours, say), so the room is always
     * dark afterwards.
     */
    fun bedtime(source: LightsPlaybackSource) {
        markPlaying(source, up = false)
        commands.trySend(Command.Playback(LightsEvent.End, source, lightsOff = true))
    }

    /** Applies the scene, holds it a few seconds, and restores — so the settings page can be checked. */
    fun runTest() {
        commands.trySend(Command.Test)
    }

    /** Fetches the account's lights into the settings cache; reports through the repository. */
    fun loadGoveeDevices() {
        val apiKey = LightsSettingsRepository.snapshot().goveeApiKey
        if (apiKey.isBlank()) return
        LightsSettingsRepository.setGoveeDeviceLoadState(GoveeDeviceLoadState.Loading)
        scope.launch {
            val state = when (val result = GoveeClient.listDevices(apiKey)) {
                is GoveeResult.Ok -> {
                    LightsSettingsRepository.setGoveeDevices(result.value)
                    GoveeDeviceLoadState.Loaded(result.value.size)
                }
                is GoveeResult.Unauthorized -> GoveeDeviceLoadState.Failed("API key rejected")
                is GoveeResult.Failed -> GoveeDeviceLoadState.Failed(result.detail)
            }
            LightsSettingsRepository.setGoveeDeviceLoadState(state)
        }
    }

    private suspend fun drain() {
        for (first in commands) {
            val batch = mutableListOf(first)
            while (true) batch += commands.tryReceive().getOrNull() ?: break
            for (command in collapse(batch)) {
                runCatching { execute(command) }
                    .onFailure { if (it is CancellationException) throw it; log.w(it) { "Lights command failed: $command" } }
            }
        }
    }

    /**
     * Drops pairs that would cancel out before the room is touched. Works on the queued list
     * only; a Start that has already run is a real session and its End must run too.
     */
    private fun collapse(batch: List<Command>): List<Command> {
        val out = mutableListOf<Command>()
        var virtualActive = sessionActive
        // Index in [out] of a queued Start that would open a session, or -1 once it is committed.
        var pendingStart = -1
        for (command in batch) {
            val playback = command as? Command.Playback
            if (playback == null) {
                out += command
                continue
            }
            val last = out.lastOrNull() as? Command.Playback
            when (playback.event) {
                LightsEvent.Start -> {
                    if (virtualActive && last?.event == LightsEvent.Pause && last.source == playback.source) {
                        out.removeAt(out.lastIndex) // Pause, Resume → nothing
                    } else {
                        if (!virtualActive) pendingStart = out.size
                        out += command
                        virtualActive = true
                    }
                }
                LightsEvent.Pause -> out += command
                LightsEvent.End -> {
                    val opener = pendingStart.takeIf { it >= 0 }?.let { out[it] as Command.Playback }
                    if (opener != null && opener.source == playback.source && !playback.lightsOff) {
                        // Start (and anything after it), End — the room was never touched. Room work queued in
                        // between (fork:shelf-home v3) is not playback and still runs.
                        for (i in out.lastIndex downTo pendingStart) if (out[i] is Command.Playback) out.removeAt(i)
                    } else {
                        out += command
                    }
                    virtualActive = false
                    pendingStart = -1
                }
                LightsEvent.Resume -> out += command
            }
        }
        return out
    }

    private suspend fun execute(command: Command) {
        when (command) {
            is Command.Playback -> executePlayback(command)
            Command.Test -> executeTest()
            is Command.Room -> command.block()
        }
    }

    private suspend fun executePlayback(command: Command.Playback) {
        val event = command.event
        val source = command.source
        val stored = LightsSettingsRepository.snapshot()
        // The scene fixed at this session's Start, so a Resume re-applies the same room.
        val settings = sessionScene?.let(stored::withScene) ?: stored
        when (event) {
            LightsEvent.Start -> {
                if (sessionActive) {
                    if (sessionSource != source) return // another surface's playback; not ours to touch
                    if (!paused) return
                    paused = false
                    dispatch(LightsEvent.Resume, settings)
                    return
                }
                if (!settings.isActive) return
                if (source == LightsPlaybackSource.External && !settings.externalPlayers) return
                // Checked once, at the Start: a film that began inside the window keeps its scene
                // even if the clock leaves it, and the End is never gated.
                if (!settings.allowsAt(localMinutesOfDay())) {
                    log.i { "Outside the lights-out hours; leaving the room alone" }
                    return
                }
                val scene = stored.sceneFor(command.kind)
                if (scene == null) {
                    log.i { "Per-content lighting leaves the room alone for ${command.kind}" }
                    return
                }
                sessionActive = true
                sessionSource = source
                sessionScene = scene
                paused = false
                log.i { "Lights out ($source, ${command.kind ?: "unknown"}: ${scene.action} ${scene.dimPercent}%)" }
                dispatch(LightsEvent.Start, stored.withScene(scene))
            }
            LightsEvent.Pause -> {
                if (!sessionActive || sessionSource != source || paused) return
                paused = true
                dispatch(LightsEvent.Pause, settings)
            }
            LightsEvent.End -> {
                if (command.lightsOff) {
                    // fork:bedtime - only another surface's running session is left alone.
                    if (sessionActive && sessionSource != source) return
                    sessionActive = false
                    sessionSource = null
                    sessionScene = null
                    paused = false
                    if (!stored.isActive) return
                    log.i { "Bedtime: lights off ($source)" }
                    dispatch(LightsEvent.Start, stored.withScene(LightsSceneTarget(LightsStartAction.TurnOff, stored.dimPercent)))
                    GoveeLightsProvider.forget()
                    return
                }
                if (!sessionActive || sessionSource != source) return
                sessionActive = false
                sessionSource = null
                sessionScene = null
                paused = false
                log.i { "Lights up ($source)" }
                dispatch(LightsEvent.End, settings)
            }
            LightsEvent.Resume -> Unit // derived from Start above, never queued directly
        }
    }

    private suspend fun executeTest() {
        val settings = LightsSettingsRepository.snapshot()
        if (sessionActive) {
            LightsSettingsRepository.setTestState(LightsTestState.Failed("playback is in progress"))
            return
        }
        if (!settings.hasAnyProvider) {
            LightsSettingsRepository.setTestState(LightsTestState.Failed("nothing configured"))
            return
        }
        LightsSettingsRepository.setTestState(LightsTestState.Running)
        try {
            dispatch(LightsEvent.Start, settings)
            delay(TEST_HOLD_MS)
            dispatch(LightsEvent.End, settings)
            LightsSettingsRepository.setTestState(LightsTestState.Done)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            LightsSettingsRepository.setTestState(LightsTestState.Failed(error.message ?: "failed"))
        }
    }

    /** Minutes past midnight on the machine's own clock; the window is set against that clock. */
    private fun localMinutesOfDay(): Int {
        val now = java.time.LocalTime.now()
        return now.hour * 60 + now.minute
    }

    private suspend fun dispatch(event: LightsEvent, settings: LightsSettings) {
        coroutineScope {
            providers.filter { it.isConfigured(settings) }.map { provider ->
                async {
                    runCatching { provider.handle(event, settings) }
                        .onFailure { if (it is CancellationException) throw it; log.w(it) { "${provider.id} failed on $event" } }
                }
            }.forEach { it.await() }
        }
    }
}
