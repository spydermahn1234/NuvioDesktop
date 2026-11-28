package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** fork:auditorium - SkSL only fails at runtime, so every hall shader is compiled and built here. */
class AuditoriumShadersTest {

    @Test
    fun everyShaderCompiles() {
        assertEquals(setOf("light", "beam", "haze", "velvet", "grain", "sky"), AUDITORIUM_SKSL_SOURCES.keys)
        for ((name, source) in AUDITORIUM_SKSL_SOURCES) {
            val effect = runCatching { RuntimeEffect.makeForShader(source) }
                .onFailure { throw AssertionError("SkSL '$name' does not compile: ${it.message}", it) }
                .getOrThrow()
            assertNotNull(effect, "SkSL '$name' gave no effect")
        }
    }

    @Test
    fun everyBrushBuildsItsShader() {
        val screen = Rect(403f, 140f, 1517f, 766f)
        val brushes: Map<String, Brush> = mapOf(
            "light" to auditoriumLightBrush(
                AuditoriumLightMap(
                    screen = screen,
                    leftLeadTop = 700f, leftLeadBottom = 710f, rightLeadTop = 1220f, rightLeadBottom = 1210f,
                    curtainTop = 92f, curtainBottom = 815f,
                    ambient = 0.4f, ambientTint = Color(1f, 0.88f, 0.74f),
                    spill = 0.3f, spillTint = Color(0.78f, 0.84f, 1f),
                    stage = 0.2f, stageTint = Color(1f, 0.6f, 0.4f), stageArea = Rect(270f, 92f, 1650f, 815f),
                    sconce = 0.3f, sconceTint = Color(1f, 0.7f, 0.4f), sconceReach = 170f,
                    sconces = listOf(Offset(88f, 380f), Offset(1832f, 380f)),
                    openAbove = -100_000f,
                ),
            ),
            "beam" to auditoriumBeamBrush(Offset(960f, -324f), screen, 1f, 3.5f, 1f, Color(1f, 0.94f, 0.84f)),
            "haze" to auditoriumHazeBrush(screen, 1f, 0.6f, 0.2f, 3.5f, 1f, Color.White),
            "velvet" to auditoriumVelvetBrush(270f, 960f, 960f, 92f, 815f, 13f, 0.8f, Color(0.46f, 0.035f, 0.055f), Color(1f, 0.42f, 0.38f), 0.5f, Color(1f, 0.6f, 0.3f), 1f),
            "grain" to auditoriumGrainBrush(3.5f, 0.045f, 0.42f, 1f),
            "sky" to auditoriumSkyBrush(848f, 3.5f, 1f),
        )
        for ((name, brush) in brushes) {
            assertTrue(brush is ShaderBrush, "$name should be a shader brush")
            assertNotNull((brush as ShaderBrush).createShader(Size(1920f, 1080f)), "$name built no shader")
        }
        // Value equality, so an unchanged frame is not a new brush.
        assertEquals(auditoriumGrainBrush(1f, 0.1f, 0.2f, 1f), auditoriumGrainBrush(1f, 0.1f, 0.2f, 1f))
    }
}
