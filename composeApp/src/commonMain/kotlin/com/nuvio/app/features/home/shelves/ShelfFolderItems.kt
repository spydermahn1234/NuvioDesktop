// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.applyCatalogPaginationProbe
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.catalog.mergeCatalogItems
import com.nuvio.app.features.catalog.nextCatalogPaginationState
import com.nuvio.app.features.catalog.supportsPagination
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.FolderTab
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.collection.TmdbCollectionSourceType
import com.nuvio.app.features.collection.fallbackCatalogId
import com.nuvio.app.features.collection.fetchCollectionCatalogPage
import com.nuvio.app.features.collection.findCollectionCatalog
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.WATCHED_FILTER_RESOLVE_BUDGET_MS
import com.nuvio.app.features.home.WatchedContentFilter
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.features.home.filterUnwatchedItems
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.posterservice.CustomPosterScreen
import com.nuvio.app.features.posterservice.withCachedCustomPosters
import com.nuvio.app.features.posterservice.withCustomPosterOverlay
import com.nuvio.app.features.trakt.TraktPublicListSourceResolver
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * fork:shelf-home - phase 3: a pinned collection's folder shelf shows the folder's titles.
 *
 * FolderDetailRepository holds ONE folder (the folder screen's), so it is not touched here: this
 * loads a folder's sources through the same lower-level calls it uses (TmdbCollectionSourceResolver,
 * TraktPublicListSourceResolver, fetchCollectionCatalogPage with the same pagination probe, the same
 * unreleased/watched filters and the Collections poster overlay), one cache entry per folder for
 * the session. Nothing loads until a shelf asks (ShelfHome asks only for the focused shelf).
 *
 * The shelf is one row, so the sources are merged round-robin as the folder's "All" tab merges them,
 * but the row never reorders what it already shows (the stage rule: nothing moves): the first
 * sources are held back for up to [ShelfFolderRules.HOLD_MS] so they interleave, after which new
 * titles (late sources, more pages) are only appended.
 */

/** What a folder shelf shows. [items] only ever grows at the end while the entry lives. */
internal data class ShelfFolderSnapshot(
    val items: List<MetaPreview> = emptyList(),
    /** True until the first titles are published (or every source has answered). */
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /** Set when every source failed and there is nothing to show. */
    val error: String? = null,
    /** One per source, the folder screen's own tab type (no "All" tab). */
    val tabs: List<FolderTab> = emptyList(),
    val holdExpired: Boolean = false,
)

/** What [rememberShelfFolderItems] hands ShelfHome. */
internal class ShelfFolderItemsState(
    val items: List<MetaPreview>,
    val isLoading: Boolean,
    val isLoadingMore: Boolean,
    val canLoadMore: Boolean,
    val error: String?,
    /** Asks every source that pages for its next page; a no-op when none can or one is already loading. */
    val loadMore: () -> Unit,
)

/**
 * The titles of [folderId] in [collectionId], loaded the first time this is composed with
 * [active] true and kept for the session. Call it only for the focused shelf.
 */
@Composable
internal fun rememberShelfFolderItems(
    collectionId: String,
    folderId: String,
    active: Boolean = true,
): ShelfFolderItemsState {
    val flow = remember(collectionId, folderId) { ShelfFolderItems.flow(collectionId, folderId) }
    LaunchedEffect(collectionId, folderId, active) {
        if (active) ShelfFolderItems.ensureLoaded(collectionId, folderId)
    }
    val snapshot by flow.collectAsState()
    return remember(snapshot, collectionId, folderId) {
        ShelfFolderItemsState(
            items = snapshot.items,
            isLoading = snapshot.isLoading,
            isLoadingMore = snapshot.isLoadingMore,
            canLoadMore = snapshot.canLoadMore,
            error = snapshot.error,
            loadMore = { ShelfFolderItems.loadMore(collectionId, folderId) },
        )
    }
}

