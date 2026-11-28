package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * fork:auditorium - the hall's GPU passes. Each is one small Skia runtime shader on desktop
 * (AuditoriumShaders.desktop.kt). Every brush works in the coordinates of the canvas it is drawn on
 * (the hall's seat-view coordinates, or the view for the grain), so positions are passed in pixels
 * and the brush must be drawn with no translate between it and the canvas.
 */

/**
 * The light that reaches the room: drawn over the lit ("albedo") hall with BlendMode.Multiply. House lights, the
 * screen's spill, a warm curtain wash, up to four wall lights, and full light (1) where the screen's picture shows
 * between the curtains and above [openAbove] (the drive-in's sky), so emissive parts are never darkened.
 */
internal data class AuditoriumLightMap(
    val screen: Rect,
    val leftLeadTop: Float,
    val leftLeadBottom: Float,
    val rightLeadTop: Float,
    val rightLeadBottom: Float,
    val curtainTop: Float,
    val curtainBottom: Float,
    val ambient: Float,
    val ambientTint: Color,
    val spill: Float,
    val spillTint: Color,
    val stage: Float,
    val stageTint: Color,
    val stageArea: Rect,
    val sconce: Float,
    val sconceTint: Color,
    val sconceReach: Float,
    /** Exactly four are used; pad with far-away points. */
    val sconces: List<Offset>,
    val openAbove: Float,
)

/** Light map, drawn with BlendMode.Multiply over the whole hall. */
internal expect fun auditoriumLightBrush(map: AuditoriumLightMap): Brush

/**
 * The projector beam from [apex] (above the view) onto [screen]: a soft cone with slow streaks, volumetric haze and
 * drifting dust motes. Premultiplied light; draw with BlendMode.Plus.
 */
internal expect fun auditoriumBeamBrush(apex: Offset, screen: Rect, intensity: Float, time: Float, px: Float, tint: Color): Brush

/** Slow room haze, lit by the screen ([glow]) and the house lights ([ambient]). Draw with BlendMode.Plus. */
internal expect fun auditoriumHazeBrush(
    screen: Rect,
    density: Float,
    glow: Float,
    ambient: Float,
    time: Float,
    px: Float,
    tint: Color,
): Brush

/**
 * One curtain panel of velvet: vertical folds attached to the fabric (they gather as the panel narrows), sheen on the
 * fold edges, a footlight glow at the hem. Opaque; draw it clipped to the panel's shape. [outerX] is the panel's
 * fixed outer edge, [leadTopX]/[leadBottomX] its leading edge at the top and the hem.
 */
internal expect fun auditoriumVelvetBrush(
    outerX: Float,
    leadTopX: Float,
    leadBottomX: Float,
    top: Float,
    bottom: Float,
    folds: Float,
    depth: Float,
    base: Color,
    sheen: Color,
    footlight: Float,
    footTint: Color,
    px: Float,
): Brush

/** Film grain (24 fps) and a vignette in one premultiplied overlay for the whole view. Draw with normal blending. */
internal expect fun auditoriumGrainBrush(time: Float, grain: Float, vignette: Float, px: Float): Brush

/** The drive-in's night sky down to [horizon]: gradient, a faint Milky Way, moonlight and twinkling stars. Opaque. */
internal expect fun auditoriumSkyBrush(horizon: Float, time: Float, px: Float): Brush
