package com.spydermahn.nuvioremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** fork:watch-along - the same line the PC's PhoneRemoteAlongTest builds: change both together. */
class AlongLineTest {
    @Test
    fun readsTheCompanion() {
        val line = "ALONG 1 ready Jurassic%20Park 30 1 600000,Music,Theme,John%20Williams%2C%201993. " +
            "Ellie%20Sattler,Laura%20Dern,https%3A%2F%2Fx%2Fp.jpg,750000,A%20palaeobotanist.,1"
        val info = (ServerLine.parse(line, 0L) as ServerLine.Along).info!!
        assertEquals("ready", info.status)
        assertEquals("Jurassic Park", info.title)
        assertEquals(30, info.total)
        assertTrue(info.fromAi)
        assertEquals(listOf(AlongNote(600_000, "Music", "Theme", "John Williams, 1993.")), info.notes)
        val ellie = info.people.single()
        assertEquals("Ellie Sattler", ellie.character)
        assertEquals("Laura Dern", ellie.actor)
        assertEquals("https://x/p.jpg", ellie.photo)
        assertEquals(750_000L, ellie.firstMs)
        assertTrue(ellie.aroundNow)
        assertEquals("1 of 30 so far · written by your AI", alongStatusLine(info))
    }

    @Test
    fun emptyAndOff() {
        assertNull((ServerLine.parse("ALONG 0", 0L) as ServerLine.Along).info)
        val building = (ServerLine.parse("ALONG 1 building Film 0 0 - -", 0L) as ServerLine.Along).info!!
        assertEquals(emptyList<AlongNote>(), building.notes)
        assertEquals("Getting the notes ready…", alongStatusLine(building))
    }

    @Test
    fun aroundNowComesFirst() {
        val people = listOf(
            AlongCastInfo("A", "a", null, 1_000, "", aroundNow = false),
            AlongCastInfo("B", "b", null, 2_000, "", aroundNow = false),
            AlongCastInfo("C", "c", null, 500, "", aroundNow = true),
        )
        assertEquals(listOf("C", "B", "A"), alongPeopleOrder(people).map { it.character })
    }
}
