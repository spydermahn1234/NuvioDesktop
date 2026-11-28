package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.withFrameNanos
import com.nuvio.app.core.ui.NuvioAsyncImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * fork:auditorium - the rendered cinema hall for one [frame] of a sequence.
 *
 * The hall is drawn in seat-view coordinates (the screen at [AuditoriumRules.seatScreen]) inside one graphicsLayer
 * that applies [AuditoriumRules.camera], so the screen always lands exactly on [AuditoriumRules.screenAt]. It is drawn
 * as a lit "albedo" pass (walls, curtains, seats as if the house lights were full), then one light map multiplied over
 * it (house lights, screen spill, wall lights; the picture is left at full light), then the emissive parts (lamps,
 * signs, LEDs, haze, the beam). Grain and the vignette sit over the whole view; [AuditoriumFrame.fade] is black on top.
 *
 * [screenContent], when given, is composed with real layout at the screen's rectangle (no transform above it), so a
 * heavyweight video surface lines up with the hall. Nothing is drawn over it.
 */
@Composable
fun AuditoriumHall(
    frame: AuditoriumFrame,
    theme: AuditoriumTheme,
    title: String,
    artUrl: String?,          // backdrop (preferred) or poster URL, may be null -> a tasteful title card on the screen instead
    modifier: Modifier = Modifier,
    screenContent: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        val w = (if (constraints.hasBoundedWidth) constraints.maxWidth else 1920).coerceAtLeast(2).toFloat()
        val h = (if (constraints.hasBoundedHeight) constraints.maxHeight else 1080).coerceAtLeast(2).toFloat()
        val density = LocalDensity.current
        val geo = remember(w, h, theme) { HallGeometry(w, h, theme) }
        val clock = rememberHallClock()
        val measurer = rememberTextMeasurer()
        val signs = remember(title, geo, density) { measureSigns(measurer, title, geo, density) }
        val levels = HallLevels(frame, theme)
        val cam = AuditoriumRules.camera(frame.camera, w, h)
        val seat = geo.seat

        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // A seat-view point p lands at (p - seatCentre) * scale + camera centre.
                transformOrigin = TransformOrigin(seat.centerX / w, seat.centerY / h)
                scaleX = cam.scale
                scaleY = cam.scale
                translationX = cam.centerX - seat.centerX
                translationY = cam.centerY - seat.centerY
            },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawHallBack(geo, levels, clock.value) }
            HallScreen(geo, levels, title, artUrl)
            Canvas(Modifier.fillMaxSize()) { drawHallFront(geo, levels, clock.value, signs) }
        }

        Canvas(Modifier.fillMaxSize()) {
            drawRect(auditoriumGrainBrush(clock.value, GRAIN, VIGNETTE, geo.px))
            val fade = AuditoriumRules.clamp01(frame.fade)
            if (fade < 1f) drawRect(Color.Black, alpha = 1f - fade)
        }

        if (screenContent != null) {
            val r = AuditoriumRules.screenAt(frame.camera, w, h)
            Box(Modifier.pixelRect(r.x, r.y, r.width, r.height)) { screenContent() }
        }
    }
}

private const val GRAIN = 0.045f
private const val VIGNETTE = 0.42f

/** Places a child at an exact pixel rectangle of its parent (top-left aligned parent). */
private fun Modifier.pixelRect(x: Float, y: Float, width: Float, height: Float): Modifier =
    this.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
        .layout { measurable, _ ->
            val wPx = width.roundToInt().coerceAtLeast(1)
            val hPx = height.roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
            layout(wPx, hPx) { placeable.place(0, 0) }
        }

/** Seconds since the hall appeared, ticking every frame. Read only in draw, so it redraws without recomposing. */
@Composable
private fun rememberHallClock(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                time.floatValue = (((now - start) / 1_000_000_000.0) % 3600.0).toFloat()
            }
        }
    }
    return time
}

// ---------------------------------------------------------------------------------------------------------------
// Levels: the frame's 0..1 values shaped into how light behaves.
// ---------------------------------------------------------------------------------------------------------------

private class HallLevels(frame: AuditoriumFrame, val theme: AuditoriumTheme) {
    val houseRaw = AuditoriumRules.clamp01(frame.houseLights)
    /** Room light: exponential, so the first part of the dim takes most of the light (as real dimmers look). */
    val house = expoLight(houseRaw)
    /** Lamps and sconces: they hold on longest. */
    val fixtures = houseRaw.pow(0.55f)
    /** Signs and the marquee. */
    val sign = houseRaw.pow(0.8f)
    val curtain = AuditoriumRules.clamp01(frame.curtain)
    /** Curtains accelerate and settle like a real traveller track. */
    val curtainEase = curtain * curtain * (3f - 2f * curtain)
    val beam = AuditoriumRules.clamp01(frame.beam)
    val picture = AuditoriumRules.clamp01(frame.picture).let { if (theme == AuditoriumTheme.DriveIn) min(it, curtain) else it }

    private fun expoLight(x: Float): Float = ((exp(3.2f * x) - 1f) / (exp(3.2f) - 1f)).coerceIn(0f, 1f)
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ---------------------------------------------------------------------------------------------------------------
// Geometry: every path and brush that depends only on the size and the theme, built once.
// ---------------------------------------------------------------------------------------------------------------

private class Sconce(val at: Offset, val size: Float)

private class SeatBox(val left: Float, val width: Float, val dy: Float)

private class SeatRow(
    val top: Float,
    val height: Float,
    val radius: CornerRadius,
    val seats: List<SeatBox>,
    val brush: Brush,
    val rim: Brush,
    val aisleLights: List<Offset>,
    val lightRadius: Float,
)

private class CarRow(val body: Path, val glass: Path, val glint: Path, val brush: Brush, val glintWidth: Float, val tails: List<Rect>)

private class HallGeometry(val w: Float, val h: Float, val theme: AuditoriumTheme) {
    val seat = AuditoriumRules.seatScreen(w, h)
    val screen = Rect(seat.x, seat.y, seat.x + seat.width, seat.y + seat.height)
    val px = (h / 1080f).coerceAtLeast(0.25f)
    val cx = w / 2f
    val vanish = screen.center
    val stageY = screen.bottom + h * 0.045f
    val apronY = stageY + h * 0.028f
    val stackW = w * 0.07f
    /** The curtains' opening: the screen plus room at each side for the gathered stacks. */
    val opening = Rect(screen.left - stackW, screen.top - h * 0.045f, screen.right + stackW, stageY)
    val band = w * 0.02f
    val archOuter = Rect(opening.left - band, opening.top - h * 0.032f, opening.right + band, stageY)
    val frontLeft = w * 0.095f
    val frontRight = w - frontLeft
    val horizon = screen.bottom + h * 0.075f
    val beamApex = Offset(cx, -h * 0.30f)
    /** Where the valance's lowest swag ends: never over the screen. */
    val valanceBottom = screen.top - h * 0.004f
    val valanceRise = h * 0.022f

    /** Left wall: a line through the vanishing point and (0, [yEdge]), at [x]. */
    fun wallY(yEdge: Float, x: Float): Float = yEdge + (vanish.y - yEdge) * (x / vanish.x)

    val beamPath = Path().apply {
        val half = screen.width * 0.5f * 1.06f
        moveTo(beamApex.x, beamApex.y)
        lineTo(beamApex.x - half, screen.top)
        lineTo(beamApex.x - half, screen.bottom)
        lineTo(beamApex.x + half, screen.bottom)
        lineTo(beamApex.x + half, screen.top)
        close()
    }

