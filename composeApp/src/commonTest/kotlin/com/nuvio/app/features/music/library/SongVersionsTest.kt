package com.nuvio.app.features.music.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongVersionsTest {
    private val titles = listOf(
        "Just Another Story", "Space Cowboy", "Light Years",
        "Space Cowboy (David Morales Mix)", "Space Cowboy (Demo Version)", "Light Years (Live)",
    )
    private val durations = listOf(530, 387, 355, 471, 259, 358)

    private fun release(title: String, kind: ReleaseKind, deezer: Long? = null) =
        LibraryRelease(key = title, title = title, kind = kind, ytBrowseId = "MPREb_$title", deezerId = deezer)

    @Test
    fun kindsComeFromTheSuffix() {
        assertEquals(VersionKind.Remix, versionKind("Space Cowboy (David Morales Mix)"))
        assertEquals(VersionKind.Demo, versionKind("Space Cowboy (Demo Version)"))
        assertEquals(VersionKind.Live, versionKind("Light Years - Live"))
        assertEquals(VersionKind.Instrumental, versionKind("Morning Glory (Instrumental)"))
        assertEquals(VersionKind.Album, versionKind("Space Cowboy"))
        assertEquals(VersionKind.Album, versionKind("Song (feat. Someone)"))
    }

    @Test
    fun theAlbumsOwnCopiesComeFirstInTrackOrder() {
        val v = songVersions(1, titles, durations, "The Return of the Space Cowboy", emptyList(), emptyList())
        assertEquals(listOf(3, 4), v.map { it.trackIndex })
        assertEquals(listOf(VersionKind.Remix, VersionKind.Demo), v.map { it.kind })
        assertEquals(471, v[0].durationSeconds)
    }

    @Test
    fun aRemixFindsThePlainSongToo() {
        val v = songVersions(3, titles, durations, "Album", emptyList(), emptyList())
        assertEquals(listOf(1, 4), v.map { it.trackIndex })
        assertEquals(VersionKind.Version, v[0].kind) // the plain song, seen from its remix
    }

    @Test
    fun singlesAndOtherRecordsFollowOncePerRecord() {
        val hits = release("High Times: Singles 1992-2006", ReleaseKind.Album, deezer = 7)
        val single = release("Space Cowboy", ReleaseKind.Single)
        val top = listOf(
            DeezerTopSong("Space Cowboy", "High Times: Singles 1992-2006", 7, 900),
            DeezerTopSong("Space Cowboy", "High Times: Singles 1992-2006", 7, 400),
            DeezerTopSong("Space Cowboy", "The Return of the Space Cowboy", 3, 800),
            DeezerTopSong("Virtual Insanity", "Travelling Without Moving", 9, 999),
        )
        val v = songVersions(1, titles, durations, "The Return of the Space Cowboy", top, listOf(hits, single))
        assertEquals(4, v.size)
        assertEquals(VersionKind.Single, v[2].kind)
        assertEquals(hits, v[3].release)
        assertEquals(VersionKind.Album, v[3].kind)
        val counts = versionCounts(v)
        assertEquals(VersionCounts(found = 4, onThisAlbum = 2, elsewhere = 2, reworks = 2), counts)
    }

    @Test
    fun aSongWithNoOtherCopiesHasNone() {
        assertTrue(songVersions(0, titles, durations, "Album", emptyList(), emptyList()).isEmpty())
        assertTrue(songVersions(99, titles, durations, "Album", emptyList(), emptyList()).isEmpty())
    }

    // fork:lenses - "2001": the explicit album lists its censored edition under YouTube Music's "Other versions".
    private val clean = AlbumEditionTracks(
        browseId = "MPREb_clean", title = "2001", explicit = false,
        trackTitles = listOf("Lolo (Intro)", "The Watcher", "Still D.R.E. (feat. Snoop Dogg)"), trackDurations = listOf(40, 206, 274),
    )

    @Test
    fun theCleanEditionOffersTheSameSong() {
        val tracks = listOf("Lolo (Intro)", "The Watcher", "Still D.R.E.")
        val v = songVersions(2, tracks, listOf(40, 206, 270), "2001", emptyList(), emptyList(), editions = listOf(clean), thisExplicit = true)
        val copy = v.single()
        assertEquals(VersionKind.Clean, copy.kind)
        assertEquals("MPREb_clean", copy.editionId)
        assertEquals(2, copy.editionTrack)
        assertEquals(274, copy.durationSeconds)
        assertTrue(copy.recordTitle!!.startsWith("Clean edition"))
        assertEquals(VersionCounts(found = 1, onThisAlbum = 0, elsewhere = 1, reworks = 0), versionCounts(v))
    }

    @Test
    fun editionKindsFollowTheBadgeAndTitle() {
        assertEquals(VersionKind.Explicit, editionKind(clean.copy(explicit = true), thisExplicit = false))
        assertEquals(VersionKind.Edition, editionKind(clean.copy(title = "2001 (Deluxe)", explicit = true), thisExplicit = true))
        assertEquals(VersionKind.Clean, editionKind(clean.copy(title = "2001 (Edited)", explicit = true), thisExplicit = true))
        assertEquals(VersionKind.Edition, editionKind(clean, thisExplicit = false))
    }

    @Test
    fun anEditionWithoutTheSongAddsNothing() {
        val v = songVersions(0, listOf("Forgot About Dre"), listOf(222), "2001", emptyList(), emptyList(), editions = listOf(clean), thisExplicit = true)
        assertTrue(v.isEmpty())
    }

    // fork:lenses - Other versions missed the clean edition "not all of the time" (TESTING.md, Nov 8): censored titles, a
    // retitled clean edit, and deluxe editions crowding the clean one out.
    @Test
    fun aCensoredTitleOnTheCleanEditionStillMatches() {
        val tracks = listOf("Intro", "Fuck You", "Bitches Ain't Shit")
        val censored = AlbumEditionTracks(
            "MPREb_c", "Album", explicit = false,
            trackTitles = listOf("Intro", "F**k You", "B#tches Ain't S--t"), trackDurations = listOf(30, 200, 240),
        )
        val you = songVersions(1, tracks, listOf(30, 201, 241), "Album", emptyList(), emptyList(), editions = listOf(censored), thisExplicit = true).single()
        assertEquals(1, you.editionTrack)
        assertEquals(VersionKind.Clean, you.kind)
        val third = songVersions(2, tracks, listOf(30, 201, 241), "Album", emptyList(), emptyList(), editions = listOf(censored), thisExplicit = true).single()
        assertEquals(2, third.editionTrack)
    }

    @Test
    fun censoredMatchNeedsTheUnmaskedLettersToAgree() {
        assertTrue(censoredMatch("F**k You", "Fuck You"))
        assertTrue(censoredMatch("F*ck You (Clean)", "Fuck You"))
        assertTrue(!censoredMatch("F**k You", "Fork Them"))
        assertTrue(!censoredMatch("Fuck You", "Fuck You")) // nothing masked: songKey's job
        assertTrue(!censoredMatch("****", ""))
    }

    @Test
    fun aRetitledCleanEditFallsBackToTheSamePlaceAndLength() {
        val tracks = listOf("Intro", "Explicit Title", "Outro")
        val twin = AlbumEditionTracks("MPREb_t", "Album", explicit = false, trackTitles = listOf("Intro", "Radio Title", "Outro"), trackDurations = listOf(30, 198, 60))
        assertEquals(1, songVersions(1, tracks, listOf(30, 201, 60), "Album", emptyList(), emptyList(), editions = listOf(twin), thisExplicit = true).single().editionTrack)
        // Too different in length: not the same song.
        assertTrue(songVersions(1, tracks, listOf(30, 260, 60), "Album", emptyList(), emptyList(), editions = listOf(twin), thisExplicit = true).isEmpty())
        // A deluxe edition (not a clean/explicit twin) is never matched by position.
        val deluxe = twin.copy(title = "Album (Deluxe)", explicit = true)
        assertTrue(songVersions(1, tracks, listOf(30, 198, 60), "Album", emptyList(), emptyList(), editions = listOf(deluxe), thisExplicit = true).isEmpty())
    }

    @Test
    fun theCleanEditionIsListedBeforeOtherEditions() {
        val tracks = listOf("Song")
        val deluxes = (1..6).map { AlbumEditionTracks("MPREb_d$it", "Album (Deluxe $it)", explicit = true, trackTitles = listOf("Song"), trackDurations = listOf(200)) }
        val cleanOne = AlbumEditionTracks("MPREb_clean", "Album", explicit = false, trackTitles = listOf("Song"), trackDurations = listOf(200))
        val v = songVersions(0, tracks, listOf(200), "Album", emptyList(), emptyList(), editions = deluxes + cleanOne, thisExplicit = true)
        assertEquals("MPREb_clean", v.first().editionId)
        assertEquals(6, v.size)
    }
}
