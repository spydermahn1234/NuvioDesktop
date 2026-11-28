package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The hall that is drawn: [AuditoriumThemeChoice] without Random. */
enum class AuditoriumTheme { Palace, Modern, DriveIn }

/** Where a sequence is. Opening ends with the camera inside the screen on black; Closing ends with the hall faded out. */
enum class AuditoriumSequence { Opening, Closing }

/**
 * Everything the hall needs to draw one moment, every value 0..1.
 *
 * - [fade]: the hall's visibility over black (0 = all black, 1 = the hall fully shown).
 * - [houseLights]: 1 = lights up, 0 = dimmed for the film.
 * - [curtain]: 0 = closed, 1 = fully open.
 * - [beam]: the projector beam and its haze, 0 = off.
 * - [camera]: 0 = the seat view, 1 = inside the screen (the screen fills the view).
 * - [picture]: how much of the film's picture shows on the screen (0 = a black screen).
 */
data class AuditoriumFrame(
    val fade: Float,
    val houseLights: Float,
    val curtain: Float,
    val beam: Float,
    val camera: Float,
    val picture: Float,
)

/** A rectangle in view pixels. */
data class AuditoriumRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

/**
 * The hall's camera: a uniform scale [scale] about the seat-view screen's centre, after which that centre sits at
 * ([centerX], [centerY]). A point p of the seat-view hall is drawn at `(p - seatCentre) * scale + centre`.
 */
data class AuditoriumCamera(val scale: Float, val centerX: Float, val centerY: Float)

/**
 * fork:auditorium - the pure rules: who gets the hall, which hall, and the two timelines.
 *
 * Times are in milliseconds. A timeline is a set of overlapping ramps, so every value is a pure function of the
 * elapsed time and the sequence can be scrubbed, skipped (jump to [openingDurationMs]) or tested frame by frame.
 */
object AuditoriumRules {
    /** The screen's shape in the hall. 16:9 so a trailer or the pre-show cards fill it without bars. */
    const val SCREEN_ASPECT = 16f / 9f
    /** Seat view: the screen takes this share of the view's width... */
    const val SEAT_SCREEN_WIDTH = 0.58f
    /** ...and its top sits this share of the view's height down from the top. */
    const val SEAT_SCREEN_TOP = 0.13f

    // Opening, at Normal length.
    const val OPEN_FADE_IN_END = 900L
    const val OPEN_DIM_START = 900L
    const val OPEN_DIM_END = 2400L
    const val OPEN_CURTAIN_START = 2000L
    const val OPEN_CURTAIN_END = 4400L
    const val OPEN_BEAM_START = 2000L
    const val OPEN_BEAM_END = 2800L
    /** The curtains are open and the screen shows the film's art; this is where the pre-show plays, when it does. */
    const val OPEN_SETTLED = 5400L
    /** The glide into the screen, after [OPEN_SETTLED] (and after the pre-show). */
    const val GLIDE_DURATION = 1600L

    // Closing, at Normal length.
    const val CLOSE_PULL_END = 1400L
    const val CLOSE_CURTAIN_START = 1400L
    const val CLOSE_CURTAIN_END = 3400L
    const val CLOSE_BEAM_START = 1400L
    const val CLOSE_BEAM_END = 2200L
    const val CLOSE_LIGHTS_START = 2600L
    const val CLOSE_LIGHTS_END = 4200L
    const val CLOSE_FADE_START = 4400L
    const val CLOSE_END = 5000L

    // ---- who gets it ----

    /**
     * The opening plays for a fresh, tracked film (not a resume, trailer, rewatch-from-progress or playlist), and for a
     * fresh series episode only when [AuditoriumSettings.includeSeries] is on.
     */
    fun opensFor(
        settings: AuditoriumSettings,
        metaType: String,
        initialPositionMs: Long,
        disableProgressTracking: Boolean,
        isPlaylist: Boolean,
    ): Boolean {
        if (!settings.enabled) return false
        if (initialPositionMs > 0L || disableProgressTracking || isPlaylist) return false
        return isFilm(metaType) || (settings.includeSeries && isSeries(metaType))
    }