    val leftWall = Path().apply {
        moveTo(-2f, -2f); lineTo(frontLeft, -2f); lineTo(frontLeft, apronY); lineTo(-2f, h + 2f); close()
    }
    val rightWall = Path().apply {
        moveTo(w + 2f, -2f); lineTo(frontRight, -2f); lineTo(frontRight, apronY); lineTo(w + 2f, h + 2f); close()
    }

    /** A quad on a side wall between x0..x1 (left-wall x) and edge heights y0..y1, mirrored for the right wall. */
    fun wallQuad(x0: Float, x1: Float, y0: Float, y1: Float, right: Boolean): Path = Path().apply {
        fun mx(x: Float) = if (right) w - x else x
        moveTo(mx(x0), wallY(y0, x0)); lineTo(mx(x1), wallY(y0, x1)); lineTo(mx(x1), wallY(y1, x1)); lineTo(mx(x0), wallY(y1, x0))
        close()
    }

    // ---- Palace ----

    val goldStops = arrayOf(
        0f to Color(0xFF3E2806), 0.22f to Color(0xFF94701F), 0.45f to Color(0xFFE4C26F),
        0.55f to Color(0xFFFFF0BE), 0.7f to Color(0xFFB7892F), 1f to Color(0xFF4E330A),
    )
    val goldVertical = Brush.verticalGradient(*goldStops, startY = archOuter.top, endY = opening.top)
    val goldLeft = Brush.horizontalGradient(*goldStops, startX = archOuter.left, endX = opening.left)
    val goldRight = Brush.horizontalGradient(*goldStops, startX = opening.right, endX = archOuter.right)
    val crown1 = Rect(cx - w * 0.21f, archOuter.top - h * 0.012f, cx + w * 0.21f, archOuter.top + 1f)
    val crown2 = Rect(cx - w * 0.15f, archOuter.top - h * 0.024f, cx + w * 0.15f, crown1.top + 1f)
    val goldCrown = Brush.verticalGradient(*goldStops, startY = crown2.top, endY = archOuter.top)
    val signRect = when (theme) {
        AuditoriumTheme.DriveIn -> Rect(w * 0.03f, h * 0.27f, w * 0.175f, h * 0.50f)
        else -> Rect(cx - w * 0.125f, h * 0.006f, cx + w * 0.125f, h * 0.068f)
    }

    val palaceWall = Brush.verticalGradient(
        0f to Color(0xFF4A1A17), 0.45f to Color(0xFF5C241E), 0.75f to Color(0xFF3A1513), 1f to Color(0xFF220B0A),
        startY = 0f, endY = apronY,
    )
    val palaceSideL = Brush.horizontalGradient(listOf(Color(0xFF240B0A), Color(0xFF4A1C18)), startX = 0f, endX = frontLeft)
    val palaceSideR = Brush.horizontalGradient(listOf(Color(0xFF4A1C18), Color(0xFF240B0A)), startX = frontRight, endX = w)
    private val palacePanelXs = listOf(0.004f to 0.026f, 0.034f to 0.058f, 0.066f to 0.089f)
    val palacePanels: List<Path> = if (theme != AuditoriumTheme.Palace) emptyList() else buildList {
        for (right in listOf(false, true)) for ((a, b) in palacePanelXs) add(wallQuad(w * a, w * b, h * 0.15f, h * 0.62f, right))
    }
    val palaceWainscot: List<Path> = if (theme != AuditoriumTheme.Palace) emptyList() else listOf(
        wallQuad(0f, frontLeft, h * 0.70f, h * 1.3f, false), wallQuad(0f, frontLeft, h * 0.70f, h * 1.3f, true),
    )
    val sconces: List<Sconce> = when (theme) {
        AuditoriumTheme.Palace -> listOf(0.046f to 1f, 0.078f to 0.78f).flatMap { (fx, s) ->
            val x = w * fx
            val y = wallY(h * 0.36f, x)
            listOf(Sconce(Offset(x, y), s * h * 0.016f), Sconce(Offset(w - x, y), s * h * 0.016f))
        }
        AuditoriumTheme.DriveIn -> listOf(
            Sconce(Offset(w * 0.885f, h * 0.455f), h * 0.010f),
            Sconce(Offset(w * 0.955f, h * 0.425f), h * 0.013f),
        )
        AuditoriumTheme.Modern -> emptyList()
    }
    val sconceHalo: List<Brush> = sconces.map { s ->
        Brush.radialGradient(
            0f to Color(0xFFFFB46A).copy(alpha = 0.55f), 0.35f to Color(0xFFFF9A4A).copy(alpha = 0.18f), 1f to Color.Transparent,
            center = s.at, radius = s.size * 7f,
        )
    }

    // Valance: a swagged pelmet across the opening, never over the screen.
    private val swags = 5
    val valancePath = Path().apply {
        moveTo(opening.left, opening.top - h * 0.004f)
        lineTo(opening.right, opening.top - h * 0.004f)
        lineTo(opening.right, valanceBottom - valanceRise)
        val step = opening.width / swags
        for (i in 0 until swags) {
            val x1 = opening.right - step * i
            val x0 = x1 - step
            cubicTo(x1 - step * 0.2f, valanceBottom + valanceRise * 0.35f, x0 + step * 0.2f, valanceBottom + valanceRise * 0.35f, x0, valanceBottom - valanceRise)
        }
        close()
    }
    val valanceEdge = Path().apply {
        val step = opening.width / swags
        moveTo(opening.right, valanceBottom - valanceRise)
        for (i in 0 until swags) {
            val x1 = opening.right - step * i
            val x0 = x1 - step
            cubicTo(x1 - step * 0.2f, valanceBottom + valanceRise * 0.35f, x0 + step * 0.2f, valanceBottom + valanceRise * 0.35f, x0, valanceBottom - valanceRise)
        }
    }
    val valanceJoins: List<Offset> = (0..swags).map { Offset(opening.left + opening.width / swags * it, valanceBottom - valanceRise) }
    val valanceBrush: Brush = auditoriumVelvetBrush(
        outerX = opening.left, leadTopX = opening.right, leadBottomX = opening.right,
        top = opening.top - h * 0.004f, bottom = valanceBottom,
        folds = 38f, depth = 0.55f, base = PalaceVelvet, sheen = PalaceSheen, footlight = 0f, footTint = Color.Black, px = px,
    )
    val stageBrush = Brush.verticalGradient(
        listOf(Color(0xFF2A1309), Color(0xFF160904)), startY = stageY, endY = apronY,
    )

    // ---- Modern ----

