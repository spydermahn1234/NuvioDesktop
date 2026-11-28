// fork:shelf-home paper-logos
package com.nuvio.app.features.home.shelves

import coil3.BitmapImage
import coil3.Image

private const val GRID = 48

internal actual fun sampleShelfLogo(image: Image): ShelfLogoSample? {
    val bitmap = (image as? BitmapImage)?.bitmap ?: return null
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    val rules = ShelfPaperLogoRules
    val counts = IntArray(4)
    repeat(GRID) { row ->
        val y = ((row + 0.5f) * bitmap.height / GRID).toInt().coerceIn(0, bitmap.height - 1)
        repeat(GRID) { column ->
            val x = ((column + 0.5f) * bitmap.width / GRID).toInt().coerceIn(0, bitmap.width - 1)
            val color = bitmap.getColor(x, y)
            val alpha = ((color ushr 24) and 0xFF) / 255f
            if (alpha < rules.MIN_ALPHA) return@repeat
            val kind = rules.classify(
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
            )
            counts[kind]++
        }
    }
    return rules.sample(
        pale = counts[rules.PALE],
        colour = counts[rules.COLOUR],
        dark = counts[rules.DARK],
        other = counts[rules.OTHER],
    )
}
