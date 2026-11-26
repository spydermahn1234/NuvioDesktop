package com.nuvio.app.features.home.shelves

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.keyLayer
import com.nuvio.app.core.ui.requestFocusLogged
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.tonight.TonightClock
import com.nuvio.app.features.tonight.TonightSettingsRepository
import com.nuvio.app.features.tonight.clockLabel
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.delay

/*
 * fork:shelf-home - the Kodi-style home (plan in design-notes/home-shelves.md; mockups on the
 * canvas "Nuvio home - Kodi-style mockups").
 *
 * The frame every view sits in, after the mockups: the focused title's backdrop full-bleed behind
 * everything, darkened from the left; the slash menu top left; on the left a typographic block for
 * the focused title (big name / year / length with accent slashes, a letter-spaced credit line,
 * "1h 16m left · ends 8:41 PM", two lines of description) above the focused shelf's cards; on the
 * right a dark panel with the clock and the tab's shelf names (the Kodi list-and-info layout).
 * Moving onto a tab or a shelf name shows it; Left from the panel goes into the shelf, Right comes
 * back. Only the focused shelf asks for more pages.
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

private val Ink = Color.White
private val InkSoft = Color.White.copy(alpha = 0.78f)
private val InkDim = Color.White.copy(alpha = 0.55f)

@OptIn(ExperimentalComposeUiApi::class)
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
    remember { TonightSettingsRepository.ensureLoaded() }
    val accent = MaterialTheme.nuvio.colors.accent
    val contentTabs = model.tabs.filter { it.kind != ShelfTabKind.Library }
    var tabId by remember { mutableStateOf(ShelfHomeMemory.tabId) }
    val tab = contentTabs.firstOrNull { it.id == tabId } ?: contentTabs.firstOrNull()
    var shelfKey by remember(tab?.id) { mutableStateOf(tab?.id?.let { ShelfHomeMemory.shelfByTab[it] }) }
    val shelf = tab?.shelves?.firstOrNull { it.key == shelfKey } ?: tab?.shelves?.firstOrNull()
    var focusedCard by remember(shelf?.key) { mutableStateOf<ShelfCard?>(null) }

    // The wall clock, re-read every 20 s so the clock and every "ends at" stay true.
    var nowMinute by remember { mutableIntStateOf(TonightClock.now().minuteOfDay) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            nowMinute = TonightClock.now().minuteOfDay
        }
    }

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
    val shown = focusedCard ?: cards.firstOrNull()

    // The app's key router (KeyLayers) gives a screen focus when it is shown and puts focus back when
    // an arrow goes nowhere; the classic home registers as "Home", so the shelf home must register
    // too or the remote and keyboard never reach it. It settles on the current shelf name.
    val entry = remember { FocusRequester() }

    Box(
        modifier
            .fillMaxSize()
            .keyLayer("ShelfHome") { entry.requestFocusLogged() }
            .focusGroup()
            .background(Color.Black),
    ) {
        // The focused title's backdrop, full-bleed, cross-fading as focus moves.
        Crossfade(
            targetState = shown?.backdropUrl(),
            animationSpec = tween(450),
            modifier = Modifier.fillMaxSize(),
        ) { url ->
            if (url != null) {
                NuvioAsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Darkened from the left, where the words are, and along the bottom, where the cards are.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.92f),
                    0.42f to Color.Black.copy(alpha = 0.72f),
                    0.75f to Color.Black.copy(alpha = 0.25f),
                    1f to Color.Black.copy(alpha = 0.35f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.25f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.9f),
                ),
            ),
        )

        Row(Modifier.fillMaxSize()) {
            // Left: the slash menu, the focused title and the shelf's cards.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 72.dp, top = topPadding.coerceAtMost(96.dp), bottom = 40.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    model.tabs.forEachIndexed { index, t ->
                        if (index > 0) Text("/", fontSize = 28.sp, fontWeight = FontWeight.Light, color = accent)
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

                Spacer(Modifier.weight(1f))
                TitleBlock(
                    shelfTitle = shelf?.title.orEmpty(),
                    count = cards.size,
                    card = shown,
                    nowMinute = nowMinute,
                )
                Spacer(Modifier.height(28.dp))

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
                val wheelScope = rememberCoroutineScope()
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 6.dp),
                    // A mouse wheel turns the row sideways (a plain wheel does nothing on a horizontal list).
                    modifier = Modifier.fillMaxWidth().onPointerEvent(PointerEventType.Scroll) { event ->
                        val delta = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                        val by = (delta.y + delta.x) * 90f
                        if (by != 0f) wheelScope.launch { rowState.scrollBy(by) }
                    },
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

            // Right: the clock and the tab's shelves, in a dark panel.
            Column(
                Modifier
                    .width(520.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(start = 36.dp, end = 40.dp, top = (topPadding.coerceAtMost(96.dp) - 12.dp).coerceAtLeast(0.dp), bottom = 32.dp),
            ) {
                Clock(nowMinute, Modifier.align(Alignment.End))
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        (tab?.title ?: "").uppercase(),
                        fontSize = 15.sp,
                        letterSpacing = 4.sp,
                        color = InkSoft,
                    )
                    Text(
                        "${tab?.shelves?.size ?: 0}",
                        fontSize = 15.sp,
                        letterSpacing = 4.sp,
                        color = InkSoft,
                    )
                }
                Spacer(Modifier.height(14.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    val shelves = tab?.shelves.orEmpty()
                    itemsIndexed(shelves, key = { _, s -> s.key }) { index, s ->
                        val isCurrent = s.key == shelf?.key
                        ShelfName(
                            title = s.title,
                            selected = isCurrent,
                            last = index == shelves.lastIndex,
                            modifier = if (isCurrent) Modifier.focusRequester(entry) else Modifier,
                            onFocus = { selectShelf(s.key) },
                        )
                    }
                    if (shelves.isEmpty()) {
                        item {
                            Text("Nothing here yet", fontSize = 22.sp, color = InkDim, modifier = Modifier.padding(vertical = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun ShelfCard.backdropUrl(): String? = when (this) {
    is ShelfCard.Meta -> item.banner ?: item.landscapePoster ?: item.poster
    is ShelfCard.Progress -> item.background ?: item.customLandscape ?: item.imageUrl ?: item.poster
    is ShelfCard.Folder -> folder.heroBackdropUrl ?: folder.coverImageUrl
}?.takeIf { it.isNotBlank() }

/** The mockups' clock: the hour heavy, the minutes light. */
@Composable
private fun Clock(nowMinute: Int, modifier: Modifier = Modifier) {
    val label = clockLabel(nowMinute)
    val hour = label.substringBefore(':')
    val rest = label.substringAfter(':', "")
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(hour, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(rest, fontSize = 46.sp, fontWeight = FontWeight.Light, color = Ink, maxLines = 1)
    }
}

