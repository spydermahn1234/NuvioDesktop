// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.nuvio.app.features.home.extractHeroAccentColor
import com.nuvio.app.features.home.extractHeroPaletteColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * fork:shelf-home - v3 phase 2, the accent from the art (mockups V3Art, V3ArtRules; plan in
 * design-notes/home-shelves-v3.md). One colour from the backdrop on screen: its most vibrant hue (the app's hue-bucket
 * sampler that Home's hero and the music accent use, which skips greys, near-blacks and near-whites), else its main
 * colour; lifted in lightness, hue and saturation kept, until it reads on the scrim (3:1 against #06090A); eased over
 * 600 ms. It paints the slashes, the dots between facts, the focus outline, the trailer line and the Shelves underline
 * through LocalShelfPalette, in Dark only (Paper keeps its own accent). Setting: Colour from the art
 * (`ShelfExtrasSettings.artAccent`).
 */

internal object ShelfAccentRules {
    /** The mockups' scrim, rgba(6,9,10): what the accent has to read on. */
    val SCRIM = Color(0xFF06090A)
    // 6:1 rather than 3:1 so the colour reads as bright as the board's swatches (Sea glass #9FD0D8) on the scrim.
    const val MIN_CONTRAST = 6f
    const val FADE_MS = 600

    /** The sample decode's longest side: enough for a colour, cheap to decode. */
    const val SAMPLE_PX = 96

    /** Lightness is raised in steps this big until the colour reads. */
    private const val STEP = 0.02f

    /**
     * [color] (opaque) with its lightness raised just enough to reach [MIN_CONTRAST] against [SCRIM]; hue and
     * saturation are kept, so a dark teal becomes a sea-glass teal, not grey. Readable colours come back as they are.
     */
    fun readable(color: Color): Color {
        val opaque = color.copy(alpha = 1f)
        if (shelfContrast(opaque, SCRIM) >= MIN_CONTRAST) return opaque
        val (h, s, l) = toHsl(opaque)
        var lightness = l
        while (lightness < 1f) {
            lightness = min(1f, lightness + STEP)
            val lifted = fromHsl(h, s, lightness)
            if (shelfContrast(lifted, SCRIM) >= MIN_CONTRAST) return lifted
        }
        return Color.White
    }

    /** Hue (0..360), saturation and lightness (0..1) of an opaque colour. */
    fun toHsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        val d = hi - lo
        if (d <= 0f) return Triple(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (hi) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return Triple(h, s.coerceIn(0f, 1f), l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
        )
    }
}

/**
 * One readable colour per picture url, for the session. A failed picture is remembered as failed, so it is not
 * decoded again on every visit. Called from the UI's coroutines only.
 */
internal object ShelfAccents {
    private val colours = HashMap<String, Color>()
    private val failed = HashSet<String>()

    fun cached(url: String): Color? = colours[url]

    /** The picture's accent, or null (load error, nothing to take). A small decode, off the main thread. */
    suspend fun load(context: PlatformContext, url: String): Color? {
        colours[url]?.let { return it }
        if (url in failed) return null
        val colour = try {
            withContext(Dispatchers.Default) {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context).data(url).size(ShelfAccentRules.SAMPLE_PX, ShelfAccentRules.SAMPLE_PX).build(),
                )
                val image = (result as? SuccessResult)?.image ?: return@withContext null
                // Vibrant first; a picture with no strong hue gives its main colour (a grey for a grey picture).
                (extractHeroAccentColor(image) ?: extractHeroPaletteColors(image, 1).firstOrNull())
                    ?.let(ShelfAccentRules::readable)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            null
        }
        if (colour != null) colours[url] = colour else failed += url
        return colour
    }
}

/**
 * The accent for the picture at [imageUrl], eased over 600 ms. [fallback] (the theme's accent) when [enabled] is
 * off, with no picture, before the first colour is known, or when the picture gives none. While the next picture's
 * colour is worked out the previous one stays, so the accent never flashes back to [fallback] between pictures.
 */
@Composable
internal fun rememberShelfAccent(imageUrl: String?, enabled: Boolean, fallback: Color): State<Color> {
    val context = LocalPlatformContext.current
    var taken by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(imageUrl, enabled) {
        if (!enabled || imageUrl.isNullOrBlank()) {
            taken = null
            return@LaunchedEffect
        }
        taken = ShelfAccents.cached(imageUrl) ?: ShelfAccents.load(context, imageUrl)
    }
    return animateColorAsState(
        targetValue = taken ?: fallback,
        animationSpec = tween(ShelfAccentRules.FADE_MS),
        label = "shelfAccent",
    )
}
