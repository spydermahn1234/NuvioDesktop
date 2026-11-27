// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/*
 * The Logos view of the fan-art home (mockup V2Logos): the focused row drawn as a line of clearlogos
 * instead of posters, and the title block's name drawn as the focused title's clearlogo.
 *
 * Both pieces are stateless: the frame (ShelfHome) owns focus and keys and passes the focused index.
 * Nothing here is focusable; a click only reports the card's index.
 */

/** The Logos view's sizes (dp / sp) and the pure rules it draws by, so they can be tested. */
internal object ShelfLogoStripRules {
    /** The logo row: 110 high, 64 between logos. */
    const val ROW_H = 110f
    const val GAP = 64f

    /** A logo image's height: the focused one, and the others (the mockup's 58 px and ~40 px type). */
    const val FOCUSED_LOGO_H = 70f
    const val LOGO_H = 48f

    /** The bar under the focused logo, and the space above it. */
    const val BAR_H = 3f
    const val BAR_GAP = 12f

    /** Logos other than the focused one: greyed out and at 60%; the focused one in full colour. */
    const val DIM_ALPHA = 0.6f
    const val DIM_SATURATION = 0f

    /**
     * Logos are sized by area, not by height: a wide wordmark gets shorter and a square badge taller,
     * so every logo carries about the same weight. A logo of [DEFAULT_ASPECT] keeps the base height;
     * the height never goes outside [MIN_H_SCALE]..[MAX_H_SCALE] of it.
     */
    const val MIN_H_SCALE = 0.6f
    const val MAX_H_SCALE = 1.35f

    /** The bar under each logo: its track and its filled part, focused and not. */
    const val TRACK_ALPHA = 0.22f
    const val FOCUSED_TRACK_ALPHA = 0.3f
    const val FILL_ALPHA = 0.55f

    /** The name drawn when a title has no logo: focused and the others. */
    const val FOCUSED_NAME_SP = 58f
    const val NAME_SP = 40f

    /** The title block's logo: at most 900 wide and 170 high, its box always 170 high. */
    const val TITLE_MAX_W = 900f
    const val TITLE_H = 170f
    const val TITLE_NAME_SP = 150f

    /** A name with no logo shrinks to fit: down to this size, on at most this many lines. */
    const val TITLE_NAME_MIN_SP = 40f
    const val TITLE_NAME_LINES = 2

    /** The next size to try for a name that does not fit (about 8% smaller each step). */
    fun shrink(sp: Float): Float = (sp * 0.92f).coerceAtLeast(TITLE_NAME_MIN_SP)

    /** True when a line ending at [end] in [text] splits a word (no space either side of the break). */
    fun breaksMidWord(text: String, end: Int): Boolean =
        end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() &&
            text[end - 1] != '-'
    const val TITLE_FADE_MS = 600

    /** Width over height of a logo before its picture has loaded (fanart.tv clearlogos are 800 x 310). */
    const val DEFAULT_ASPECT = 800f / 310f
    const val MIN_ASPECT = 0.8f
    const val MAX_ASPECT = 6f

    /** Past this many items a focus change jumps instead of scrolling (a row swapped in, not a step). */
    const val JUMP_AFTER = 8

    fun clamp(index: Int, count: Int): Int = if (count <= 0) 0 else index.coerceIn(0, count - 1)

    /** A picture's width over height, kept inside sane bounds; [DEFAULT_ASPECT] when unknown. */
    fun aspect(widthPx: Int, heightPx: Int): Float =
        if (widthPx <= 0 || heightPx <= 0) DEFAULT_ASPECT
        else (widthPx.toFloat() / heightPx.toFloat()).coerceIn(MIN_ASPECT, MAX_ASPECT)

    /** A logo's width at [heightDp], from its [aspect] (null = not loaded yet). */
    fun logoWidth(heightDp: Float, aspect: Float?): Float =
        heightDp * (aspect ?: DEFAULT_ASPECT).coerceIn(MIN_ASPECT, MAX_ASPECT)

    /**
     * A logo's height for a base height of [baseDp] (the row's [LOGO_H] or [FOCUSED_LOGO_H]), so that
     * its area matches a [DEFAULT_ASPECT] logo's at that height; null (not loaded yet) keeps the base.
     */
    fun logoHeight(baseDp: Float, aspect: Float?): Float {
        if (aspect == null) return baseDp
        val a = aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        return (baseDp * sqrt(DEFAULT_ASPECT / a)).coerceIn(baseDp * MIN_H_SCALE, baseDp * MAX_H_SCALE)
    }

