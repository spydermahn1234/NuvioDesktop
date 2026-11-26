// fork:shelf-home
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.tonight.clockLabel

/*
 * fork:shelf-home - the Ledger view (mockup "Tonight - Ledger view"): the shelf as a big-type text
 * list, no artwork, over the frame's backdrop. Each row: [rank or nothing] [title] [length · ends].
 * Every row is the same fixed height and every column a fixed width, so nothing moves or resizes
 * as focus or content changes; the focused row gets an accent bar and an accent, SemiBold title.
 */

/** The Ledger's words, pure so they are tested without Compose. */
internal object ShelfLedgerRules {
    private val yearPattern = Regex("""\b(\d{4})\b""")

    /** "1", "2", ... on ranked shelves; nothing otherwise. */
    fun rankLabel(index: Int, ranked: Boolean): String = if (ranked && index >= 0) "${index + 1}" else ""

    /** The first four-digit year in a release line ("2019", "2019-2023", "2019-05-01"), or null. */
    fun year(text: String?): String? = text?.let { yearPattern.find(it)?.groupValues?.get(1) }

    /**
     * The right-hand column:
     *  - a film or show with a length: "1h 49m · 21:16" (starts now, ends then);
     *  - a part-watched item: "1h 16m left · 20:41"; one not yet started (Up Next): "52m · 20:17";
     *  - no length: the year, or the episode ("S4 E1") for a Continue Watching card without one;
     *  - a folder: nothing.
     * [clock] formats a minute of the day (the screen passes Tonight's clockLabel).
     */
    fun rightLabel(card: ShelfCard, nowMinute: Int, clock: (Int) -> String): String = when (card) {
        is ShelfCard.Meta -> {
            val minutes = ShelfTime.runtimeMinutes(card.item.runtime)
            if (minutes != null) {
                "${ShelfTime.length(minutes)} · ${clock(ShelfTime.endsAt(nowMinute, minutes))}"
            } else {
                year(card.item.releaseInfo) ?: year(card.item.rawReleaseDate).orEmpty()
            }
        }
        is ShelfCard.Progress -> {
            val item = card.item
            val left = ShelfTime.minutesLeft(item.durationMs, item.resumePositionMs)
            if (left != null) {
                val started = item.resumePositionMs > 0L
                val length = ShelfTime.length(left) + if (started) " left" else ""
                "$length · ${clock(ShelfTime.endsAt(nowMinute, left))}"
            } else if (item.seasonNumber != null && item.episodeNumber != null) {
                "S${item.seasonNumber} E${item.episodeNumber}"
            } else {
                year(item.released).orEmpty()
            }
        }
        is ShelfCard.Folder -> ""
    }
}

private val LedgerRowHeight = 64.dp
private val LedgerLeadWidth = 56.dp
private val LedgerRightWidth = 330.dp
private val LedgerBarWidth = 4.dp
private val LedgerInk: Color
    @Composable @ReadOnlyComposable get() = LocalShelfPalette.current.ink
private val LedgerInkSoft: Color
    @Composable @ReadOnlyComposable get() = LocalShelfPalette.current.inkSoft
private val LedgerInkDim: Color
    @Composable @ReadOnlyComposable get() = LocalShelfPalette.current.inkDim

@Composable
internal fun ShelfLedgerView(state: ShelfViewState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.shelfKey) { runCatching { listState.scrollToItem(0) } }

    val nearEnd by rememberUpdatedState(state.onNearEnd)
    LaunchedEffect(listState, state.shelfKey) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (last >= 0) nearEnd(last) }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(end = 40.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(state.cards, key = { index, _ -> "${state.shelfKey}:$index" }) { index, card ->
            LedgerRow(
                rank = ShelfLedgerRules.rankLabel(index, state.ranked),
                title = card.title(),
                right = ShelfLedgerRules.rightLabel(card, state.nowMinute) { clockLabel(it) },
                onFocus = { state.onFocusCard(card) },
                onClick = { state.onOpen(card) },
                onLongClick = state.onLongPress?.let { press -> { press(card) } },
            )
        }
    }
}

@Composable
private fun LedgerRow(
    rank: String,
    title: String,
    right: String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LedgerRowHeight)
            .background(if (focused) LocalShelfPalette.current.rowFocus else Color.Transparent)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The accent bar is always laid out; only its colour changes with focus.
        Box(
            Modifier
                .width(LedgerBarWidth)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .background(if (focused) colors.accent else Color.Transparent),
        )
        Box(Modifier.width(LedgerLeadWidth), contentAlignment = Alignment.Center) {
            Text(
                rank,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                color = if (focused) LocalShelfPalette.current.accentInk else LedgerInkDim,
                maxLines = 1,
            )
        }
        Text(
            title,
            fontSize = 40.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Light,
            color = if (focused) LocalShelfPalette.current.accentInk else LedgerInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(24.dp))
        Text(
            right,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            color = if (focused) LedgerInk else LedgerInkSoft,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(LedgerRightWidth),
        )
    }
}
