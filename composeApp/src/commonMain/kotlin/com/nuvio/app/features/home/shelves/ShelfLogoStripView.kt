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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import kotlin.math.abs

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

    /** Logos other than the focused one. */
    const val DIM_ALPHA = 0.55f

    /** The name drawn when a title has no logo: focused and the others. */
    const val FOCUSED_NAME_SP = 58f
    const val NAME_SP = 40f

    /** The title block's logo: at most 900 wide and 170 high, its box always 170 high. */
    const val TITLE_MAX_W = 900f
    const val TITLE_H = 170f
    const val TITLE_NAME_SP = 150f
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

    /** True when moving the row from [from] to [to] should jump rather than scroll smoothly. */
    fun jumps(from: Int, to: Int): Boolean = abs(to - from) > JUMP_AFTER

    /** The card's clearlogo, or null when it has none (then its name is drawn instead). */
    fun logoUrl(card: ShelfCard): String? = when (card) {
        is ShelfCard.Meta -> card.item.logo
        is ShelfCard.Progress -> card.item.logo
        is ShelfCard.Folder -> card.folder.titleLogoUrl
    }?.takeIf { it.isNotBlank() }

    /** The name drawn when there is no logo: capitals, trimmed. */
    fun nameLabel(card: ShelfCard): String = card.title().trim().uppercase()
}

/**
 * The logo row (V2Logos, top 880): every card's clearlogo in a line, 64 apart, the focused one full
 * white with a 3 dp bar under it as wide as the logo, the others at 55%. The row keeps the focused
 * logo's left edge at its start when it can (the last logos stop at the end) and is always
 * [ShelfLogoStripRules.ROW_H] high.
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

    LaunchedEffect(focused, count) {
        if (count == 0) return@LaunchedEffect
        if (rules.jumps(listState.firstVisibleItemIndex, focused)) {
            listState.scrollToItem(focused)
        } else {
            listState.animateScrollToItem(focused)
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
    aspects: MutableMap<String, Float>,
    modifier: Modifier = Modifier,
) {
    val rules = ShelfLogoStripRules
    val ink = LocalShelfPalette.current.ink
    val alpha by animateFloatAsState(if (focused) 1f else rules.DIM_ALPHA, tween(200))
    val logoH by animateDpAsState((if (focused) rules.FOCUSED_LOGO_H else rules.LOGO_H).dp, tween(200))
    val url = rules.logoUrl(card)
    // A logo that fails to load falls back to the name, so the row never shows a hole.
    var failed by remember(url) { mutableStateOf(false) }

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
        if (focused) {
            Spacer(Modifier.height(rules.BAR_GAP.dp))
            Box(Modifier.fillMaxWidth().height(rules.BAR_H.dp).background(ink))
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
        Text(
            rules.nameLabel(card),
            fontSize = rules.TITLE_NAME_SP.sp,
            lineHeight = (rules.TITLE_NAME_SP * 0.9f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = LocalShelfPalette.current.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