    val modernWall = Brush.verticalGradient(
        0f to Color(0xFF0B0C0E), 0.5f to Color(0xFF15161A), 1f to Color(0xFF0A0A0C), startY = 0f, endY = apronY,
    )
    val modernSlices: List<Pair<Path, Color>> = if (theme != AuditoriumTheme.Modern) emptyList() else buildList {
        val xs = listOf(0f, 0.022f, 0.042f, 0.059f, 0.074f, 0.086f, 0.095f).map { it * w }
        for (right in listOf(false, true)) {
            for (i in 0 until xs.size - 1) {
                val c = if (i % 2 == 0) Color(0xFF121317) else Color(0xFF1B1C21)
                add(wallQuad(xs[i], xs[i + 1], -h * 0.2f, h * 1.3f, right) to c)
            }
        }
    }
    private val ledEdges = listOf(h * 0.09f, h * 0.60f)
    val ledLines: List<Pair<Offset, Offset>> = if (theme != AuditoriumTheme.Modern) emptyList() else buildList {
        for (y0 in ledEdges) {
            val yf = wallY(y0, frontLeft)
            add(Offset(0f, y0) to Offset(frontLeft, yf))
            add(Offset(w, y0) to Offset(frontRight, yf))
            add(Offset(frontLeft, yf) to Offset(opening.left - band, yf))
            add(Offset(frontRight, yf) to Offset(opening.right + band, yf))
        }
        add(Offset(opening.left - band, apronY - 1f) to Offset(opening.right + band, apronY - 1f))
    }
    val ledPools: List<Offset> = if (theme != AuditoriumTheme.Modern) emptyList() else listOf(
        Offset(w * 0.03f, wallY(ledEdges[1], w * 0.03f)), Offset(w * 0.97f, wallY(ledEdges[1], w * 0.03f)),
        Offset(w * 0.07f, wallY(ledEdges[0], w * 0.07f)), Offset(w * 0.93f, wallY(ledEdges[0], w * 0.07f)),
    )

    // ---- Seats ----

    val seatRows: List<SeatRow> = if (theme == AuditoriumTheme.DriveIn) emptyList() else buildSeats()

    private fun buildSeats(): List<SeatRow> {
        val palace = theme == AuditoriumTheme.Palace
        val rows = if (palace) 5 else 4
        val body = if (palace) Color(0xFF5E1318) else Color(0xFF1D1E22)
        val rimC = if (palace) Color(0xFF9E3D38) else Color(0xFF4A4C54)
        val dark = if (palace) Color(0xFF1C0506) else Color(0xFF070709)
        return (0 until rows).map { r ->
            val f = r / (rows - 1f)
            val top = h * (0.755f + 0.185f * f.pow(1.25f))
            val seatW = w * (0.036f + 0.085f * f.pow(1.5f)) * (if (palace) 1f else 1.18f)
            val seatH = seatW * (if (palace) 0.95f else 0.8f) + h * 0.12f
            val gap = seatW * (if (palace) 0.10f else 0.07f)
            val radius = seatW * (if (palace) 0.30f else 0.16f)
            val aisleHalf = seatW * 0.55f
            val aisleL = w * (0.17f - 0.15f * f)
            val aisleR = w - aisleL
            val stagger = if (r % 2 == 1) seatW * 0.5f else 0f
            val n = ceil(w / 2f / seatW).toInt() + 2
            val seats = (-n..n).mapNotNull { i ->
                val x = cx + stagger + i * seatW
                if (abs(x - aisleL) < aisleHalf || abs(x - aisleR) < aisleHalf) return@mapNotNull null
                val dx = (x - cx) / (w / 2f)
                SeatBox(left = x - seatW / 2f + gap / 2f, width = seatW - gap, dy = -h * 0.028f * (1f - f * 0.6f) * dx * dx)
            }
            SeatRow(
                top = top,
                height = seatH,
                radius = CornerRadius(radius, radius * 0.8f),
                seats = seats,
                brush = Brush.verticalGradient(
                    0f to rimC, 0.05f to body, 0.35f to lerp(body, dark, 0.5f), 1f to dark,
                    startY = top, endY = top + seatH * 0.8f,
                ),
                rim = Brush.verticalGradient(
                    0f to Color.White, 0.08f to Color.White.copy(alpha = 0.35f), 0.3f to Color.Transparent,
                    startY = top, endY = top + seatH * 0.6f,
                ),
                aisleLights = listOf(Offset(aisleL, top + seatW * 0.55f), Offset(aisleR, top + seatW * 0.55f)),
                lightRadius = max(1.2f, h * 0.0022f * (0.6f + f)),
            )
        }
    }

    // ---- Drive-in ----

    val treeline: Path = Path().apply {
        moveTo(-2f, horizon + 2f)
        val step = w * 0.008f
        var i = 0
        var x = -2f
        while (x <= w + step) {
            val k = i.toFloat()
            val tall = if (i % 23 == 7) h * 0.03f else 0f
            val y = horizon - h * (0.012f + 0.014f * abs(sin(k * 0.37f)) + 0.010f * abs(sin(k * 1.13f + 1f))) - tall
            lineTo(x, y)
            x += step
            i++
        }
        lineTo(w + 2f, horizon + 2f)
        close()
    }
    val groundBrush = Brush.verticalGradient(listOf(Color(0xFF15171B), Color(0xFF0A0B0D)), startY = horizon, endY = h)
    val screenFrame = Rect(screen.left - w * 0.014f, screen.top - w * 0.014f, screen.right + w * 0.014f, screen.bottom + w * 0.014f)
    val legs: List<Rect> = listOf(0.16f, 0.84f).map { f ->
        val x = screen.left + screen.width * f
        Rect(x - w * 0.007f, screenFrame.bottom, x + w * 0.007f, horizon + h * 0.012f)
    }
    val carRows: List<CarRow> = if (theme != AuditoriumTheme.DriveIn) emptyList() else listOf(
        buildCars(cw = w * 0.12f, roofH = h * 0.045f, base = h * 0.845f, bodyH = h * 0.07f, spacing = w * 0.19f, offset = w * 0.02f),
        buildCars(cw = w * 0.26f, roofH = h * 0.085f, base = h * 0.95f, bodyH = h * 0.12f, spacing = w * 0.40f, offset = -w * 0.08f),
    )
    val speakerPosts: List<Rect> = if (theme != AuditoriumTheme.DriveIn) emptyList() else
        (-1..6).map { i ->
            val x = w * 0.02f + i * w * 0.19f + w * 0.12f + (w * 0.19f - w * 0.12f) / 2f
            Rect(x - 1.2f * px, h * 0.815f, x + 1.2f * px, h * 0.92f)
        }
    val lampPosts: List<Rect> = sconces.map { Rect(it.at.x - 1.6f * px, it.at.y, it.at.x + 1.6f * px, h * 0.92f) }

    private fun buildCars(cw: Float, roofH: Float, base: Float, bodyH: Float, spacing: Float, offset: Float): CarRow {
        val body = Path()
        val glass = Path()
        val glint = Path()
        val tails = mutableListOf<Rect>()
        var x0 = offset - spacing
        while (x0 < w + spacing) {
            // From behind: a wide body with a narrower cabin on top.
            body.addRoundRect(
                RoundRect(x0, base, x0 + cw, base + bodyH, topLeftCornerRadius = CornerRadius(cw * 0.09f, roofH * 0.5f),
                    topRightCornerRadius = CornerRadius(cw * 0.09f, roofH * 0.5f),
                    bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero),
            )
            body.moveTo(x0 + cw * 0.10f, base + 2f)
            body.cubicTo(x0 + cw * 0.13f, base - roofH * 0.6f, x0 + cw * 0.16f, base - roofH, x0 + cw * 0.27f, base - roofH)
            body.lineTo(x0 + cw * 0.73f, base - roofH)
            body.cubicTo(x0 + cw * 0.84f, base - roofH, x0 + cw * 0.87f, base - roofH * 0.6f, x0 + cw * 0.90f, base + 2f)
            body.close()
            glass.moveTo(x0 + cw * 0.17f, base - roofH * 0.08f)
            glass.cubicTo(x0 + cw * 0.19f, base - roofH * 0.55f, x0 + cw * 0.22f, base - roofH * 0.84f, x0 + cw * 0.30f, base - roofH * 0.84f)
            glass.lineTo(x0 + cw * 0.70f, base - roofH * 0.84f)
            glass.cubicTo(x0 + cw * 0.78f, base - roofH * 0.84f, x0 + cw * 0.81f, base - roofH * 0.55f, x0 + cw * 0.83f, base - roofH * 0.08f)
            glass.close()
            glint.moveTo(x0 + cw * 0.22f, base - roofH * 0.985f)
            glint.lineTo(x0 + cw * 0.78f, base - roofH * 0.985f)
            glint.moveTo(x0 + cw * 0.04f, base + roofH * 0.03f)
            glint.lineTo(x0 + cw * 0.96f, base + roofH * 0.03f)
            tails += Rect(x0 + cw * 0.03f, base + bodyH * 0.22f, x0 + cw * 0.13f, base + bodyH * 0.32f)
            tails += Rect(x0 + cw * 0.87f, base + bodyH * 0.22f, x0 + cw * 0.97f, base + bodyH * 0.32f)
            x0 += spacing
        }
        return CarRow(
            body = body,
            glass = glass,
            glint = glint,
            brush = Brush.verticalGradient(listOf(Color(0xFF1A1D24), Color(0xFF0B0C10), Color(0xFF050608)), startY = base - roofH, endY = base + bodyH),
            glintWidth = max(1f, roofH * 0.035f),
            tails = tails,
        )
    }

