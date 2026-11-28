package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.nuvio.app.features.player.PlayerAutoPlayMode
import com.nuvio.app.features.player.PlayerLaunch
import kotlinx.coroutines.flow.first

/** What the hall gate tells its caller (PrerollGateHook's prerollGateCleared). */
enum class AuditoriumGateResult {
    /** The hall owns the screen: the caller shows nothing else and holds the film back. */
    Holding,
    /** Done, or never needed: what comes next is the caller's (the full-screen pre-show, if eligible). */
    Cleared,
    /** Done, and the pre-show already played (or was skipped) on the hall's screen: do not play it again. */
    ClearedPreshowPlayed,
}

private enum class HallStage { Opening, Preshow, Glide, Done }

/** One launch's hall: decided once, then driven by the clock and the keys. Never rebuilt for the same launch. */
private class HallState(
    val opens: Boolean,
    val preshowInHall: Boolean,
    val theme: AuditoriumTheme,
    val length: AuditoriumLength,
    val title: String,
    val artUrl: String?,
) {
    var stage by mutableStateOf(if (opens) HallStage.Opening else HallStage.Done)
    /** Elapsed time in the current stage (Opening, Glide); 0 in the others. */
    var elapsedMs by mutableLongStateOf(0L)
    var preshowPlayed by mutableStateOf(false)
    /** The frame time the current stage started at; -1 = take the next frame's. Clock-only, not drawn. */
    var stageStartNanos: Long = -1L

    private fun enter(next: HallStage) {
        stage = next
        elapsedMs = 0L
        stageStartNanos = -1L
    }

    /** The next stage: Opening -> Preshow (hall mode) or Glide -> Done. */
    fun next() {
        when (stage) {
            HallStage.Opening -> enter(if (preshowInHall) HallStage.Preshow else HallStage.Glide)
            HallStage.Preshow -> { preshowPlayed = true; enter(HallStage.Glide) }
            HallStage.Glide -> enter(HallStage.Done)
            HallStage.Done -> Unit
        }
    }

    /** The pre-show on the hall's screen ended (played out or skipped inside it). Called repeatedly; acts once. */
    fun preshowFinished() {
        if (stage == HallStage.Preshow) next()
    }

    /** Escape / Back: the whole hall goes at once, straight to what comes next. */
    fun skipAll() {
        if (stage == HallStage.Done) return
        // Skipped during the pre-show counts as the pre-show done: Escape there skips that too, as it does full screen.
        if (stage == HallStage.Preshow) preshowPlayed = true
        enter(HallStage.Done)
    }

    fun frame(): AuditoriumFrame = when (stage) {
        HallStage.Opening -> AuditoriumRules.openingFrame(elapsedMs, length)
        // The settled hall, held while the pre-show plays on the screen. The camera and curtains are pinned so the
        // hall lays the pre-show out (it only does at camera 0 with the curtains fully open).
        HallStage.Preshow -> AuditoriumRules.openingFrame(AuditoriumRules.openingSettledMs(length), length)
            .copy(camera = 0f, curtain = 1f)
        HallStage.Glide -> AuditoriumRules.glideFrame(elapsedMs, length)
        HallStage.Done -> AuditoriumRules.glideFrame(AuditoriumRules.glideMs(length), length)
    }
}

/**
 * fork:auditorium - the cinema hall in front of the film, in the same style as prerollGateCleared (and called from
 * it, so App.kt's player route keeps its one gate line).
 *
 * The order, when the hall opens for this launch ([AuditoriumRules.opensFor]):
 * - pre-show on the hall's screen ([AuditoriumSettings.preshowInHall] on and [preshowEligible]): opening (fade in,
 *   lights down, curtains part on the film's art) -> the pre-show plays on the screen ([preshow]) -> the glide into
 *   the screen, ending on black -> [AuditoriumGateResult.ClearedPreshowPlayed] (the caller skips its own pre-show).
 * - otherwise: opening -> glide -> [AuditoriumGateResult.Cleared], and the caller's full-screen pre-show (if any)
 *   follows from black. A full-screen pre-show before the hall would end on black and then open the hall again,
 *   and one after the hall is a cut from black to black, so the hall always comes first.
 *
 * Previously-on and the player come after either way (the caller's business).
 *
 * Keys (a pad arrives as keys too): Enter, Space, Right, OK / A = the next stage (opening -> settled, glide -> done);
 * during the pre-show they belong to the pre-show (skip this trailer). Escape, Back, B = skip the whole hall at once.
 * The hall can never hold the film for good: the opening and the glide run on the clock, and the pre-show has
 * its own per-item time-outs (PrerollGate) plus Escape, which this gate sees first whatever has focus inside it.
 *
 * Off ([AuditoriumSettings.enabled] false, or not a title it opens for), this composes nothing and returns
 * [AuditoriumGateResult.Cleared] from the first frame.
 */
