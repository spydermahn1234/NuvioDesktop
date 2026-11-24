package com.nuvio.app.features.lights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LightsPerContentTest {
    private val usual = LightsSettings(startAction = LightsStartAction.Dim, dimPercent = 30)

    @Test
    fun offUsesTheUsualStartAction() {
        val target = LightsSceneTarget(LightsStartAction.Dim, 30)
        assertEquals(target, usual.sceneFor(LightsContentKind.Film))
        assertEquals(target, usual.sceneFor(LightsContentKind.Tv))
    }

    @Test
    fun defaultsAreFilmsDarkAndTvHalf() {
        val on = usual.copy(perContent = true)
        assertEquals(LightsStartAction.TurnOff, on.sceneFor(LightsContentKind.Film)?.action)
        assertEquals(LightsSceneTarget(LightsStartAction.Dim, 50), on.sceneFor(LightsContentKind.Tv))
    }

    @Test
    fun unknownKindAndUsualFallBackToTheStartAction() {
        val on = usual.copy(perContent = true, filmScene = LightsContentScene.Usual)
        assertEquals(LightsSceneTarget(LightsStartAction.Dim, 30), on.sceneFor(null))
        assertEquals(LightsSceneTarget(LightsStartAction.Dim, 30), on.sceneFor(LightsContentKind.Film))
    }

    @Test
    fun leaveMeansNoScene() {
        val on = usual.copy(perContent = true, tvScene = LightsContentScene.Leave)
        assertNull(on.sceneFor(LightsContentKind.Tv))
    }

    @Test
    fun musicTurnsOnBeatLightsOnlyWithLightsOutAndPerContentOn() {
        assertFalse(LightsSettings(perContent = true).musicTurnsOnBeatLights)
        assertFalse(LightsSettings(enabled = true).musicTurnsOnBeatLights)
        assertTrue(LightsSettings(enabled = true, perContent = true).musicTurnsOnBeatLights)
        assertFalse(LightsSettings(enabled = true, perContent = true, musicBeatLights = false).musicTurnsOnBeatLights)
    }

    @Test
    fun storedSettingsKeepTheirScenes() {
        val stored = LightsStoredSettings(perContent = true, filmScene = LightsContentScene.Half, tvScene = LightsContentScene.Leave)
        assertEquals(stored, decodeLightsSettingsPayload(encodeLightsSettingsPayload(stored)))
        assertEquals(LightsStoredSettings(), decodeLightsSettingsPayload("""{"enabled":false}"""))
    }
}
