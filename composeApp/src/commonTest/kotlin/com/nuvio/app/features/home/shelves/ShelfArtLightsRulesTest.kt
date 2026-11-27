package com.nuvio.app.features.home.shelves

import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.lights.GoveeDevice
import com.nuvio.app.features.lights.LightsSettings
import com.nuvio.app.features.music.beatlights.BeatLightsRules
import com.nuvio.app.features.music.beatlights.BeatLightsSettings
import com.nuvio.app.features.music.beatlights.GoveeLanLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfArtLightsRulesTest {
    @Test
    fun colourNamesFromTheBoards() {
        assertEquals("dusk rose", ShelfArtLightsRules.colourName(Color(0xFFC79A7E)))
        assertEquals("desert gold", ShelfArtLightsRules.colourName(Color(0xFFC9A04A)))
        assertEquals("sea glass", ShelfArtLightsRules.colourName(Color(0xFF9FD3C7)))
    }

    @Test
    fun colourNamesForGreysAndReds() {
        assertEquals("pewter", ShelfArtLightsRules.colourName(Color(0xFF808080)))
        assertEquals("charcoal", ShelfArtLightsRules.colourName(Color(0xFF1A1A1A)))
        assertEquals("moonlight", ShelfArtLightsRules.colourName(Color(0xFFF4F4F4)))
        assertEquals("ember red", ShelfArtLightsRules.colourName(Color(0xFFFF0000)))
        // A hue just under 360 wraps into the red band, not off the table.
        assertEquals("ember red", ShelfArtLightsRules.colourName(Color(0xFFFF0020)))
    }

    @Test
    fun bulbColourIsTheHueAtFullStrength() {
        val c = ShelfArtLightsRules.bulbColour(Color(0xFFC79A7E))
        assertNull(c.kelvin)
        assertEquals(255, c.r)
        assertTrue(c.g in 160..180, "g=${c.g}")
        assertTrue(c.b in 105..125, "b=${c.b}")
    }

    @Test
    fun greyArtGoesWarmWhite() {
        val c = ShelfArtLightsRules.bulbColour(Color(0xFF7F8080))
        assertEquals(ShelfArtLightsRules.WARM_WHITE_K, c.kelvin)
    }

    @Test
    fun closeColoursAreTheSame() {
        val a = ShelfBulbColour(200, 100, 50)
        assertTrue(ShelfArtLightsRules.close(a, ShelfBulbColour(205, 95, 58)))
        assertFalse(ShelfArtLightsRules.close(a, ShelfBulbColour(220, 100, 50)))
        assertFalse(ShelfArtLightsRules.close(a, a.copy(kelvin = 2700)))
        assertTrue(ShelfArtLightsRules.close(ShelfBulbColour(1, 2, 3, 2700), ShelfBulbColour(9, 9, 9, 2700)))
    }

    @Test
    fun waitsForSteadyAndSpacesChanges() {
        assertEquals(1_000L, ShelfArtLightsRules.waitMs(nowMs = 10_000, steadySinceMs = 10_000, lastSentMs = 0))
        assertEquals(1_500L, ShelfArtLightsRules.waitMs(nowMs = 10_000, steadySinceMs = 9_000, lastSentMs = 9_500))
        assertEquals(0L, ShelfArtLightsRules.waitMs(nowMs = 10_000, steadySinceMs = 1_000, lastSentMs = 1_000))
    }

    @Test
    fun fadeEndsOnTheTarget() {
        val steps = ShelfArtLightsRules.fade(ShelfBulbColour(0, 0, 0), ShelfBulbColour(80, 160, 240))
        assertEquals(ShelfArtLightsRules.FADE_STEPS, steps.size)
        assertEquals(ShelfBulbColour(10, 20, 30), steps.first())
        assertEquals(ShelfBulbColour(80, 160, 240), steps.last())
        assertEquals(listOf(ShelfBulbColour(1, 2, 3)), ShelfArtLightsRules.fade(null, ShelfBulbColour(1, 2, 3)))
        assertEquals(1, ShelfArtLightsRules.fade(ShelfBulbColour(0, 0, 0), ShelfBulbColour(1, 1, 1, 2700)).size)
    }

    @Test
    fun brightnessIsCappedAndHalvedAfterBedtime() {
        val bed = 23 * 60 + 30
        assertEquals(40, ShelfArtLightsRules.brightness(40, 22 * 60, bed))
        assertEquals(20, ShelfArtLightsRules.brightness(40, 23 * 60 + 45, bed))
        assertEquals(20, ShelfArtLightsRules.brightness(40, 3 * 60, bed))
        assertEquals(40, ShelfArtLightsRules.brightness(40, 7 * 60, bed))
        assertEquals(10, ShelfArtLightsRules.brightness(15, 1 * 60, bed))
        assertEquals(100, ShelfArtLightsRules.brightness(250, 12 * 60, bed))
    }

    @Test
    fun bulbsComeFromTheLightsAlreadySetUp() {
        val beat = BeatLightsSettings(
            wizAddress = "192.168.1.40",
            goveeFound = listOf(GoveeLanLight(id = "AA:BB", ip = "192.168.1.50", sku = "H6076")),
        )
        val lights = LightsSettings(goveeApiKey = "key", goveeDevices = listOf(GoveeDevice("H6008", "11:22", "Lamp")))
        val bulbs = ShelfArtLightsRules.bulbs(beat, lights)
        assertEquals(listOf("wiz:192.168.1.40", "goveelan:AA:BB", "govee:11:22"), bulbs.map { it.id })
        assertEquals(
            listOf(ShelfArtBulbKind.Wiz, ShelfArtBulbKind.GoveeLan, ShelfArtBulbKind.GoveeCloud),
            bulbs.map { it.kind },
        )
        // No API key: the account's lights cannot be reached; a bad WiZ address is not a bulb.
        val none = ShelfArtLightsRules.bulbs(beat.copy(wizAddress = "lamp", goveeFound = emptyList()), lights.copy(goveeApiKey = ""))
        assertTrue(none.isEmpty())
    }

    @Test
    fun wizPilotRestoresColourWhiteSceneOrOff() {
        val colour = ShelfArtLightsRules.wizPilotOf(
            """{"method":"getPilot","env":"pro","result":{"mac":"a","rssi":-60,"state":true,"sceneId":0,"r":255,"g":10,"b":0,"c":0,"w":0,"dimming":50}}""",
        )!!
        val body = ShelfArtLightsRules.wizRestoreBody(colour)
        assertTrue("\"r\":255" in body && "\"dimming\":50" in body, body)

        val white = ShelfArtLightsRules.wizPilotOf(
            """{"method":"getPilot","result":{"state":true,"sceneId":0,"temp":2700,"dimming":80}}""",
        )!!
        assertTrue("\"temp\":2700" in ShelfArtLightsRules.wizRestoreBody(white))

        val scene = ShelfArtLightsRules.wizPilotOf(
            """{"method":"getPilot","result":{"state":true,"sceneId":11,"speed":100,"dimming":60}}""",
        )!!
        val sceneBody = ShelfArtLightsRules.wizRestoreBody(scene)
        assertTrue("\"sceneId\":11" in sceneBody && "\"speed\":100" in sceneBody, sceneBody)

        val off = ShelfArtLightsRules.wizPilotOf("""{"method":"getPilot","result":{"state":false,"sceneId":0}}""")!!
        assertEquals(BeatLightsRules.WIZ_OFF, ShelfArtLightsRules.wizRestoreBody(off))

        assertNull(ShelfArtLightsRules.wizPilotOf("""{"method":"setPilot","result":{"success":true}}"""))
        assertNull(ShelfArtLightsRules.wizPilotOf("not json"))
    }

    @Test
    fun wizColourBody() {
        val body = ShelfArtLightsRules.wizColourBody(ShelfBulbColour(255, 169, 115), 40)
        assertEquals("""{"method":"setPilot","params":{"state":true,"r":255,"g":169,"b":115,"dimming":40}}""", body)
        assertTrue("\"temp\":2700" in ShelfArtLightsRules.wizColourBody(ShelfBulbColour(0, 0, 0, 2700), 5))
        assertTrue("\"dimming\":10" in ShelfArtLightsRules.wizColourBody(ShelfBulbColour(0, 0, 0, 2700), 5))
    }

    @Test
    fun goveeLanStatusRestores() {
        val on = ShelfArtLightsRules.goveeLanStatusOf(
            """{"msg":{"cmd":"devStatus","data":{"onOff":1,"brightness":70,"color":{"r":255,"g":120,"b":0},"colorTemInKelvin":0}}}""",
        )!!
        assertTrue(on.on)
        val restore = ShelfArtLightsRules.goveeLanRestore(on)
        assertEquals(3, restore.size)
        assertEquals(ShelfArtLightsRules.goveeLanTurn(true), restore[0])
        assertTrue("\"g\":120" in restore[1])
        assertEquals(ShelfArtLightsRules.goveeLanBrightness(70), restore[2])

        val off = ShelfArtLightsRules.goveeLanStatusOf(
            """{"msg":{"cmd":"devStatus","data":{"onOff":0,"brightness":100,"color":{"r":0,"g":0,"b":0},"colorTemInKelvin":4000}}}""",
        )!!
        assertEquals(listOf(ShelfArtLightsRules.goveeLanTurn(false)), ShelfArtLightsRules.goveeLanRestore(off))

        assertNull(ShelfArtLightsRules.goveeLanStatusOf("""{"msg":{"cmd":"scan","data":{}}}"""))
    }
}
