package com.nuvio.app.features.home.shelves

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem

/*
 * fork:shelf-home - what every shelf view is given, so the views (Strip, Ledger, Spotlight, ...) are
 * interchangeable inside ShelfHome's frame. See design-notes/home-shelves.md.
 */

/** One card on a shelf, whatever the shelf's source. */
internal sealed interface ShelfCard {
    data class Meta(val item: MetaPreview) : ShelfCard
    data class Progress(val item: ContinueWatchingItem) : ShelfCard
    data class Folder(val collectionId: String, val folder: CollectionFolder) : ShelfCard
}

internal fun ShelfCard.title(): String = when (this) {
    is ShelfCard.Meta -> item.name
    is ShelfCard.Progress -> item.title
    is ShelfCard.Folder -> folder.title
}

internal fun ShelfCard.posterUrl(): String? = when (this) {
    is ShelfCard.Meta -> item.poster ?: item.posterFallback
    is ShelfCard.Progress -> item.poster ?: item.imageUrl
    is ShelfCard.Folder -> folder.coverImageUrl
}?.takeIf { it.isNotBlank() }

internal fun ShelfCard.backdropUrl(): String? = when (this) {
    is ShelfCard.Meta -> item.banner ?: item.landscapePoster ?: item.poster
    is ShelfCard.Progress -> item.background ?: item.customLandscape ?: item.imageUrl ?: item.poster
    is ShelfCard.Folder -> folder.heroBackdropUrl ?: folder.coverImageUrl
}?.takeIf { it.isNotBlank() }

/** How a shelf is drawn. Strip is the classic row of cards. */
enum class ShelfViewMode { Strip, Ledger, Spotlight, Wall, Logos }

/**
 * Everything a view needs. A view fills the area ShelfHome gives it (below the title block, left of
 * the shelf panel; about 1300 x 560 dp on a 1080p TV) and must:
 *  - make each card focusable and call [onFocusCard] when it gains focus (the frame shows its
 *    backdrop and details) and [onOpen] when it is selected (OK / click);
 *  - call [onNearEnd] with the index of the last card it shows, so the focused shelf can page;
 *  - keep its own layout fixed in size: nothing may move or resize as focus or content changes.
 * Left/Right/Up/Down between the view, the shelf panel (on the right) and the slash menu (above)
 * is left to Compose's focus search.
 */
internal class ShelfViewState(
    val shelfKey: String,
    val cards: List<ShelfCard>,
    /** Minutes after midnight, for "ends 9:30 PM" lines (format with tonight.clockLabel). */
    val nowMinute: Int,
    /** True for ranked rows (Top 10, best this week): number the cards. */
    val ranked: Boolean,
    val onFocusCard: (ShelfCard) -> Unit,
    val onOpen: (ShelfCard) -> Unit,
    val onLongPress: ((ShelfCard) -> Unit)?,
    val onNearEnd: (lastShownIndex: Int) -> Unit,
)

/** A view: a composable drawing [ShelfViewState] into the given modifier's area. */
internal typealias ShelfView = @Composable (state: ShelfViewState, modifier: Modifier) -> Unit
