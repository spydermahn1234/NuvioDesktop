package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * fork:auditorium - the hall at 1920x1080 for every theme at three moments, saved to build/test-screenshots (the
 * screen gallery CI collects) for review: lights up with the curtains closed, settled, and mid-glide. No network in
 * tests, so the screen shows the title card (artUrl = null).
 */
@OptIn(ExperimentalComposeUiApi::class)
class AuditoriumHallRenderTest {

    private val width = 1920
    private val height = 1080

    private fun render(theme: AuditoriumTheme, frame: AuditoriumFrame, name: String): Bitmap {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            AuditoriumHall(frame = frame, theme = theme, title = "The Grand Budapest Hotel", artUrl = null)
        }
        try {
            scene.render(0L)
            // A second frame so the hall's clock has ticked (dust, grain and stars have moved on).
            val image = scene.render(1_500_000_000L)
            runCatching {
                val dir = File("build/test-screenshots").apply { mkdirs() }
                val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode failed")
                File(dir, name).writeBytes(data.bytes)
            }
            return Bitmap().apply {
                allocN32Pixels(image.width, image.height)
                image.readPixels(this)
            }
        } finally {
            scene.close()
        }
    }

    private fun luma(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    @Test
    fun rendersEveryThemeAtThreeMoments() {
        val moments = listOf(
            "lightsup" to AuditoriumRules.openingFrame(500, AuditoriumLength.Normal),
            "settled" to AuditoriumRules.openingFrame(6000, AuditoriumLength.Normal),
            "glide" to AuditoriumRules.glideFrame(800, AuditoriumLength.Normal),
        )
        for (theme in AuditoriumTheme.entries) {
            for ((moment, frame) in moments) {
                val bitmap = render(theme, frame, "auditorium_${theme.name.lowercase()}_$moment.png")
                if (moment != "settled") continue
                // The settled hall must not be black: the title card is on the screen and the screen lights the room.
                var lit = 0
                var total = 0
                var brightest = 0
                for (y in 0 until height step 8) for (x in 0 until width step 8) {
                    val l = luma(bitmap.getColor(x, y))
                    if (l > 10) lit++
                    if (l > brightest) brightest = l
                    total++
                }
                assertTrue(brightest > 60, "$theme settled: brightest sample is only $brightest")
                assertTrue(lit > total / 20, "$theme settled: only $lit of $total samples are above black")
                // The screen's centre shows the card, not black.
                val screen = AuditoriumRules.seatScreen(width.toFloat(), height.toFloat())
                val cx = screen.centerX.toInt()
                val cy = screen.centerY.toInt()
                val centre = (-40..40 step 4).sumOf { dx -> (-20..20 step 4).sumOf { dy -> luma(bitmap.getColor(cx + dx, cy + dy)) } }
                assertTrue(centre > 0, "$theme settled: the screen's centre is black")
            }
        }
    }
}