@Composable
fun auditoriumGate(
    launchKey: String,
    launch: PlayerLaunch,
    /** The caller's pre-show would play for this launch (it is fresh and something in it is switched on). */
    preshowEligible: Boolean,
    /** The caller's pre-show, laid out on the hall's screen; it calls onDone when it has finished or was skipped. */
    preshow: @Composable (onDone: () -> Unit) -> Unit,
): AuditoriumGateResult {
    val state = remember(launchKey) {
        // A snapshot read, not collected: the plan is fixed for the launch, so a settings change mid-hall cannot
        // restart or reshape it.
        val settings = AuditoriumSettingsRepository.state.value
        val opens = AuditoriumRules.opensFor(
            settings = settings,
            metaType = launch.parentMetaType,
            initialPositionMs = launch.initialPositionMs,
            disableProgressTracking = launch.disableProgressTracking,
            isPlaylist = launch.autoPlayMode == PlayerAutoPlayMode.Playlist,
        )
        HallState(
            opens = opens,
            preshowInHall = settings.preshowInHall && preshowEligible,
            theme = AuditoriumRules.themeFor(settings.theme, launch.parentMetaId),
            length = settings.length,
            title = launch.title,
            artUrl = launch.background?.takeIf { it.isNotBlank() } ?: launch.poster?.takeIf { it.isNotBlank() },
        )
    }

    // The clock. Keyed on the launch only: recomposition never restarts it, and a stage change from a key is
    // picked up on the next frame (the stage is re-read inside the frame callback).
    LaunchedEffect(launchKey) {
        if (!state.opens) return@LaunchedEffect
        while (state.stage != HallStage.Done) {
            if (state.stage == HallStage.Preshow) {
                // No clock while the pre-show plays: the frame is held; wait for it (or a key) to move on.
                snapshotFlow { state.stage }.first { it != HallStage.Preshow }
                continue
            }
            withFrameNanos { now ->
                val stage = state.stage
                if (stage != HallStage.Opening && stage != HallStage.Glide) return@withFrameNanos
                if (state.stageStartNanos < 0L) state.stageStartNanos = now
                val elapsed = (now - state.stageStartNanos) / 1_000_000L
                state.elapsedMs = elapsed
                val end = if (stage == HallStage.Opening) {
                    AuditoriumRules.openingSettledMs(state.length)
                } else {
                    AuditoriumRules.glideMs(state.length)
                }
                if (elapsed >= end) state.next()
                Unit
            }
        }
    }

    // Only the stage is read here: auditoriumGate returns a value, so it is not a restart scope of its own and
    // every state it reads recomposes its caller (App.kt's player route). The per-frame values (elapsedMs) are read
    // inside HallGateScreen, which recomposes alone.
    val stage = state.stage
    if (stage == HallStage.Done) {
        return if (state.preshowPlayed) AuditoriumGateResult.ClearedPreshowPlayed else AuditoriumGateResult.Cleared
    }
    HallGateScreen(launchKey = launchKey, state = state, preshow = preshow)
    return AuditoriumGateResult.Holding
}

/** The hall on screen while the gate holds: the per-frame part, its own restart scope. */
@Composable
private fun HallGateScreen(
    launchKey: String,
    state: HallState,
    preshow: @Composable (onDone: () -> Unit) -> Unit,
) {
    val stage = state.stage
    val focus = remember(launchKey) { FocusRequester() }
    // Focus on the hall, so its keys land; again after the pre-show (whose own box held focus) leaves the screen.
    LaunchedEffect(launchKey, stage) {
        if (stage != HallStage.Preshow) runCatching { focus.requestFocus() }
    }
    val frame = state.frame()
    // Declared with its type so it is certainly a composable lambda.
    val preshowOnScreen: @Composable () -> Unit = { preshow { state.preshowFinished() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape, Key.Back, Key.ButtonB -> {
                        state.skipAll()
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionRight, Key.DirectionCenter, Key.ButtonA -> {
                        // The pre-show's own keys (skip this trailer, the trivia's answers) pass through to it.
                        if (state.stage == HallStage.Preshow) return@onPreviewKeyEvent false
                        state.next()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AuditoriumHall(
            frame = frame,
            theme = state.theme,
            title = state.title,
            artUrl = state.artUrl,
            modifier = Modifier.fillMaxSize(),
            // Only while the hall is settled (camera 0, curtains open): the pre-show's native trailer surface is
            // laid out on the screen's real bounds there, and must be gone before the camera moves.
            screenContent = if (stage == HallStage.Preshow && frame.camera == 0f && frame.curtain == 1f) preshowOnScreen else null,
        )
    }
}
