// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
 * The Logo strip view (mockup "Series - Logo strip", after a Kodi skin): a row of title logos
 * (clearlogos) instead of posters, the frame's backdrop above doing the visual work. The focused logo
 * sits full size in a fixed slot centred-left with an underline below it; neighbours either side are
 * smaller and faded, and slide past the slot through graphicsLayer so nothing re-lays out. A card
 * with no logo shows its name in big capitals in the same box. One line under the slot: year and
 * genres, the episode for Continue Watching, nothing for folders. Like Spotlight, the view is one
 * focusable box that owns its selection.
 */

/** The strip's geometry, words and selection rules, in dp. Pure, so they can be tested. */
internal object ShelfLogoStripRules {
    const val FOCUSED_W = 520f
    const val FOCUSED_H = 160f
    const val NEIGHBOUR_SCALE = 0.45f
    const val NEIGHBOUR_ALPHA = 0.35f
    const val SMALL_W = FOCUSED_W * NEIGHBOUR_SCALE
    const val GAP = 80f

    /** Centre of the focused slot from the view's left edge: one whole neighbour fits to its left. */
    const val ANCHOR = SMALL_W + GAP + FOCUSED_W / 2f

    const val UNDERLINE_W = 240f
    const val UNDERLINE_H = 4f

    /** From the bottom of the logo box to the underline, and from the underline to the info line. */
    const val UNDERLINE_GAP = 18f
    const val INFO_GAP = 18f
    const val INFO_H = 30f

    /** How far the strip sits above the view's centre so strip + underline + info line are centred. */
    const val LIFT = (UNDERLINE_GAP + UNDERLINE_H + INFO_GAP + INFO_H) / 2f

    const val RANK_W = 64f

    private const val FIRST_STEP = FOCUSED_W / 2f + GAP + SMALL_W / 2f
    private const val STEP = SMALL_W + GAP

    /** Size of a logo [distance] slots from the focused one (fractional while sliding), 1 = focused. */
    fun scaleFor(distance: Float): Float = 1f - min(abs(distance), 1f) * (1f - NEIGHBOUR_SCALE)

    /** Opacity of a logo [distance] slots from the focused one. */
    fun alphaFor(distance: Float): Float = 1f - min(abs(distance), 1f) * (1f - NEIGHBOUR_ALPHA)

    /** Horizontal offset of a logo box's centre from the focused slot's centre. */
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

    /** Left at the first logo is kept; Right at the last is let go so focus reaches the shelf panel. */
    fun consumes(index: Int, delta: Int, count: Int): Boolean =
        move(index, delta, count) != null || delta < 0

    /** The card's clearlogo, or null when it has none (then its name is drawn instead). */
    fun logoUrl(card: ShelfCard): String? = when (card) {
        is ShelfCard.Meta -> card.item.logo
        is ShelfCard.Progress -> card.item.logo
        is ShelfCard.Folder -> card.folder.titleLogoUrl
    }?.takeIf { it.isNotBlank() }

    /** The name drawn when there is no logo: capitals, trimmed. */
    fun nameLabel(card: ShelfCard): String = card.title().trim().uppercase()

    /**
     * The line under the strip:
     *  - a film or show: "2019 · Drama, Comedy" (year and up to three genres, either may be missing);
     *  - a Continue Watching card: "S4 E1", or the year when it is not an episode;
     *  - a folder: nothing.
     */
    fun infoLine(card: ShelfCard): String = when (card) {
        is ShelfCard.Meta -> {
            val year = ShelfLedgerRules.year(card.item.releaseInfo) ?: ShelfLedgerRules.year(card.item.rawReleaseDate)
            val genres = card.item.genres.map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(", ")
            listOfNotNull(year, genres.takeIf { it.isNotEmpty() }).joinToString(" · ")
        }
        is ShelfCard.Progress -> {
            val item = card.item
            if (item.seasonNumber != null && item.episodeNumber != null) {
                "S${item.seasonNumber} E${item.episodeNumber}"
            } else {
                ShelfLedgerRules.year(item.released).orEmpty()
            }
        }
        is ShelfCard.Folder -> ""
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ShelfLogoStripView(state: ShelfViewState, modifier: Modifier = Modifier) {
    val rules = ShelfLogoStripRules
    val accent = MaterialTheme.nuvio.colors.accent
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
                StripLogo(
                    card = card,
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
                            translationY = (-rules.LIFT).dp.toPx()
                            alpha = rules.alphaFor(d)
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

        // Rank just left of the focused slot, in the gap before it.
        if (state.ranked && focusedCard != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = (rules.ANCHOR - rules.FOCUSED_W / 2f - rules.RANK_W - 8f).dp.toPx()
                        translationY = (-rules.LIFT).dp.toPx()
                    }
                    .size(rules.RANK_W.dp, rules.FOCUSED_H.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    "${index + 1}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    color = accent,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                )
            }
        }

        if (focusedCard != null) {
            // The underline under the focused slot: white, accent while the view has focus.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = (rules.ANCHOR - rules.UNDERLINE_W / 2f).dp.toPx()
                        translationY = (-rules.LIFT + rules.FOCUSED_H / 2f + rules.UNDERLINE_GAP + rules.UNDERLINE_H / 2f).dp.toPx()
                    }
                    .size(rules.UNDERLINE_W.dp, rules.UNDERLINE_H.dp)
                    .background(if (hasFocus) accent else LocalShelfPalette.current.ink),
            )

            // One line under it: year and genres, the episode, or nothing.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = (rules.ANCHOR - rules.FOCUSED_W / 2f).dp.toPx()
                        translationY = (
                            -rules.LIFT + rules.FOCUSED_H / 2f + rules.UNDERLINE_GAP + rules.UNDERLINE_H +
                                rules.INFO_GAP + rules.INFO_H / 2f
                            ).dp.toPx()
                    }
                    .size(rules.FOCUSED_W.dp, rules.INFO_H.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rules.infoLine(focusedCard),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = LocalShelfPalette.current.inkSoft,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StripLogo(card: ShelfCard, modifier: Modifier) {
    val url = ShelfLogoStripRules.logoUrl(card)
    // A logo that fails to load falls back to the name, so the strip never shows a hole.
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (url != null && !failed) {
            NuvioAsyncImage(
                model = url,
                contentDescription = card.title(),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                ShelfLogoStripRules.nameLabel(card),
                fontSize = 54.sp,
                lineHeight = 58.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                color = LocalShelfPalette.current.ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
