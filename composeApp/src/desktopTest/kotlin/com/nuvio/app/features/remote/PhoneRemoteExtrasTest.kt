package com.nuvio.app.features.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneRemoteExtrasTest {
    @Test
    fun theFilmExtrasRideOnTheStateLine() {
        val line = PhoneRemoteProtocol.stateLine(
            RemoteNowPlaying(
                kind = "film", title = "Show", speedPercent = 125, skipLabel = "Skip intro", canNextEpisode = true, sleepMinutes = 30,
            ),
        )
        val fields = line.split(' ')
        // After the thirteen fields an older phone knows: speed, skip label, next episode, sleep, shuffle, repeat, liked.
        assertEquals(listOf("125", "Skip%20intro", "1", "30", "-", "-", "-", "-", "0"), fields.drop(13))
    }

    @Test
    fun theMusicExtrasRideOnTheStateLine() {
        val line = PhoneRemoteProtocol.stateLine(RemoteNowPlaying(kind = "music", title = "Song", shuffle = true, repeat = 2, liked = false))
        assertEquals(listOf("-", "-", "0", "0", "1", "2", "0", "-", "0"), line.split(' ').drop(13))
    }

    @Test
    fun theAspectRidesOnTheStateLineAndParses() {
        val line = PhoneRemoteProtocol.stateLine(RemoteNowPlaying(kind = "film", title = "Film", aspect = 2))
        assertEquals("2", line.split(' ')[20])
        assertEquals(RemoteMessage.Control(ControlAction.Aspect, 3), PhoneRemoteProtocol.parse("ASPECT 3"))
        assertNull(PhoneRemoteProtocol.parse("ASPECT"))
        assertNull(PhoneRemoteProtocol.parse("ASPECT wide"))
    }

    @Test
    fun bedtimeRidesOnTheStateLineAndParses() {
        val line = PhoneRemoteProtocol.stateLine(RemoteNowPlaying(kind = "film", title = "Show", bedtime = true))
        assertEquals("1", line.split(' ')[21])
        assertEquals(RemoteMessage.Control(ControlAction.Bedtime, 1), PhoneRemoteProtocol.parse("BEDTIME 1"))
        assertEquals(RemoteMessage.Control(ControlAction.Bedtime, 0), PhoneRemoteProtocol.parse("BEDTIME 0"))
        assertNull(PhoneRemoteProtocol.parse("BEDTIME"))
    }

    @Test
    fun theControlsParse() {
        assertEquals(RemoteMessage.Control(ControlAction.Speed, 150), PhoneRemoteProtocol.parse("SPEED 150"))
        assertEquals(RemoteMessage.Control(ControlAction.Sleep, 0), PhoneRemoteProtocol.parse("SLEEP 0"))
        assertEquals(RemoteMessage.Control(ControlAction.Repeat, 2), PhoneRemoteProtocol.parse("REPEAT 2"))
        assertEquals(RemoteMessage.Control(ControlAction.SkipIntro, 0), PhoneRemoteProtocol.parse("SKIP_INTRO"))
        assertEquals(RemoteMessage.Control(ControlAction.Radio, 0), PhoneRemoteProtocol.parse("RADIO"))
        assertNull(PhoneRemoteProtocol.parse("SPEED"))
        assertNull(PhoneRemoteProtocol.parse("SPEED fast"))
        assertNull(PhoneRemoteProtocol.parse("SLEEP -5"))
    }

    @Test
    fun theSkipButtonSaysWhatItSkips() {
        assertEquals("Skip intro", com.nuvio.app.features.player.phoneRemoteSkipLabel("op"))
        assertEquals("Skip credits", com.nuvio.app.features.player.phoneRemoteSkipLabel("ed"))
        assertTrue(com.nuvio.app.features.player.phoneRemoteSkipLabel("something else").startsWith("Skip"))
    }
}