    /** Fireflies: fixed seeds; their motion is a function of time only. */
    val fireflies: List<FloatArray> = if (theme != AuditoriumTheme.DriveIn) emptyList() else (0 until 14).map { i ->
        val a = abs(sin(i * 12.9898f + 1.3f)) // 0..1 pseudo-random
        val b = abs(sin(i * 78.233f + 0.7f))
        val c = abs(sin(i * 37.719f + 2.1f))
        floatArrayOf(w * (0.04f + 0.92f * a), horizon - h * 0.03f + h * 0.10f * b, 0.2f + 0.5f * c, 6.283f * b, 0.6f + 1.4f * a)
    }
}

private val PalaceVelvet = Color(0.46f, 0.035f, 0.055f)
private val PalaceSheen = Color(1f, 0.42f, 0.38f)
private val ModernCloth = Color(0.17f, 0.175f, 0.185f)
private val ModernSheen = Color(0.62f, 0.66f, 0.72f)
private val Led = Color(0xFFCFE2FF)
private val Spill = Color(0.78f, 0.84f, 1f)

// ---------------------------------------------------------------------------------------------------------------
// Signs: measured once per title and size.
// ---------------------------------------------------------------------------------------------------------------

private class HallSigns(val small: TextLayoutResult?, val title: TextLayoutResult?, val brand: TextLayoutResult?)

private fun measureSigns(measurer: TextMeasurer, title: String, geo: HallGeometry, density: Density): HallSigns {
    val h = geo.h
    fun sp(v: Float) = with(density) { v.toSp() }
    val shown = title.ifBlank { "Feature Presentation" }
    fun limit(v: Float) = Constraints(maxWidth = v.roundToInt().coerceAtLeast(1))
    return when (geo.theme) {
        AuditoriumTheme.Palace -> HallSigns(
            small = measurer.measure(
                "NOW SHOWING",
                TextStyle(fontSize = sp(h * 0.0105f), letterSpacing = sp(h * 0.0042f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                density = density,
            ),
            title = measurer.measure(
                shown.uppercase(),
                TextStyle(fontSize = sp(h * 0.0235f), letterSpacing = sp(h * 0.0016f), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = limit(geo.signRect.width * 0.88f),
                density = density,
            ),
            brand = null,
        )
        AuditoriumTheme.Modern -> HallSigns(
            small = measurer.measure(
                "NOW SHOWING",
                TextStyle(fontSize = sp(h * 0.0095f), letterSpacing = sp(h * 0.006f), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
                maxLines = 1,
                density = density,
            ),
            title = measurer.measure(
                shown,
                TextStyle(fontSize = sp(h * 0.026f), letterSpacing = sp(h * 0.0028f), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = limit(geo.w * 0.5f),
                density = density,
            ),
            brand = null,
        )
        AuditoriumTheme.DriveIn -> HallSigns(
            small = measurer.measure(
                "NOW SHOWING",
                TextStyle(fontSize = sp(h * 0.0095f), letterSpacing = sp(h * 0.004f), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
                maxLines = 1,
                density = density,
            ),
            title = measurer.measure(
                shown.uppercase(),
                TextStyle(fontSize = sp(h * 0.0175f), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = sp(h * 0.021f)),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                constraints = limit(geo.signRect.width * 0.82f),
                density = density,
            ),
            brand = measurer.measure(
                "Drive-In",
                TextStyle(
                    fontSize = sp(h * 0.036f),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color(0xFFFF3D7F), Offset.Zero, h * 0.012f),
                ),
                maxLines = 1,
                density = density,
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The screen: bare screen, the art (or the title card) at frame.picture.
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun HallScreen(geo: HallGeometry, levels: HallLevels, title: String, artUrl: String?) {
    val r = geo.screen
    val vignette = remember(r) {
        Brush.radialGradient(
            0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.32f),
            center = Offset(r.width / 2f, r.height / 2f), radius = r.width * 0.62f,
        )
    }
    Box(Modifier.pixelRect(r.left, r.top, r.width, r.height)) {
        Canvas(Modifier.fillMaxSize()) {
            if (levels.theme == AuditoriumTheme.DriveIn) {
                drawRect(Color(0xFF15161A))
                // The projector lamp coming up on the blank screen.
                drawRect(Color(0xFFE9E5DA), alpha = 0.10f * levels.curtain)
            } else {
                // A matte white screen, seen in the house light.
                drawRect(lerp(Color(0xFF030303), Color(0xFF5E5A55), levels.house * 0.9f))
            }
        }
        Box(Modifier.fillMaxSize().alpha(levels.picture)) {
            TitleCard(title, r.width, r.height)
            if (!artUrl.isNullOrBlank()) {
                NuvioAsyncImage(
                    model = artUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (levels.picture > 0f) drawRect(vignette, alpha = levels.picture)
        }
    }
}

/** No art: the title, set like a feature presentation card. */
@Composable
private fun TitleCard(title: String, widthPx: Float, heightPx: Float) {
    val density = LocalDensity.current
    val unit = heightPx / 100f
    fun sp(v: Float) = with(density) { v.toSp() }
    val background = remember(widthPx, heightPx) {
        Brush.radialGradient(
            0f to Color(0xFF2A211A), 0.55f to Color(0xFF120E0B), 1f to Color(0xFF040303),
            center = Offset(widthPx / 2f, heightPx * 0.46f), radius = widthPx * 0.62f,
        )
    }
    val gold = Color(0xFFC9A45C)
    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                "FEATURE PRESENTATION",
                color = gold.copy(alpha = 0.85f),
                fontSize = sp(unit * 3.0f),
                letterSpacing = sp(unit * 1.3f),
                fontFamily = FontFamily.Serif,
                maxLines = 1,
            )
            Box(
                Modifier.pixelRect(0f, 0f, widthPx * 0.16f, max(1f, unit * 0.18f))
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, gold.copy(alpha = 0.7f), Color.Transparent))),
            )
            Text(
                title.ifBlank { "Feature Presentation" },
                color = Color(0xFFF4EBDD),
                fontSize = sp(unit * 10.5f),
                lineHeight = sp(unit * 12.5f),
                letterSpacing = sp(unit * 0.15f),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pixelRect(0f, 0f, widthPx * 0.82f, unit * 27f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Back pass: room behind the screen (albedo).
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawHallBack(geo: HallGeometry, levels: HallLevels, time: Float) {
    when (geo.theme) {
        AuditoriumTheme.Palace -> drawPalaceBack(geo)
        AuditoriumTheme.Modern -> drawModernBack(geo)
        AuditoriumTheme.DriveIn -> drawDriveInBack(geo, levels, time)
    }
}

private fun DrawScope.drawPalaceBack(geo: HallGeometry) {
    val w = geo.w
    val h = geo.h
    drawRect(Color(0xFF120707))
    drawRect(geo.palaceWall, topLeft = Offset(geo.frontLeft, -2f), size = Size(geo.frontRight - geo.frontLeft, geo.apronY + 2f))
    // Front-wall pilasters beside the arch, fluted in gold.
    for (right in listOf(false, true)) {
        val x0 = if (right) geo.archOuter.right else geo.frontLeft
        val x1 = if (right) geo.frontRight else geo.archOuter.left
        drawRect(Color(0xFF4F1E1A), topLeft = Offset(x0, -2f), size = Size(x1 - x0, geo.apronY + 2f))
        val n = 3
        for (i in 1..n) {
            val x = x0 + (x1 - x0) * i / (n + 1f)
            drawLine(Color(0xFF9C7A35).copy(alpha = 0.55f), Offset(x, h * 0.08f), Offset(x, geo.apronY), strokeWidth = max(1f, geo.px * 1.2f))
        }
    }
    // Side walls in perspective: wall, fabric panels framed in gold, the dado rail and the wainscot.
    drawPath(geo.leftWall, geo.palaceSideL)
    drawPath(geo.rightWall, geo.palaceSideR)
    val frame = Stroke(width = max(1f, geo.px * 1.6f))
    for (p in geo.palacePanels) {
        drawPath(p, Color(0xFF66281F).copy(alpha = 0.85f))
        drawPath(p, Color(0xFFB08A3E).copy(alpha = 0.7f), style = frame)
    }
    for (p in geo.palaceWainscot) drawPath(p, Color(0xFF200C09))
    for (right in listOf(false, true)) {
        fun mx(x: Float) = if (right) w - x else x
        drawLine(
            Color(0xFFC29A45), Offset(mx(0f), h * 0.70f), Offset(mx(geo.frontLeft), geo.wallY(h * 0.70f, geo.frontLeft)),
            strokeWidth = max(1f, geo.px * 2.4f),
        )
        drawLine(
            Color(0xFFB08A3E).copy(alpha = 0.8f), Offset(mx(0f), h * 0.075f), Offset(mx(geo.frontLeft), geo.wallY(h * 0.075f, geo.frontLeft)),
            strokeWidth = max(1f, geo.px * 3f),
        )
    }
    // Black masking around the screen.
    val m = w * 0.006f
    drawRect(Color(0xFF020202), topLeft = Offset(geo.screen.left - m, geo.screen.top - m), size = Size(geo.screen.width + 2 * m, geo.screen.height + 2 * m))
}

private fun DrawScope.drawModernBack(geo: HallGeometry) {
    val w = geo.w
    val h = geo.h
    drawRect(Color(0xFF070709))
    drawRect(geo.modernWall, topLeft = Offset(geo.frontLeft, -2f), size = Size(geo.frontRight - geo.frontLeft, geo.apronY + 2f))
    // Acoustic fabric panels: fine vertical seams.
    val seam = w * 0.045f
    var x = geo.frontLeft + seam
    while (x < geo.frontRight) {
        drawLine(Color(0xFF060607), Offset(x, -2f), Offset(x, geo.apronY), strokeWidth = max(1f, geo.px * 2f))
        x += seam
    }
    for ((p, c) in geo.modernSlices) drawPath(p, c)
    drawPath(geo.leftWall, Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), startX = 0f, endX = geo.frontLeft))
    drawPath(geo.rightWall, Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startX = geo.frontRight, endX = w))
    // A slim frame around the opening and the masking.
    val o = geo.opening
    val b = geo.band * 0.35f
    drawRect(Color(0xFF1A1B1F), topLeft = Offset(o.left - b, o.top - b), size = Size(o.width + 2 * b, o.height + b))
    drawRect(Color(0xFF050506), topLeft = Offset(o.left, o.top), size = Size(o.width, o.height))
    val m = w * 0.004f
    drawRect(Color.Black, topLeft = Offset(geo.screen.left - m, geo.screen.top - m), size = Size(geo.screen.width + 2 * m, geo.screen.height + 2 * m))
    drawRect(Color(0xFF0B0B0D), topLeft = Offset(o.left - b, geo.stageY), size = Size(o.width + 2 * b, geo.apronY - geo.stageY + h * 0.01f))
}

private fun DrawScope.drawDriveInBack(geo: HallGeometry, levels: HallLevels, time: Float) {
    val h = geo.h
    drawRect(auditoriumSkyBrush(geo.horizon, time, geo.px))
    drawPath(geo.treeline, Color(0xFF03050A))
    drawRect(geo.groundBrush, topLeft = Offset(0f, geo.horizon), size = Size(geo.w, h - geo.horizon + 2f))
    // The screen tower: legs, braces and the painted surround, catching a little of the picture.
    val structure = lerp(Color(0xFF0E1014), Color(0xFF262A31), 0.35f * levels.picture)
    for (leg in geo.legs) drawRect(structure, topLeft = leg.topLeft, size = leg.size)
    if (geo.legs.size == 2) {
        val a = geo.legs[0]
        val b = geo.legs[1]
        val stroke = max(1f, geo.px * 3f)
        drawLine(structure, Offset(a.center.x, a.top), Offset(b.center.x, a.bottom - h * 0.01f), strokeWidth = stroke)
        drawLine(structure, Offset(b.center.x, b.top), Offset(a.center.x, b.bottom - h * 0.01f), strokeWidth = stroke)
    }
    val f = geo.screenFrame
    drawRect(lerp(Color(0xFF16181D), Color(0xFF3A3E46), 0.3f * levels.picture), topLeft = f.topLeft, size = f.size)
    drawLine(Color(0xFF2E3138), Offset(f.left, f.top), Offset(f.right, f.top), strokeWidth = max(1f, geo.px * 2f))
}

// ---------------------------------------------------------------------------------------------------------------
// Front pass: curtains, arch, seats, the light map, then everything that glows.
// ---------------------------------------------------------------------------------------------------------------

private class CurtainPose(val lt: Float, val lb: Float, val rt: Float, val rb: Float)

private fun curtainPose(geo: HallGeometry, levels: HallLevels): CurtainPose {
    if (geo.theme == AuditoriumTheme.DriveIn) {
        return CurtainPose(geo.screen.left, geo.screen.left, geo.screen.right, geo.screen.right)
    }
    val e = levels.curtainEase
    val closed = geo.w * 0.004f
    val leftTop = mix(geo.cx + closed, geo.screen.left - geo.w * 0.002f, e)
    // The hem trails the rail a little while the curtain travels.
    val sway = sin(PI.toFloat() * levels.curtain) * geo.w * 0.006f
    val rightTop = geo.w - leftTop
    return CurtainPose(leftTop, leftTop + sway, rightTop, rightTop - sway)
}

private fun DrawScope.drawHallFront(geo: HallGeometry, levels: HallLevels, time: Float, signs: HallSigns) {
    val pose = curtainPose(geo, levels)
    when (geo.theme) {
        AuditoriumTheme.Palace -> drawPalaceFront(geo, levels, pose)
        AuditoriumTheme.Modern -> drawModernFront(geo, levels, pose)
        AuditoriumTheme.DriveIn -> drawDriveInFront(geo)
    }
    drawRect(auditoriumLightBrush(lightMap(geo, levels, pose)), blendMode = BlendMode.Multiply)
    when (geo.theme) {
        AuditoriumTheme.Palace -> drawPalaceGlow(geo, levels, time, signs)
        AuditoriumTheme.Modern -> drawModernGlow(geo, levels, signs)
        AuditoriumTheme.DriveIn -> drawDriveInGlow(geo, levels, time, signs)
    }
    drawSeatRims(geo, levels)
    val hazeTint = when (geo.theme) {
        AuditoriumTheme.Palace -> Color(1f, 0.92f, 0.82f)
        AuditoriumTheme.Modern -> Color(0.85f, 0.9f, 1f)
        AuditoriumTheme.DriveIn -> Color(0.7f, 0.78f, 0.95f)
    }
    val hazeDensity = when (geo.theme) {
        AuditoriumTheme.Palace -> 1f
        AuditoriumTheme.Modern -> 0.8f
        AuditoriumTheme.DriveIn -> 0.55f
    }
    drawRect(
        auditoriumHazeBrush(geo.screen, hazeDensity, 0.6f * levels.picture + 0.5f * levels.beam, levels.house, time, geo.px, hazeTint),
        blendMode = BlendMode.Plus,
    )
    if (levels.beam > 0.001f) {
        val beamTint = when (geo.theme) {
            AuditoriumTheme.Palace -> Color(1f, 0.94f, 0.84f)
            AuditoriumTheme.Modern -> Color(0.92f, 0.96f, 1f)
            AuditoriumTheme.DriveIn -> Color(0.95f, 0.95f, 1f)
        }
        drawPath(geo.beamPath, auditoriumBeamBrush(geo.beamApex, geo.screen, levels.beam, time, geo.px, beamTint), blendMode = BlendMode.Plus)
    }
}

private fun lightMap(geo: HallGeometry, levels: HallLevels, pose: CurtainPose): AuditoriumLightMap {
    val h = geo.h
    val common = AuditoriumLightMap(
        screen = geo.screen,
        leftLeadTop = pose.lt,
        leftLeadBottom = pose.lb,
        rightLeadTop = pose.rt,
        rightLeadBottom = pose.rb,
        curtainTop = geo.opening.top,
        curtainBottom = geo.opening.bottom,
        ambient = 0f,
        ambientTint = Color.White,
        spill = 0f,
        spillTint = Spill,
        stage = 0f,
        stageTint = Color.White,
        stageArea = geo.opening,
        sconce = 0f,
        sconceTint = Color.White,
        sconceReach = h * 0.16f,
        sconces = geo.sconces.map { it.at },
        openAbove = -100_000f,
    )
    return when (geo.theme) {
        AuditoriumTheme.Palace -> common.copy(
            ambient = 0.022f + 0.72f * levels.house,
            ambientTint = Color(1f, 0.88f, 0.74f),
            spill = 0.50f * levels.picture,
            stage = 0.32f * levels.fixtures * (1f - 0.7f * levels.curtainEase),
            stageTint = Color(1f, 0.62f, 0.40f),
            sconce = 0.38f * levels.fixtures,
            sconceTint = Color(1f, 0.70f, 0.40f),
        )
        AuditoriumTheme.Modern -> common.copy(
            ambient = 0.018f + 0.62f * levels.house,
            ambientTint = Color(0.86f, 0.90f, 1f),
            spill = 0.55f * levels.picture,
            stage = 0.18f * levels.fixtures * (1f - 0.7f * levels.curtainEase),
            stageTint = Color(0.75f, 0.82f, 1f),
            sconce = 0.22f * (0.25f + 0.75f * levels.fixtures),
            sconceTint = Color(0.55f, 0.72f, 1f),
            sconceReach = h * 0.12f,
            sconces = geo.ledPools,
        )
        AuditoriumTheme.DriveIn -> common.copy(
            ambient = 0.10f + 0.30f * levels.house,
            ambientTint = Color(0.55f, 0.62f, 0.85f),
            spill = 0.45f * levels.picture,
            sconce = 0.40f * levels.fixtures,
            sconceTint = Color(1f, 0.66f, 0.32f),
            sconceReach = h * 0.22f,
            openAbove = geo.horizon,
        )
    }
}

private fun DrawScope.drawCurtains(geo: HallGeometry, pose: CurtainPose, levels: HallLevels, base: Color, sheen: Color, foot: Float, footTint: Color) {
    val o = geo.opening
    val top = o.top - geo.h * 0.004f
    val bottom = o.bottom
    val depth = mix(0.55f, 1f, levels.curtainEase)
    val folds = 13f
    val left = Path().apply { moveTo(o.left - 2f, top); lineTo(pose.lt, top); lineTo(pose.lb, bottom); lineTo(o.left - 2f, bottom); close() }
    val right = Path().apply { moveTo(o.right + 2f, top); lineTo(pose.rt, top); lineTo(pose.rb, bottom); lineTo(o.right + 2f, bottom); close() }
    // The leading edges throw a soft shadow onto the screen.
    val shadowW = geo.w * 0.012f
    val shadowA = 0.45f * (1f - 0.6f * levels.curtainEase)
    drawRect(
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = shadowA), Color.Transparent), startX = min(pose.lt, pose.lb), endX = min(pose.lt, pose.lb) + shadowW),
        topLeft = Offset(min(pose.lt, pose.lb), geo.screen.top), size = Size(shadowW, geo.screen.height),
    )
    val rEdge = max(pose.rt, pose.rb)
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = shadowA)), startX = rEdge - shadowW, endX = rEdge),
        topLeft = Offset(rEdge - shadowW, geo.screen.top), size = Size(shadowW, geo.screen.height),
    )
    drawPath(left, auditoriumVelvetBrush(o.left, pose.lt, pose.lb, top, bottom, folds, depth, base, sheen, foot, footTint, geo.px))
    drawPath(right, auditoriumVelvetBrush(o.right, pose.rt, pose.rb, top, bottom, folds, depth, base, sheen, foot, footTint, geo.px))
}

