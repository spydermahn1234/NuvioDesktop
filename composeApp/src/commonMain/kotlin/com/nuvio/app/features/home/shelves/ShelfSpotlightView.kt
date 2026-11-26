// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.navigationKey
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.requestFocusLogged
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The Spotlight view (mockup "Films - Spotlight", after a Kodi skin's coverflow): one large poster
 * in a fixed slot middle-left, smaller posters either side, a small square marker under the big one.
 * Left/Right move the selection one poster and the strip slides past the fixed slot. The view owns
 * its selection; it is one focusable box, so nothing inside it takes focus.
 */

/** The coverflow's geometry and selection rules, in dp. Pure, so they can be tested. */
internal object ShelfSpotlightRules {
    const val FOCUSED_W = 300f
    const val FOCUSED_H = 450f
    const val SMALL_W = 150f
    const val GAP = 54f

    /** Centre of the focused slot from the view's left edge: one whole neighbour fits to its left. */
    const val ANCHOR = SMALL_W + GAP + FOCUSED_W / 2f

    /** From the focused centre to a neighbour's centre. */
    private const val FIRST_STEP = FOCUSED_W / 2f + GAP + SMALL_W / 2f

    /** Between two neighbours' centres. */
    private const val STEP = SMALL_W + GAP

    /** Size of a poster [distance] slots from the focused one (fractional while sliding), 1 = focused. */
    fun scaleFor(distance: Float): Float {
        val d = min(abs(distance), 1f)
        return 1f - d * (1f - SMALL_W / FOCUSED_W)
    }

    /** Horizontal offset of a poster's centre from the focused slot's centre. */
    fun centreOffset(distance: Float): Float {
        val d = abs(distance)
        val offset = if (d <= 1f) d * FIRST_STEP else FIRST_STEP + (d - 1f) * STEP
        return if (distance < 0f) -offset else offset
    }

    /** How many neighbours on one side are at least partly inside [spanDp] from the focused centre. */
    fun slotsFitting(spanDp: Float): Int {
        var n = 0
        while (n < 1000 && centreOffset((n + 1).toFloat()) - SMALL_W / 2f < spanDp) n++
        return n
    }

    /** Indices to draw around [focus]: [before] and [after] neighbours plus one more each side for the slide. */
    fun visibleRange(focus: Int, count: Int, before: Int, after: Int): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val f = clamp(focus, count)
        return max(0, f - before - 1)..min(count - 1, f + after + 1)
    }

    fun clamp(index: Int, count: Int): Int = if (count <= 0) 0 else index.coerceIn(0, count - 1)

    /** The index after a step of [delta], or null when it cannot move (at either end). */
    fun move(index: Int, delta: Int, count: Int): Int? {
        if (count <= 0) return null
        val next = index + delta
        return if (next in 0 until count && next != index) next else null
    }

    /**
     * Whether an arrow is used here. Left at the first poster is kept (stays put: nothing useful is on
     * the left); Right at the last poster is let go so focus can reach the shelf panel on the right.
     */
    fun consumes(index: Int, delta: Int, count: Int): Boolean =
        move(index, delta, count) != null || delta < 0
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ShelfSpotlightView(state: ShelfViewState, modifier: Modifier = Modifier) {
    val rules = ShelfSpotlightRules
    val accent = MaterialTheme.nuvio.colors.accent
    val onAccent = LocalShelfPalette.current.focusText
    val cards = state.cards
    val count = cards.size

    var rawIndex by remember(state.shelfKey) { mutableIntStateOf(0) }
    val index = rules.clamp(rawIndex, count)
    // Keep the stored index inside the shelf when its cards shrink.
    LaunchedEffect(state.shelfKey, count) { rawIndex = rules.clamp(rawIndex, count) }
    val position = remember(state.shelfKey) { Animatable(0f) }
    LaunchedEffect(state.shelfKey, index) {
        position.animateTo(index.toFloat(), tween(durationMillis = 260))
    }

    var hasFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val current by rememberUpdatedState(state)

    fun select(to: Int) {
        if (to in 0 until current.cards.size) rawIndex = to
    }

    BoxWithConstraints(
        modifier
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused }
            .onKeyEvent { event ->
                if (count == 0) return@onKeyEvent false
                val nav = event.navigationKey()
                val delta = when (nav) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (delta != 0) {
                    val consumes = rules.consumes(index, delta, count)
                    if (consumes && event.type == KeyEventType.KeyDown) {
                        rules.move(index, delta, count)?.let { select(it) }
                    }
                    return@onKeyEvent consumes
                }
                val isOk = nav == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter
                if (isOk) {
                    if (event.type == KeyEventType.KeyDown) cards.getOrNull(index)?.let { current.onOpen(it) }
                    return@onKeyEvent true
                }
                if (event.key == Key.Menu) {
                    val hold = current.onLongPress ?: return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) cards.getOrNull(index)?.let(hold)
                    return@onKeyEvent true
                }
                false
            }
            .focusable()
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                val by = delta.y + delta.x
                if (by > 0f) rules.move(index, 1, count)?.let { select(it) }
                if (by < 0f) rules.move(index, -1, count)?.let { select(it) }
            },
    ) {
        val viewWidth = maxWidth.value
        val before = rules.slotsFitting(rules.ANCHOR)
        val after = rules.slotsFitting(viewWidth - rules.ANCHOR)

        val focusedCard = cards.getOrNull(index)
        LaunchedEffect(state.shelfKey, index, focusedCard) {
            val card = focusedCard ?: return@LaunchedEffect
            current.onFocusCard(card)
            current.onNearEnd(index + after)
        }

        val range = rules.visibleRange(index, count, before, after)
        for (i in range) {
            val card = cards[i]
            key(state.shelfKey, i) {
                val distanceNow = abs(i - position.value)
                SpotlightPoster(
                    card = card,
                    ring = hasFocus && i == index,
                    accent = accent,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .zIndex(-distanceNow)
                        .size(rules.FOCUSED_W.dp, rules.FOCUSED_H.dp)
                        .graphicsLayer {
                            val d = i - position.value
                            val s = rules.scaleFor(d)
                            scaleX = s
                            scaleY = s
                            translationX = (rules.ANCHOR - rules.FOCUSED_W / 2f + rules.centreOffset(d)).dp.toPx()
                            alpha = 1f - 0.3f * min(abs(d), 1f)
                        }
                        .pointerInput(state.shelfKey, i) {
                            detectTapGestures(
                                onTap = {
                                    focusRequester.requestFocusLogged()
                                    current.cards.getOrNull(i)?.let { tapped ->
                                        if (i == rules.clamp(rawIndex, current.cards.size)) current.onOpen(tapped) else select(i)
                                    }
                                },
                                onLongPress = {
                                    current.cards.getOrNull(i)?.let { held -> current.onLongPress?.invoke(held) }
                                },
                            )
                        },
                )
            }
        }

        // Rank on the focused poster's top-left corner, drawn over it at the fixed slot.
        if (state.ranked && focusedCard != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = (rules.ANCHOR - rules.FOCUSED_W / 2f).dp.toPx()
                        translationY = (-rules.FOCUSED_H / 2f + 44f / 2f).dp.toPx()
                    }
                    .size(64.dp, 44.dp)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = onAccent, maxLines = 1)
            }
        }

        // The small square marker under the focused slot.
        if (focusedCard != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = (rules.ANCHOR - 4f).dp.toPx()
                        translationY = (rules.FOCUSED_H / 2f + 18f).dp.toPx()
                    }
                    .size(8.dp)
                    .background(accent),
            )
        }
    }
}

private val PosterShape = RoundedCornerShape(6.dp)

@Composable
private fun SpotlightPoster(card: ShelfCard, ring: Boolean, accent: Color, modifier: Modifier) {
    Box(
        modifier
            .clip(PosterShape)
            .background(LocalShelfPalette.current.placeholder),
    ) {
        val url = card.posterUrl()
        if (url != null) {
            NuvioAsyncImage(
                model = url,
                contentDescription = card.title(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                card.title(),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalShelfPalette.current.placeholderInk,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
            )
        }
        // The border is drawn last so the image does not cover it.
        if (ring) Box(Modifier.fillMaxSize().border(4.dp, accent, PosterShape))
    }
}
