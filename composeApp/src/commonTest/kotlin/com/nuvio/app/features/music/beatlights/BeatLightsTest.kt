package com.nuvio.app.features.music.beatlights

import com.nuvio.app.features.music.visualiser.BeatGrid
import com.nuvio.app.features.music.visualiser.MAP_BANDS
import com.nuvio.app.features.music.visualiser.MAP_FRAME_MS
import com.nuvio.app.features.music.visualiser.Onset
import com.nuvio.app.features.music.visualiser.TrackMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** fork:beat-lights - which hits flash the light, and that each one fires once however the playhead moves. */
class BeatLightsTest {
    /** A map of [seconds] with a kick at each of [kicksMs] and, when given, a 4/4 beat grid every [beatMs] from 0. */
    private fun map(seconds: Int, kicksMs: List<Int>, beatMs: Double? = null, shift: Int = 0): TrackMap {
        val frames = seconds * 1_000 / MAP_FRAME_MS
        val onsets = ByteArray(frames)
        for (k in kicksMs) onsets[k / MAP_FRAME_MS] = (onsets[k / MAP_FRAME_MS].toInt() or Onset.KICK).toByte()
        val grid = beatMs?.let { b ->
            val n = (seconds * 1_000 / b).toInt()
            BeatGrid(DoubleArray(n) { it * b }, ByteArray(n) { (it % 4).toByte() }, shift)
        }
        return TrackMap(frames, ByteArray(frames * MAP_BANDS), ByteArray(frames), ByteArray(frames), ByteArray(frames), onsets, grid, complete = true)
    }

    @Test
    fun kicksAreFoundInTheWindowOnlyOnce() {
        val m = map(4, listOf(100, 600, 1100))
        assertEquals(listOf(100.0, 600.0), BeatLightsRules.hitsBetween(m, 0.0, 700.0, BeatLightsOn.Kick))
        // The window is (from, to]: a hit at its start was the last look's.
        assertEquals(listOf(600.0), BeatLightsRules.hitsBetween(m, 100.0, 700.0, BeatLightsOn.Kick))
    }

    @Test
    fun aKickFlaggedInTwoFramesInARowFlashesOnce() {
        val m = map(2, listOf(500, 525))
        assertEquals(listOf(500.0), BeatLightsRules.hitsBetween(m, 0.0, 1_000.0, BeatLightsOn.Kick))
    }

    @Test
    fun aKickInTwoFramesSplitAcrossTwoLooksStillFlashesOnce() {
        val m = map(2, listOf(500, 525))
        val c = BeatLightsCursor()
        c.advance(1, m, 490.0, BeatLightsOn.Kick)
        assertEquals(listOf(500.0), c.advance(1, m, 510.0, BeatLightsOn.Kick))
        assertTrue(c.advance(1, m, 530.0, BeatLightsOn.Kick).isEmpty())
    }

    @Test
    fun beatOneIsTheDownbeatAndFollowsAMovedBarLine() {
        val m = map(4, emptyList(), beatMs = 500.0)
        assertEquals(8, BeatLightsRules.hitsBetween(m, -1.0, 4_000.0, BeatLightsOn.Beat).size)
        assertEquals(listOf(0.0, 2_000.0), BeatLightsRules.hitsBetween(m, -1.0, 4_000.0, BeatLightsOn.Downbeat))
        // Bar line moved on by one beat by hand: the old fourth beat is now beat one.
        val moved = map(4, emptyList(), beatMs = 500.0, shift = 1)
        assertEquals(listOf(1_500.0, 3_500.0), BeatLightsRules.hitsBetween(moved, -1.0, 4_000.0, BeatLightsOn.Downbeat))
    }

    @Test
    fun aTrackWithNoBeatGridFallsBackToItsKicks() {
        val m = map(2, listOf(250, 750))
        assertEquals(listOf(250.0, 750.0), BeatLightsRules.hitsBetween(m, 0.0, 1_000.0, BeatLightsOn.Downbeat))
    }

    @Test
    fun eachHitFiresOnceWhileTheSongPlays() {
        val m = map(4, listOf(100, 600, 1100, 1600))
        val c = BeatLightsCursor()
        val fired = mutableListOf<Double>()
        // First look only sets the start.
        assertTrue(c.advance(1, m, 0.0, BeatLightsOn.Kick).isEmpty())
        var t = 4.0
        while (t <= 2_000.0) {
            fired += c.advance(1, m, t, BeatLightsOn.Kick)
            t += 4.0
        }
        assertEquals(listOf(100.0, 600.0, 1100.0, 1600.0), fired)
    }

    @Test
    fun aPositionReportJustBehindTheClockDoesNotFireAHitAgain() {
        val m = map(2, listOf(500))
        val c = BeatLightsCursor()
        c.advance(1, m, 480.0, BeatLightsOn.Kick)
        assertEquals(listOf(500.0), c.advance(1, m, 510.0, BeatLightsOn.Kick))
        // The player's report lands 30 ms behind the clock's guess, then the clock carries on.
        assertTrue(c.advance(1, m, 480.0, BeatLightsOn.Kick).isEmpty())
        assertTrue(c.advance(1, m, 520.0, BeatLightsOn.Kick).isEmpty())
    }