private fun DrawScope.drawSeats(geo: HallGeometry) {
    for (row in geo.seatRows) {
        for (s in row.seats) {
            translate(left = 0f, top = s.dy) {
                drawRoundRect(row.brush, topLeft = Offset(s.left, row.top), size = Size(s.width, row.height), cornerRadius = row.radius)
            }
        }
    }
}

/** Seat tops catching the screen and the house light, after the light map. */
private fun DrawScope.drawSeatRims(geo: HallGeometry, levels: HallLevels) {
    val a = 0.16f * levels.picture + 0.05f * levels.house
    if (a <= 0.002f) return
    for (row in geo.seatRows) {
        for (s in row.seats) {
            translate(left = 0f, top = s.dy) {
                drawRoundRect(
                    row.rim, topLeft = Offset(s.left, row.top), size = Size(s.width, row.height), cornerRadius = row.radius,
                    alpha = a, colorFilter = null, blendMode = BlendMode.Plus,
                )
            }
        }
    }
    for (car in geo.carRows) {
        drawPath(car.glint, Spill, alpha = 0.30f * levels.picture, style = Stroke(width = car.glintWidth, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawPalaceFront(geo: HallGeometry, levels: HallLevels, pose: CurtainPose) {
    val w = geo.w
    val h = geo.h
    val o = geo.opening
    val a = geo.archOuter
    drawCurtains(geo, pose, levels, PalaceVelvet, PalaceSheen, 0.55f, Color(1f, 0.6f, 0.3f))
    // Swagged valance with a gold-trimmed edge and tassels.
    drawPath(geo.valancePath, geo.valanceBrush)
    drawPath(geo.valanceEdge, Color(0xFFD9B566), style = Stroke(width = max(1.5f, h * 0.0035f)))
    for (j in geo.valanceJoins) drawOval(Color(0xFFC79F4E), topLeft = Offset(j.x - h * 0.004f, j.y), size = Size(h * 0.008f, h * 0.018f))
    // Gold art-deco proscenium: pilasters, the top beam and a stepped crown.
    drawRect(geo.goldLeft, topLeft = Offset(a.left, a.top), size = Size(o.left - a.left, a.bottom - a.top))
    drawRect(geo.goldRight, topLeft = Offset(o.right, a.top), size = Size(a.right - o.right, a.bottom - a.top))
    drawRect(geo.goldVertical, topLeft = Offset(a.left, a.top), size = Size(a.width, o.top - a.top))
    drawRect(geo.goldCrown, topLeft = geo.crown1.topLeft, size = geo.crown1.size)
    drawRect(geo.goldCrown, topLeft = geo.crown2.topLeft, size = geo.crown2.size)
    val line = max(1f, geo.px * 1.4f)
    val dark = Color(0xFF3A2405)
    for (x in listOf(a.left + (o.left - a.left) * 0.33f, a.left + (o.left - a.left) * 0.66f)) {
        drawLine(dark.copy(alpha = 0.6f), Offset(x, a.top + h * 0.01f), Offset(x, a.bottom), strokeWidth = line)
        val xr = w - x
        drawLine(dark.copy(alpha = 0.6f), Offset(xr, a.top + h * 0.01f), Offset(xr, a.bottom), strokeWidth = line)
    }
    drawLine(dark, Offset(o.left, o.top), Offset(o.right, o.top), strokeWidth = line)
    drawLine(dark, Offset(o.left, o.top), Offset(o.left, o.bottom), strokeWidth = line)
    drawLine(dark, Offset(o.right, o.top), Offset(o.right, o.bottom), strokeWidth = line)
    // Sunburst rays on the crown.
    val c = Offset(geo.cx, geo.crown1.bottom)
    for (i in -6..6) {
        val ang = PI.toFloat() * (0.5f + i * 0.075f)
        val len = w * 0.2f
        drawLine(
            dark.copy(alpha = 0.35f), c, Offset(c.x - cos(ang) * len, c.y - sin(ang) * len * 0.3f), strokeWidth = line,
        )
    }
    // The stage apron.
    drawRect(geo.stageBrush, topLeft = Offset(a.left, geo.stageY), size = Size(a.width, geo.apronY - geo.stageY))
    drawLine(Color(0xFFC9A04A), Offset(a.left, geo.stageY), Offset(a.right, geo.stageY), strokeWidth = max(1f, geo.px * 2f))
    // Sconce fixtures (a deco fan of gold), unlit.
    for (s in geo.sconces) drawSconceFixture(s, Color(0xFF8C6A2A))
    // Marquee sign body.
    val sr = geo.signRect
    drawRect(Color(0xFF1C0E08), topLeft = sr.topLeft, size = sr.size)
    drawRect(Color(0xFFB8913E), topLeft = sr.topLeft, size = sr.size, style = Stroke(width = max(1.5f, h * 0.003f)))
    drawSeats(geo)
}

private fun DrawScope.drawSconceFixture(s: Sconce, color: Color) {
    val r = s.size
    drawArc(color, 180f, 180f, true, topLeft = Offset(s.at.x - r, s.at.y - r), size = Size(2 * r, 2 * r))
    drawRect(color, topLeft = Offset(s.at.x - r * 0.18f, s.at.y), size = Size(r * 0.36f, r * 0.9f))
}

private fun DrawScope.drawPalaceGlow(geo: HallGeometry, levels: HallLevels, time: Float, signs: HallSigns) {
    val h = geo.h
    val f = levels.fixtures
    if (f > 0.002f) {
        geo.sconces.forEachIndexed { i, s ->
            // Warm light washing up the wall, then the frosted glass fan, then its gold ribs.
            scale(scaleX = 1f, scaleY = 2.2f, pivot = s.at) {
                drawCircle(geo.sconceHalo[i], radius = s.size * 7f, center = s.at, alpha = 0.8f * f, blendMode = BlendMode.Plus)
            }
            val r = s.size * 0.92f
            drawArc(Color(0xFFFFE2B5), 180f, 180f, true, topLeft = Offset(s.at.x - r, s.at.y - r), size = Size(2 * r, 2 * r), alpha = 0.92f * f)
            for (k in 1..4) {
                val ang = PI.toFloat() * (1f + k / 5f)
                drawLine(
                    Color(0xFF8C6A2A), s.at, Offset(s.at.x + cos(ang) * r, s.at.y + sin(ang) * r),
                    strokeWidth = max(1f, s.size * 0.08f), alpha = f,
                )
            }
        }
        // Footlights along the stage lip.
        val o = geo.opening
        for (k in 0..10) {
            val x = o.left + o.width * (k + 0.5f) / 11f
            drawCircle(Color(0xFFFFB36B), radius = h * 0.006f, center = Offset(x, geo.stageY), alpha = 0.35f * f * (1f - 0.6f * levels.curtainEase), blendMode = BlendMode.Plus)
        }
    }
    // Aisle lights stay on low through the film.
    for (row in geo.seatRows) for (p in row.aisleLights) {
        drawCircle(Color(0xFFFFC58A), radius = row.lightRadius * 3f, center = p, alpha = 0.10f + 0.10f * f, blendMode = BlendMode.Plus)
        drawCircle(Color(0xFFFFD9A8), radius = row.lightRadius, center = p, alpha = 0.55f + 0.3f * f)
    }
    // The marquee: a backlit cream panel, chaser bulbs, the title.
    val sl = levels.sign
    if (sl > 0.002f) {
        val sr = geo.signRect
        val inset = h * 0.008f
        val panel = Rect(sr.left + inset, sr.top + inset, sr.right - inset, sr.bottom - inset)
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFFFF4DC), Color(0xFFF1DDB4)), startY = panel.top, endY = panel.bottom),
            topLeft = panel.topLeft, size = panel.size, alpha = sl,
        )
        val ink = Color(0xFF3A0B0A)
        signs.small?.let { drawText(it, color = Color(0xFF8A5A1E), topLeft = Offset(geo.cx - it.size.width / 2f, panel.top + panel.height * 0.08f), alpha = sl) }
        signs.title?.let { drawText(it, color = ink, topLeft = Offset(geo.cx - it.size.width / 2f, panel.bottom - it.size.height - panel.height * 0.06f), alpha = sl) }
        val perSide = 18
        val bulbR = h * 0.0028f
        val chase = time * 6f
        fun bulb(p: Offset, idx: Int) {
            val on = 0.6f + 0.4f * (0.5f + 0.5f * sin(chase - idx * 0.9f))
            drawCircle(Color(0xFFFFC56E), radius = bulbR * 3.2f, center = p, alpha = 0.22f * sl * on, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFF1CF), radius = bulbR, center = p, alpha = sl * on)
        }
        var idx = 0
        for (k in 0..perSide) {
            val x = sr.left + sr.width * k / perSide
            bulb(Offset(x, sr.top + inset * 0.5f), idx++)
        }
        for (k in perSide downTo 0) {
            val x = sr.left + sr.width * k / perSide
            bulb(Offset(x, sr.bottom - inset * 0.5f), idx++)
        }
    }
}