/** Pure parts, tested in ShelfFolderItemsTest. */
internal object ShelfFolderRules {
    /** How long the first sources wait for each other before titles show in arrival order. */
    const val HOLD_MS = 2_500L

    /** Round-robin across [lists], first copy of each title (by type:id) kept; as the folder's All tab. */
    fun roundRobin(lists: List<List<MetaPreview>>): List<MetaPreview> {
        val merged = mutableListOf<MetaPreview>()
        val seen = mutableSetOf<String>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (list in lists) {
                val item = list.getOrNull(i) ?: continue
                if (seen.add(item.stableKey())) merged += item
            }
        }
        return merged
    }

    /**
     * [previous] unchanged, then whatever the sources hold that it does not, in round-robin order.
     * Before the hold expires nothing is published while any source is still on its first load.
     */
    fun publish(previous: List<MetaPreview>, tabs: List<FolderTab>, holdExpired: Boolean): List<MetaPreview> {
        val firstLoadsPending = tabs.any { it.isLoading && it.items.isEmpty() }
        if (previous.isEmpty() && firstLoadsPending && !holdExpired) return previous
        val shown = previous.mapTo(mutableSetOf()) { it.stableKey() }
        val added = roundRobin(tabs.map { it.items }).filter { it.stableKey() !in shown }
        return if (added.isEmpty()) previous else previous + added
    }

    fun snapshot(previous: ShelfFolderSnapshot, tabs: List<FolderTab>, holdExpired: Boolean): ShelfFolderSnapshot {
        val items = publish(previous.items, tabs, holdExpired)
        val anyFirstLoad = tabs.any { it.isLoading }
        return ShelfFolderSnapshot(
            items = items,
            isLoading = items.isEmpty() && anyFirstLoad,
            isLoadingMore = tabs.any { it.isLoadingMore },
            canLoadMore = tabs.any { it.canLoadMore },
            error = if (items.isEmpty() && !anyFirstLoad) tabs.firstNotNullOfOrNull { it.error } else null,
            tabs = tabs,
            holdExpired = holdExpired,
        )
    }

    /** The sources that should be asked for their next page now. */
    fun pagesToLoad(tabs: List<FolderTab>): List<Int> =
        tabs.indices.filter { i ->
            val tab = tabs[i]
            tab.canLoadMore && !tab.isLoading && !tab.isLoadingMore
        }

    /** One tab per source, before anything is fetched; the addon catalogs are matched at load time. */
    fun tabsFor(sources: List<CollectionSource>): List<FolderTab> = sources.map { source ->
        when {
            source.isTmdb -> FolderTab(
                label = source.title?.takeIf { it.isNotBlank() } ?: "TMDB",
                source = source,
                supportsPagination = source.tmdbSourceType !in setOf(
                    TmdbCollectionSourceType.COLLECTION.name,
                    TmdbCollectionSourceType.PERSON.name,
                    TmdbCollectionSourceType.DIRECTOR.name,
                ),
                isLoading = false,
            )
            source.isTrakt -> FolderTab(
                label = source.title?.takeIf { it.isNotBlank() } ?: "Trakt",
                source = source,
                supportsPagination = true,
                isLoading = false,
            )
            else -> {
                val catalog = source.addonCatalogSource()
                FolderTab(
                    label = catalog?.catalogId ?: "addon",
                    source = source,
                    type = catalog?.type.orEmpty(),
                    catalogId = catalog?.catalogId.orEmpty(),
                    genre = catalog?.genre,
                    isLoading = false,
                )
            }
        }
    }
}

/** The session cache of folder shelves. Entry bookkeeping is touched from the UI thread only. */
internal object ShelfFolderItems {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("ShelfFolderItems")