    @Test
    fun aSeekOrANewTrackFiresNothingAndStartsAgainFromThere() {
        val m = map(10, listOf(3_000, 3_100))
        val c = BeatLightsCursor()
        c.advance(1, m, 0.0, BeatLightsOn.Kick)
        // Seek forward past both kicks: nothing fires for what was skipped.
        assertTrue(c.advance(1, m, 3_200.0, BeatLightsOn.Kick).isEmpty())
        // Seek back to just before them: nothing on the jump, then they fire again as the song reaches them.
        assertTrue(c.advance(1, m, 2_000.0, BeatLightsOn.Kick).isEmpty())
        assertTrue(c.advance(1, m, 2_500.0, BeatLightsOn.Kick).isEmpty())
        assertEquals(listOf(3_000.0, 3_100.0), c.advance(1, m, 3_050.0, BeatLightsOn.Kick) + c.advance(1, m, 3_150.0, BeatLightsOn.Kick))
        // A new track (another load) starts fresh.
        assertTrue(c.advance(2, m, 3_160.0, BeatLightsOn.Kick).isEmpty())
    }

    @Test
    fun hitsPastWhatIsAnalysedWaitForTheMap() {
        val whole = map(4, listOf(1_000))
        val half = TrackMap(20, whole.bands, whole.level, whole.left, whole.right, whole.onsets, null, complete = false) // 500 ms
        assertEquals(emptyList(), BeatLightsRules.hitsBetween(half, 900.0, 1_100.0, BeatLightsOn.Kick))
    }

    @Test
    fun thePlayheadRunsOnTheClockForAtMostASecondAndAHalf() {
        assertEquals(10_250L, BeatLightsRules.playheadMs(10_000L, 250L, playing = true))
        assertEquals(11_500L, BeatLightsRules.playheadMs(10_000L, 9_000L, playing = true))
        assertEquals(10_000L, BeatLightsRules.playheadMs(10_000L, 250L, playing = false))
    }

    @Test
    fun onlyAnIpv4AddressTurnsItOn() {
        assertTrue(BeatLightsRules.validAddress("192.168.1.40"))
        assertTrue(BeatLightsRules.validAddress(" 10.0.0.7 "))
        assertFalse(BeatLightsRules.validAddress("192.168.1.400"))
        assertFalse(BeatLightsRules.validAddress("wiz-bulb.local"))
        assertFalse(BeatLightsSettings(enabled = true, wizAddress = "").ready)
        assertTrue(BeatLightsSettings(enabled = true, wizAddress = "192.168.1.40").ready)
    }

    @Test
    fun theWizCommandKeepsBrightnessInTheBulbsRange() {
        assertEquals("""{"method":"setPilot","params":{"state":true,"dimming":10}}""", BeatLightsRules.wizBrightness(0))
        assertEquals("""{"method":"setPilot","params":{"state":true,"dimming":100}}""", BeatLightsRules.wizBrightness(150))
    }

    @Test
    fun strobeIsTheDefaultAndSwitchesTheBulbOff() {
        assertEquals(BeatLightsStyle.Strobe, BeatLightsSettings().style)
        assertEquals("""{"method":"setPilot","params":{"state":false}}""", BeatLightsRules.WIZ_OFF)
    }

    @Test
    fun aSeekIsCountedAsAJump() {
        val m = map(10, emptyList())
        val c = BeatLightsCursor()
        c.advance(1, m, 0.0, BeatLightsOn.Kick)
        c.advance(1, m, 100.0, BeatLightsOn.Kick)
        assertEquals(0, c.jumps)
        c.advance(1, m, 5_000.0, BeatLightsOn.Kick)
        c.advance(1, m, 1_000.0, BeatLightsOn.Kick)
        assertEquals(2, c.jumps)
    }

    @Test
    fun goveeRealTimePacketsMatchTheKnownOnes() {
        // The community-known enable and disable strings: BB 00 01 B1 01 0A and BB 00 01 B1 00 0B.
        assertEquals("""{"msg":{"cmd":"razer","data":{"pt":"uwABsQEK"}}}""", BeatLightsRules.goveeRazerJson(BeatLightsRules.goveeRealtime(true)))
        assertEquals("""{"msg":{"cmd":"razer","data":{"pt":"uwABsQAL"}}}""", BeatLightsRules.goveeRazerJson(BeatLightsRules.goveeRealtime(false)))
        val white = BeatLightsRules.goveeWhite(255).map { it.toInt() and 0xFF }
        assertEquals(listOf(0xBB, 0x00, 0x05, 0xB0, 0x00, 0x01, 0xFF, 0xFF, 0xFF), white.dropLast(1))
        assertEquals(white.dropLast(1).fold(0) { x, b -> x xor b }, white.last())
    }

    @Test
    fun aGoveeScanAnswerGivesTheLight() {
        val reply = """{"msg":{"cmd":"scan","data":{"ip":"192.168.1.23","device":"1F:80:C5:32:32:36:72:4E","sku":"H618E","bleVersionHard":"3.01.01"}}}"""
        assertEquals(GoveeLanLight("1F:80:C5:32:32:36:72:4E", "192.168.1.23", "H618E"), BeatLightsRules.goveeScanReply(reply))
        assertEquals(null, BeatLightsRules.goveeScanReply("""{"msg":{"cmd":"devStatus","data":{"onOff":1}}}"""))
    }

    @Test
    fun goveeLightsAloneAreEnoughToRun() {
        val light = GoveeLanLight("A", "192.168.1.23", "H618E")
        assertFalse(BeatLightsSettings(enabled = true, goveeFound = listOf(light)).ready)
        assertTrue(BeatLightsSettings(enabled = true, goveeFound = listOf(light), goveeOn = setOf("A")).ready)
    }
}