    /**
     * How far through a Continue Watching card is (0..1), for the bar under its logo; null for a card
     * with no progress to show (a film or show from a catalog, a folder). A card not started is 0.
     */
    fun progress(card: ShelfCard): Float? = when (card) {
        is ShelfCard.Progress -> {
            val item = card.item
            when {
                item.durationMs > 0L -> (item.resumePositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                else -> item.progressFraction.coerceIn(0f, 1f)
            }
        }
        else -> null
    }

    /** True when moving the row from [from] to [to] should jump rather than scroll smoothly. */
    fun jumps(from: Int, to: Int): Boolean = abs(to - from) > JUMP_AFTER

    /** The card's clearlogo, or null when it has none (then its name is drawn instead). */
    fun logoUrl(card: ShelfCard): String? = when (card) {
        is ShelfCard.Meta -> card.item.logo
        is ShelfCard.Progress -> card.item.logo
        is ShelfCard.Folder -> card.folder.titleLogoUrl
    }?.takeIf { it.isNotBlank() }

    /**
     * The row's scroll eases toward the focused logo: each frame it covers the share of the remaining
     * distance a [SCROLL_TAU_MS] time constant gives (about 95% in three of them), so it slows into
     * place and never overshoots. It stops after [SETTLE_FRAMES] frames with nothing to do, which
     * outlasts the 200 ms size change.
     */
    const val SCROLL_TAU_MS = 70f
    const val SETTLE_FRAMES = 15

    /** This frame's scroll toward a logo [deltaPx] away, [dtMs] after the last frame. */
    fun easeStep(deltaPx: Float, dtMs: Float): Float {
        if (abs(deltaPx) < 0.5f) return deltaPx
        return deltaPx * (1f - exp(-dtMs / SCROLL_TAU_MS))
    }

    /** How fast a logo at the row's edge fades out when it no longer fits, or back in when it does. */
    const val EDGE_FADE_MS = 150

    /**
     * True when a logo at [offset] (px, from the row's start) and [size] wide sits wholly inside the
     * row's [viewportStart]..[viewportEnd]. A logo that would be cut by either edge is not drawn at all:
     * the row shows a whole logo or none.
     */
    fun fullyInside(offset: Int, size: Int, viewportStart: Int, viewportEnd: Int): Boolean =
        offset >= viewportStart && offset + size <= viewportEnd

    /** The name drawn when there is no logo: capitals, trimmed. */
    fun nameLabel(card: ShelfCard): String = card.title().trim().uppercase()
}

/**
 * The logo row (V2Logos, top 880): every card's clearlogo in a line, 64 apart, sized by area. The
 * focused one is in full colour, the others greyed out at 60%. Under each logo, as wide as it, a 3 dp
 * bar: on a Continue Watching card a track filled as far as you got (brighter when focused); on any
 * other card only the focused one has it, filled. Every logo keeps the bar's room, so none moves
 * when the focus does. The row keeps the focused
 * logo's left edge at its start when it can (the last logos stop at the end) and is always
 * [ShelfLogoStripRules.ROW_H] high. A logo the row's edges would cut is hidden, not cropped.
 */
@Composable
internal fun ShelfLogoRow(
    cards: List<ShelfCard>,
    focusedIndex: Int,
    onClickCard: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rules = ShelfLogoStripRules
    val count = cards.size
    val focused = rules.clamp(focusedIndex, count)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focused)
    // Each logo's shape once its picture has loaded, by url, so its width is right at either size.
    val aspects = remember { mutableStateMapOf<String, Float>() }
    val click by rememberUpdatedState(onClickCard)
    // The logos wholly inside the row; one cut by either edge is faded out rather than cropped.
    val whole by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo
                .filter { rules.fullyInside(it.offset, it.size, info.viewportStartOffset, info.viewportEndOffset) }
                .mapTo(HashSet()) { it.index }
        }
    }

    LaunchedEffect(focused, count) {
        if (count == 0) return@LaunchedEffect
        if (rules.jumps(listState.firstVisibleItemIndex, focused)) {
            listState.scrollToItem(focused)
            return@LaunchedEffect
        }
        // Ease toward where the focused logo is now, every frame. animateScrollToItem measured the
        // distance once while the logos were still changing size, overshot and snapped back (a bounce
        // into the left edge); chasing the live position can't overshoot.
        listState.scroll {
            var last = 0L
            var still = 0
            while (still < rules.SETTLE_FRAMES) {
                val now = withFrameNanos { it }
                val dtMs = if (last == 0L) 16f else ((now - last) / 1_000_000f).coerceIn(1f, 50f)
                last = now
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == focused }
                val delta = when {
                    item != null -> (item.offset - info.viewportStartOffset).toFloat()
                    focused > (info.visibleItemsInfo.lastOrNull()?.index ?: focused) ->
                        (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                    else -> -(info.viewportEndOffset - info.viewportStartOffset).toFloat()
                }
                val step = rules.easeStep(delta, dtMs)
                val used = if (step == 0f) 0f else scrollBy(step)
                // Settled once there is nothing left to do, or the row can't go further (its end).
                still = if (abs(delta) < 0.5f || abs(used) < 0.5f) still + 1 else 0
            }
        }
    }

    LazyRow(
        state = listState,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(rules.GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(rules.ROW_H.dp),
    ) {
        itemsIndexed(cards) { index, card ->
            RowLogo(
                card = card,
                focused = index == focused,
                whole = index in whole,
                aspects = aspects,
                modifier = Modifier.pointerInput(index) {
                    detectTapGestures(onTap = { click(index) })
                },
            )
        }
    }
}