    /** The closing plays when the film played to its end by itself (not Back), for the same titles as the opening. */
    fun closesFor(settings: AuditoriumSettings, metaType: String, endedNaturally: Boolean, isPlaylist: Boolean): Boolean {
        if (!settings.enabled || !settings.closing || !endedNaturally || isPlaylist) return false
        return isFilm(metaType) || (settings.includeSeries && isSeries(metaType))
    }

    fun isFilm(metaType: String): Boolean = metaType.equals("movie", ignoreCase = true)
    fun isSeries(metaType: String): Boolean =
        metaType.equals("series", ignoreCase = true) || metaType.equals("tv", ignoreCase = true)

    /** [AuditoriumThemeChoice.Random] gives every film its own hall, stable across runs (a hash of its id). */
    fun themeFor(choice: AuditoriumThemeChoice, metaId: String): AuditoriumTheme = when (choice) {
        AuditoriumThemeChoice.Palace -> AuditoriumTheme.Palace
        AuditoriumThemeChoice.Modern -> AuditoriumTheme.Modern
        AuditoriumThemeChoice.DriveIn -> AuditoriumTheme.DriveIn
        AuditoriumThemeChoice.Random -> {
            // A fixed string hash (not String.hashCode, which is fine too but this keeps it explicit and portable).
            var h = 0
            for (c in metaId) h = 31 * h + c.code
            AuditoriumTheme.entries[abs(h % AuditoriumTheme.entries.size)]
        }
    }

    // ---- timelines ----

    /** The opening's length before the glide, scaled by [AuditoriumLength.scale]. */
    fun openingSettledMs(length: AuditoriumLength): Long = scaled(OPEN_SETTLED, length)
    fun glideMs(length: AuditoriumLength): Long = scaled(GLIDE_DURATION, length)
    fun closingMs(length: AuditoriumLength): Long = scaled(CLOSE_END, length)

    /**
     * The opening up to the settled hall: fade in, house lights down, beam on, curtains open, the screen showing the
     * film's art. [elapsedMs] past [openingSettledMs] holds the settled frame (the pre-show plays there).
     */
    fun openingFrame(elapsedMs: Long, length: AuditoriumLength): AuditoriumFrame {
        val t = unscaled(elapsedMs, length)
        val curtain = ramp(t, OPEN_CURTAIN_START, OPEN_CURTAIN_END)
        return AuditoriumFrame(
            fade = ramp(t, 0L, OPEN_FADE_IN_END),
            houseLights = 1f - ramp(t, OPEN_DIM_START, OPEN_DIM_END),
            curtain = curtain,
            beam = ramp(t, OPEN_BEAM_START, OPEN_BEAM_END),
            camera = 0f,
            // The art comes up on the screen as the curtains part.
            picture = curtain,
        )
    }

    /**
     * The glide from the seat into the screen, after the settled hall (and the pre-show). It ends on black: the
     * picture goes out over the last 30%, so the player's own launch shield (black) takes over without a seam.
     */
    fun glideFrame(elapsedMs: Long, length: AuditoriumLength): AuditoriumFrame {
        val p = clamp01(elapsedMs.toFloat() / glideMs(length).toFloat())
        return AuditoriumFrame(
            fade = 1f,
            houseLights = 0f,
            curtain = 1f,
            beam = 1f - clamp01((p - 0.4f) / 0.6f),
            camera = easeInOut(p),
            picture = 1f - clamp01((p - 0.7f) / 0.3f),
        )
    }

