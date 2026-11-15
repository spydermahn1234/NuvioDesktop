package com.spydermahn.nuvioremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicResultsLineTest {
    @Test
    fun readsThePcsLine() {
        // As PhoneRemoteMusicSearchTest builds it on the PC.
        val line = "MUSIC_RESULTS 0 portishead " +
            "abc123,Glory%20Box,Portishead,306,https%3A%2F%2Fi%2Fc.jpg,0;vid9,Sour%20Times%20%28Live%29,Portishead,0,-,1 " +
            "UCport,Portishead,- " +
            "MPREb_dummy,Dummy,Portishead,UCport,1994,Album,-"
        val parsed = LibraryLines.parseMusicResults(line) as ServerLine.MusicResults
        assertEquals("portishead", parsed.query)
        assertEquals(false, parsed.failed)
        assertEquals(
            listOf(
                FoundSong("abc123", "Glory Box", "Portishead", 306, "https://i/c.jpg", video = false),
                FoundSong("vid9", "Sour Times (Live)", "Portishead", 0, null, video = true),
            ),
            parsed.songs,
        )
        assertEquals(listOf(FoundArtist("UCport", "Portishead", null)), parsed.artists)
        assertEquals(listOf(FoundAlbum("MPREb_dummy", "Dummy", "Portishead", "UCport", "1994", "Album", null)), parsed.albums)
    }

    @Test
    fun anEmptyOrFailedAnswer() {
        val parsed = LibraryLines.parseMusicResults("MUSIC_RESULTS 1 x - - -") as ServerLine.MusicResults
        assertTrue(parsed.failed)
        assertTrue(parsed.songs.isEmpty() && parsed.artists.isEmpty() && parsed.albums.isEmpty())
        assertNull(LibraryLines.parseMusicResults("MUSIC_RESULTS 0"))
    }

    @Test
    fun theClientParsesIt() {
        assertTrue(ServerLine.parse("MUSIC_RESULTS 0 q - - -", 0L) is ServerLine.MusicResults)
    }
}
