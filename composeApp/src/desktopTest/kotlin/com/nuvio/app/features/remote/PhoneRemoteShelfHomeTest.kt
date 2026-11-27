package com.nuvio.app.features.remote

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.shelves.Shelf
import com.nuvio.app.features.home.shelves.ShelfCard
import com.nuvio.app.features.home.shelves.ShelfHomeRules
import com.nuvio.app.features.home.shelves.ShelfSource
import com.nuvio.app.features.home.shelves.ShelfTab
import com.nuvio.app.features.home.shelves.ShelfTabKind
import com.nuvio.app.features.home.shelves.ShelfViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// fork:phone-remote shelf-home. The same lines are read on the phone by ShelfHomeLineTest (remote-android).
class PhoneRemoteShelfHomeTest {
    private val screen = RemoteShelfHomeScreen(
        mode = RemoteShelfHomeScreen.BROWSE,
        tabId = "home",
        tabTitle = "Home",
        shelfKey = "aimovies",
        rowTitle = "AI for you — Movies",
        row = 3,
        rows = 20,
        index = 2,
        count = 50,
        from = 0,
        cards = listOf(RemoteShelfHomeCard("There Will Be Blood", "https://x/p.jpg"), RemoteShelfHomeCard("Heat", null)),
        title = "There Will Be Blood",
        facts = "2007 · DRAMA · ENDS 23:42",
        art = "https://x/b.jpg",
        view = "Posters",
        shelves = listOf(
            RemoteShelfRow("home", "Home", "FOR YOU", null, "aimovies", "AI for you — Movies"),
            RemoteShelfRow("home", "Home", "CATALOGS", "A – C", "cin:movie:top", "Cinemeta, Top"),
        ),
    )

    @Test
    fun theHomeTravelsAsOneLine() {
        assertEquals(
            "HOME 1 browse home Home aimovies AI%20for%20you%20%E2%80%94%20Movies 3 20 2 50 0 There%20Will%20Be%20Blood " +
                "2007%20%C2%B7%20DRAMA%20%C2%B7%20ENDS%2023%3A42 https%3A%2F%2Fx%2Fb.jpg Posters " +
                "There%20Will%20Be%20Blood,https%3A%2F%2Fx%2Fp.jpg;Heat,-",
            PhoneRemoteLibraryLines.homeLine(screen),
        )
        assertEquals("HOME 0", PhoneRemoteLibraryLines.homeLine(null))
    }

    @Test
    fun theRowsTravelOnTheirOwnLine() {
        assertEquals(
            "SHELVES 1 home,Home,FOR%20YOU,-,aimovies,AI%20for%20you%20%E2%80%94%20Movies;" +
                "home,Home,CATALOGS,A%20%E2%80%93%20C,cin%3Amovie%3Atop,Cinemeta%2C%20Top",
            PhoneRemoteLibraryLines.shelvesLine(screen),
        )
        assertEquals("SHELVES 0", PhoneRemoteLibraryLines.shelvesLine(null))
    }

    @Test
    fun tapsAreReadBack() {
        assertEquals(RemoteShelfHomeAction.Move(-1), RemoteShelfHomeAction.parse(listOf("move", "-1")))
        assertEquals(RemoteShelfHomeAction.Pick(7), RemoteShelfHomeAction.parse(listOf("pick", "7")))
        assertEquals(RemoteShelfHomeAction.Row(1), RemoteShelfHomeAction.parse(listOf("row", "1")))
        assertEquals(RemoteShelfHomeAction.Play, RemoteShelfHomeAction.parse(listOf("play")))
        assertEquals(RemoteShelfHomeAction.View, RemoteShelfHomeAction.parse(listOf("VIEW")))
        assertEquals(RemoteShelfHomeAction.PickShelf("home", "cin:movie:top"), RemoteShelfHomeAction.parse(listOf("shelf", "home", "cin:movie:top")))
        assertNull(RemoteShelfHomeAction.parse(listOf("move", "0")))
        assertNull(RemoteShelfHomeAction.parse(listOf("pick", "-2")))
        assertNull(RemoteShelfHomeAction.parse(listOf("shelf", "home")))
        assertNull(RemoteShelfHomeAction.parse(listOf("jump")))
        assertEquals(
            RemoteMessage.HomeDo(RemoteShelfHomeAction.PickShelf("home", "cin:movie:top")),
            PhoneRemoteProtocol.parse("HOME_DO shelf home cin%3Amovie%3Atop"),
        )
        assertEquals(RemoteMessage.HomeDo(RemoteShelfHomeAction.Info), PhoneRemoteProtocol.parse("HOME_DO info"))
    }

    @Test
    fun theWindowStaysInsideTheRow() {
        assertEquals(0, RemoteShelfHomeRules.windowStart(3, 50))
        assertEquals(15, RemoteShelfHomeRules.windowStart(25, 50))
        assertEquals(30, RemoteShelfHomeRules.windowStart(49, 50))
        assertEquals(0, RemoteShelfHomeRules.windowStart(5, 8))
    }

    @Test
    fun theScreenIsBuiltFromTheFrame() {
        val tab = ShelfTab(
            id = ShelfHomeRules.HOME_TAB,
            title = "Home",
            kind = ShelfTabKind.Home,
            shelves = listOf(
                Shelf(ShelfHomeRules.CONTINUE_WATCHING_KEY, "Continue watching", ShelfSource.ContinueWatching),
                Shelf("ai", "AI for you — Movies", ShelfSource.Catalog("ai")),
                Shelf("top", "Top 100 Today", ShelfSource.Catalog("top")),
                Shelf("west", "Westerns", ShelfSource.Catalog("west")),
            ),
        )
        val cards = (0 until 30).map { i ->
            ShelfCard.Meta(
                MetaPreview(
                    id = "tt$i",
                    type = "movie",
                    name = "Film $i",
                    poster = "https://x/$i.jpg",
                    releaseInfo = "2007",
                    runtime = "158 min",
                    genres = listOf("Drama", "History"),
                ),
            )
        }
        val clock: (Int) -> String = { m -> "${m / 60}:${(m % 60).toString().padStart(2, '0')}" }
        val built = RemoteShelfHomeRules.screen(
            mode = RemoteShelfHomeScreen.BROWSE,
            tabs = listOf(tab),
            tab = tab,
            rowIndex = 1,
            cards = cards,
            cardIndex = 25,
            viewMode = ShelfViewMode.Posters,
            artUrl = "https://x/art.jpg",
            nowMinute = 21 * 60 + 4,
            clock = clock,
        )
        assertEquals("ai", built.shelfKey)
        assertEquals("AI for you — Movies", built.rowTitle)
        assertEquals(1, built.row)
        assertEquals(4, built.rows)
        assertEquals(25, built.index)
        assertEquals(30, built.count)
        assertEquals(10, built.from)
        assertEquals(20, built.cards.size)
        assertEquals(RemoteShelfHomeCard("Film 10", "https://x/10.jpg"), built.cards.first())
        assertEquals("Film 25", built.title)
        assertEquals("2007 · DRAMA · ENDS 23:42", built.facts)
        assertEquals("Posters", built.view)
        assertEquals(
            listOf("YOURS" to "Continue watching", "FOR YOU" to "AI for you — Movies", "CHARTS" to "Top 100 Today", "CATALOGS" to "Westerns"),
            built.shelves.map { it.group to it.title },
        )
    }
}
