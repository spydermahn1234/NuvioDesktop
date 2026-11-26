package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfHomeModelTest {
    private fun meta(id: String) = MetaPreview(id = id, type = "movie", name = id)

    private fun section(key: String, title: String = key, items: Int = 3) = HomeCatalogSection(
        key = key,
        title = title,
        subtitle = "AIOMetadata",
        addonName = "AIOMetadata",
        items = (1..items).map { meta("$key-$it") },
    )

    private fun row(key: String, customTitle: String = "", default: String = key) =
        HomeCatalogSettingsItem(key = key, defaultTitle = default, addonName = "AIOMetadata", customTitle = customTitle)

    private fun collectionRow(id: String, pinned: Boolean = false) = HomeCatalogSettingsItem(
        key = "collection_$id",
        defaultTitle = "",
        addonName = "",
        isCollection = true,
        collectionId = id,
        isPinnedToTop = pinned,
    )

    private fun collection(id: String, title: String, vararg folders: String, pinToTop: Boolean = false) = Collection(
        id = id,
        title = title,
        pinToTop = pinToTop,
        folders = folders.map { CollectionFolder(id = it, title = it.replaceFirstChar(Char::uppercase)) },
    )

    private val trending = "aio:movie:trending"
    private val netflix = "aio:series:netflix_best_week"
    private val sections = mapOf(
        trending to section(trending, "Trending"),
        netflix to section(netflix, "Netflix best of this week"),
    )

    @Test
    fun homeShelvesFollowTheClassicRowOrderAndCustomTitles() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(row(netflix, default = "Netflix best of this week"), row(trending, customTitle = "What's hot")),
            sections = sections,
            collections = emptyMap(),
        )
        val home = model.tab(ShelfHomeRules.HOME_TAB)!!
        assertEquals(listOf(netflix, trending), home.shelves.map { it.key })
        assertEquals(listOf("Netflix best of this week", "What's hot"), home.shelves.map { it.title })
        assertEquals(ShelfSource.Catalog(trending), home.shelves[1].source)
    }

    @Test
    fun rowsThatAreMissingOrEmptyAreLeftOutAsTheClassicHomeDoes() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(row("gone:movie:x"), row("empty:movie:y"), row(trending)),
            sections = sections + ("empty:movie:y" to section("empty:movie:y", items = 0)),
            collections = emptyMap(),
        )
        assertEquals(listOf(trending), model.tab(ShelfHomeRules.HOME_TAB)!!.shelves.map { it.key })
    }

    @Test
    fun continueWatchingRowsLeadHomeOnlyWhenTheyHaveSomething() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(row(trending)),
            sections = sections,
            collections = emptyMap(),
            progress = ShelfProgressRows(continueWatching = true, upNext = false, upcoming = true),
        )
        assertEquals(
            listOf(ShelfHomeRules.CONTINUE_WATCHING_KEY, ShelfHomeRules.UPCOMING_KEY, trending),
            model.tab(ShelfHomeRules.HOME_TAB)!!.shelves.map { it.key },
        )
    }

    @Test
    fun aPinnedCollectionBecomesATabWithAShelfPerFolder() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(row(trending), collectionRow("films", pinned = true)),
            sections = sections,
            collections = mapOf("collection_films" to collection("films", "Films", "comedy", "horror")),
        )
        assertEquals(listOf("home", "collection_films", "library"), model.tabs.map { it.id })
        val films = model.tab("collection_films")!!
        assertEquals(ShelfTabKind.Collection, films.kind)
        assertEquals("Films", films.title)
        assertEquals(listOf("collection_films/comedy", "collection_films/horror"), films.shelves.map { it.key })
        assertEquals(ShelfSource.CollectionFolder("films", "horror"), films.shelves[1].source)
        // Not on Home as well.
        assertTrue(model.tab("home")!!.shelves.none { it.source is ShelfSource.CollectionFolders })
    }

    @Test
    fun collectionPinnedInItsOwnSettingsIsATabToo() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(collectionRow("kids")),
            sections = sections,
            collections = mapOf("collection_kids" to collection("kids", "Kids", "pixar", pinToTop = true)),
        )
        assertEquals(ShelfTabKind.Collection, model.tab("collection_kids")?.kind)
    }

    @Test
    fun anUnpinnedCollectionStaysOnHomeAsOneShelfOfItsFolders() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(row(trending), collectionRow("hang"), row(netflix)),
            sections = sections,
            collections = mapOf("collection_hang" to collection("hang", "The Hang", "short", "funny")),
        )
        val home = model.tab(ShelfHomeRules.HOME_TAB)!!
        assertEquals(listOf(trending, "collection_hang", netflix), home.shelves.map { it.key })
        assertEquals("The Hang", home.shelves[1].title)
        assertEquals(ShelfSource.CollectionFolders("hang"), home.shelves[1].source)
        assertNull(model.tab("collection_hang"))
    }

    @Test
    fun collectionsWithoutFoldersOrNotLoadedAreLeftOut() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(collectionRow("empty", pinned = true), collectionRow("unknown")),
            sections = sections,
            collections = mapOf("collection_empty" to collection("empty", "Empty")),
        )
        assertEquals(listOf("home", "library"), model.tabs.map { it.id })
        assertTrue(model.tab("home")!!.shelves.isEmpty())
    }

    @Test
    fun homeAndLibraryAreAlwaysThereAndLibraryHasNoShelves() {
        val model = ShelfHomeRules.build(emptyList(), emptyMap(), emptyMap())
        assertEquals(listOf(ShelfTabKind.Home, ShelfTabKind.Library), model.tabs.map { it.kind })
        assertTrue(model.tab(ShelfHomeRules.LIBRARY_TAB)!!.shelves.isEmpty())
    }

    @Test
    fun shelfKeysAreUniqueWithinATab() {
        val model = ShelfHomeRules.build(
            enabledItems = listOf(row(trending), row(trending)),
            sections = sections,
            collections = mapOf("collection_c" to collection("c", "C", "a", "a")),
        )
        assertEquals(listOf(trending), model.tab("home")!!.shelves.map { it.key })
        val pinned = ShelfHomeRules.build(
            enabledItems = listOf(collectionRow("c", pinned = true)),
            sections = sections,
            collections = mapOf("collection_c" to collection("c", "C", "a", "a")),
        )
        assertEquals(1, pinned.tab("collection_c")!!.shelves.size)
    }
}
