package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsItem

/*
 * fork:shelf-home - phase 1: the shelf home's model (plan in design-notes/home-shelves.md).
 *
 * Pure: it reads only what the classic home already has (its enabled rows in their order, the
 * loaded sections, the collections, whether Continue Watching has anything) and says which tabs and
 * shelves to show. It never decides WHAT a row contains; the renderer looks the live section or
 * collection up by the shelf's source every frame, so paging and refreshes need no rebuild here.
 */

enum class ShelfTabKind {
    /** The classic home's rows, in its order, one shelf each. */
    Home,

    /** A collection pinned to the top: each folder is a shelf. */
    Collection,

    /** Opens the existing Library page; it has no shelves of its own. */
    Library,
}

/** Where a shelf's items come from. The renderer resolves it; the model only names it. */
sealed interface ShelfSource {
    /** A home catalog row, by its section key (`manifestId:type:catalogId[:genre=…]`, or a fork row's key). */
    data class Catalog(val sectionKey: String) : ShelfSource

    data object ContinueWatching : ShelfSource
    data object UpNext : ShelfSource
    data object Upcoming : ShelfSource

    /** A collection that is not pinned: shown on Home as one shelf of its folders (as the classic home does). */
    data class CollectionFolders(val collectionId: String) : ShelfSource

    /** One folder of a pinned collection. */
    data class CollectionFolder(val collectionId: String, val folderId: String) : ShelfSource

    /** A shelf the user built from several home rows and some filters (ShelfMixes.kt). */
    data class Mix(val mixId: String) : ShelfSource

    /** v3 phase 4: "Fits before bed", the Home rows' titles that end before bedtime (ShelfEvening.kt). */
    data object Evening : ShelfSource
}

data class Shelf(
    /**
     * Stable across launches and unique within its tab: the key per-shelf choices (view, filters)
     * are stored under, so a catalog that moves or is renamed keeps them.
     */
    val key: String,
    val title: String,
    val source: ShelfSource,
)

data class ShelfTab(
    val id: String,
    val title: String,
    val kind: ShelfTabKind,
    val shelves: List<Shelf> = emptyList(),
)

data class ShelfHomeModel(val tabs: List<ShelfTab>) {
    fun tab(id: String): ShelfTab? = tabs.firstOrNull { it.id == id }

    companion object {
        val Empty = ShelfHomeModel(emptyList())
    }
}

/** Which of the Continue Watching rows have something in them (the classic home can split them in three). */
data class ShelfProgressRows(
    val continueWatching: Boolean = false,
    val upNext: Boolean = false,
    val upcoming: Boolean = false,
)

object ShelfHomeRules {
    const val HOME_TAB = "home"
    const val LIBRARY_TAB = "library"
    const val CONTINUE_WATCHING_KEY = "continueWatching"
    const val UP_NEXT_KEY = "upNext"
    const val UPCOMING_KEY = "upcoming"
    const val EVENING_KEY = "evening"
    private const val COLLECTION_KEY_PREFIX = "collection_"

    fun collectionTabId(collectionId: String) = "$COLLECTION_KEY_PREFIX$collectionId"
    fun folderShelfKey(collectionId: String, folderId: String) = "$COLLECTION_KEY_PREFIX$collectionId/$folderId"

    /**
     * @param enabledItems the classic home's enabled rows, in order (`buildEnabledHomeItems`).
     * @param sections the loaded sections by key; a row with no section, or an empty one, is left out
     *   just as the classic home leaves it out.
     * @param collections collections by their settings key (`collection_<id>`), those with folders only.
     * @param evening true when the evening home is on and it is evening (ShelfEveningRules): a "Fits before bed"
     *   shelf ([EVENING_KEY], [ShelfSource.Evening]) is put first on Home.
     */
    fun build(
        enabledItems: List<HomeCatalogSettingsItem>,
        sections: Map<String, HomeCatalogSection>,
        collections: Map<String, Collection>,
        progress: ShelfProgressRows = ShelfProgressRows(),
        titles: ShelfTitles = ShelfTitles(),
        mixes: List<ShelfMix> = emptyList(),
        evening: Boolean = false,
    ): ShelfHomeModel {
        val homeShelves = mutableListOf<Shelf>()
        // v3 phase 4: in the evening "Fits before bed" leads Home, ahead of Continue watching (board V3Evening).
        if (evening) {
            homeShelves += Shelf(EVENING_KEY, titles.evening, ShelfSource.Evening)
        }
        if (progress.continueWatching) {
            homeShelves += Shelf(CONTINUE_WATCHING_KEY, titles.continueWatching, ShelfSource.ContinueWatching)
        }
        if (progress.upNext) {
            homeShelves += Shelf(UP_NEXT_KEY, titles.upNext, ShelfSource.UpNext)
        }
        if (progress.upcoming) {
            homeShelves += Shelf(UPCOMING_KEY, titles.upcoming, ShelfSource.Upcoming)
        }
        // The user's own shelves come next, before the catalog rows.
        for (mix in mixes) {
            homeShelves += Shelf(ShelfMixRules.shelfKey(mix.id), mix.title, ShelfSource.Mix(mix.id))
        }

        val collectionTabs = mutableListOf<ShelfTab>()
        for (item in enabledItems) {
            if (item.isCollection) {
                val collection = collections[item.key] ?: continue
                if (collection.folders.isEmpty()) continue
                val title = item.displayTitle.ifBlank { collection.title }
                if (item.isPinnedToTop || collection.pinToTop) {
                    val tabId = collectionTabId(collection.id)
                    if (collectionTabs.any { it.id == tabId }) continue
                    collectionTabs += ShelfTab(
                        id = tabId,
                        title = title,
                        kind = ShelfTabKind.Collection,
                        shelves = collection.folders
                            .distinctBy { it.id }
                            .map { folder ->
                                Shelf(
                                    key = folderShelfKey(collection.id, folder.id),
                                    title = folder.title,
                                    source = ShelfSource.CollectionFolder(collection.id, folder.id),
                                )
                            },
                    )
                } else {
                    homeShelves += Shelf(item.key, title, ShelfSource.CollectionFolders(collection.id))
                }
            } else {
                val section = sections[item.key] ?: continue
                if (section.items.isEmpty()) continue
                homeShelves += Shelf(item.key, item.displayTitle.ifBlank { section.title }, ShelfSource.Catalog(section.key))
            }
        }

        val tabs = buildList {
            add(ShelfTab(HOME_TAB, titles.home, ShelfTabKind.Home, homeShelves.distinctBy { it.key }))
            addAll(collectionTabs)
            add(ShelfTab(LIBRARY_TAB, titles.library, ShelfTabKind.Library))
        }
        return ShelfHomeModel(tabs)
    }
}

/** The fixed words the model hands out; the screen passes its own (translated) ones. */
data class ShelfTitles(
    val home: String = "Home",
    val library: String = "Library",
    val continueWatching: String = "Continue watching",
    val upNext: String = "Up next",
    val upcoming: String = "Upcoming",
    val evening: String = "Fits before bed",
)
