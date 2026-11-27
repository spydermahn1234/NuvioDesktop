package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.nuvio.app.features.home.components.HomeHeroTrailerGate
import com.nuvio.app.features.home.components.HomeHeroTrailerSurface
import com.nuvio.app.features.home.isOffCatalogHeroItem
import com.nuvio.app.features.trailer.HeroTrailerMetadataService
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * fork:shelf-home v3 - phase 5, the trailer behind the art (board V3Art; plan in design-notes/home-shelves-v3.md).
 *
 * After 4 s still on a title in Browse, the title's hero trailer plays, always muted, with a 4 dp progress line along
 * the very top in the accent and "● TRAILER · MUTED" under the clock. It reuses the home hero's trailer: the same
 * lookup and ranking (HeroTrailerMetadataService: season-1-safe trailers, rankHeroTrailers, cached for the session)
 * and the same native surface (HomeHeroTrailerSurface, chromeless).
 *
 * The surface is a native mpv window: Compose can neither fade it nor draw over it, so a full-bleed trailer would
 * cover the breadcrumb, clock, title block and row. It therefore plays in the open part of the art, right of the title
 * block and between the status lines and the row (ShelfTrailerRules.window), fades in by starting black and lifting
 * (revealFadeMs), and is taken out of composition (so the player is released) the moment it should stop.
 */

private val shelfTrailerLog = Logger.withTag("ShelfTrailer")

/** The title whose trailer is looked up: a catalog type and id. */
internal data class ShelfTrailerSubject(val type: String, val id: String) {
    val key: String get() = "$type:$id"
}

/** Where the trailer window sits, in dp from the home's top left. */
internal data class ShelfTrailerWindow(val left: Float, val top: Float, val width: Float, val height: Float)

internal object ShelfTrailerRules {
    /** Still on one title this long (after the art's own 250 ms settle) before the trailer is looked up. */
    const val WAIT_MS = 4_000L

    /** The picture lifts from black over this long; the window's shade and the top line fade in as long. */
    const val FADE_MS = 600

    /** The progress line along the very top. */
    const val LINE_DP = 4

    // The 1080p frame's fixed places (ShelfHome.kt): the title block runs from x 96 to 1096, the status lines under
    // the clock from y 120 (up to three lines), the row's header at y 790 (840 with logos) counted from the bottom.
    private const val LEFT_DP = 1136f
    private const val RIGHT_MARGIN_DP = 96f
    private const val TOP_DP = 236f
    private const val ROW_HEADER_FROM_BOTTOM_DP = 1080f - 790f
    private const val GAP_ABOVE_ROW_DP = 40f
    private const val MIN_WIDTH_DP = 320f
    private const val MIN_HEIGHT_DP = 180f

    /** The title to ask about, or null for cards that have no trailer (folders, collections, music, live TV). */
    fun subject(card: ShelfCard?): ShelfTrailerSubject? = when (card) {
        null -> null
        is ShelfCard.Meta -> card.item.takeIf { it.id.isNotBlank() && it.type.isNotBlank() && !it.isOffCatalogHeroItem() }
            ?.let { ShelfTrailerSubject(it.type, it.id) }
        // An episode's card plays its show's trailer (the lookup keeps to season 1, so no spoilers).
        is ShelfCard.Progress -> card.item.takeIf { it.parentMetaId.isNotBlank() && it.parentMetaType.isNotBlank() }
            ?.let { ShelfTrailerSubject(it.parentMetaType, it.parentMetaId) }
        is ShelfCard.Folder -> null
    }

    /**
     * Whether the 4 s wait may start: browsing (active), a title with a trailer to ask about, somewhere to show it, and
     * not the title whose trailer already ended or failed while it stayed focused.
     */
    fun shouldArm(active: Boolean, key: String?, spentKey: String?, hasWindow: Boolean): Boolean =
        active && hasWindow && key != null && key != spentKey

    /** The line's filled share: position over duration, 0 while the length is unknown. */
    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /**
     * The trailer's window, 16:9, right-aligned with the clock (96 from the right), from under the status lines down to
     * at most 40 above the row's header. Null when the home is too small for one; then no trailer starts.
     */
    fun window(widthDp: Float, heightDp: Float, topShiftDp: Float = 0f): ShelfTrailerWindow? {
        val right = widthDp - RIGHT_MARGIN_DP
        val top = TOP_DP + topShiftDp
        val bottomLimit = heightDp - ROW_HEADER_FROM_BOTTOM_DP - GAP_ABOVE_ROW_DP
        var width = right - LEFT_DP
        var height = width * 9f / 16f
        if (top + height > bottomLimit) {
            height = bottomLimit - top
            width = height * 16f / 9f
        }
        if (width < MIN_WIDTH_DP || height < MIN_HEIGHT_DP) return null
        return ShelfTrailerWindow(left = right - width, top = top, width = width, height = height)
    }
}

/** What the home reads back: [showing] for "● TRAILER · MUTED", [progress] for anything else that wants it. */
@Stable
internal class ShelfTrailerState {
    var showing by mutableStateOf(false)
        internal set
    var progress by mutableFloatStateOf(0f)
        internal set
}

