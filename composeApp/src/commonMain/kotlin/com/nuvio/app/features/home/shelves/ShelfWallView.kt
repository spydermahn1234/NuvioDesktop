// fork:shelf-home
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.nuvio

/*
 * fork:shelf-home - the Wall view (mockups' "Everything" shelf): a dense grid of 2:3 posters for big
 * catalogs, a one-line title under each. The column count comes from the view's width once, so every
 * cell has the same fixed size; focus only changes colours (an accent ring that is always laid out,
 * a lifted dim), so nothing moves or resizes. The shelf panel is to the right: Compose's focus search
 * leaves the grid from its last column.
 */

/** The Wall's geometry, in dp. Pure, so it is tested without Compose. */
internal object ShelfWallRules {
    /** Posters are at least this wide; the columns share whatever is left over. */
    const val MIN_POSTER_W = 140f
    const val GAP = 14f
    const val POSTER_RATIO = 2f / 3f
    const val TITLE_GAP = 6f
    const val TITLE_H = 22f

    /** As many columns of at least [MIN_POSTER_W] as fit in [widthDp] with [GAP] between them (at least 1). */
    fun columns(widthDp: Float): Int {
        if (widthDp <= 0f) return 1
        return ((widthDp + GAP) / (MIN_POSTER_W + GAP)).toInt().coerceAtLeast(1)
    }

    /** Width of one poster when [columns] share [widthDp]. */
    fun posterWidth(widthDp: Float, columns: Int): Float {
        val c = columns.coerceAtLeast(1)
        return ((widthDp - GAP * (c - 1)) / c).coerceAtLeast(0f)
    }

    fun posterHeight(posterWidth: Float): Float = posterWidth / POSTER_RATIO

    /** One cell's height: poster, gap, title line. */
    fun cellHeight(posterWidth: Float): Float = posterHeight(posterWidth) + TITLE_GAP + TITLE_H

    /** Rows at least partly visible in [heightDp] (a row cut off at the bottom counts). */
    fun rowsVisible(heightDp: Float, posterWidth: Float): Int {
        val row = cellHeight(posterWidth) + GAP
        if (heightDp <= 0f || row <= 0f) return 0
        return kotlin.math.ceil((heightDp + GAP) / row).toInt()
    }

    /** "1", "2", ... on ranked shelves; nothing otherwise. */
    fun rankLabel(index: Int, ranked: Boolean): String = if (ranked && index >= 0) "${index + 1}" else ""
}

private val WallPosterShape = RoundedCornerShape(6.dp)
private val WallTileColor: Color
    @Composable @ReadOnlyComposable get() = LocalShelfPalette.current.tile
private val WallDim: Color
    @Composable @ReadOnlyComposable get() = LocalShelfPalette.current.posterDim

@Composable
internal fun ShelfWallView(state: ShelfViewState, modifier: Modifier = Modifier) {
    val rules = ShelfWallRules
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.shelfKey) { runCatching { gridState.scrollToItem(0) } }

    val nearEnd by rememberUpdatedState(state.onNearEnd)
    LaunchedEffect(gridState, state.shelfKey) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (last >= 0) nearEnd(last) }
    }

    BoxWithConstraints(modifier) {
        val columns = rules.columns(maxWidth.value)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(rules.GAP.dp),
            verticalArrangement = Arrangement.spacedBy(rules.GAP.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.cards, key = { index, _ -> "${state.shelfKey}:$index" }) { index, card ->
                WallCell(
                    card = card,
                    rank = rules.rankLabel(index, state.ranked),
                    onFocus = { state.onFocusCard(card) },
                    onClick = { state.onOpen(card) },
                    onLongClick = state.onLongPress?.let { press -> { press(card) } },
                )
            }
        }
    }
}

@Composable
private fun WallCell(
    card: ShelfCard,
    rank: String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = MaterialTheme.nuvio.colors
    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ShelfWallRules.POSTER_RATIO)
                .clip(WallPosterShape)
                .background(WallTileColor),
        ) {
            // Folder cards: posterUrl() is the folder's cover.
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalShelfPalette.current.placeholderInk,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
            }
            // Unfocused posters sit slightly dimmed; the focused one is lifted to full brightness.
            Box(Modifier.fillMaxSize().background(if (focused) Color.Transparent else WallDim))
            if (rank.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(width = 34.dp, height = 26.dp)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        rank,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = LocalShelfPalette.current.focusText,
                        maxLines = 1,
                    )
                }
            }
            // The ring is always laid out (drawn last, over the image); only its colour changes.
            Box(
                Modifier
                    .fillMaxSize()
                    .border(3.dp, if (focused) colors.accent else Color.Transparent, WallPosterShape),
            )
        }
        Spacer(Modifier.height(ShelfWallRules.TITLE_GAP.dp))
        Text(
            card.title(),
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) LocalShelfPalette.current.accentInk else LocalShelfPalette.current.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().height(ShelfWallRules.TITLE_H.dp),
        )
    }
}