private fun DrawScope.drawModernFront(geo: HallGeometry, levels: HallLevels, pose: CurtainPose) {
    val h = geo.h
    val o = geo.opening
    drawCurtains(geo, pose, levels, ModernCloth, ModernSheen, 0.25f, Color(0.6f, 0.72f, 1f))
    // A slim black soffit hides the curtain track.
    drawRect(Color(0xFF060607), topLeft = Offset(o.left - geo.band * 0.35f, o.top - h * 0.012f), size = Size(o.width + geo.band * 0.7f, h * 0.018f))
    drawSeats(geo)
}

private fun DrawScope.drawModernGlow(geo: HallGeometry, levels: HallLevels, signs: HallSigns) {
    val led = 0.18f + 0.82f * levels.fixtures.pow(0.7f)
    val glowW = max(2f, geo.px * 7f)
    val coreW = max(1f, geo.px * 1.6f)
    for ((a, b) in geo.ledLines) {
        drawLine(Led, a, b, strokeWidth = glowW * 2.2f, alpha = 0.05f * led, blendMode = BlendMode.Plus)
        drawLine(Led, a, b, strokeWidth = glowW, alpha = 0.14f * led, blendMode = BlendMode.Plus)
        drawLine(Led, a, b, strokeWidth = coreW, alpha = 0.95f * led)
    }
    for (row in geo.seatRows) for (p in row.aisleLights) {
        drawCircle(Led, radius = row.lightRadius * 3f, center = p, alpha = 0.10f + 0.08f * led, blendMode = BlendMode.Plus)
        drawCircle(Led, radius = row.lightRadius, center = p, alpha = 0.7f)
    }
    val sl = levels.sign
    if (sl > 0.002f) {
        val h = geo.h
        signs.small?.let { drawText(it, color = Led.copy(alpha = 0.75f), topLeft = Offset(geo.cx - it.size.width / 2f, h * 0.018f), alpha = sl) }
        val titleTop = h * 0.034f
        signs.title?.let {
            drawText(it, color = Color(0xFFF2F5FA), topLeft = Offset(geo.cx - it.size.width / 2f, titleTop), alpha = sl)
            val y = titleTop + it.size.height + h * 0.004f
            val half = it.size.width * 0.35f
            drawLine(Led, Offset(geo.cx - half, y), Offset(geo.cx + half, y), strokeWidth = max(1f, geo.px), alpha = 0.6f * sl)
        }
    }
}

