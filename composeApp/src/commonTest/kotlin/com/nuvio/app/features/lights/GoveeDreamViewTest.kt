package com.nuvio.app.features.lights

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** fork:govee-dreamview - reading Movie DreamView out of Govee's /user/devices answer. */
class GoveeDreamViewTest {
    private fun devices(json: String): JsonArray =
        Json.parseToJsonElement(json).jsonObject["data"]!!.jsonArray

    private val account = """
        {"code":200,"message":"success","data":[
          {"sku":"H6604","device":"AA:BB","deviceName":"AI Sync Box","capabilities":[
            {"type":"devices.capabilities.on_off","instance":"powerSwitch","parameters":{"dataType":"ENUM","options":[{"name":"on","value":1},{"name":"off","value":0}]}},
            {"type":"devices.capabilities.toggle","instance":"dreamViewToggle","parameters":{"dataType":"ENUM","options":[{"name":"on","value":1},{"name":"off","value":0}]}}
          ]},
          {"sku":"H66A0","device":"CC:DD","deviceName":"TV Backlight","capabilities":[
            {"type":"devices.capabilities.on_off","instance":"powerSwitch"},
            {"type":"devices.capabilities.movie_setting","instance":"movieMode","parameters":{"dataType":"STRUCT","fields":[
              {"fieldName":"movieMode","dataType":"ENUM","options":[{"name":"Game","value":0},{"name":"Movie","value":1}],"required":true}
            ]}}
          ]},
          {"sku":"H6076","device":"EE:FF","deviceName":"Floor Lamp Basic Left","capabilities":[
            {"type":"devices.capabilities.on_off","instance":"powerSwitch"},
            {"type":"devices.capabilities.range","instance":"brightness"}
          ]},
          {"sku":"H5179","device":"11:22","deviceName":"Thermometer","capabilities":[
            {"type":"devices.capabilities.property","instance":"sensorTemperature"}
          ]}
        ]}
    """.trimIndent()

    @Test
    fun findsTheDreamViewToggle() {
        val box = GoveeClient.parseDevices(devices(account)).single { it.sku == "H6604" }
        assertEquals(GoveeDreamView("devices.capabilities.toggle", "dreamViewToggle", null, 1, 0), box.dreamView)
        assertTrue(box.switchable)
    }

    @Test
    fun findsMovieModeAndPicksTheMovieOption() {
        val backlight = GoveeClient.parseDevices(devices(account)).single { it.sku == "H66A0" }
        assertEquals(GoveeDreamView("devices.capabilities.movie_setting", "movieMode", "movieMode", 1, null), backlight.dreamView)
    }

    @Test
    fun keepsPlainLightsAndDropsSensors() {
        val parsed = GoveeClient.parseDevices(devices(account))
        assertNull(parsed.single { it.sku == "H6076" }.dreamView)
        assertFalse(parsed.any { it.sku == "H5179" })
    }

    @Test
    fun aDreamViewDeviceIsNeverDimmed() {
        val parsed = GoveeClient.parseDevices(devices(account))
        val settings = LightsSettings(
            goveeApiKey = "key",
            goveeDevices = parsed,
            goveeSelectedDeviceIds = setOf("AA:BB", "EE:FF"),
            goveeDreamViewDeviceIds = setOf("AA:BB"),
        )
        assertEquals(listOf("EE:FF"), settings.goveeActiveDevices.map { it.deviceId })
        assertEquals(listOf("AA:BB"), settings.goveeDreamViewDevices.map { it.deviceId })
    }
}