    private class Entry(val collectionId: String, val folderId: String) {
        val state = MutableStateFlow(ShelfFolderSnapshot())
        val shown: StateFlow<ShelfFolderSnapshot> = state.asStateFlow()
            .withCustomPosterOverlay(CustomPosterScreen.Collections) { settings, keys ->
                copy(items = items.withCachedCustomPosters(settings, keys))
            }
        var sources: List<CollectionSource>? = null
        val jobs = mutableMapOf<Int, Job>()
        var holdJob: Job? = null

        /** Bumped on every (re)load; a result from an older generation is dropped. */
        @Volatile
        var generation = 0
    }

    private val entries = mutableMapOf<String, Entry>()

    private fun entry(collectionId: String, folderId: String): Entry =
        entries.getOrPut("$collectionId/$folderId") { Entry(collectionId, folderId) }

    fun flow(collectionId: String, folderId: String): StateFlow<ShelfFolderSnapshot> =
        entry(collectionId, folderId).shown

    /** Starts loading the folder unless it already has (for the same sources) this session. */
    fun ensureLoaded(collectionId: String, folderId: String) {
        val entry = entry(collectionId, folderId)
        val folder = CollectionRepository.getCollection(collectionId)?.folders?.firstOrNull { it.id == folderId }
        val sources = folder?.resolvedSources.orEmpty()
        if (entry.sources == sources) return
        // First load, or the collection was edited: start over.
        entry.jobs.values.forEach { it.cancel() }
        entry.jobs.clear()
        entry.holdJob?.cancel()
        entry.generation++
        entry.sources = sources
        val tabs = ShelfFolderRules.tabsFor(sources).map { it.copy(isLoading = true) }
        entry.state.value = ShelfFolderRules.snapshot(ShelfFolderSnapshot(), tabs, holdExpired = false)
        if (tabs.isEmpty()) return
        val generation = entry.generation
        entry.holdJob = scope.launch {
            delay(ShelfFolderRules.HOLD_MS)
            if (entry.generation != generation) return@launch
            entry.state.update { ShelfFolderRules.snapshot(it, it.tabs, holdExpired = true) }
        }
        tabs.indices.forEach { loadPage(entry, it, reset = true) }
    }

    fun loadMore(collectionId: String, folderId: String) {
        val entry = entries["$collectionId/$folderId"] ?: return
        ShelfFolderRules.pagesToLoad(entry.state.value.tabs).forEach { loadPage(entry, it, reset = false) }
    }

    private fun updateTab(entry: Entry, generation: Int, index: Int, transform: (FolderTab) -> FolderTab) {
        if (entry.generation != generation) return
        entry.state.update { current ->
            if (index !in current.tabs.indices) return@update current
            val tabs = current.tabs.toMutableList().also { it[index] = transform(it[index]) }
            ShelfFolderRules.snapshot(current, tabs, current.holdExpired)
        }
    }