private fun DrawScope.drawDriveInFront(geo: HallGeometry) {
    val h = geo.h
    // Lamp posts and the sign's pole (dark, above the horizon they stay silhouettes).
    for (p in geo.lampPosts) drawRect(Color(0xFF0B0C0F), topLeft = p.topLeft, size = p.size)
    for (s in geo.sconces) drawRect(Color(0xFF15171B), topLeft = Offset(s.at.x - s.size * 1.2f, s.at.y - s.size * 0.6f), size = Size(s.size * 2.4f, s.size * 0.8f))
    val sr = geo.signRect
    drawRect(Color(0xFF0C0D10), topLeft = Offset(sr.center.x - geo.px * 4f, sr.bottom), size = Size(geo.px * 8f, h * 0.93f - sr.bottom))
    drawRoundRect(Color(0xFF130E10), topLeft = sr.topLeft, size = sr.size, cornerRadius = CornerRadius(h * 0.012f))
    for (post in geo.speakerPosts) {
        drawRect(Color(0xFF0D0E11), topLeft = post.topLeft, size = post.size)
        drawRect(Color(0xFF14161A), topLeft = Offset(post.center.x - geo.px * 6f, post.top - geo.px * 7f), size = Size(geo.px * 12f, geo.px * 9f))
    }
    for (car in geo.carRows) {
        drawPath(car.body, car.brush)
        drawPath(car.glass, Color(0xFF1B2232))
        for (t in car.tails) drawRect(Color(0xFF3A0B0C), topLeft = t.topLeft, size = t.size)
    }
}