@Composable
private fun RowLogo(
    card: ShelfCard,
    focused: Boolean,
    whole: Boolean,
    aspects: MutableMap<String, Float>,
    modifier: Modifier = Modifier,
) {
    val rules = ShelfLogoStripRules
    val ink = LocalShelfPalette.current.ink
    val dim by animateFloatAsState(if (focused) 1f else rules.DIM_ALPHA, tween(200))
    val edge by animateFloatAsState(if (whole) 1f else 0f, tween(rules.EDGE_FADE_MS))
    val alpha = dim * edge
    val saturation by animateFloatAsState(if (focused) 1f else rules.DIM_SATURATION, tween(200))
    val url = rules.logoUrl(card)
    val base = if (focused) rules.FOCUSED_LOGO_H else rules.LOGO_H
    val logoH by animateDpAsState(rules.logoHeight(base, url?.let { aspects[it] }).dp, tween(200))
    val progress = rules.progress(card)
    // A logo that fails to load falls back to the name, so the row never shows a hole.
    var failed by remember(url) { mutableStateOf(false) }
    val greyed = remember(saturation) {
        if (saturation >= 1f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }

    Column(
        modifier.width(IntrinsicSize.Max).alpha(alpha),
        horizontalAlignment = Alignment.Start,
    ) {
        if (url != null && !failed) {
            val w = rules.logoWidth(logoH.value, aspects[url])
            NuvioAsyncImage(
                model = url,
                contentDescription = card.title(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                colorFilter = greyed,
                onSuccess = { state ->
                    aspects[url] = rules.aspect(state.result.image.width, state.result.image.height)
                },
                onError = { failed = true },
                modifier = Modifier.width(w.dp).height(logoH),
            )
        } else {
            Text(
                rules.nameLabel(card),
                fontSize = (if (focused) rules.FOCUSED_NAME_SP else rules.NAME_SP).sp,
                lineHeight = (if (focused) rules.FOCUSED_NAME_SP else rules.NAME_SP).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(rules.BAR_GAP.dp))
        Box(Modifier.fillMaxWidth().height(rules.BAR_H.dp)) {
            when {
                progress != null -> {
                    // The row's alpha already dims an unfocused logo; these are on top of it.
                    Box(Modifier.fillMaxSize().background(ink.copy(alpha = if (focused) rules.FOCUSED_TRACK_ALPHA else rules.TRACK_ALPHA)))
                    if (progress > 0f) {
                        Box(
                            Modifier.fillMaxWidth(progress).height(rules.BAR_H.dp)
                                .background(ink.copy(alpha = if (focused) 1f else rules.FILL_ALPHA)),
                        )
                    }
                }
                focused -> Box(Modifier.fillMaxSize().background(ink))
            }
        }
    }
}

/**
 * The title block's title in the Logos view: the focused title's clearlogo, fitted inside 900 x 170
 * and sat on the bottom-left; with no logo, its name in big capitals. Always 170 high, so the facts
 * line below never moves; cross-fades over 600 ms when the card changes.
 */
@Composable
internal fun ShelfTitleLogo(card: ShelfCard?, modifier: Modifier = Modifier) {
    val rules = ShelfLogoStripRules
    Crossfade(
        targetState = card,
        animationSpec = tween(rules.TITLE_FADE_MS),
        modifier = modifier.widthIn(max = rules.TITLE_MAX_W.dp).fillMaxWidth().height(rules.TITLE_H.dp),
    ) { shown ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            if (shown != null) TitleLogoContent(shown)
        }
    }
}

@Composable
private fun TitleLogoContent(card: ShelfCard) {
    val rules = ShelfLogoStripRules
    val url = rules.logoUrl(card)
    var failed by remember(url) { mutableStateOf(false) }
    if (url != null && !failed) {
        // Nothing is drawn while it loads (the name first and then the logo would flash).
        NuvioAsyncImage(
            model = url,
            contentDescription = card.title(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        TitleName(rules.nameLabel(card))
    }
}

/**
 * The title block's name when there is no logo: as big as [ShelfLogoStripRules.TITLE_NAME_SP] when it
 * fits, otherwise shrunk step by step until it fits the box whole, on at most two lines broken only
 * between words. Never cut short with "...". Hidden until it fits, so no oversized frame flashes.
 */
@Composable
private fun TitleName(label: String) {
    val rules = ShelfLogoStripRules
    var size by remember(label) { mutableStateOf(rules.TITLE_NAME_SP) }
    var fits by remember(label) { mutableStateOf(false) }
    Text(
        label,
        fontSize = size.sp,
        lineHeight = (size * 0.9f).sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (3f * size / rules.TITLE_NAME_SP).sp,
        color = LocalShelfPalette.current.ink,
        maxLines = rules.TITLE_NAME_LINES,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            val midWord = (0 until layout.lineCount - 1).any { line ->
                rules.breaksMidWord(label, layout.getLineEnd(line))
            }
            val tooBig = layout.hasVisualOverflow || midWord
            if (tooBig && size > rules.TITLE_NAME_MIN_SP) {
                size = rules.shrink(size)
            } else {
                fits = true
            }
        },
        modifier = Modifier.alpha(if (fits) 1f else 0f),
    )
}
