// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.secondaryClick

/*
 * fork:shelf-home - the Posters row of the fan-art-first home (mockup V2Browse): posters 100 x 150 at
 * 55%, gap 16, the focused one 132 x 198 at full strength with a 3 dp white outline 4 dp outside it
 * and its name in small tracked capitals inside at the bottom. Bottom-aligned, so the row's baseline
 * never moves. The row does not take focus: ShelfHome owns the selection and the keys, and this only
 * draws [focusedIndex], scrolling smoothly so it stays in view (three cards before it, as the
 * prototype's window does) and never clips the first or the last card.
 */

internal object ShelfPosterRowRules {
    const val CARD_W = 100
    const val CARD_H = 150
    const val FOCUSED_W = 132
    const val FOCUSED_H = 198
    const val GAP = 16
    const val OUTLINE = 3
    const val OUTLINE_OFFSET = 4

    /** Room around the cards so the focused card's outline is never clipped: offset + width. */
    const val RING_ROOM = OUTLINE_OFFSET + OUTLINE

    /** The row's height: the focused card plus its outline above and below. */
    const val HEIGHT = FOCUSED_H + 2 * RING_ROOM

    /** Cards kept before the focused one when the row scrolls. */
    const val LEAD = 3

    fun firstVisible(focusedIndex: Int): Int = (focusedIndex - LEAD).coerceAtLeast(0)
}

/**
 * The row's left edge is the screen's 96 dp margin less [ShelfPosterRowRules.RING_ROOM] (the caller
 * places it); the row runs to the right edge with 60 dp of end padding.
 */
@Composable
internal fun ShelfPosterRow(
    cards: List<ShelfCard>,
    focusedIndex: Int,
    onClickCard: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSecondaryClickCard: ((Int) -> Unit)? = null,
) {
    val rules = ShelfPosterRowRules
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rules.firstVisible(focusedIndex))
    LaunchedEffect(focusedIndex, cards.size) {
        if (cards.isEmpty()) return@LaunchedEffect
        runCatching { listState.animateScrollToItem(rules.firstVisible(focusedIndex)) }
    }
    LazyRow(
        state = listState,
        // Arrows and the wheel are ShelfHome's; the row scrolls only to follow the selection.
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(rules.GAP.dp),
        verticalAlignment = Alignment.Bottom,
        contentPadding = PaddingValues(
            start = rules.RING_ROOM.dp,
            end = 60.dp,
            top = rules.RING_ROOM.dp,
            bottom = rules.RING_ROOM.dp,
        ),
        modifier = modifier.fillMaxWidth().height(rules.HEIGHT.dp),
    ) {
        itemsIndexed(cards, key = { index, card -> "$index:${card.stableId()}" }) { index, card ->
            PosterCard(
                card = card,
                focused = index == focusedIndex,
                onClick = { onClickCard(index) },
                onSecondaryClick = onSecondaryClickCard?.let { { it(index) } },
            )
        }
    }
}

@Composable
private fun PosterCard(
    card: ShelfCard,
    focused: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: (() -> Unit)?,
) {
    val rules = ShelfPosterRowRules
    val palette = LocalShelfPalette.current
    val motion = tween<Dp>(200)
    val width by animateDpAsState(if (focused) rules.FOCUSED_W.dp else rules.CARD_W.dp, motion, label = "posterW")
    val height by animateDpAsState(if (focused) rules.FOCUSED_H.dp else rules.CARD_H.dp, motion, label = "posterH")
    val strength by animateFloatAsState(if (focused) 1f else 0.55f, tween(200), label = "posterAlpha")
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "posterRing")
    val ringColor = palette.ink
    val click by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .size(width, height)
            .drawWithContent {
                drawContent()
                if (ring > 0f) {
                    val stroke = rules.OUTLINE.dp.toPx()
                    val out = rules.OUTLINE_OFFSET.dp.toPx() + stroke / 2f
                    drawRect(
                        color = ringColor.copy(alpha = ring),
                        topLeft = Offset(-out, -out),
                        size = Size(size.width + 2 * out, size.height + 2 * out),
                        style = Stroke(width = stroke),
                    )
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { click() }) }
            .secondaryClick(onSecondaryClick),
    ) {
        Box(Modifier.fillMaxSize().alpha(strength).background(palette.placeholder)) {
            val url = card.posterUrl()
            if (url != null) {
                NuvioAsyncImage(
                    model = url,
                    contentDescription = card.title(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // No poster: the name, so the card is still a title.
                Text(
                    card.title(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.placeholderInk,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
        }
        if (focused) {
            // The name inside, at the bottom, over a soft shade so it reads on a pale poster.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
            )
            Text(
                card.title().uppercase(),
                fontSize = 12.sp,
                letterSpacing = 1.7.sp,
                lineHeight = 14.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
    }
}
