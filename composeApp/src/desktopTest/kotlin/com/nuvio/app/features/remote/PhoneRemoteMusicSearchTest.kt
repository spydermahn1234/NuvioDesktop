package com.nuvio.app.features.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneRemoteMusicSearchTest {
    @Test
    fun theResultsTravelAsOneLine() {
        val line = PhoneRemoteLibraryLines.musicSearchLine(
            query = "portishead",
            failed = false,
            songs = listOf(
                RemoteMusicSong("abc123", "Glory Box", "Portishead", 306, "https://i/c.jpg", video = false),
                RemoteMusicSong("vid9", "Sour Times (Live)", "Portishead", 0, null, video = true),
            ),
            artists = listOf(RemoteMusicArtist("UCport", "Portishead", null)),
            albums = listOf(RemoteMusicAlbum("MPREb_dummy", "Dummy", "Portishead", "UCport", "1994", "Album", null)),
        )
        assertEquals(
            "MUSIC_RESULTS 0 portishead " +
                "abc123,Glory%20Box,Portishead,306,https%3A%2F%2Fi%2Fc.jpg,0;vid9,Sour%20Times%20%28Live%29,Portishead,0,-,1 " +
                "UCport,Portishead,- " +
                "MPREb_dummy,Dummy,Portishead,UCport,1994,Album,-",
            line,
        )
        assertEquals("MUSIC_RESULTS 1 x - - -", PhoneRemoteLibraryLines.musicSearchLine("x", true, emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun theRequestsParse() {
        assertEquals(RemoteMessage.MusicSearch("glory box"), PhoneRemoteProtocol.parse("MUSIC_SEARCH glory%20box"))
        assertNull(PhoneRemoteProtocol.parse("MUSIC_SEARCH"))
        assertEquals(RemoteMessage.PlaySong("abc123", SongHow.Next), PhoneRemoteProtocol.parse("PLAY_SONG abc123 next"))
        assertEquals(RemoteMessage.PlaySong("abc123", SongHow.Radio), PhoneRemoteProtocol.parse("PLAY_SONG abc123 radio"))
        assertNull(PhoneRemoteProtocol.parse("PLAY_SONG abc123 sideways"))
        assertNull(PhoneRemoteProtocol.parse("PLAY_SONG abc123"))
    }
}
