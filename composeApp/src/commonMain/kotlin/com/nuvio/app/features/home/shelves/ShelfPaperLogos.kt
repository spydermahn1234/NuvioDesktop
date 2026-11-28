// fork:shelf-home paper-logos
package com.nuvio.app.features.home.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import coil3.Image
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * fork:shelf-home paper-logos - in the Paper look, a clearlogo that is white or pale grey all over (Ellie & Natasia,
 * Nova, Mourinho) all but vanishes on the cream page. Such a logo is drawn in Paper's ink instead, like printed type;
 * a logo with real colour in it (Sally4Ever's red 4) keeps its own colours. Nick chose this on Nov 28 ("ink only
 * light logos"). Dark is untouched.
 */

/** What the logo's pixels are made of: shares of its visible (non-transparent) pixels. */
internal data class ShelfLogoSample(
    /** Visible pixels that are pale and colourless (white, light grey). */
    val paleShare: Float,
    /** Visible pixels with a real colour (saturated, not near-black). */
    val colourShare: Float,
    /** Visible pixels that are dark (a shadow, black type). */
    val darkShare: Float,
)

internal object ShelfPaperLogoRules {
    /** At least this share pale... */
    const val MIN_PALE = 0.55f

    /** ...and no more than this share coloured, or the logo keeps its colours. */
    const val MAX_COLOUR = 0.06f

    /** A pixel counts once at least this opaque. */
    const val MIN_ALPHA = 0.5f

    /** Sample decode size: enough to judge, cheap to decode. */
    const val SAMPLE_PX = 128

    /** True when the logo should be drawn in ink on Paper. */
    fun inks(sample: ShelfLogoSample?): Boolean =
        sample != null && sample.paleShare >= MIN_PALE && sample.colourShare <= MAX_COLOUR

    /** One pixel's kind from its channels (0..1): pale, coloured, dark or none of those (mid grey). */
    fun classify(red: Float, green: Float, blue: Float): Int {
        val hi = maxOf(red, green, blue)
        val lo = minOf(red, green, blue)
        val sat = if (hi <= 0f) 0f else (hi - lo) / hi
        return when {
            hi < 0.25f -> DARK
            sat >= 0.30f && hi >= 0.25f -> COLOUR
            hi >= 0.70f && sat < 0.18f -> PALE
            else -> OTHER
        }
    }

    /** The sample from visible pixels' kinds (counts); null with nothing visible. */
    fun sample(pale: Int, colour: Int, dark: Int, other: Int): ShelfLogoSample? {
        val visible = pale + colour + dark + other
        if (visible <= 0) return null
        return ShelfLogoSample(pale / visible.toFloat(), colour / visible.toFloat(), dark / visible.toFloat())
    }

    const val PALE = 0
    const val COLOUR = 1
    const val DARK = 2
    const val OTHER = 3
}

/** The image's pixels sorted by [ShelfPaperLogoRules.classify]; null when it cannot be read. */
internal expect fun sampleShelfLogo(image: Image): ShelfLogoSample?

/** Whether each logo url is pale, for the session (a failed one counts as not pale and is not asked again). */
internal object ShelfPaperLogos {
    private val known = HashMap<String, Boolean>()

    fun cached(url: String): Boolean? = known[url]

    suspend fun load(context: PlatformContext, url: String): Boolean {
        known[url]?.let { return it }
        val inks = try {
            withContext(Dispatchers.Default) {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context).data(url).size(ShelfPaperLogoRules.SAMPLE_PX, ShelfPaperLogoRules.SAMPLE_PX).build(),
                )
                val image = (result as? SuccessResult)?.image ?: return@withContext false
                ShelfPaperLogoRules.inks(sampleShelfLogo(image))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            false
        }
        known[url] = inks
        return inks
    }
}

/**
 * The colour filter for the logo at [url]: Paper's ink for a pale logo in the Paper look, else [otherwise] (the row's
 * greying, or none). Dark never inks.
 */
@Composable
internal fun rememberPaperLogoFilter(url: String?, otherwise: ColorFilter?): ColorFilter? {
    val palette = LocalShelfPalette.current
    val paper = palette.backdropAlpha <= 0f
    val context = LocalPlatformContext.current
    var inks by remember(url) { mutableStateOf(url?.let(ShelfPaperLogos::cached) ?: false) }
    LaunchedEffect(url, paper) {
        if (!paper || url == null || ShelfPaperLogos.cached(url) != null) return@LaunchedEffect
        inks = ShelfPaperLogos.load(context, url)
    }
    val ink = palette.ink
    val tint = remember(ink) { ColorFilter.tint(ink, BlendMode.SrcIn) }
    return if (paper && inks) tint else otherwise
}