/**
 * The focused title in type, after the mockups' Ledger and Kodi's list view:
 *   CONTINUE WATCHING · 6
 *   Black Mirror / S4 E1 / 1h 16m left
 *   USS CALLISTER
 *   ends 8:41 PM
 *   two lines of description
 * Fixed height, so the cards below never move as focus changes.
 */
@Composable
private fun TitleBlock(shelfTitle: String, count: Int, card: ShelfCard?, nowMinute: Int) {
    val accent = MaterialTheme.nuvio.colors.accent
    val title: String
    val parts: List<String>
    val credit: String
    val minutes: Int?
    val minutesAreLeft: Boolean
    val about: String?
    when (card) {
        is ShelfCard.Meta -> {
            val item = card.item
            val length = ShelfTime.runtimeMinutes(item.runtime)
            title = item.name
            parts = listOfNotNull(
                item.releaseInfo?.takeIf { it.isNotBlank() }?.take(4),
                length?.let { ShelfTime.length(it) },
            )
            credit = listOfNotNull(
                item.genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(" / "),
                item.imdbRating?.takeIf { it.isNotBlank() }?.let { "IMDb $it" },
                item.ageRating?.takeIf { it.isNotBlank() },
            ).joinToString("  ·  ")
            minutes = length
            minutesAreLeft = false
            about = item.description
        }
        is ShelfCard.Progress -> {
            val item = card.item
            val left = ShelfTime.minutesLeft(item.durationMs, item.resumePositionMs)
            title = item.title
            val episode = if (item.seasonNumber != null && item.episodeNumber != null) {
                "S${item.seasonNumber} E${item.episodeNumber}"
            } else {
                null
            }
            parts = listOfNotNull(episode, left?.takeIf { item.progressFraction > 0f }?.let { "${ShelfTime.length(it)} left" })
            credit = item.episodeTitle?.takeIf { it.isNotBlank() } ?: item.subtitle.takeIf { episode == null }.orEmpty()
            minutes = left
            minutesAreLeft = true
            about = item.pauseDescription
        }
        is ShelfCard.Folder -> {
            title = card.folder.title
            parts = emptyList()
            credit = ""
            minutes = null
            minutesAreLeft = false
            about = null
        }
        null -> {
            title = ""
            parts = emptyList()
            credit = ""
            minutes = null
            minutesAreLeft = false
            about = null
        }
    }
    Column(Modifier.height(330.dp).padding(end = 40.dp), verticalArrangement = Arrangement.Bottom) {
        Text(
            buildString {
                append(shelfTitle.uppercase())
                if (count > 0) append("  ·  $count")
            },
            fontSize = 16.sp,
            letterSpacing = 4.sp,
            color = InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                fontSize = 68.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            for (part in parts) {
                Text("  /  ", fontSize = 34.sp, color = accent, maxLines = 1, modifier = Modifier.padding(bottom = 8.dp))
                Text(part.uppercase(), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        if (credit.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(credit.uppercase(), fontSize = 19.sp, letterSpacing = 3.sp, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (minutes != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (minutesAreLeft) "finish by " else "starts now, ends ", fontSize = 22.sp, fontWeight = FontWeight.Light, color = InkSoft)
                Text(clockLabel(ShelfTime.endsAt(nowMinute, minutes)), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
        }
        if (!about.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                about,
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 28.sp,
                color = InkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 1000.dp),
            )
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
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val accent = MaterialTheme.nuvio.colors.accent
    Text(
        text = title,
        fontSize = 28.sp,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Light,
        color = when {
            focused -> accent
            selected -> Ink
            else -> InkSoft
        },
        maxLines = 1,
        modifier = Modifier
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    )
}

/** A row of the right panel, as in Kodi's list view: the focused row filled with the accent. */
@Composable
private fun ShelfName(
    title: String,
    selected: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = MaterialTheme.nuvio.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(interactionSource = source, indication = null, onClick = onFocus)
            .background(if (focused) colors.accent else Color.Transparent),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Light,
            color = when {
                focused -> colors.onAccent
                selected -> Ink
                else -> InkSoft
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (!last && !focused) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.10f)),
            )
        }
        if (selected && !focused) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(30.dp)
                    .background(colors.accent),
            )
        }
    }
}
