package com.spydermahn.nuvioremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same lines the PC's PhoneRemoteShelfHomeTest builds: change both together. */
class ShelfHomeLineTest {
    @Test
    fun readsTheHome() {
        val line = "HOME 1 browse home Home aimovies AI%20for%20you%20%E2%80%94%20Movies 3 20 2 50 0 There%20Will%20Be%20Blood " +
            "2007%20%C2%B7%20DRAMA%20%C2%B7%20ENDS%2023%3A42 https%3A%2F%2Fx%2Fb.jpg Posters " +
            "There%20Will%20Be%20Blood,https%3A%2F%2Fx%2Fp.jpg;Heat,-"
        val home = (ServerLine.parse(line, 0L) as ServerLine.Home).screen!!
        assertEquals("browse", home.mode)
        assertEquals("home", home.tabId)
        assertEquals("Home", home.tabTitle)
        assertEquals("aimovies", home.shelfKey)
        assertEquals("AI for you — Movies", home.rowTitle)
        assertEquals(3, home.row)
        assertEquals(20, home.rows)
        assertEquals(2, home.index)
        assertEquals(50, home.count)
        assertEquals("There Will Be Blood", home.title)
        assertEquals("2007 · DRAMA · ENDS 23:42", home.facts)
        assertEquals("https://x/b.jpg", home.art)
        assertEquals("Posters", home.view)
        assertEquals(
            listOf(ShelfCardInfo(0, "There Will Be Blood", "https://x/p.jpg"), ShelfCardInfo(1, "Heat", null)),
            home.cards,
        )
    }

    @Test
    fun cardsCountFromTheWindowsStart() {
        val line = "HOME 1 browse home Home k Row 0 1 31 40 20 B - - Logos A,-;B,-"
        val home = (ServerLine.parse(line, 0L) as ServerLine.Home).screen!!
        assertEquals(listOf(20, 21), home.cards.map { it.index })
        assertNull(home.art)
        assertEquals("", home.facts)
        assertEquals("Logos", home.view)
    }

    @Test
    fun noHome() {
        assertNull((ServerLine.parse("HOME 0", 0L) as ServerLine.Home).screen)
        assertEquals(ServerLine.Shelves(emptyList()), ServerLine.parse("SHELVES 0", 0L))
    }

    @Test
    fun readsTheRows() {
        val line = "SHELVES 1 home,Home,FOR%20YOU,-,aimovies,AI%20for%20you%20%E2%80%94%20Movies;" +
            "home,Home,CATALOGS,A%20%E2%80%93%20C,cin%3Amovie%3Atop,Cinemeta%2C%20Top"
        val rows = (ServerLine.parse(line, 0L) as ServerLine.Shelves).rows
        assertEquals(
            listOf(
                ShelfRowInfo("home", "Home", "FOR YOU", null, "aimovies", "AI for you — Movies"),
                ShelfRowInfo("home", "Home", "CATALOGS", "A – C", "cin:movie:top", "Cinemeta, Top"),
            ),
            rows,
        )
    }

    private val rows = listOf(
        ShelfRowInfo("home", "Home", "YOURS", null, "cw", "Continue watching"),
        ShelfRowInfo("home", "Home", "CATALOGS", "A – C", "a", "Action"),
        ShelfRowInfo("home", "Home", "CATALOGS", "A – C", "c", "Comedy"),
        ShelfRowInfo("home", "Home", "CATALOGS", "G – K", "h", "Horror"),
        ShelfRowInfo("films", "Films", "YOURS", null, "f1", "Horror nights"),
    )

    @Test
    fun theListIsGroupedWithBucketsFolded() {
        assertEquals(
            listOf(
                ShelvesLine.Heading("YOURS · 1"),
                ShelvesLine.Item(rows[0]),
                ShelvesLine.Heading("CATALOGS · 3"),
                ShelvesLine.Bucket("home|A – C", "A – C", 2, open = false),
                ShelvesLine.Bucket("home|G – K", "G – K", 1, open = true),
                ShelvesLine.Item(rows[3]),
                ShelvesLine.Heading("FILMS · YOURS · 1"),
                ShelvesLine.Item(rows[4]),
            ),
            shelvesLines(rows, currentTabId = "home", query = "", openBuckets = setOf("home|G – K")),
        )
    }

    @Test
    fun searchKeepsOnlyMatchingRowsUnfolded() {
        assertEquals(
            listOf(
                ShelvesLine.Heading("CATALOGS · 1"),
                ShelvesLine.Item(rows[3]),
                ShelvesLine.Heading("FILMS · YOURS · 1"),
                ShelvesLine.Item(rows[4]),
            ),
            shelvesLines(rows, currentTabId = "home", query = "HORROR", openBuckets = emptySet()),
        )
    }
}
