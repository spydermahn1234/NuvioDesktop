package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.delay

/*
 * fork:shelf-home - the Kodi-style home (plan in design-notes/home-shelves.md).
 *
 * Phase 2: the real layout. A slash menu of tabs along the top, the tab's shelf names down the
 * left, and the focused shelf on the right with the focused title's details above it. Moving onto a
 * tab or a shelf name shows it (as Kodi's menus do); Right goes into the shelf, Left comes back.
 * Shelves draw with the classic home's own cards for now (the Strip view); phase 4 on add views.
 * Only the focused shelf asks for more pages.
 */

/** Where the shelf home was, kept for the session so coming back to Home lands in the same place. */
internal object ShelfHomeMemory {
    var tabId: String = ShelfHomeRules.HOME_TAB
    val shelfByTab = HashMap<String, String>()
}

/** One card on a shelf, whatever the shelf's source. */
private sealed interface ShelfCard {
    data class Meta(val item: MetaPreview) : ShelfCard
    data class Progress(val item: ContinueWatchingItem) : ShelfCard
    data class Folder(val collectionId: String, val folder: CollectionFolder) : ShelfCard
}

@Composable
fun ShelfHome(
    model: ShelfHomeModel,
    sections: Map<String, HomeCatalogSection>,
    collections: Map<String, Collection>,
    continueWatching: List<ContinueWatchingItem>,
    upNext: List<ContinueWatchingItem>,
    upcoming: List<ContinueWatchingItem>,
    modifier: Modifier = Modifier,
    topPadding: Dp = 72.dp,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)? = null,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
    onLoadMoreCatalog: ((HomeCatalogSection) -> Unit)? = null,
) {
    val colors = MaterialTheme.nuvio.colors
    val contentTabs = model.tabs.filter { it.kind != ShelfTabKind.Library }
    var tabId by remember { mutableStateOf(ShelfHomeMemory.tabId) }
    val tab = contentTabs.firstOrNull { it.id == tabId } ?: contentTabs.firstOrNull()
    var shelfKey by remember(tab?.id) { mutableStateOf(tab?.id?.let { ShelfHomeMemory.shelfByTab[it] }) }
    val shelf = tab?.shelves?.firstOrNull { it.key == shelfKey } ?: tab?.shelves?.firstOrNull()
    var focusedCard by remember(shelf?.key) { mutableStateOf<ShelfCard?>(null) }

    fun selectTab(id: String) {
        tabId = id
        ShelfHomeMemory.tabId = id
    }
    fun selectShelf(key: String) {
        val t = tab ?: return
        shelfKey = key
        ShelfHomeMemory.shelfByTab[t.id] = key
    }

    val cards: List<ShelfCard> = remember(shelf, sections, collections, continueWatching, upNext, upcoming) {
        when (val source = shelf?.source) {
            null -> emptyList()
            is ShelfSource.Catalog -> sections[source.sectionKey]?.items.orEmpty().map(ShelfCard::Meta)
            ShelfSource.ContinueWatching -> continueWatching.map(ShelfCard::Progress)
            ShelfSource.UpNext -> upNext.map(ShelfCard::Progress)
            ShelfSource.Upcoming -> upcoming.map(ShelfCard::Progress)
            is ShelfSource.CollectionFolders ->
                collections[ShelfHomeRules.collectionTabId(source.collectionId)]?.folders.orEmpty()
                    .map { ShelfCard.Folder(source.collectionId, it) }
            is ShelfSource.CollectionFolder ->
                collections[ShelfHomeRules.collectionTabId(source.collectionId)]?.folders.orEmpty()
                    .filter { it.id == source.folderId }
                    .map { ShelfCard.Folder(source.collectionId, it) }
        }
    }
    val section = (shelf?.source as? ShelfSource.Catalog)?.let { sections[it.sectionKey] }

    // Land on the shelf list when Home opens, so the remote has somewhere to start.
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { entry.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 72.dp, end = 0.dp, bottom = 32.dp),
    ) {
        // The slash menu.
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            model.tabs.forEachIndexed { index, t ->
                if (index > 0) Text("/", fontSize = 34.sp, fontWeight = FontWeight.Light, color = colors.accent)
                TabLabel(
                    title = t.title,
                    selected = t.id == tab?.id,
                    onFocus = { if (t.kind != ShelfTabKind.Library) selectTab(t.id) },
                    onClick = {
                        if (t.kind == ShelfTabKind.Library) onOpenLibrary?.invoke() else selectTab(t.id)
                    },
                )
            }
        }
        Spacer(Modifier.height(36.dp))

        Row(Modifier.fillMaxSize()) {
            // Shelf names.
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val shelves = tab?.shelves.orEmpty()
                itemsIndexed(shelves, key = { _, s -> s.key }) { _, s ->
                    val isCurrent = s.key == shelf?.key
                    ShelfName(
                        title = s.title,
                        selected = isCurrent,
                        modifier = if (isCurrent) Modifier.focusRequester(entry) else Modifier,
                        onFocus = { selectShelf(s.key) },
                    )
                }
                if (shelves.isEmpty()) {
                    item {
                        Text(
                            "Nothing here yet",
                            fontSize = 20.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(40.dp))

            // The focused shelf.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    shelf?.title.orEmpty(),
                    fontSize = 16.sp,
                    letterSpacing = 3.sp,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                FocusedDetails(focusedCard ?: cards.firstOrNull())
                Spacer(Modifier.height(24.dp))

                val rowState = rememberLazyListState()
                LaunchedEffect(shelf?.key) { runCatching { rowState.scrollToItem(0) } }
                val currentSection by rememberUpdatedState(section)
                val loadMore by rememberUpdatedState(onLoadMoreCatalog)
                LaunchedEffect(rowState, shelf?.key) {
                    snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .collect { last ->
                            val s = currentSection ?: return@collect
                            if (s.nextSkip != null && !s.isLoadingMore && last >= s.items.size - 6) {
                                loadMore?.invoke(s)
                            }
                        }
                }
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(end = 72.dp, top = 6.dp, bottom = 6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(cards, key = { index, _ -> "${shelf?.key}:$index" }) { _, card ->
                        val focusModifier = Modifier.onFocusChanged { if (it.hasFocus) focusedCard = card }
                        when (card) {
                            is ShelfCard.Meta -> HomePosterCard(
                                item = card.item,
                                modifier = focusModifier,
                                onClick = onPosterClick?.let { { it(card.item) } },
                                onLongClick = onPosterLongClick?.let { { it(card.item) } },
                            )
                            is ShelfCard.Progress -> NuvioPosterCard(
                                title = card.item.title,
                                imageUrl = card.item.customLandscape ?: card.item.episodeThumbnail
                                    ?: card.item.background ?: card.item.imageUrl,
                                fallbackImageUrl = card.item.poster,
                                shape = NuvioPosterShape.Landscape,
                                detailLine = card.item.subtitle,
                                modifier = focusModifier,
                                onClick = onContinueWatchingClick?.let { { it(card.item) } },
                            )
                            is ShelfCard.Folder -> NuvioPosterCard(
                                title = card.folder.title,
                                imageUrl = card.folder.coverImageUrl,
                                shape = NuvioPosterShape.Poster,
                                showTitleBelow = !card.folder.hideTitle,
                                modifier = focusModifier,
                                onClick = onFolderClick?.let { { it(card.collectionId, card.folder.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabLabel(
    title: String,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Text(
        text = title,
        fontSize = if (selected) 44.sp else 36.sp,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Light,
        color = when {
            focused -> colors.focusRing
            selected -> colors.textPrimary
            else -> colors.textSecondary
        },
        maxLines = 1,
        modifier = Modifier
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    )
}

@Composable
private fun ShelfName(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(interactionSource = source, indication = null, onClick = onFocus)
            .background(if (focused) colors.focusBackground else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(26.dp)
                .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected || focused) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The focused card's facts above the shelf: the Kodi skin's typographic block. */
@Composable
private fun FocusedDetails(card: ShelfCard?) {
    val colors = MaterialTheme.nuvio.colors
    val title: String
    val facts: String
    val about: String?
    when (card) {
        is ShelfCard.Meta -> {
            title = card.item.name
            facts = listOfNotNull(
                card.item.releaseInfo?.takeIf { it.isNotBlank() },
                card.item.genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(" / "),
                card.item.runtime?.takeIf { it.isNotBlank() },
                card.item.imdbRating?.takeIf { it.isNotBlank() }?.let { "IMDb $it" },
            ).joinToString("  ·  ")
            about = card.item.description
        }
        is ShelfCard.Progress -> {
            title = card.item.title
            facts = card.item.subtitle
            about = card.item.pauseDescription
        }
        is ShelfCard.Folder -> {
            title = card.folder.title
            facts = ""
            about = null
        }
        null -> {
            title = ""
            facts = ""
            about = null
        }
    }
    // Fixed height, so the shelf below never jumps as the focused title changes.
    Column(Modifier.height(150.dp).padding(end = 72.dp)) {
        Text(title, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (facts.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(facts.uppercase(), fontSize = 16.sp, letterSpacing = 2.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!about.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(about, fontSize = 17.sp, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