    /**
     * The closing, which starts where the film left the screen: inside the screen on black. The camera pulls back as
     * the art comes up, the beam goes off, the curtains close, the house lights come up and the hall fades out.
     */
    fun closingFrame(elapsedMs: Long, length: AuditoriumLength): AuditoriumFrame {
        val t = unscaled(elapsedMs, length)
        val pull = ramp(t, 0L, CLOSE_PULL_END)
        val curtainClose = ramp(t, CLOSE_CURTAIN_START, CLOSE_CURTAIN_END)
        return AuditoriumFrame(
            fade = 1f - ramp(t, CLOSE_FADE_START, CLOSE_END),
            houseLights = ramp(t, CLOSE_LIGHTS_START, CLOSE_LIGHTS_END),
            curtain = 1f - curtainClose,
            beam = 1f - ramp(t, CLOSE_BEAM_START, CLOSE_BEAM_END),
            camera = 1f - easeInOut(pull),
            picture = min(clamp01(pull / 0.4f), 1f - curtainClose),
        )
    }

    // ---- geometry ----

    /** The screen in the seat view, for a view of [viewWidth] x [viewHeight]. */
    fun seatScreen(viewWidth: Float, viewHeight: Float): AuditoriumRect {
        val w = viewWidth * SEAT_SCREEN_WIDTH
        val h = w / SCREEN_ASPECT
        return AuditoriumRect(x = (viewWidth - w) / 2f, y = viewHeight * SEAT_SCREEN_TOP, width = w, height = h)
    }

    /**
     * The camera for [camera] 0..1. The zoom is geometric (equal steps feel equal), from 1 to the scale at which the
     * screen covers the whole view; the screen's centre travels to the view's centre in step with the zoom, so at 1 the
     * screen is centred and covers the view (cropping its top and bottom, or its sides, when the view is not 16:9).
     */
    fun camera(camera: Float, viewWidth: Float, viewHeight: Float): AuditoriumCamera {
        val seat = seatScreen(viewWidth, viewHeight)
        val end = endScale(viewWidth, viewHeight)
        val c = clamp01(camera)
        val scale = end.pow(c)
        val f = if (end > 1f) (scale - 1f) / (end - 1f) else 1f
        return AuditoriumCamera(
            scale = scale,
            centerX = seat.centerX + (viewWidth / 2f - seat.centerX) * f,
            centerY = seat.centerY + (viewHeight / 2f - seat.centerY) * f,
        )
    }

    /** Where the screen is drawn for [camera] 0..1: [seatScreen] moved by [camera]'s transform. */
    fun screenAt(camera: Float, viewWidth: Float, viewHeight: Float): AuditoriumRect {
        val seat = seatScreen(viewWidth, viewHeight)
        val cam = camera(camera, viewWidth, viewHeight)
        val w = seat.width * cam.scale
        val h = seat.height * cam.scale
        return AuditoriumRect(x = cam.centerX - w / 2f, y = cam.centerY - h / 2f, width = w, height = h)
    }

    /** The zoom at which the screen covers the whole view. */
    fun endScale(viewWidth: Float, viewHeight: Float): Float {
        val seat = seatScreen(viewWidth, viewHeight)
        if (seat.width <= 0f || seat.height <= 0f) return 1f
        return max(viewWidth / seat.width, viewHeight / seat.height)
    }

    // ---- helpers ----

    internal fun ramp(t: Long, start: Long, end: Long): Float =
        if (end <= start) (if (t >= end) 1f else 0f) else clamp01((t - start).toFloat() / (end - start).toFloat())

    internal fun easeInOut(p: Float): Float {
        val x = clamp01(p)
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    }

    internal fun clamp01(v: Float): Float = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)

    private fun scaled(ms: Long, length: AuditoriumLength): Long = (ms * length.scale).toLong()

    /** Elapsed time mapped back onto the Normal-length timeline. */
    private fun unscaled(elapsedMs: Long, length: AuditoriumLength): Long =
        if (length.scale <= 0f) elapsedMs else (elapsedMs / length.scale).toLong()
}
