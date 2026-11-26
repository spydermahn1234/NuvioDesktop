// fork:shelf-home
package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.FolderTab
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfFolderItemsTest {
    private fun m(id: String, type: String = "movie") = MetaPreview(id = id, type = type, name = id)
    private fun ids(items: List<MetaPreview>) = items.map { it.id }

    private fun tab(
        vararg ids: String,
        loading: Boolean = false,
        loadingMore: Boolean = false,
        pages: Boolean = false,
        nextSkip: Int? = null,
        error: String? = null,
    ) = FolderTab(
        label = "t",
        items = ids.map { m(it) },
        isLoading = loading,
        isLoadingMore = loadingMore,
        supportsPagination = pages,
        nextSkip = nextSkip,
        error = error,
    )

    @Test
    fun roundRobinInterleavesAndKeepsTheFirstCopy() {
        val merged = ShelfFolderRules.roundRobin(
            listOf(listOf(m("a"), m("b"), m("c")), listOf(m("x"), m("a"))),
        )
        assertEquals(listOf("a", "x", "b", "c"), ids(merged))
    }

    @Test
    fun sameIdOfAnotherTypeIsADifferentTitle() {
        val merged = ShelfFolderRules.roundRobin(listOf(listOf(m("1", "movie")), listOf(m("1", "series"))))
        assertEquals(2, merged.size)
    }

    @Test
    fun holdsBackWhileAFirstLoadIsPendingUntilTheHoldExpires() {
        val tabs = listOf(tab("a"), tab(loading = true))
        assertTrue(ShelfFolderRules.publish(emptyList(), tabs, holdExpired = false).isEmpty())
        assertEquals(listOf("a"), ids(ShelfFolderRules.publish(emptyList(), tabs, holdExpired = true)))
    }

    @Test
    fun neverReordersWhatIsShownOnlyAppends() {
        val shown = listOf(m("a"), m("b"))
        // Source 2 arrived late: its titles go after, not between.
        val tabs = listOf(tab("a", "b"), tab("x", "y"))
        assertEquals(listOf("a", "b", "x", "y"), ids(ShelfFolderRules.publish(shown, tabs, holdExpired = true)))
    }

    @Test
    fun snapshotFlags() {
        val loading = ShelfFolderRules.snapshot(ShelfFolderSnapshot(), listOf(tab(loading = true)), false)
        assertTrue(loading.isLoading)
        assertNull(loading.error)

        val failed = ShelfFolderRules.snapshot(ShelfFolderSnapshot(), listOf(tab(error = "boom")), false)
        assertFalse(failed.isLoading)
        assertEquals("boom", failed.error)

        val paging = ShelfFolderRules.snapshot(
            ShelfFolderSnapshot(),
            listOf(tab("a", pages = true, nextSkip = 2), tab("b")),
            false,
        )
        assertTrue(paging.canLoadMore)
        assertEquals(listOf("a", "b"), ids(paging.items))
    }

    @Test
    fun onlyIdleSourcesThatPageAreAskedForMore() {
        val tabs = listOf(
            tab("a", pages = true, nextSkip = 2),
            tab("b", pages = true, nextSkip = 2, loadingMore = true),
            tab("c", pages = false, nextSkip = 2),
            tab("d", pages = true, nextSkip = null),
        )
        assertEquals(listOf(0), ShelfFolderRules.pagesToLoad(tabs))
    }

    @Test
    fun tabsForSourcesNeedNoNetwork() {
        val tabs = ShelfFolderRules.tabsFor(
            listOf(
                CollectionSource(provider = "tmdb", tmdbSourceType = "COLLECTION"),
                CollectionSource(provider = "trakt"),
            ),
        )
        assertFalse(tabs[0].supportsPagination)
        assertTrue(tabs[1].supportsPagination)
    }
}