private fun DrawScope.drawDriveInGlow(geo: HallGeometry, levels: HallLevels, time: Float, signs: HallSigns) {
    val h = geo.h
    val f = levels.fixtures
    if (f > 0.002f) {
        geo.sconces.forEachIndexed { i, s ->
            drawCircle(geo.sconceHalo[i], radius = s.size * 7f, center = s.at, alpha = 0.9f * f, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFD29A), radius = s.size * 0.6f, center = s.at, alpha = f)
        }
    }
    // The sign: neon border, the brand, NOW SHOWING and a lit letter board with the title.
    val neon = 0.06f + 0.94f * levels.sign
    val sr = geo.signRect
    val pink = Color(0xFFFF4F8B)
    val corner = CornerRadius(h * 0.012f)
    drawRoundRect(pink, topLeft = sr.topLeft, size = sr.size, cornerRadius = corner, style = Stroke(width = h * 0.010f), alpha = 0.12f * neon, blendMode = BlendMode.Plus)
    drawRoundRect(pink, topLeft = sr.topLeft, size = sr.size, cornerRadius = corner, style = Stroke(width = max(1.5f, h * 0.0025f)), alpha = neon)
    signs.brand?.let { drawText(it, color = Color(0xFFFF7AA8), topLeft = Offset(sr.center.x - it.size.width / 2f, sr.top + sr.height * 0.06f), alpha = neon) }
    val board = Rect(sr.left + sr.width * 0.07f, sr.top + sr.height * 0.48f, sr.right - sr.width * 0.07f, sr.bottom - sr.height * 0.07f)
    signs.small?.let { drawText(it, color = Color(0xFFFFC2D6), topLeft = Offset(sr.center.x - it.size.width / 2f, board.top - it.size.height - h * 0.004f), alpha = neon) }
    drawRect(Color(0xFFF3ECDD), topLeft = board.topLeft, size = board.size, alpha = 0.08f + 0.85f * levels.sign)
    signs.title?.let { drawText(it, color = Color(0xFF111111), topLeft = Offset(sr.center.x - it.size.width / 2f, board.center.y - it.size.height / 2f), alpha = 0.1f + 0.9f * levels.sign) }
    // Fireflies over the field, more visible as it gets dark.
    val dark = 1f - 0.6f * levels.houseRaw
    for (fl in geo.fireflies) {
        val x = fl[0] + sin(time * fl[2] + fl[3]) * geo.w * 0.02f
        val y = fl[1] + cos(time * fl[2] * 0.77f + fl[3] * 1.3f) * h * 0.012f
        val blink = max(0f, sin(time * fl[4] + fl[3] * 2f)).pow(3)
        if (blink <= 0.01f) continue
        val c = Offset(x, y)
        drawCircle(Color(0xFFD8FF7A), radius = h * 0.006f, center = c, alpha = 0.18f * blink * dark, blendMode = BlendMode.Plus)
        drawCircle(Color(0xFFF2FFC2), radius = max(1f, h * 0.0016f), center = c, alpha = 0.9f * blink * dark, blendMode = BlendMode.Plus)
    }
}
