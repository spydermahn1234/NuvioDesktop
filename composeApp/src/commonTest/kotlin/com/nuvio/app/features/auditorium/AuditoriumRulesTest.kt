package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditoriumRulesTest {
    private val on = AuditoriumSettings(enabled = true)
    private val onWithSeries = AuditoriumSettings(enabled = true, includeSeries = true)

    private fun near(expected: Float, actual: Float, tolerance: Float = 1e-3f, what: String = "") {
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, got $actual")
    }

    private fun sameFrame(expected: AuditoriumFrame, actual: AuditoriumFrame, tolerance: Float = 1e-3f) {
        near(expected.fade, actual.fade, tolerance, "fade")
        near(expected.houseLights, actual.houseLights, tolerance, "houseLights")
        near(expected.curtain, actual.curtain, tolerance, "curtain")
        near(expected.beam, actual.beam, tolerance, "beam")
        near(expected.camera, actual.camera, tolerance, "camera")
        near(expected.picture, actual.picture, tolerance, "picture")
    }

    // ---- who gets it ----

    @Test
    fun offByDefaultAndOffMeansNever() {
        assertFalse(AuditoriumSettings().enabled)
        assertFalse(AuditoriumRules.opensFor(AuditoriumSettings(), "movie", 0L, false, false))
        assertFalse(AuditoriumRules.closesFor(AuditoriumSettings(), "movie", endedNaturally = true, isPlaylist = false))
    }

    @Test
    fun opensForAFreshFilm() {
        assertTrue(AuditoriumRules.opensFor(on, "movie", 0L, false, false))
        assertTrue(AuditoriumRules.opensFor(on, "Movie", 0L, false, false), "the type is matched without case")
    }

    @Test
    fun doesNotOpenForAResumeATrailerOrAPlaylist() {
        assertFalse(AuditoriumRules.opensFor(on, "movie", 1L, false, false), "a resume")
        assertFalse(AuditoriumRules.opensFor(on, "movie", 600_000L, false, false), "a resume")
        assertFalse(AuditoriumRules.opensFor(on, "movie", 0L, true, false), "untracked, e.g. a trailer")
        assertFalse(AuditoriumRules.opensFor(on, "movie", 0L, false, true), "a playlist")
    }

    @Test
    fun seriesOnlyWithIncludeSeries() {
        assertFalse(AuditoriumRules.opensFor(on, "series", 0L, false, false))
        assertFalse(AuditoriumRules.opensFor(on, "tv", 0L, false, false))
        assertTrue(AuditoriumRules.opensFor(onWithSeries, "series", 0L, false, false))
        assertTrue(AuditoriumRules.opensFor(onWithSeries, "tv", 0L, false, false))
        assertTrue(AuditoriumRules.opensFor(onWithSeries, "movie", 0L, false, false), "films still open")
        assertFalse(AuditoriumRules.opensFor(onWithSeries, "series", 5_000L, false, false), "a resumed episode")
        assertFalse(AuditoriumRules.opensFor(onWithSeries, "channel", 0L, false, false), "other types never")
    }

    @Test
    fun closesOnlyAtANaturalEnd() {
        assertTrue(AuditoriumRules.closesFor(on, "movie", endedNaturally = true, isPlaylist = false))
        assertFalse(AuditoriumRules.closesFor(on, "movie", endedNaturally = false, isPlaylist = false), "Back")
        assertFalse(
            AuditoriumRules.closesFor(on.copy(closing = false), "movie", endedNaturally = true, isPlaylist = false),
            "closing off",
        )
        assertFalse(AuditoriumRules.closesFor(on, "movie", endedNaturally = true, isPlaylist = true), "a playlist")
        assertFalse(AuditoriumRules.closesFor(on, "series", endedNaturally = true, isPlaylist = false))
        assertTrue(AuditoriumRules.closesFor(onWithSeries, "tv", endedNaturally = true, isPlaylist = false))
    }

    // ---- which hall ----

    @Test
    fun fixedChoicesGiveTheirHall() {
        for (id in listOf("", "tt0111161", "a")) {
            assertEquals(AuditoriumTheme.Palace, AuditoriumRules.themeFor(AuditoriumThemeChoice.Palace, id))
            assertEquals(AuditoriumTheme.Modern, AuditoriumRules.themeFor(AuditoriumThemeChoice.Modern, id))
            assertEquals(AuditoriumTheme.DriveIn, AuditoriumRules.themeFor(AuditoriumThemeChoice.DriveIn, id))
        }
    }

    @Test
    fun randomIsStablePerFilm() {
        for (id in listOf("tt0111161", "tt0068646", "tt1375666", "")) {
            assertEquals(
                AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, id),
                AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, id),
            )
        }
    }

    @Test
    fun randomReachesEveryHall() {
        // h = 31 * h + c over the id, then |h % 3|: "c" = 99 -> 0, "a" = 97 -> 1, "b" = 98 -> 2.
        assertEquals(AuditoriumTheme.Palace, AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, "c"))
        assertEquals(AuditoriumTheme.Modern, AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, "a"))
        assertEquals(AuditoriumTheme.DriveIn, AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, "b"))
        // Real ids, whose hash overflows to a negative Int: the abs keeps the index in range.
        assertEquals(AuditoriumTheme.Palace, AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, "tt1375666"))
        assertEquals(AuditoriumTheme.Modern, AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, "tt0068646"))
        assertEquals(AuditoriumTheme.DriveIn, AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, "tt0111161"))
        // An empty id hashes to 0.
        assertEquals(AuditoriumTheme.Palace, AuditoriumRules.themeFor(AuditoriumThemeChoice.Random, ""))
    }

    // ---- opening ----

    private val settled = AuditoriumFrame(fade = 1f, houseLights = 0f, curtain = 1f, beam = 1f, camera = 0f, picture = 1f)

    @Test
    fun openingStartsOnBlackWithTheLightsUpAndTheCurtainsClosed() {
        val f = AuditoriumRules.openingFrame(0L, AuditoriumLength.Normal)
        sameFrame(AuditoriumFrame(fade = 0f, houseLights = 1f, curtain = 0f, beam = 0f, camera = 0f, picture = 0f), f)
    }

    @Test
    fun openingSettlesWithTheCurtainsOpenAndTheLightsDown() {
        val at = AuditoriumRules.openingSettledMs(AuditoriumLength.Normal)
        assertEquals(AuditoriumRules.OPEN_SETTLED, at)
        sameFrame(settled, AuditoriumRules.openingFrame(at, AuditoriumLength.Normal))
        // Past the settled point the frame holds (the pre-show plays there).
        sameFrame(settled, AuditoriumRules.openingFrame(at + 60_000L, AuditoriumLength.Normal))
        sameFrame(settled, AuditoriumRules.openingFrame(AuditoriumRules.openingSettledMs(AuditoriumLength.Short), AuditoriumLength.Short))
    }

    @Test
    fun openingLightsOnlyGoDownAndCurtainsOnlyOpen() {
        var lights = 2f
        var curtain = -1f
        var fade = -1f
        for (t in 0L..6_000L step 50L) {
            val f = AuditoriumRules.openingFrame(t, AuditoriumLength.Normal)
            assertTrue(f.houseLights <= lights + 1e-6f, "lights rose at $t")
            assertTrue(f.curtain >= curtain - 1e-6f, "curtain closed at $t")
            assertTrue(f.fade >= fade - 1e-6f, "fade went back at $t")
            assertEquals(0f, f.camera, "the camera stays at the seat in the opening")
            lights = f.houseLights
            curtain = f.curtain
            fade = f.fade
        }
    }

    @Test
    fun openingMidpointsMatchTheRamps() {
        // 1650 ms is halfway through the dim (900..2400); 3200 ms is halfway through the curtains (2000..4400).
        near(0.5f, AuditoriumRules.openingFrame(1_650L, AuditoriumLength.Normal).houseLights)
        near(0.5f, AuditoriumRules.openingFrame(3_200L, AuditoriumLength.Normal).curtain)
        near(0.5f, AuditoriumRules.openingFrame(450L, AuditoriumLength.Normal).fade)
        // The art comes up with the curtains.
        val f = AuditoriumRules.openingFrame(3_200L, AuditoriumLength.Normal)
        near(f.curtain, f.picture)
    }

    @Test
    fun shortIsTheNormalTimelineScaled() {
        val scale = AuditoriumLength.Short.scale
        assertTrue(abs(AuditoriumRules.openingSettledMs(AuditoriumLength.Short) - 2_970L) <= 1L)
        assertTrue(abs(AuditoriumRules.glideMs(AuditoriumLength.Short) - 880L) <= 1L)
        assertTrue(abs(AuditoriumRules.closingMs(AuditoriumLength.Short) - 2_750L) <= 1L)
        assertTrue(AuditoriumRules.openingSettledMs(AuditoriumLength.Short) < AuditoriumRules.openingSettledMs(AuditoriumLength.Normal))
        // Normal times that are multiples of 20 scale to whole milliseconds. Converting back loses at most a
        // millisecond to rounding, and the steepest ramp (the 800 ms beam) moves 1/800 per millisecond, hence 5e-3.
        for (t in 0L..6_000L step 100L) {
            val shortMs = (t * scale).toLong()
            sameFrame(
                AuditoriumRules.openingFrame(t, AuditoriumLength.Normal),
                AuditoriumRules.openingFrame(shortMs, AuditoriumLength.Short),
                tolerance = 5e-3f,
            )
            sameFrame(
                AuditoriumRules.closingFrame(t, AuditoriumLength.Normal),
                AuditoriumRules.closingFrame(shortMs, AuditoriumLength.Short),
                tolerance = 5e-3f,
            )
        }
    }

    // ---- glide ----

    @Test
    fun glideStartsFromTheSettledHall() {
        sameFrame(settled, AuditoriumRules.glideFrame(0L, AuditoriumLength.Normal))
        sameFrame(settled, AuditoriumRules.glideFrame(0L, AuditoriumLength.Short))
    }

    @Test
    fun glideEndsInsideTheScreenOnBlack() {
        for (length in AuditoriumLength.entries) {
            val f = AuditoriumRules.glideFrame(AuditoriumRules.glideMs(length), length)
            near(1f, f.camera, what = "camera")
            near(0f, f.picture, what = "picture")
            near(0f, f.beam, what = "beam")
            near(1f, f.fade, what = "fade")
        }
        // Halfway the camera is halfway (ease in-out is symmetric) and the picture is still up.
        val mid = AuditoriumRules.glideFrame(800L, AuditoriumLength.Normal)
        near(0.5f, mid.camera)
        near(1f, mid.picture)
    }

    @Test
    fun glideCameraOnlyMovesIn() {
        var last = -1f
        for (t in 0L..1_600L step 40L) {
            val c = AuditoriumRules.glideFrame(t, AuditoriumLength.Normal).camera
            assertTrue(c >= last - 1e-6f, "camera went back at $t")
            last = c
        }
    }

    // ---- closing ----

    @Test
    fun closingStartsInsideTheScreenOnBlack() {
        val f = AuditoriumRules.closingFrame(0L, AuditoriumLength.Normal)
        sameFrame(AuditoriumFrame(fade = 1f, houseLights = 0f, curtain = 1f, beam = 1f, camera = 1f, picture = 0f), f)
    }

    @Test
    fun closingEndsFadedWithTheLightsUpAndTheCurtainsClosed() {
        for (length in AuditoriumLength.entries) {
            // Short maps its end back through a float division, so look a few ms past it (the frame holds there).
            val end = AuditoriumRules.closingMs(length) + if (length == AuditoriumLength.Normal) 0L else 5L
            val f = AuditoriumRules.closingFrame(end, length)
            near(0f, f.fade, what = "fade")
            near(1f, f.houseLights, what = "houseLights")
            near(0f, f.curtain, what = "curtain")
            near(0f, f.beam, what = "beam")
            near(0f, f.camera, what = "camera")
            near(0f, f.picture, what = "picture")
        }
    }

    @Test
    fun closingLightsOnlyComeUpAndCurtainsOnlyClose() {
        var lights = -1f
        var curtain = 2f
        var camera = 2f
        for (t in 0L..5_000L step 50L) {
            val f = AuditoriumRules.closingFrame(t, AuditoriumLength.Normal)
            assertTrue(f.houseLights >= lights - 1e-6f, "lights went down at $t")
            assertTrue(f.curtain <= curtain + 1e-6f, "curtain opened at $t")
            assertTrue(f.camera <= camera + 1e-6f, "camera went back in at $t")
            lights = f.houseLights
            curtain = f.curtain
            camera = f.camera
        }
    }

    // ---- geometry ----

    private val views = listOf(1920f to 1080f, 1024f to 768f, 3440f to 1440f)
    private val px = 0.5f

    @Test
    fun seatScreenIsSixteenByNineCentredNearTheTop() {
        for ((w, h) in views) {
            val s = AuditoriumRules.seatScreen(w, h)
            near(16f / 9f, s.width / s.height, what = "aspect for ${w}x$h")
            near(w * AuditoriumRules.SEAT_SCREEN_WIDTH, s.width, px, "width for ${w}x$h")
            near(w / 2f, s.centerX, px, "centre for ${w}x$h")
            near(h * AuditoriumRules.SEAT_SCREEN_TOP, s.y, px, "top for ${w}x$h")
            assertTrue(s.x > 0f && s.x + s.width < w, "inside the view for ${w}x$h")
            assertTrue(s.y + s.height < h, "inside the view for ${w}x$h")
        }
        val s = AuditoriumRules.seatScreen(1920f, 1080f)
        near(1113.6f, s.width, px, "width")
        near(626.4f, s.height, px, "height")
        near(403.2f, s.x, px, "x")
        near(140.4f, s.y, px, "y")
    }

    @Test
    fun cameraAtZeroIsTheSeatView() {
        for ((w, h) in views) {
            val seat = AuditoriumRules.seatScreen(w, h)
            val at = AuditoriumRules.screenAt(0f, w, h)
            near(seat.x, at.x, px, "x for ${w}x$h")
            near(seat.y, at.y, px, "y for ${w}x$h")
            near(seat.width, at.width, px, "width for ${w}x$h")
            near(seat.height, at.height, px, "height for ${w}x$h")
            near(1f, AuditoriumRules.camera(0f, w, h).scale, what = "scale for ${w}x$h")
        }
    }

    @Test
    fun cameraAtOneCoversTheViewCentred() {
        for ((w, h) in views) {
            val at = AuditoriumRules.screenAt(1f, w, h)
            assertTrue(at.x <= px, "left edge for ${w}x$h: ${at.x}")
            assertTrue(at.y <= px, "top edge for ${w}x$h: ${at.y}")
            assertTrue(at.x + at.width >= w - px, "right edge for ${w}x$h")
            assertTrue(at.y + at.height >= h - px, "bottom edge for ${w}x$h")
            near(w / 2f, at.centerX, px, "centre x for ${w}x$h")
            near(h / 2f, at.centerY, px, "centre y for ${w}x$h")
            near(16f / 9f, at.width / at.height, what = "still 16:9 for ${w}x$h")
            near(AuditoriumRules.endScale(w, h), AuditoriumRules.camera(1f, w, h).scale, what = "scale for ${w}x$h")
        }
        // A 16:9 view is covered exactly; 4:3 crops the sides, ultrawide the top and bottom.
        val exact = AuditoriumRules.screenAt(1f, 1920f, 1080f)
        near(1920f, exact.width, px, "width")
        near(1080f, exact.height, px, "height")
        val square = AuditoriumRules.screenAt(1f, 1024f, 768f)
        near(768f, square.height, px, "4:3 height fits")
        assertTrue(square.width > 1024f + px, "4:3 crops the sides")
        val wide = AuditoriumRules.screenAt(1f, 3440f, 1440f)
        near(3440f, wide.width, px, "ultrawide width fits")
        assertTrue(wide.height > 1440f + px, "ultrawide crops the top and bottom")
    }

    @Test
    fun cameraScaleOnlyGrows() {
        for ((w, h) in views) {
            var last = 0f
            for (i in 0..40) {
                val scale = AuditoriumRules.camera(i / 40f, w, h).scale
                assertTrue(scale > last, "scale did not grow at ${i / 40f} for ${w}x$h")
                last = scale
            }
            assertTrue(AuditoriumRules.endScale(w, h) > 1f)
        }
    }
}
