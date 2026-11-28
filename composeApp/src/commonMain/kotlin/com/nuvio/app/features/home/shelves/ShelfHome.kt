package com.nuvio.app.features.home.shelves

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.material3.MaterialTheme
import com.nuvio.app.core.ui.nuvio
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalScreenCovered
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.keyLayer
import com.nuvio.app.core.ui.navigationKey
import com.nuvio.app.core.ui.requestFocusLogged
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.WatchedContentFilter
import com.nuvio.app.features.tonight.TonightClock
import com.nuvio.app.features.tonight.TonightSettingsRepository
import com.nuvio.app.features.tonight.clockLabel
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.delay
import kotlin.math.abs

/*
 * fork:shelf-home - the fan-art-first home (mockups V2Browse, V2Logos, V2Idle, V2Flow and V2Prototype;
 * plan in design-notes/home-shelves.md).
 *
 * The focused card's fan-art fills the screen and is never covered by a panel. Words sit lower left
 * over two soft scrims: a breadcrumb (tab / row) top left, the clock top right, the focused title's
 * block, then ONE row of cards along the bottom (Posters, or Logos) with "i OF n · ROW r OF m" above
 * it and the next row's name below. Left/Right move along the row and the art follows (after the
 * selection has been still for 250 ms, cross-fading 600 ms); Up/Down swap the whole row in place
 * (old one slides down and fades, the new one rises: 300 ms) and each row remembers its card.
 *
 * Everything else appears only when asked for: the Shelves overlay (Up on the first row, Menu, or
 * Back on a row's first card; ShelfShelvesOverlay.kt), Info (I or hold OK; ShelfInfoOverlay.kt), the
 * view picker and the shelf builder. After 12 s untouched the screen becomes a gallery: everything
 * but the title and the clock fades away, the art drifts and moves on to the next card every 20 s.
 *
 * The screen is one focusable root that owns the selection and every key (Compose Desktop moves
 * focus on Tab only); the rows draw the selection and never take focus.
 */

/** Where the shelf home was, kept for the session so coming back to Home lands in the same place. */
internal object ShelfHomeMemory {
    var tabId: String = ShelfHomeRules.HOME_TAB
    val shelfByTab = HashMap<String, String>()

    /** The focused card of each row, by [rowId]. */
    val cardByRow = HashMap<String, Int>()

    fun rowId(tabId: String?, shelfKey: String?): String = "${tabId.orEmpty()}|${shelfKey.orEmpty()}"
}

private enum class ShelfScreenMode { Browse, Shelves, Info }

/** The frame's timings, from V2Flow. */
private object ShelfMotion {
    const val ART_WAIT_MS = 250L
    const val ART_FADE_MS = 600
    const val WORDS_FADE_MS = 200
    const val ROW_HALF_MS = 150
    const val INFO_FADE_MS = 300
    const val SHELVES_FADE_MS = 200
    const val IDLE_AFTER_MS = 12_000L
    const val IDLE_FADE_MS = 1_500
    const val WAKE_FADE_MS = 400
    const val GALLERY_STEP_MS = 20_000L
    const val DRIFT_MS = 30_000
    const val TOAST_MS = 1_800L
    const val HOLD_MS = 500L
}

/** The mockups' scrim colour, rgba(6,9,10). */
private val MockScrim = Color(0xFF06090A)

/** The row's cards and where it is, as the row area draws it; [id] changes only when the row does. */
private data class ShelfRowView(
    val id: String,
    val title: String,
    val cards: List<ShelfCard>,
    val index: Int,
    val mode: ShelfViewMode,
    val row: Int,
    val rows: Int,
    /** Fits before bed: an "ENDS hh:mm" line under each poster, and the row sits 50 higher to fit it (V3Evening). */
    val evening: Boolean = false,
    val nowMinute: Int = 0,
    /** "ART 2 OF 5" while the focused title has several backdrops (V3Art); not part of [id], so no re-animation. */
    val artLabel: String? = null,
)

