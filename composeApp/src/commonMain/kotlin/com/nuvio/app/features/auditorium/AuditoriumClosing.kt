package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One closing: the film [launchId] ended by itself and its hall closes before [onFinished] leaves the player route
 * (the same pop App.kt did before). [finished] is set the moment that has been called, after which the route draws
 * black until it goes; it never draws its player again.
 */
class AuditoriumClosingRequest internal constructor(
    val launchId: Long,
    val theme: AuditoriumTheme,
    val title: String,
    val artUrl: String?,
    val length: AuditoriumLength,
    internal val onFinished: () -> Unit,
) {
    var finished by mutableStateOf(false)
        internal set
}

/**
 * fork:auditorium - the closing's state. Snapshot state (not only a flow) on purpose: App.kt's player route reads
 * [current] in the same composition in which it stops publishing the player to PlayerHostLayer, and a flow
 * collected into state lags a frame, in which the route would hand the player back to the layer (and start the
 * film again). [request] mirrors it for anything outside composition.
 */
object AuditoriumClosingController {
    var current: AuditoriumClosingRequest? by mutableStateOf(null)
        private set
    private val _request = MutableStateFlow<AuditoriumClosingRequest?>(null)
    val request: StateFlow<AuditoriumClosingRequest?> = _request.asStateFlow()

    private fun set(value: AuditoriumClosingRequest?) {
        current = value
        _request.value = value
    }

    /**
     * From the natural-end path (App.kt's onPlaybackCompleted): starts the closing for [launch] and returns true when
     * [AuditoriumRules.closesFor] says it closes; false (and nothing changes) otherwise, the feature off included.
     */
    fun startFor(launchId: Long, launch: PlayerLaunch, onFinished: () -> Unit): Boolean {
        val settings = AuditoriumSettingsRepository.state.value
        val closes = AuditoriumRules.closesFor(
            settings = settings,
            metaType = launch.parentMetaType,
            endedNaturally = true,
            isPlaylist = launch.autoPlayMode == PlayerAutoPlayMode.Playlist,
        )
        if (!closes) return false
        set(
            AuditoriumClosingRequest(
                launchId = launchId,
                theme = AuditoriumRules.themeFor(settings.theme, launch.parentMetaId),
                title = launch.title,
                artUrl = launch.background?.takeIf { it.isNotBlank() } ?: launch.poster?.takeIf { it.isNotBlank() },
                length = settings.length,
                onFinished = onFinished,
            ),
        )
        return true
    }

    /** The closing played out, or was skipped: leave the route. Acts once; true when it did. */
    fun finish(launchId: Long? = null): Boolean {
        val active = current ?: return false
        if (active.finished || (launchId != null && active.launchId != launchId)) return false
        active.finished = true
        active.onFinished()
        return true
    }

    /** The app's own Back (DesktopNavigationGestureBridge) during a closing: finish it, the way the closing's keys do. */
    fun finishFromBack(): Boolean = finish()

    /** Whether [launchId]'s route must draw the closing (or black after it) instead of its player. */
    fun owns(launchId: Long): Boolean = current?.launchId == launchId

    /** The route went (however): forget its closing, without calling anything. */
    internal fun drop(request: AuditoriumClosingRequest) {
        if (current === request) set(null)
    }
}

/**
 * fork:auditorium - App.kt's player route asks this before drawing its player: true while [launchId] is closing (or
 * has closed and is leaving), in which case the closing has been drawn here and the route draws nothing else.
 * False, and nothing is composed, in every other case.
 */
@Composable
fun auditoriumClosingShown(launchId: Long): Boolean {
    val request = AuditoriumClosingController.current?.takeIf { it.launchId == launchId } ?: return false
    DisposableEffect(request) {
        onDispose { AuditoriumClosingController.drop(request) }
    }
    if (request.finished) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    } else {
        AuditoriumClosing(request = request, modifier = Modifier.fillMaxSize())
    }
    return true
}

/** The closing, full screen: [AuditoriumRules.closingFrame] over [AuditoriumRules.closingMs], then the finish. */
@Composable
private fun AuditoriumClosing(request: AuditoriumClosingRequest, modifier: Modifier = Modifier) {
    var elapsedMs by remember(request) { mutableLongStateOf(0L) }
    val totalMs = remember(request) { AuditoriumRules.closingMs(request.length) }
    LaunchedEffect(request) {
        var start = -1L
        while (elapsedMs < totalMs) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                elapsedMs = (now - start) / 1_000_000L
            }
        }
        AuditoriumClosingController.finish(request.launchId)
    }
    val focus = remember(request) { FocusRequester() }
    LaunchedEffect(request) { runCatching { focus.requestFocus() } }
    Box(
        modifier = modifier
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape, Key.Back, Key.ButtonB,
                    Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter, Key.ButtonA -> {
                        AuditoriumClosingController.finish(request.launchId)
                        true
                    }
                    else -> false
                }
            },
    ) {
        AuditoriumHall(
            frame = AuditoriumRules.closingFrame(elapsedMs.coerceAtMost(totalMs), request.length),
            theme = request.theme,
            title = request.title,
            artUrl = request.artUrl,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