    private fun loadPage(entry: Entry, index: Int, reset: Boolean) {
        val tab = entry.state.value.tabs.getOrNull(index) ?: return
        val source = tab.source ?: return
        val requestedSkip = if (reset) 0 else tab.nextSkip ?: return
        val generation = entry.generation
        if (!reset) updateTab(entry, generation, index) { it.copy(isLoadingMore = true, error = null) }

        entry.jobs.remove(index)?.cancel()
        entry.jobs[index] = scope.launch {
            var manifestUrl = tab.manifestUrl
            var fallbackCatalogId = tab.fallbackCatalogId
            var supportsPagination = tab.supportsPagination
            var loadedCatalogId = tab.catalogId
            runCatching {
                if (!source.isTmdb && !source.isTrakt && manifestUrl == null) {
                    val catalogSource = source.addonCatalogSource()
                        ?: error("Source has no addon catalog")
                    val resolved = AddonRepository.uiState.value.addons.findCollectionCatalog(catalogSource)
                        ?: withTimeoutOrNull(ADDON_LOAD_WAIT_MS) {
                            // Opened straight after launch: wait for the addons, as the folder screen does.
                            AddonRepository.uiState.first { s -> s.addons.none(ManagedAddon::isStillLoading) }
                        }?.addons?.findCollectionCatalog(catalogSource)
                        ?: error("Addon ${catalogSource.addonId} not found")
                    manifestUrl = resolved.addon.manifestUrl
                    fallbackCatalogId = resolved.fallbackCatalogId(catalogSource)
                    supportsPagination = resolved.catalog.supportsPagination()
                }
                val initialPage = when {
                    source.isTmdb -> TmdbCollectionSourceResolver.resolve(source, page = if (reset) 1 else requestedSkip)
                    source.isTrakt -> TraktPublicListSourceResolver.resolve(source, page = if (reset) 1 else requestedSkip)
                    else -> fetchCollectionCatalogPage(
                        manifestUrl = requireNotNull(manifestUrl),
                        type = tab.type,
                        catalogId = tab.catalogId,
                        fallbackCatalogId = fallbackCatalogId.takeIf { reset },
                        genre = tab.genre,
                        skip = requestedSkip.takeIf { it > 0 },
                    ).also { loadedCatalogId = it.catalogId }.page
                }
                // Same probe as the folder screen: an addon that pages without saying so.
                if (reset && !source.isTmdb && !source.isTrakt && !supportsPagination &&
                    initialPage.rawItemCount > 0 && initialPage.nextSkip != null
                ) {
                    val probe = runCatching {
                        fetchCatalogPage(
                            manifestUrl = requireNotNull(manifestUrl),
                            type = tab.type,
                            catalogId = loadedCatalogId,
                            genre = tab.genre,
                            skip = initialPage.nextSkip,
                        )
                    }.getOrNull()
                    if (probe != null) {
                        val result = applyCatalogPaginationProbe(initialPage, probe)
                        supportsPagination = result.supportsPagination
                        result.page
                    } else {
                        initialPage
                    }
                } else {
                    initialPage
                }
            }.onSuccess { loaded ->
                val page = loaded.withShelfFilters()
                updateTab(entry, generation, index) { t ->
                    val merged = if (reset) page.items else mergeCatalogItems(t.items, page.items)
                    val pages = t.supportsPagination || supportsPagination
                    val state = nextCatalogPaginationState(
                        supportsPagination = pages,
                        requestedSkip = requestedSkip,
                        page = page,
                        loadedNewItems = reset || merged.size > t.items.size,
                        consecutiveDuplicatePages = if (reset) 0 else t.consecutiveDuplicatePages,
                    )
                    t.copy(
                        items = merged,
                        manifestUrl = manifestUrl,
                        fallbackCatalogId = fallbackCatalogId,
                        catalogId = loadedCatalogId,
                        supportsPagination = pages,
                        isLoading = false,
                        isLoadingMore = false,
                        nextSkip = state.nextSkip,
                        consecutiveDuplicatePages = state.consecutiveDuplicatePages,
                        error = null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                log.i { "Folder ${entry.folderId} source ${index + 1} failed: ${error.message}" }
                updateTab(entry, generation, index) { t ->
                    t.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        nextSkip = if (reset) null else t.nextSkip,
                        error = error.message,
                    )
                }
            }
        }
    }

    /** The folder screen's filters (its own copy is private to FolderDetailRepository). */
    private suspend fun CatalogPage.withShelfFilters(): CatalogPage {
        val released = if (HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) {
            items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
        } else {
            items
        }
        WatchedContentFilter.prepareForLoad(released, resolveBudgetMs = WATCHED_FILTER_RESOLVE_BUDGET_MS)
        val filtered = released.filterUnwatchedItems(WatchedContentFilter.current())
        return if (filtered.size == items.size) this else copy(items = filtered)
    }

    private const val ADDON_LOAD_WAIT_MS = 30_000L
}

/** An addon has not answered yet: enabled, no manifest, and no failure recorded (as FolderDetailRepository). */
private fun ManagedAddon.isStillLoading(): Boolean = enabled && manifest == null && errorMessage == null