private data class ShelfTitleView(val card: ShelfCard?, val mode: ShelfViewMode)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ShelfHome(
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
    onContinueWatchingLongPress: ((ContinueWatchingItem) -> Unit)? = null,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
    onLoadMoreCatalog: ((HomeCatalogSection) -> Unit)? = null,
    infoActions: ShelfInfoActions = ShelfInfoActions(),
) {
    remember { TonightSettingsRepository.ensureLoaded() }
    remember { ShelfThemeRepository.ensureLoaded() }
    remember { ShelfMixRepository.ensureLoaded() }
    remember { ShelfPresentationRepository.ensureLoaded() }
    val shelfTheme by ShelfThemeRepository.mode.collectAsStateWithLifecycle()
    val tonightSettings by TonightSettingsRepository.state.collectAsStateWithLifecycle()
    val mixes by ShelfMixRepository.mixes.collectAsStateWithLifecycle()
    val viewOverrides by ShelfPresentationRepository.overrides.collectAsStateWithLifecycle()
    val watchedState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val watchedFilter = remember(watchedState.items) { WatchedContentFilter.forHistory(watchedState.items) }

    // ---- Where we are: the tab, the row within it, the card within the row. ----
    val contentTabs = model.tabs.filter { it.kind != ShelfTabKind.Library }
    var tabId by remember { mutableStateOf(ShelfHomeMemory.tabId) }
    val tab = contentTabs.firstOrNull { it.id == tabId } ?: contentTabs.firstOrNull()
    val shelves = tab?.shelves.orEmpty()
    var shelfKey by remember { mutableStateOf(ShelfHomeMemory.shelfByTab[ShelfHomeMemory.tabId]) }
    val rowIndex = shelves.indexOfFirst { it.key == shelfKey }.takeIf { it >= 0 } ?: 0
    val shelf = shelves.getOrNull(rowIndex)
    val rowId = ShelfHomeMemory.rowId(tab?.id, shelf?.key)
    val cardByRow = remember { mutableStateMapOf<String, Int>().apply { putAll(ShelfHomeMemory.cardByRow) } }

    fun go(toTab: String, toShelf: String?) {
        tabId = toTab
        ShelfHomeMemory.tabId = toTab
        val key = toShelf ?: ShelfHomeMemory.shelfByTab[toTab]
        shelfKey = key
        if (key != null) ShelfHomeMemory.shelfByTab[toTab] = key
    }

    // The wall clock, re-read every 20 s so the clock and every "ends at" stay true.
    var nowMinute by remember { mutableIntStateOf(TonightClock.now().minuteOfDay) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            nowMinute = TonightClock.now().minuteOfDay
        }
    }

    // ---- The focused row's cards. ----
    // A pinned collection's folder shelf shows the folder's titles, loaded only while it is the row in focus.
    val folderSrc = shelf?.source as? ShelfSource.CollectionFolder
    val folderItems = folderSrc?.let { rememberShelfFolderItems(it.collectionId, it.folderId) }
    // A shelf the user built: its rows merged and filtered (ShelfMixes.kt).
    val mix = (shelf?.source as? ShelfSource.Mix)?.let { src -> mixes.firstOrNull { it.id == src.mixId } }
    val mixItems = remember(mix, sections, watchedFilter, nowMinute, tonightSettings.bedMinuteOfDay) {
        mix?.let {
            ShelfMixRules.build(it, sections, { m -> watchedFilter.isWatched(m.type, m.id) }, nowMinute, tonightSettings.bedMinuteOfDay)
        }.orEmpty()
    }
    // Pages asked of each source for the focused mix, so a mix with few matches fills itself and then stops.
    val mixPages = remember(mix?.id) { mutableMapOf<String, Int>() }
    LaunchedEffect(mix, mixItems.size, sections) {
        val m = mix ?: return@LaunchedEffect
        for (key in ShelfMixRules.needsMore(m, mixItems, sections, mixPages)) {
            val s = sections[key] ?: continue
            mixPages[key] = (mixPages[key] ?: 0) + 1
            onLoadMoreCatalog?.invoke(s)
        }
    }
    // fork:shelf-home v3 - Fits before bed (ShelfEvening.kt), built like a mix from the Home rows.
    remember { ShelfExtrasRepository.ensureLoaded() }
    val extras by ShelfExtrasRepository.state.collectAsStateWithLifecycle()
    val homeSectionKeys = remember(model) {
        model.tab(ShelfHomeRules.HOME_TAB)?.shelves.orEmpty().mapNotNull { (it.source as? ShelfSource.Catalog)?.sectionKey }
    }
    val eveningActive = extras.eveningHome &&
        model.tab(ShelfHomeRules.HOME_TAB)?.shelves.orEmpty().any { it.source == ShelfSource.Evening } &&
        ShelfEveningRules.isEvening(nowMinute, extras.eveningStartMinute, tonightSettings.bedMinuteOfDay)
    val eveningWarmth = if (eveningActive) {
        ShelfEveningRules.warmth(nowMinute, extras.eveningStartMinute, tonightSettings.bedMinuteOfDay)
    } else {
        0f
    }
    val isEveningShelf = shelf?.source == ShelfSource.Evening
    val eveningItems = remember(isEveningShelf, sections, homeSectionKeys, watchedFilter, nowMinute, tonightSettings.bedMinuteOfDay) {
        if (!isEveningShelf) {
            emptyList()
        } else {
            ShelfEveningRules.build(sections, homeSectionKeys, { m -> watchedFilter.isWatched(m.type, m.id) }, nowMinute, tonightSettings.bedMinuteOfDay)
        }
    }
    val eveningPages = remember(isEveningShelf) { mutableMapOf<String, Int>() }
    LaunchedEffect(isEveningShelf, eveningItems.size, sections) {
        if (!isEveningShelf) return@LaunchedEffect
        for (key in ShelfEveningRules.needsMore(eveningItems, sections, homeSectionKeys, eveningPages)) {
            val s = sections[key] ?: continue
            eveningPages[key] = (eveningPages[key] ?: 0) + 1
            onLoadMoreCatalog?.invoke(s)
        }
    }
    val cards: List<ShelfCard> = remember(shelf, sections, collections, continueWatching, upNext, upcoming, folderItems?.items, mixItems, eveningItems) {
        shelfCardsFor(shelf, sections, collections, continueWatching, upNext, upcoming, folderItems?.items, mixItems, eveningItems)
    }
    val section = (shelf?.source as? ShelfSource.Catalog)?.let { sections[it.sectionKey] }
    val cardIndex = (cardByRow[rowId] ?: 0).coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    val focusedCard = cards.getOrNull(cardIndex)

    fun setIndex(i: Int) {
        cardByRow[rowId] = i
        ShelfHomeMemory.cardByRow[rowId] = i
    }

    /** Any shelf's cards from what is loaded, for the Shelves overlay's preview. Loads nothing. */
    val previewFor: (Shelf) -> List<ShelfCard> = { s ->
        if (s.key == shelf?.key) {
            cards
        } else {
            val folder = (s.source as? ShelfSource.CollectionFolder)?.let {
                ShelfFolderItems.flow(it.collectionId, it.folderId).value.items
            }
            val built = (s.source as? ShelfSource.Mix)?.let { src ->
                mixes.firstOrNull { it.id == src.mixId }?.let {
                    ShelfMixRules.build(it, sections, { m -> watchedFilter.isWatched(m.type, m.id) }, nowMinute, tonightSettings.bedMinuteOfDay)
                }
            }
            val evening = if (s.source == ShelfSource.Evening) {
                ShelfEveningRules.build(sections, homeSectionKeys, { m -> watchedFilter.isWatched(m.type, m.id) }, nowMinute, tonightSettings.bedMinuteOfDay)
            } else {
                null
            }
            shelfCardsFor(s, sections, collections, continueWatching, upNext, upcoming, folder, built, evening)
        }
    }

    // ---- Paging: only the focused row asks for more, when its card is within 6 of the loaded end. ----
    val loadMore by rememberUpdatedState(onLoadMoreCatalog)
    LaunchedEffect(rowId, cardIndex, cards.size) {
        if (!ShelfFrameRules.nearEnd(cardIndex, cards.size)) return@LaunchedEffect
        val m = mix
        val f = folderItems
        val s = section
        when {
            m != null ->
                // Scrolling on raises the target, so a mix keeps filling as far as the user goes.
                ShelfMixRules.needsMore(m, mixItems, sections, mixPages, target = cardIndex + 7, maxPages = 50).forEach { k ->
                    sections[k]?.let {
                        mixPages[k] = (mixPages[k] ?: 0) + 1
                        loadMore?.invoke(it)
                    }
                }
            isEveningShelf ->
                ShelfEveningRules.needsMore(eveningItems, sections, homeSectionKeys, eveningPages, target = cardIndex + 7, maxPages = 50).forEach { k ->
                    sections[k]?.let {
                        eveningPages[k] = (eveningPages[k] ?: 0) + 1
                        loadMore?.invoke(it)
                    }
                }
            f != null -> if (f.canLoadMore && !f.isLoadingMore) f.loadMore()
            s != null -> if (s.nextSkip != null && !s.isLoadingMore) loadMore?.invoke(s)
        }
    }

    // ---- The row's view (Posters or Logos), the overlays and the gallery. ----
    val viewMode = shelf?.let { ShelfPresentationRepository.resolve(viewOverrides, it.key, it.title) } ?: ShelfViewMode.Posters
    var mode by remember { mutableStateOf(ShelfScreenMode.Browse) }
    var infoCard by remember { mutableStateOf<ShelfCard?>(null) }
    var pickerFor by remember { mutableStateOf<Shelf?>(null) }
    // The shelf builder: open with null for a new shelf, or the mix being edited.
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShelfMix?>(null) }
    // Up past Shelves' tab line hands the keyboard to the app's top bar (TopBarKeys); the home lets go meanwhile.
    var inTopBar by remember { mutableStateOf(false) }
    val browsing = mode == ShelfScreenMode.Browse && pickerFor == null && !editorOpen && !inTopBar

    var idle by remember { mutableStateOf(false) }
    var inputSerial by remember { mutableIntStateOf(0) }
    val covered = LocalScreenCovered.current
    val canIdle = browsing && !covered && cards.isNotEmpty()
    fun noteInput() {
        inputSerial++
    }
    fun wake() {
        idle = false
        noteInput()
    }
    LaunchedEffect(inputSerial, canIdle, idle) {
        if (!canIdle) {
            idle = false
            return@LaunchedEffect
        }
        if (idle) return@LaunchedEffect
        delay(ShelfMotion.IDLE_AFTER_MS)
        idle = true
    }
    // The gallery moves on to the next card every 20 s, back to the first after the last loaded one.
    val galleryCards by rememberUpdatedState(cards)
    val galleryIndex by rememberUpdatedState(cardIndex)
    val gallerySetIndex by rememberUpdatedState<(Int) -> Unit>(::setIndex)
    LaunchedEffect(idle) {
        while (idle) {
            delay(ShelfMotion.GALLERY_STEP_MS)
            gallerySetIndex(ShelfFrameRules.galleryNext(galleryIndex, galleryCards.size))
        }
    }

    // A short note in the middle of the screen ("View this row as Logos").
    var toast by remember { mutableStateOf("") }
    var toastShown by remember { mutableStateOf(false) }
    var toastSerial by remember { mutableIntStateOf(0) }
    fun say(text: String) {
        toast = text
        toastShown = true
        toastSerial++
    }
    LaunchedEffect(toastSerial) {
        if (toastSerial == 0) return@LaunchedEffect
        delay(ShelfMotion.TOAST_MS)
        toastShown = false
    }

    // ---- Actions. ----
    fun openCard(card: ShelfCard) {
        when (card) {
            is ShelfCard.Meta -> onPosterClick?.invoke(card.item)
            is ShelfCard.Progress -> onContinueWatchingClick?.invoke(card.item)
            is ShelfCard.Folder -> onFolderClick?.invoke(card.collectionId, card.folder.id)
        }
    }
    fun longPress(card: ShelfCard) {
        when (card) {
            is ShelfCard.Meta -> onPosterLongClick?.invoke(card.item)
            is ShelfCard.Progress -> onContinueWatchingLongPress?.invoke(card.item)
            is ShelfCard.Folder -> Unit
        }
    }
    fun openShelves() {
        mode = ShelfScreenMode.Shelves
    }
    fun openInfo() {
        val card = focusedCard ?: return
        infoCard = card
        mode = ShelfScreenMode.Info
    }
    fun move(delta: Int) {
        if (cards.isEmpty()) return
        setIndex(ShelfFrameRules.move(cardIndex, delta, cards.size))
    }
    fun changeRow(to: Int) {
        val target = shelves.getOrNull(to) ?: return
        val t = tab ?: return
        go(t.id, target.key)
    }
    fun toggleView() {
        val s = shelf ?: return
        val next = ShelfPresentationRules.toggled(viewMode)
        ShelfPresentationRepository.set(s.key, next)
        say("View this row as ${next.name}")
    }
    fun clickCard(i: Int) {
        if (i !in cards.indices) return
        if (i == cardIndex) openCard(cards[i]) else setIndex(i)
    }

    // ---- Keys. ----
    val rootFocus = remember { FocusRequester() }
    var enterDownAt by remember { mutableStateOf(0L) }
    var holdFired by remember { mutableStateOf(false) }
    var backDown by remember { mutableStateOf(false) }
    var swallowUp by remember { mutableStateOf<Key?>(null) }

    fun onKey(event: KeyEvent): Boolean {
        val navKey = event.navigationKey()
        val isEnter = navKey == Key.Enter || navKey == Key.NumPadEnter
        // An OK held into Info: its repeats and its release belong to the press that opened Info, not to Info's buttons.
        if (isEnter && enterDownAt != 0L && !browsing) {
            if (event.type == KeyEventType.KeyUp) enterDownAt = 0L
            return true
        }
        if (!browsing) return false
        noteInput()
        // In the gallery any key only wakes the screen; its release is swallowed too.
        if (idle) {
            if (event.type == KeyEventType.KeyDown) {
                wake()
                swallowUp = event.key
            }
            return true
        }
        if (event.type == KeyEventType.KeyUp && swallowUp == event.key) {
            swallowUp = null
            return true
        }
        val isBack = event.key == Key.Back || navKey == Key.Backspace || navKey == Key.Escape
        if (isBack) {
            // Back acts as the key comes up, as it does across the app; only for a press that started here.
            when (event.type) {
                KeyEventType.KeyDown -> backDown = true
                KeyEventType.KeyUp -> if (backDown) {
                    backDown = false
                    if (cardIndex > 0) setIndex(0) else openShelves()
                }
                else -> Unit
            }
            return true
        }
        if (isEnter) {
            val now = System.currentTimeMillis()
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (enterDownAt == 0L) {
                        enterDownAt = now
                        holdFired = false
                    } else if (!holdFired && now - enterDownAt >= ShelfMotion.HOLD_MS) {
                        // The key has started repeating: held long enough, so Info now rather than on release.
                        holdFired = true
                        openInfo()
                    }
                }
                KeyEventType.KeyUp -> {
                    if (enterDownAt != 0L) {
                        val held = now - enterDownAt
                        enterDownAt = 0L
                        when {
                            holdFired -> Unit
                            held >= ShelfMotion.HOLD_MS -> openInfo()
                            else -> focusedCard?.let(::openCard)
                        }
                    }
                }
                else -> Unit
            }
            return true
        }
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        val handled = navKey in BrowseKeys
        if (event.type != KeyEventType.KeyDown) return handled
        when (navKey) {
            Key.DirectionLeft -> move(-1)
            Key.DirectionRight -> move(1)
            Key.DirectionUp -> if (rowIndex > 0) changeRow(rowIndex - 1) else openShelves()
            Key.DirectionDown -> if (rowIndex < shelves.lastIndex) changeRow(rowIndex + 1)
            Key.I -> openInfo()
            Key.V -> toggleView()
            Key.Menu -> openShelves()
            else -> return false
        }
        return true
    }

    // Back from an overlay: the root takes the keyboard again (the router only does when nothing holds focus).
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(browsing) {
        if (!browsing) {
            wasCovered = true
        } else if (wasCovered) {
            wasCovered = false
            delay(32)
            // Not when another screen is over the home (a tab opened from the top bar): the router settles that one.
            if (!covered) rootFocus.requestFocusLogged()
        }
    }

    // ---- The art, after the selection has been still for 250 ms; the previous art stays meanwhile. ----
    var artCard by remember { mutableStateOf<ShelfCard?>(null) }
    LaunchedEffect(focusedCard?.stableId()) {
        val next = focusedCard ?: return@LaunchedEffect
        if (artCard != null) delay(ShelfMotion.ART_WAIT_MS)
        artCard = next
    }
    // fork:shelf-home v3 - the art set (textless first, several per title) and the colour taken from it.
    val art = rememberShelfArt(artCard, enabled = extras.textlessArt)
    // fork:shelf-home live-tv - a Live TV favourite: what is on now, its art, and the channel's logo for the colour.
    val liveCards = rememberShelfLiveCards()
    val artIsLive = ShelfLiveRules.isLive(artCard)
    val liveBackdrop = rememberShelfLiveBackdrop(ShelfLiveRules.ref(artCard)?.let(liveCards::get))
    val shownBackdrop = if (artIsLive) liveBackdrop else art.backdrop
    // fork:shelf-home v3 - the focused show's theme, quietly (ShelfThemeTunes.kt).
    val themePlaying = ShelfThemeTune(
        card = artCard,
        active = extras.themeTunes && (browsing || idle) && !covered,
    )
    LaunchedEffect(artCard?.stableId(), extras.textlessArt) {
        if (extras.textlessArt) cards.getOrNull(cardIndex + 1)?.let { runCatching { ShelfArtSets.load(it) } }
    }
    val artAccent by rememberShelfAccent(
        imageUrl = if (artIsLive) ShelfLiveRules.logo(artCard) ?: liveBackdrop else art.current,
        enabled = extras.artAccent && shelfTheme == ShelfThemeMode.Dark,
        fallback = MaterialTheme.nuvio.colors.accent,
    )
    // fork:shelf-home v3 - the chosen lights take the art's colour while browsing and in the gallery (ShelfArtLights.kt).
    val artLightsOn = extras.artLights && !covered
    LaunchedEffect(artLightsOn, browsing || idle, artAccent) {
        when {
            !artLightsOn -> ShelfArtLights.release()
            browsing || idle -> ShelfArtLights.show(artAccent)
            else -> ShelfArtLights.show(null) // Info / Shelves: keep what is lit
        }
    }
    DisposableEffect(Unit) { onDispose { ShelfArtLights.release() } }
    val artLit by ShelfArtLights.lit.collectAsStateWithLifecycle()
    // fork:phone-remote shelf-home - the phone's mirror (v3 phase 9): what the home shows, and the phone's taps.
    val artUrlForPhone = shownBackdrop ?: artCard?.posterUrl()
    val remoteScreen = remember(mode, contentTabs, tab, rowIndex, cards, cardIndex, viewMode, artUrlForPhone, nowMinute, covered, inTopBar, editorOpen) {
        if (covered || inTopBar || editorOpen) {
            null
        } else {
            com.nuvio.app.features.remote.RemoteShelfHomeRules.screen(
                mode = when (mode) {
                    ShelfScreenMode.Browse -> com.nuvio.app.features.remote.RemoteShelfHomeScreen.BROWSE
                    ShelfScreenMode.Shelves -> com.nuvio.app.features.remote.RemoteShelfHomeScreen.SHELVES
                    ShelfScreenMode.Info -> com.nuvio.app.features.remote.RemoteShelfHomeScreen.INFO
                },
                tabs = contentTabs,
                tab = tab,
                rowIndex = rowIndex,
                cards = cards,
                cardIndex = cardIndex,
                viewMode = viewMode,
                artUrl = artUrlForPhone,
                nowMinute = nowMinute,
            )
        }
    }
    LaunchedEffect(remoteScreen) { com.nuvio.app.features.remote.RemoteShelfHome.publish(remoteScreen) }
    DisposableEffect(Unit) { onDispose { com.nuvio.app.features.remote.RemoteShelfHome.publish(null) } }
    fun onPhone(action: com.nuvio.app.features.remote.RemoteShelfHomeAction) {
        if (editorOpen || pickerFor != null || inTopBar) return
        if (idle) wake() else noteInput()
        when (action) {
            is com.nuvio.app.features.remote.RemoteShelfHomeAction.Move -> {
                mode = ShelfScreenMode.Browse
                move(action.delta)
            }
            is com.nuvio.app.features.remote.RemoteShelfHomeAction.Pick -> {
                mode = ShelfScreenMode.Browse
                if (action.index in cards.indices) setIndex(action.index)
            }
            is com.nuvio.app.features.remote.RemoteShelfHomeAction.Row -> {
                mode = ShelfScreenMode.Browse
                if (shelves.isNotEmpty()) changeRow((rowIndex + action.delta).coerceIn(0, shelves.lastIndex))
            }
            com.nuvio.app.features.remote.RemoteShelfHomeAction.Play -> {
                mode = ShelfScreenMode.Browse
                focusedCard?.let(::openCard)
            }
            com.nuvio.app.features.remote.RemoteShelfHomeAction.Info -> openInfo()
            com.nuvio.app.features.remote.RemoteShelfHomeAction.Shelves -> openShelves()
            com.nuvio.app.features.remote.RemoteShelfHomeAction.View -> {
                mode = ShelfScreenMode.Browse
                toggleView()
            }
            is com.nuvio.app.features.remote.RemoteShelfHomeAction.PickShelf -> {
                go(action.tabId, action.shelfKey)
                mode = ShelfScreenMode.Browse
            }
        }
    }
    val phoneAction by rememberUpdatedState<(com.nuvio.app.features.remote.RemoteShelfHomeAction) -> Unit>(::onPhone)
    LaunchedEffect(Unit) { com.nuvio.app.features.remote.RemoteShelfHome.actions.collect { phoneAction(it) } }
    // The gallery's slow drift: 30 s one way and back, eased off when the screen wakes.
    val drift = remember { Animatable(0f) }
    LaunchedEffect(idle) {
        if (idle) {
            while (true) {
                drift.animateTo(1f, tween(ShelfMotion.DRIFT_MS))
                drift.animateTo(0f, tween(ShelfMotion.DRIFT_MS))
            }
        } else {
            drift.animateTo(0f, tween(ShelfMotion.WAKE_FADE_MS))
        }
    }

    val browseAlpha by animateFloatAsState(
        targetValue = if (mode == ShelfScreenMode.Browse && !idle) 1f else 0f,
        animationSpec = tween(
            when {
                idle -> ShelfMotion.IDLE_FADE_MS
                mode == ShelfScreenMode.Shelves -> ShelfMotion.SHELVES_FADE_MS
                mode == ShelfScreenMode.Info -> ShelfMotion.INFO_FADE_MS
                else -> ShelfMotion.WAKE_FADE_MS
            },
        ),
        label = "shelfBrowseAlpha",
    )
    val galleryAlpha by animateFloatAsState(
        targetValue = if (idle) 1f else 0f,
        animationSpec = tween(if (idle) ShelfMotion.IDLE_FADE_MS else ShelfMotion.WAKE_FADE_MS),
        label = "shelfGalleryAlpha",
    )
    val toastAlpha by animateFloatAsState(if (toastShown) 1f else 0f, tween(600), label = "shelfToast")

    // A wheel over the row moves along it, a notch a card.
    var wheel by remember { mutableStateOf(0f) }
    var lastPointer by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    ProvideShelfPalette(shelfTheme, artAccent = artAccent.takeIf { extras.artAccent }) {
        val palette = LocalShelfPalette.current
        val ink = palette.ink
        val accent = palette.accentInk
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(palette.page)
                .keyLayer("ShelfHome", active = browsing) { rootFocus.requestFocusLogged() }
                .focusGroup()
                .focusRequester(rootFocus)
                .onPreviewKeyEvent(::onKey)
                // An Enter nothing used would be read by desktop Compose as "focus the first thing inside" and jump.
                .onKeyEvent { it.key == Key.Enter || it.key == Key.NumPadEnter }
                .focusable()
                // Any real mouse use counts as input; in the gallery it only wakes the screen.
                .onPointerEvent(PointerEventType.Move, PointerEventPass.Initial) { event ->
                    val at = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    val before = lastPointer
                    lastPointer = at
                    // Compose sends a move when the layout under a still pointer changes: only a real move counts.
                    if (before != null && (abs(at.x - before.x) > 2f || abs(at.y - before.y) > 2f) && browsing) {
                        if (idle) wake() else noteInput()
                    }
                }
                .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                    if (!browsing) return@onPointerEvent
                    if (idle) {
                        wake()
                        event.changes.forEach { it.consume() }
                    } else {
                        noteInput()
                    }
                    rootFocus.requestFocusLogged()
                }
                .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                    if (!browsing) return@onPointerEvent
                    if (idle) {
                        wake()
                        event.changes.forEach { it.consume() }
                    } else {
                        noteInput()
                    }
                },
        ) {
            val dy = maxHeight - 1080.dp
            val topShift = (topPadding - 72.dp).coerceIn(0.dp, 40.dp)
            fun y(v: Int): Dp = (v.dp + dy).coerceAtLeast(0.dp)

            // The art, full-bleed, drifting slowly in the gallery.
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        val d = drift.value
                        scaleX = 1f + 0.06f * d
                        scaleY = 1f + 0.06f * d
                        translationX = -36.dp.toPx() * d
                        translationY = -14.dp.toPx() * d
                    },
            ) {
                Crossfade(
                    targetState = ShelfArtFrame(artCard, shownBackdrop),
                    animationSpec = tween(ShelfMotion.ART_FADE_MS),
                    modifier = Modifier.fillMaxSize(),
                    label = "shelfArt",
                ) { frame -> ShelfArt(frame.card, frame.backdrop, nowMinute) }
            }

            // The scrims: from the left, where the words are, and along the bottom, where the row is: a band that
            // is clear down to 200 above the row's header and 92% dark from 20 above it to the bottom, eased
            // (smoothstep) so its start never shows as a line. Under it the art can never fight the row.
            Box(
                Modifier.fillMaxSize().alpha(browseAlpha).background(
                    Brush.horizontalGradient(
                        0f to scrimOf(palette, 0.82f),
                        0.30f to scrimOf(palette, 0.45f),
                        0.55f to scrimOf(palette, 0f),
                    ),
                ),
            )
            val bandHeader by animateDpAsState(
                y(
                    when {
                        viewMode == ShelfViewMode.Logos -> 840
                        isEveningShelf -> 790 - ShelfEveningRules.ROW_LIFT
                        else -> 790
                    },
                ),
                tween(ShelfMotion.WORDS_FADE_MS),
                label = "shelfBand",
            )
            val bandStart = (bandHeader - ShelfBand.FADE_DP.dp).coerceAtLeast(0.dp)
            val bandHeight = (maxHeight - bandStart).coerceAtLeast(1.dp)
            val bandFull = ((ShelfBand.FADE_DP - ShelfBand.FULL_ABOVE_DP).dp / bandHeight).coerceIn(0.05f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(bandHeight)
                    .alpha(browseAlpha)
                    .background(
                        Brush.verticalGradient(
                            *ShelfBand.stops(bandFull).map { (at, a) -> at to scrimOf(palette, a) }.toTypedArray(),
                        ),
                    ),
            )
            // The gallery's own shade under its title line (V2Idle).
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(260.dp)
                    .alpha(galleryAlpha)
                    .background(Brush.verticalGradient(0f to scrimOf(palette, 0f), 1f to scrimOf(palette, 0.7f))),
            )
            // fork:shelf-home v3 - the evening's amber wash, over the art and under the words (V3Evening).
            ShelfEveningWash(eveningWarmth)
            // fork:shelf-home v3 - the trailer in the open part of the art (ShelfTrailer.kt): a native video window,
            // so it sits beside the words rather than under them; always muted.
            val trailer = ShelfTrailerLayer(
                card = artCard,
                active = extras.trailers && browsing && !idle && !covered && mode == ShelfScreenMode.Browse,
                accent = accent,
                modifier = Modifier.fillMaxSize(),
                input = inputSerial,
                topInset = topShift,
                onFocusEscaped = { if (!covered) rootFocus.requestFocusLogged() },
            )
            // fork:shelf-home live-tv - Paper: what is on now as a framed picture in the open part (no backdrop there).
            if (palette.backdropAlpha <= 0f && artIsLive) {
                ShelfLivePaperPicture(
                    url = liveBackdrop.takeIf { artCard?.stableId() == focusedCard?.stableId() },
                    modifier = Modifier.fillMaxSize().alpha(browseAlpha),
                    topInset = topShift,
                )
            }
            // fork:shelf-home live-tv - the focused favourite playing, muted, in the same open part (ShelfLiveTv.kt).
            val liveShowing = ShelfLivePreviewLayer(
                card = artCard,
                active = extras.livePreview && browsing && !idle && !covered && mode == ShelfScreenMode.Browse &&
                    artCard?.stableId() == focusedCard?.stableId(),
                modifier = Modifier.fillMaxSize(),
                topInset = topShift,
            )

            // ---- Browse ----
            if (browseAlpha > 0f) {
                Box(Modifier.fillMaxSize().alpha(browseAlpha)) {
                    // Breadcrumb: TAB / ROW.
                    Row(
                        modifier = Modifier.padding(start = 96.dp, top = 64.dp + topShift),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            (tab?.title ?: "").uppercase(),
                            fontSize = 17.sp,
                            letterSpacing = 4.76.sp,
                            color = ink.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                        if (shelf != null) {
                            Text("/", fontSize = 17.sp, color = accent, maxLines = 1)
                            Crossfade(targetState = shelf.title, animationSpec = tween(ShelfMotion.WORDS_FADE_MS), label = "shelfCrumb") { title ->
                                Text(
                                    title.uppercase(),
                                    fontSize = 17.sp,
                                    letterSpacing = 4.76.sp,
                                    color = ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 1200.dp),
                                )
                            }
                        }
                    }

                    // The focused title's block; its words fade, never slide.
                    Crossfade(
                        targetState = ShelfTitleView(focusedCard, viewMode),
                        animationSpec = tween(ShelfMotion.WORDS_FADE_MS),
                        modifier = Modifier.padding(
                            start = 96.dp,
                            top = when {
                                viewMode == ShelfViewMode.Logos -> y(450)
                                isEveningShelf -> y(484 - ShelfEveningRules.ROW_LIFT)
                                else -> y(484)
                            },
                        ),
                        label = "shelfTitle",
                    ) { view ->
                        TitleBlock(
                            view,
                            nowMinute,
                            if (isEveningShelf) tonightSettings.bedMinuteOfDay else null,
                            live = ShelfLiveRules.ref(view.card)?.let(liveCards::get),
                        )
                    }

                    // The row: header and cards, swapped in place on Up/Down.
                    val rowView = ShelfRowView(
                        id = rowId,
                        title = shelf?.title.orEmpty(),
                        cards = cards,
                        index = cardIndex,
                        mode = viewMode,
                        row = rowIndex,
                        rows = shelves.size,
                        evening = isEveningShelf,
                        nowMinute = if (isEveningShelf) nowMinute else 0,
                        artLabel = art.label.takeIf { artCard?.stableId() == focusedCard?.stableId() },
                    )
                    val rise = with(LocalDensity.current) { 30.dp.roundToPx() }
                    AnimatedContent(
                        targetState = rowView,
                        transitionSpec = {
                            (fadeIn(tween(ShelfMotion.ROW_HALF_MS, delayMillis = ShelfMotion.ROW_HALF_MS)) +
                                slideInVertically(tween(ShelfMotion.ROW_HALF_MS, delayMillis = ShelfMotion.ROW_HALF_MS)) { rise }) togetherWith
                                (fadeOut(tween(ShelfMotion.ROW_HALF_MS)) + slideOutVertically(tween(ShelfMotion.ROW_HALF_MS)) { rise }) using
                                SizeTransform(clip = false)
                        },
                        contentKey = { it.id },
                        modifier = Modifier
                            .fillMaxSize()
                            .onPointerEvent(PointerEventType.Scroll) { event ->
                                if (!browsing || idle) return@onPointerEvent
                                val delta = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                                wheel += delta.y + delta.x
                                while (wheel >= 1f) {
                                    wheel -= 1f
                                    move(1)
                                }
                                while (wheel <= -1f) {
                                    wheel += 1f
                                    move(-1)
                                }
                            },
                        label = "shelfRow",
                    ) { view ->
                        Crossfade(targetState = view.mode, animationSpec = tween(ShelfMotion.WORDS_FADE_MS), label = "shelfRowMode") { rowMode ->
                            RowArea(
                                view = view,
                                rowMode = rowMode,
                                top = { v -> y(if (view.evening && rowMode == ShelfViewMode.Posters) v - ShelfEveningRules.ROW_LIFT else v) },
                                onClickCard = ::clickCard,
                                onSecondaryClickCard = { i -> view.cards.getOrNull(i)?.let(::longPress) },
                            )
                        }
                    }

                    // The next row's name, bottom left.
                    val next = shelves.getOrNull(rowIndex + 1)?.title?.uppercase()
                        ?: "END OF ${(tab?.title ?: "Home").uppercase()}"
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 34.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = ink.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
                        Crossfade(targetState = next, animationSpec = tween(ShelfMotion.WORDS_FADE_MS), label = "shelfNext") { name ->
                            Text(name, fontSize = 14.sp, letterSpacing = 3.64.sp, color = ink.copy(alpha = 0.45f), maxLines = 1)
                        }
                    }
                }
            }

            // ---- The gallery's line, bottom left, and its clock, bottom right ----
            if (galleryAlpha > 0f) {
                Crossfade(
                    targetState = focusedCard,
                    animationSpec = tween(ShelfMotion.ART_FADE_MS),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 72.dp).alpha(galleryAlpha),
                    label = "shelfGalleryLine",
                ) { card ->
                    if (card != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            Text(
                                card.title(),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Light,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alignByBaseline().widthIn(max = 1100.dp),
                            )
                            Text(
                                ShelfFrameRules.yearLength(card),
                                fontSize = 15.sp,
                                letterSpacing = 4.2.sp,
                                color = ink.copy(alpha = 0.7f),
                                maxLines = 1,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                }
                ShelfClock(
                    nowMinute = nowMinute,
                    hourSize = 40,
                    minuteSize = 30,
                    color = ink.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 66.dp).alpha(galleryAlpha),
                )
            }

            // ---- The clock, top right, in every mode but the gallery ----
            ShelfClock(
                nowMinute = nowMinute,
                hourSize = 44,
                minuteSize = 34,
                color = ink,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 52.dp + topShift).alpha(1f - galleryAlpha),
            )
            // fork:shelf-home v3 - quiet status lines under the clock (bedtime now; trailer, theme and lights later).
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 120.dp + topShift).alpha((1f - galleryAlpha) * browseAlpha),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (eveningActive) {
                    Text(
                        ShelfEveningRules.bedLine(tonightSettings.bedMinuteOfDay, nowMinute),
                        fontSize = 14.sp,
                        letterSpacing = 3.64.sp,
                        color = ink.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
                artLit?.let { lit ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(10.dp).background(lit.color, androidx.compose.foundation.shape.CircleShape))
                        Text(
                            "LIGHTS · ${lit.name.uppercase()}",
                            fontSize = 14.sp,
                            letterSpacing = 3.64.sp,
                            color = ink.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
                if (liveShowing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(8.dp).background(Color(0xFFE5484D), androidx.compose.foundation.shape.CircleShape))
                        Text("LIVE · MUTED", fontSize = 14.sp, letterSpacing = 3.64.sp, color = ink.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
                if (trailer.showing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(8.dp).background(accent, androidx.compose.foundation.shape.CircleShape))
                        Text("TRAILER · MUTED", fontSize = 14.sp, letterSpacing = 3.64.sp, color = ink.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
                if (themePlaying) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("♪", fontSize = 18.sp, color = accent, maxLines = 1)
                        Text("THEME · QUIET", fontSize = 14.sp, letterSpacing = 3.64.sp, color = ink.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
            }

            // ---- Shelves: Up on the first row, Menu, or Back on a row's first card ----
            androidx.compose.animation.AnimatedVisibility(
                visible = mode == ShelfScreenMode.Shelves,
                enter = fadeIn(tween(ShelfMotion.SHELVES_FADE_MS)),
                exit = fadeOut(tween(ShelfMotion.SHELVES_FADE_MS)),
                modifier = Modifier.fillMaxSize(),
            ) {
                ShelfShelvesOverlay(
                    model = model,
                    currentTabId = tab?.id ?: ShelfHomeRules.HOME_TAB,
                    currentShelfKey = shelf?.key,
                    previewFor = previewFor,
                    onPick = { pickedTab, pickedShelf ->
                        if (pickedTab == ShelfHomeRules.LIBRARY_TAB) {
                            mode = ShelfScreenMode.Browse
                            onOpenLibrary?.invoke()
                        } else {
                            go(pickedTab, pickedShelf)
                            mode = ShelfScreenMode.Browse
                        }
                    },
                    onOpenLibrary = onOpenLibrary?.let { open ->
                        {
                            mode = ShelfScreenMode.Browse
                            open()
                        }
                    },
                    onViewAs = { s -> pickerFor = s },
                    onNewShelf = {
                        editing = null
                        editorOpen = true
                    },
                    onDismiss = { mode = ShelfScreenMode.Browse },
                    modifier = Modifier.fillMaxSize(),
                    onUpToTopBar = {
                        // Down, Back or Escape on the bar comes back to Shelves; leaving it any other way (OK on
                        // another tab, a click) lands back on the row.
                        inTopBar = true
                        mode = ShelfScreenMode.Browse
                        val moved = com.nuvio.app.features.navbar.TopBarKeys.enter(
                            onDown = {
                                inTopBar = false
                                mode = ShelfScreenMode.Shelves
                            },
                            onLost = { inTopBar = false },
                        )
                        if (!moved) {
                            inTopBar = false
                            mode = ShelfScreenMode.Shelves
                        }
                    },
                )
            }

            // ---- Info: I or hold OK ----
            androidx.compose.animation.AnimatedVisibility(
                visible = mode == ShelfScreenMode.Info && infoCard != null,
                enter = fadeIn(tween(ShelfMotion.INFO_FADE_MS)),
                exit = fadeOut(tween(ShelfMotion.INFO_FADE_MS)),
                modifier = Modifier.fillMaxSize(),
            ) {
                val card = infoCard
                if (card != null) {
                    ShelfInfoOverlay(
                        card = card,
                        rowTitle = shelf?.title.orEmpty(),
                        nowMinute = nowMinute,
                        actions = infoActions,
                        onDismiss = { mode = ShelfScreenMode.Browse },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // ---- "View this row as Logos" ----
            if (toastAlpha > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = maxHeight * 0.48f - 40.dp)
                        .alpha(toastAlpha)
                        .background(Color(0xDB0A0A0C))
                        .border(1.dp, Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 40.dp, vertical = 22.dp),
                ) {
                    Text(toast, fontSize = 26.sp, color = Color.White, maxLines = 1)
                }
            }

            // ---- "View <shelf> as": from the Shelves overlay's Menu ----
            pickerFor?.let { target ->
                ShelfViewPicker(
                    shelfTitle = target.title,
                    current = ShelfPresentationRepository.resolve(viewOverrides, target.key, target.title),
                    isDefault = ShelfPresentationRepository.isDefault(target.key),
                    onPick = { picked ->
                        ShelfPresentationRepository.set(target.key, picked)
                        pickerFor = null
                    },
                    onReset = {
                        ShelfPresentationRepository.set(target.key, null)
                        pickerFor = null
                    },
                    onDismiss = { pickerFor = null },
                    onEdit = (target.source as? ShelfSource.Mix)?.let { src ->
                        {
                            pickerFor = null
                            editing = ShelfMixRepository.mix(src.mixId)
                            editorOpen = true
                        }
                    },
                )
            }

            // ---- The shelf builder ("+ New shelf" in Shelves, or "Edit this shelf" in the picker) ----
            if (editorOpen) {
                val rows = model.tab(ShelfHomeRules.HOME_TAB)?.shelves.orEmpty()
                    .mapNotNull { s -> (s.source as? ShelfSource.Catalog)?.let { it.sectionKey to s.title } }
                val editingNow = editing
                ShelfMixEditor(
                    existing = editingNow,
                    availableRows = rows,
                    onSave = { m ->
                        ShelfMixRepository.save(if (m.id.isEmpty()) m.copy(id = ShelfMixRepository.newId()) else m)
                        editorOpen = false
                    },
                    onDelete = editingNow?.let { e ->
                        {
                            ShelfMixRepository.delete(e.id)
                            editorOpen = false
                        }
                    },
                    onDismiss = { editorOpen = false },
                )
            }
        }
    }
}

/** The dark band under the row (fix 1 of the Nov 27 review). */
internal object ShelfBand {
    /** The band starts this far above the row's header... */
    const val FADE_DP = 200
    /** ...and is fully dark from this far above it. */
    const val FULL_ABOVE_DP = 20
    const val DARK = 0.92f

    /** Gradient stops (position 0..1 in the band, alpha): smoothstep up to [full], then [DARK] to the end. */
    fun stops(full: Float): List<Pair<Float, Float>> {
        val f = full.coerceIn(0.05f, 1f)
        val eased = (0..8).map { i ->
            val t = i / 8f
            (t * f) to DARK * t * t * (3f - 2f * t)
        }
        return if (f < 1f) eased + (1f to DARK) else eased
    }
}

/** The keys Browse uses, so their releases are taken too. */
private val BrowseKeys = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.I, Key.V, Key.Menu,
)

private fun scrimOf(palette: ShelfPalette, alpha: Float): Color =
    // Dark uses the mockups' near-black; Paper keeps its own page colour.
    (if (palette.backdropAlpha > 0f) MockScrim else palette.scrim).copy(alpha = alpha)

/**
 * One card's art: its fan-art over its own poster enlarged and blurred, so a missing or failed
 * backdrop still leaves a picture (never black). Nothing at all (no poster either): a soft accent wash.
 */
@Composable
private fun ShelfArt(card: ShelfCard?, fanart: String?, nowMinute: Int) {
    val palette = LocalShelfPalette.current
    if (palette.backdropAlpha <= 0f) return
    Box(Modifier.fillMaxSize().alpha(palette.backdropAlpha)) {
        val poster = card?.posterUrl()
        if (ShelfLiveRules.isLive(card)) {
            // fork:shelf-home live-tv - a channel: the time of day's sky in the channel's colour, under what is on now.
            ShelfLiveSky(nowMinute, palette.accent)
        } else if (poster != null) {
            NuvioAsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                    }
                    .blur(40.dp, BlurredEdgeTreatment.Rectangle),
            )
        } else if (fanart == null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(palette.accent.copy(alpha = 0.35f), palette.page)),
                ),
            )
        }
        if (fanart != null) {
            NuvioAsyncImage(
                model = fanart,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The mockups' clock: the hour SemiBold, a dim colon, the minutes Light (Tonight's 12-hour setting respected). */
@Composable
private fun ShelfClock(nowMinute: Int, hourSize: Int, minuteSize: Int, color: Color, modifier: Modifier = Modifier) {
    val label = clockLabel(nowMinute)
    val hour = label.substringBefore(':')
    val rest = label.substringAfter(':', "")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(hour, fontSize = hourSize.sp, lineHeight = hourSize.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
        // A dim colon, so "07 03" never reads as a date and matches the "Finish by 07:09" line.
        Text(":", fontSize = minuteSize.sp, lineHeight = minuteSize.sp, fontWeight = FontWeight.Light, color = color.copy(alpha = color.alpha * 0.5f), maxLines = 1, modifier = Modifier.alignByBaseline())
        Text(rest, fontSize = minuteSize.sp, lineHeight = minuteSize.sp, fontWeight = FontWeight.Light, color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
    }
}

/**
 * The focused title's block (V2Browse; Logos: V2Logos). Every line has a fixed height and is drawn
 * even when empty, so no line ever moves when the card changes.
 */
@Composable
private fun TitleBlock(
    view: ShelfTitleView,
    nowMinute: Int,
    bedMinute: Int? = null,
    live: com.nuvio.app.features.home.LiveTvHomeCard? = null,
) {
    val ink = LocalShelfPalette.current.ink
    val accent = LocalShelfPalette.current.accentInk
    val card = view.card
    // fork:shelf-home live-tv - a channel's lines: what is on, how far through, what is next (same heights).
    val isLive = ShelfLiveRules.isLive(card)
    val liveNow = live?.now?.takeIf { isLive }
    val liveUntil = if (isLive) ShelfLiveRules.until(liveNow, live?.next, ::shelfLiveClock) else null
    val liveProgress = remember(liveNow, nowMinute) { liveNow?.progress(System.currentTimeMillis()) }
    // fork:shelf-home v3 - Fits before bed: "· 37 min to spare" after the end time.
    val spare = bedMinute?.let { bed ->
        (card as? ShelfCard.Meta)?.item?.let { ShelfEveningRules.spare(ShelfEveningRules.runtimeOf(it), nowMinute, bed) }
    }?.let(ShelfEveningRules::spareLabel)
    val facts = if (isLive) ShelfLiveRules.facts(liveNow) else card?.let(ShelfFrameRules::facts).orEmpty()
    val episodeTitle = if (isLive) ShelfLiveRules.title(liveNow).orEmpty() else card?.let(ShelfFrameRules::episodeTitle).orEmpty()
    val ends = card?.let { ShelfFrameRules.ends(it, nowMinute) { m -> clockLabel(m) } }
    Column(Modifier.width(if (view.mode == ShelfViewMode.Logos) 900.dp else 1000.dp)) {
        if (view.mode == ShelfViewMode.Logos) {
            ShelfTitleLogo(card = card, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        } else {
            Text(
                card?.title().orEmpty(),
                fontSize = 84.sp,
                lineHeight = 88.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.68).sp,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(92.dp),
            )
            Spacer(Modifier.height(18.dp))
        }
        Text(
            facts,
            fontSize = 19.sp,
            letterSpacing = 4.56.sp,
            color = ink.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(28.dp),
        )
        // The episode's name on its own line, as written: kept out of the capitals above so it is easy
        // to read and its own "/" is never taken for the breadcrumb's. Always 36 high (empty for a film).
        Spacer(Modifier.height(8.dp))
        Text(
            episodeTitle,
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(36.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (isLive) {
            // fork:shelf-home live-tv - [progress bar] Until 09:10 · Next: Heat, on the "ends" line's 32.
            Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                if (liveProgress != null) {
                    Box(
                        Modifier
                            .width(ShelfLiveRules.BAR_W.dp)
                            .height(ShelfLiveRules.BAR_H.dp)
                            .background(ink.copy(alpha = 0.22f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(liveProgress)
                                .background(accent, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                }
                Text(
                    liveUntil.orEmpty(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    color = ink.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else Text(
            buildAnnotatedString {
                if (ends != null) {
                    append(ends.first)
                    append(' ')
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) { append(ends.second) }
                    if (spare != null) withStyle(SpanStyle(color = accent)) { append(" · $spare") }
                }
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            color = ink.copy(alpha = 0.85f),
            maxLines = 1,
            modifier = Modifier.height(32.dp),
        )
        if (view.mode == ShelfViewMode.Posters) {
            Spacer(Modifier.height(12.dp))
            Text(
                (if (isLive) liveNow?.desc else card?.let(ShelfFrameRules::description)).orEmpty(),
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 28.sp,
                color = ink.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 880.dp).height(30.dp),
            )
        }
    }
}

/** The row header and the cards: Posters at 790 / 826 (V2Browse), Logos at 840 / 880 (V2Logos). */
@Composable
private fun RowArea(
    view: ShelfRowView,
    rowMode: ShelfViewMode,
    top: (Int) -> Dp,
    onClickCard: (Int) -> Unit,
    onSecondaryClickCard: (Int) -> Unit,
) {
    val ink = LocalShelfPalette.current.ink
    val logos = rowMode == ShelfViewMode.Logos
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 96.dp, end = 96.dp, top = top(if (logos) 840 else 790)),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                view.title.uppercase() + if (logos) " · VIEW AS LOGOS" else "",
                fontSize = 15.sp,
                letterSpacing = 4.2.sp,
                color = ink.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().weight(1f, fill = false),
            )
            Spacer(Modifier.width(32.dp))
            val dot = LocalShelfPalette.current.accentInk
            val position = if (logos) ShelfFrameRules.count(view.index, view.cards.size)
            else ShelfFrameRules.position(view.index, view.cards.size, view.row, view.rows)
            Text(
                buildAnnotatedString {
                    view.artLabel?.takeIf { !logos }?.let { label ->
                        append(label)
                        append(" ")
                        withStyle(SpanStyle(color = dot)) { append("●") }
                        append(" · ")
                    }
                    append(position)
                },
                fontSize = 15.sp,
                letterSpacing = 4.2.sp,
                color = ink.copy(alpha = 0.55f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        when {
            view.cards.isEmpty() -> Box(
                Modifier
                    .padding(start = 96.dp, top = top(if (logos) 880 else 826))
                    .height(if (logos) 110.dp else ShelfPosterRowRules.CARD_H.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Loading…", fontSize = 18.sp, fontWeight = FontWeight.Light, color = ink.copy(alpha = 0.55f), maxLines = 1)
            }
            logos -> ShelfLogoRow(
                cards = view.cards,
                focusedIndex = view.index,
                onClickCard = onClickCard,
                modifier = Modifier.padding(start = 96.dp, end = 96.dp, top = top(880)),
            )
            else -> ShelfPosterRow(
                cards = view.cards,
                focusedIndex = view.index,
                onClickCard = onClickCard,
                onSecondaryClickCard = onSecondaryClickCard,
                labels = if (view.evening) {
                    { c ->
                        (c as? ShelfCard.Meta)?.item?.let { ShelfEveningRules.endsAt(it, view.nowMinute) }
                            ?.let { ShelfEveningRules.endsLabel(it) }
                    }
                } else {
                    null
                },
                // The row's own padding keeps the focused card's outline clear of its edges.
                modifier = Modifier.padding(
                    start = (96 - ShelfPosterRowRules.RING_ROOM).dp,
                    top = top(826) - ShelfPosterRowRules.RING_ROOM.dp,
                ),
            )
        }
    }
}
