// fork:shelf-home
package com.nuvio.app.features.home.shelves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShelfGroupRulesTest {

    private fun catalog(title: String, key: String = "addon:movie:${title.lowercase()}") =
        Shelf(key, title, ShelfSource.Catalog(key))

    private fun tab(vararg shelves: Shelf, kind: ShelfTabKind = ShelfTabKind.Home) =
        ShelfTab(if (kind == ShelfTabKind.Home) ShelfHomeRules.HOME_TAB else "collection_x", "Home", kind, shelves.toList())

    private fun titles(group: ShelfGroup): List<String> = group.entries.map {
        when (it) {
            is ShelfGroupEntry.Row -> it.shelf.title
            is ShelfGroupEntry.Bucket -> it.label
        }
    }

    @Test
    fun theMockupsHomeLandsInTheFourGroupsInOrder() {
        val home = tab(
            Shelf(ShelfHomeRules.CONTINUE_WATCHING_KEY, "Continue watching", ShelfSource.ContinueWatching),
            Shelf(ShelfHomeRules.UP_NEXT_KEY, "Up next", ShelfSource.UpNext),
            Shelf(ShelfHomeRules.UPCOMING_KEY, "Upcoming", ShelfSource.Upcoming),
            Shelf("mix:hang", "The Hang", ShelfSource.Mix("hang")),
            catalog("AI for you — Movies"),
            catalog("Top 100 Today — Movies"),
            catalog("Live TV"),
            catalog("Because: Musk"),
            catalog("Trending — Series"),
            catalog("Comedy"),
            catalog("Random Play"),
            catalog("Action"),
            catalog("Top Rated — Movies"),
        )
        val groups = ShelfGroupRules.group(home)
        assertEquals(listOf("YOURS", "FOR YOU", "CHARTS", "CATALOGS"), groups.map { it.label })
        assertEquals(listOf("Continue watching", "Up next", "Upcoming", "The Hang", "Live TV"), titles(groups[0]))
        assertEquals(listOf("AI for you — Movies", "Because: Musk", "Random Play"), titles(groups[1]))
        assertEquals(listOf("Top 100 Today — Movies", "Trending — Series", "Top Rated — Movies"), titles(groups[2]))
        // Catalogs are A-Z.
        assertEquals(listOf("Action", "Comedy"), titles(groups[3]))
        assertEquals(listOf(5, 3, 3, 2), groups.map { it.total })
    }

    @Test
    fun wordsMatchWholeWordsOnly() {
        val groups = ShelfGroupRules.group(
            tab(catalog("Airplane movies"), catalog("Librarything picks"), catalog("Popularity contest"), catalog("Topless"))
        )
        // "airplane" is not "ai"; "picks" is not "picked"; "popularity" is not "popular"; no number after "top".
        assertEquals(listOf("CATALOGS"), groups.map { it.label })
        assertEquals(4, groups.single().total)
    }

    @Test
    fun matchingIgnoresCaseAndPunctuation() {
        val groups = ShelfGroupRules.group(
            tab(catalog("RECOMMENDED"), catalog("Most-Watched shows"), catalog("My List"), catalog("Netflix top10 today"))
        )
        assertEquals(listOf("YOURS", "FOR YOU", "CHARTS"), groups.map { it.label })
        assertEquals(listOf("My List"), titles(groups[0]))
        assertEquals(listOf("RECOMMENDED"), titles(groups[1]))
        assertEquals(listOf("Most-Watched shows", "Netflix top10 today"), titles(groups[2]))
    }

    @Test
    fun unpinnedCollectionsAreYours() {
        val groups = ShelfGroupRules.group(
            tab(Shelf("collection_abc", "Marvel", ShelfSource.CollectionFolders("abc")), catalog("Drama"))
        )
        assertEquals(listOf("Marvel"), titles(groups[0]))
        assertEquals("CATALOGS", groups[1].label)
    }

    @Test
    fun emptyGroupsAreDropped() {
        assertTrue(ShelfGroupRules.group(tab()).isEmpty())
        val groups = ShelfGroupRules.group(tab(catalog("Trending now")))
        assertEquals(listOf("CHARTS"), groups.map { it.label })
    }

    @Test
    fun eightCatalogsStayRowsNineAreBucketed() {
        val eight = (1..8).map { catalog("Genre $it") }.toTypedArray()
        val asRows = ShelfGroupRules.group(tab(*eight)).single()
        assertTrue(asRows.entries.all { it is ShelfGroupEntry.Row })

        val nine = ShelfGroupRules.group(tab(*eight, catalog("Westerns"))).single()
        assertEquals(9, nine.total)
        assertEquals(listOf("G – K", "U – Z"), titles(nine))
        val g = assertIs<ShelfGroupEntry.Bucket>(nine.entries[0])
        assertEquals(8, g.shelves.size)
    }

    @Test
    fun bucketsFollowTheMockupsRangesAndDropEmptyOnes() {
        val shelves = listOf(
            "Zombies", "action", "Best Picture", "Crime", "Drama", "Fantasy", "Horror", "Kids", "Mystery",
            "Noir", "Romance", "Sci-Fi", "Thriller", "Westerns", "1980s", "#1 Hits",
        ).map { catalog(it) }
        val group = ShelfGroupRules.group(tab(*shelves.toTypedArray())).single()
        assertEquals("CATALOGS", group.label)
        assertEquals(shelves.size, group.total)
        assertEquals(listOf("A – C", "D – F", "G – K", "L – N", "O – R", "S – T", "U – Z"), titles(group))
        val first = assertIs<ShelfGroupEntry.Bucket>(group.entries[0])
        // Digits and symbols go to A – C; inside a bucket the order is A-Z.
        assertEquals(listOf("#1 Hits", "1980s", "action", "Best Picture", "Crime").sorted(), first.shelves.map { it.title }.sorted())
        assertEquals(listOf("Westerns", "Zombies"), (group.entries.last() as ShelfGroupEntry.Bucket).shelves.map { it.title })

        val sparse = ShelfGroupRules.group(tab(*(1..9).map { catalog("Anime $it") }.toTypedArray(), catalog("Zen"))).single()
        assertEquals(listOf("A – C", "U – Z"), titles(sparse))
    }

    @Test
    fun aPinnedCollectionsFoldersUseTheSameRules() {
        val folders = listOf("Iron Man", "Trending in the MCU", "Because you liked Thor", "Avengers").map { name ->
            Shelf("collection_x/$name", name, ShelfSource.CollectionFolder("x", name))
        }
        val groups = ShelfGroupRules.group(tab(*folders.toTypedArray(), kind = ShelfTabKind.Collection))
        assertEquals(listOf("FOR YOU", "CHARTS", "CATALOGS"), groups.map { it.label })
        assertEquals(listOf("Avengers", "Iron Man"), titles(groups[2]))
    }

    @Test
    fun bucketOfReadsTheFirstLetter() {
        assertEquals("A – C", ShelfGroupRules.bucketOf("2001 picks"))
        assertEquals("S – T", ShelfGroupRules.bucketOf("  thrillers"))
        assertEquals("L – N", ShelfGroupRules.bucketOf("Noir"))
        assertEquals("A – C", ShelfGroupRules.bucketOf(""))
    }
}