/**
 * The trailer behind the art. Place it full size inside ShelfHome's BoxWithConstraints after the scrims and the evening
 * wash, before the Browse words. [active] is the lead's browse gate; [input] is the home's input serial (any key or real
 * pointer move stops the trailer and starts the 4 s again); [topInset] is ShelfHome's `topShift`;
 * [onFocusEscaped] gives the keyboard back to the home if the native window ever takes it.
 */
@Composable
internal fun ShelfTrailerLayer(
    card: ShelfCard?,
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    input: Int = 0,
    topInset: Dp = 0.dp,
    onFocusEscaped: () -> Unit = {},
): ShelfTrailerState {
    val state = remember { ShelfTrailerState() }
    val homeActive by HomeHeroTrailerGate.homeActive.collectAsState()
    val subject = remember(card) { ShelfTrailerRules.subject(card) }
    val key = subject?.key
    val live = active && homeActive
    val ink = LocalShelfPalette.current.ink
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<TrailerPlaybackSource?>(null) }
    var sourceKey by remember { mutableStateOf<String?>(null) }
    var spentKey by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }

    fun stop() {
        source = null
        sourceKey = null
        revealed = false
        state.progress = 0f
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val window = ShelfTrailerRules.window(maxWidth.value, maxHeight.value, topInset.value)
        val hasWindow = window != null

        LaunchedEffect(key, live, input, hasWindow) {
            stop()
            if (!ShelfTrailerRules.shouldArm(live, key, spentKey, hasWindow)) return@LaunchedEffect
            val asked = subject ?: return@LaunchedEffect
            delay(maxOf(ShelfTrailerRules.WAIT_MS, HomeHeroTrailerGate.startupGraceRemainingMillis()))
            val resolved = HeroTrailerMetadataService.resolve(asked.type, asked.id)
            if (resolved == null) {
                shelfTrailerLog.i { "no trailer for ${asked.key}" }
                spentKey = asked.key
                return@LaunchedEffect
            }
            source = resolved
            sourceKey = asked.key
        }

        val playing = source
        // Same frame as the gate or the card changes: the native window leaves composition and the player is released.
        val mounted = playing != null && live && window != null && sourceKey != null && sourceKey == key
        SideEffect { state.showing = mounted && revealed }

        val shade by animateFloatAsState(
            targetValue = if (mounted) 1f else 0f,
            animationSpec = tween(if (mounted) ShelfTrailerRules.FADE_MS else 0),
            label = "shelfTrailerShade",
        )
        val lineAlpha by animateFloatAsState(
            targetValue = if (mounted && revealed) 1f else 0f,
            animationSpec = tween(if (mounted && revealed) ShelfTrailerRules.FADE_MS else 0),
            label = "shelfTrailerLine",
        )

        if (window != null && (mounted || shade > 0f)) {
            // A hairline frame just outside the picture (the picture itself cannot be drawn over), and under it a shade
            // that darkens the art while the trailer loads, so the art goes dark and the picture lifts out of it.
            Box(
                Modifier
                    .offset((window.left - 1f).dp, (window.top - 1f).dp)
                    .size((window.width + 2f).dp, (window.height + 2f).dp)
                    .alpha(shade)
                    .border(1.dp, ink.copy(alpha = 0.16f)),
            ) {
                Box(Modifier.padding(1.dp).fillMaxSize().background(Color(0xFF06090A).copy(alpha = 0.88f)))
            }
            if (mounted && playing != null) {
                val playingKey = sourceKey
                Box(
                    Modifier
                        .offset(window.left.dp, window.top.dp)
                        .size(window.width.dp, window.height.dp),
                ) {
                    HomeHeroTrailerSurface(
                        sourceUrl = playing.videoUrl,
                        // Always muted: the audio stream is not even fetched.
                        sourceAudioUrl = null,
                        sourceHeaders = playing.headers,
                        playWhenReady = true,
                        muted = true,
                        volume = 0,
                        backgroundColor = Color(0xFF06090A),
                        logoUrl = null,
                        title = "",
                        meta = "",
                        description = "",
                        modifier = Modifier.fillMaxSize(),
                        navDismissEdge = "none",
                        onReady = { revealed = true },
                        onEnded = {
                            spentKey = playingKey
                            stop()
                        },
                        onError = {
                            shelfTrailerLog.i { "trailer failed for $playingKey" }
                            spentKey = playingKey
                            stop()
                            // As the hero does: the cached googlevideo URLs may be the dead part; look again next time.
                            subject?.takeIf { it.key == playingKey }?.let { s ->
                                scope.launch { HeroTrailerMetadataService.invalidate(s.type, s.id) }
                            }
                        },
                        onSurfaceDisposed = onFocusEscaped,
                        normalizeAudio = false,
                        chromeless = true,
                        revealFadeMs = ShelfTrailerRules.FADE_MS,
                        onProgress = { position, duration ->
                            state.progress = ShelfTrailerRules.progress(position, duration)
                        },
                    )
                }
            }
        }

        // The progress line along the very top, in the accent.
        if (lineAlpha > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(ShelfTrailerRules.LINE_DP.dp)
                    .alpha(lineAlpha)
                    .background(ink.copy(alpha = 0.12f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(state.progress).background(accent))
            }
        }
    }
    return state
}
