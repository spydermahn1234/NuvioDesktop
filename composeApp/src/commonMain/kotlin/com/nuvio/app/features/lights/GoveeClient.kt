package com.nuvio.app.features.lights

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/** What the account reports for one bulb; `online == false` means the cloud has lost it. */
internal data class GoveeDeviceState(
    val online: Boolean,
    val powerOn: Boolean,
    /** 1..100, or null when the device reports no brightness capability. */
    val brightness: Int?,
)

internal sealed interface GoveeResult<out T> {
    data class Ok<T>(val value: T) : GoveeResult<T>
    data class Unauthorized(val detail: String) : GoveeResult<Nothing>
    data class Failed(val detail: String) : GoveeResult<Nothing>
}

/**
 * Govee's cloud Platform API (v1). Cloud rather than LAN on purpose: the Wi-Fi bulbs this was built
 * against (H6008 and its siblings) never expose the LAN-control toggle, so the documented local UDP
 * API is not an option for them. The account key is issued from the Govee Home app and the limits
 * are generous for this use — 10k requests a day, 2 a second per device.
 *
 * Responses are read through [JsonElement] rather than typed models: the capability list is
 * open-ended and per-SKU, and this only ever needs three entries out of it.
 */
internal object GoveeClient {
    private const val BASE_URL = "https://openapi.api.govee.com/router/api/v1"
    private const val CALL_TIMEOUT_MS = 8_000L
    private const val CONNECT_TIMEOUT_MS = 5_000L
    private const val CAP_ON_OFF = "devices.capabilities.on_off"
    private const val CAP_RANGE = "devices.capabilities.range"
    private const val CAP_ONLINE = "devices.capabilities.online"
    private const val INSTANCE_POWER = "powerSwitch"
    private const val INSTANCE_BRIGHTNESS = "brightness"
    private const val CAP_TOGGLE = "devices.capabilities.toggle"
    private const val CAP_MOVIE_SETTING = "devices.capabilities.movie_setting"
    private const val INSTANCE_DREAMVIEW = "dreamViewToggle"
    private const val INSTANCE_MOVIE_MODE = "movieMode"

    private val log = Logger.withTag("Govee")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listDevices(apiKey: String): GoveeResult<List<GoveeDevice>> {
        val response = request(apiKey, "GET", "$BASE_URL/user/devices", body = "")
        return response.map { root ->
            val data = root["data"] as? JsonArray ?: JsonArray(emptyList())
            // fork:govee-dreamview - every device's capabilities go to the log, so a DreamView this misses can be seen.
            data.forEach { entry ->
                val obj = entry as? JsonObject ?: return@forEach
                log.i { "Govee ${obj.string("sku")} \"${obj.string("deviceName")}\": ${describeCapabilities(obj)}" }
            }
            parseDevices(data)
        }
    }

    /** fork:govee-dreamview - switches a device's Movie DreamView on (or off, where the capability has an off). */
    suspend fun setDreamView(apiKey: String, device: GoveeDevice, on: Boolean): GoveeResult<Unit> {
        val dreamView = device.dreamView ?: return GoveeResult.Failed("${device.name} has no DreamView")
        val value = if (on) dreamView.onValue else dreamView.offValue ?: return GoveeResult.Failed("${device.name}'s DreamView has no off")
        val payload: JsonElement = dreamView.field?.let { field -> buildJsonObject { put(field, value) } } ?: JsonPrimitive(value)
        return control(apiKey, device, dreamView.type, dreamView.instance, payload)
    }

    suspend fun readState(apiKey: String, device: GoveeDevice): GoveeResult<GoveeDeviceState> {
        val body = buildJsonObject {
            put("requestId", requestId())
            putJsonObject("payload") {
                put("sku", device.sku)
                put("device", device.deviceId)
            }
        }
        val response = request(apiKey, "POST", "$BASE_URL/device/state", body = body.toString())
        return response.map { root ->
            val capabilities = (root["payload"] as? JsonObject)?.get("capabilities") as? JsonArray
                ?: JsonArray(emptyList())
            var online = true
            var powerOn = false
            var brightness: Int? = null
            capabilities.forEach { entry ->
                val cap = entry as? JsonObject ?: return@forEach
                val value = (cap["state"] as? JsonObject)?.get("value") as? JsonPrimitive
                when (cap.string("type")) {
                    CAP_ONLINE -> online = value?.booleanOrNull ?: value?.content?.equals("true", ignoreCase = true) ?: true
                    CAP_ON_OFF -> if (cap.string("instance") == INSTANCE_POWER) {
                        powerOn = value?.intOrNull == 1 || value?.booleanOrNull == true
                    }
                    CAP_RANGE -> if (cap.string("instance") == INSTANCE_BRIGHTNESS) {
                        brightness = value?.intOrNull?.coerceIn(1, 100)
                    }
                }
            }
            GoveeDeviceState(online = online, powerOn = powerOn, brightness = brightness)
        }
    }

    /** The devices worth listing: anything with a power switch (keeps thermometers out), and anything with DreamView. */
    internal fun parseDevices(data: JsonArray): List<GoveeDevice> = data.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val sku = obj.string("sku") ?: return@mapNotNull null
        val deviceId = obj.string("device") ?: return@mapNotNull null
        val capabilities = obj["capabilities"] as? JsonArray ?: JsonArray(emptyList())
        val switchable = capabilities.any { cap -> (cap as? JsonObject)?.string("type") == CAP_ON_OFF }
        val dreamView = dreamViewOf(capabilities)
        if (!switchable && dreamView == null) return@mapNotNull null
        GoveeDevice(
            sku = sku,
            deviceId = deviceId,
            name = obj.string("deviceName")?.takeIf { it.isNotBlank() } ?: sku,
            dreamView = dreamView,
            switchable = switchable,
        )
    }

    /**
     * fork:govee-dreamview - `toggle`/`dreamViewToggle` when the device has it (the usual form, and the one with an
     * off), otherwise `movie_setting`/`movieMode`: an ENUM, or a STRUCT of one ENUM field, whose option named like
     * "Movie" is picked (the first option when none is).
     */
    internal fun dreamViewOf(capabilities: JsonArray): GoveeDreamView? {
        val caps = capabilities.mapNotNull { it as? JsonObject }
        caps.firstOrNull { it.string("instance") == INSTANCE_DREAMVIEW }?.let { cap ->
            val options = (cap["parameters"] as? JsonObject)?.options().orEmpty()
            return GoveeDreamView(
                type = cap.string("type") ?: CAP_TOGGLE,
                instance = INSTANCE_DREAMVIEW,
                onValue = options.firstOrNull { it.first.equals("on", ignoreCase = true) }?.second ?: 1,
                offValue = options.firstOrNull { it.first.equals("off", ignoreCase = true) }?.second ?: 0,
            )
        }
        val movie = caps.firstOrNull {
            it.string("instance") == INSTANCE_MOVIE_MODE || it.string("type") == CAP_MOVIE_SETTING
        } ?: return null
        val parameters = movie["parameters"] as? JsonObject ?: return null
        val fields = parameters["fields"] as? JsonArray
        val field = fields?.firstNotNullOfOrNull { f ->
            (f as? JsonObject)?.takeIf { (it["options"] as? JsonArray)?.isNotEmpty() == true }
        }
        val options = (field ?: parameters).options()
        if (options.isEmpty()) return null
        val pick = options.firstOrNull { it.first.contains("movie", ignoreCase = true) } ?: options.first()
        return GoveeDreamView(
            type = movie.string("type") ?: CAP_MOVIE_SETTING,
            instance = movie.string("instance") ?: INSTANCE_MOVIE_MODE,
            field = field?.string("fieldName"),
            onValue = pick.second,
            offValue = null,
        )
    }

    private fun JsonObject.options(): List<Pair<String, Int>> =
        (this["options"] as? JsonArray).orEmpty().mapNotNull { option ->
            val o = option as? JsonObject ?: return@mapNotNull null
            val value = (o["value"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            (o.string("name") ?: value.toString()) to value
        }

    private fun describeCapabilities(device: JsonObject): String =
        (device["capabilities"] as? JsonArray).orEmpty().joinToString { cap ->
            val o = cap as? JsonObject
            "${o?.string("type")?.removePrefix("devices.capabilities.")}/${o?.string("instance")}"
        }

    suspend fun setPower(apiKey: String, device: GoveeDevice, on: Boolean): GoveeResult<Unit> =
        control(apiKey, device, CAP_ON_OFF, INSTANCE_POWER, JsonPrimitive(if (on) 1 else 0))

    suspend fun setBrightness(apiKey: String, device: GoveeDevice, percent: Int): GoveeResult<Unit> =
        control(apiKey, device, CAP_RANGE, INSTANCE_BRIGHTNESS, JsonPrimitive(percent.coerceIn(1, 100)))

    private suspend fun control(
        apiKey: String,
        device: GoveeDevice,
        type: String,
        instance: String,
        value: JsonElement,
    ): GoveeResult<Unit> {
        val body = buildJsonObject {
            put("requestId", requestId())
            putJsonObject("payload") {
                put("sku", device.sku)
                put("device", device.deviceId)
                putJsonObject("capability") {
                    put("type", type)
                    put("instance", instance)
                    put("value", value)
                }
            }
        }
        return request(apiKey, "POST", "$BASE_URL/device/control", body = body.toString()).map { }
    }

    private suspend fun request(
        apiKey: String,
        method: String,
        url: String,
        body: String,
    ): GoveeResult<JsonObject> {
        val response = runCatching {
            httpRequestRaw(
                method = method,
                url = url,
                headers = mapOf(
                    "Govee-API-Key" to apiKey,
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                ),
                body = body,
                callTimeoutMs = CALL_TIMEOUT_MS,
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
            )
        }.onFailure { if (it is CancellationException) throw it }
            .getOrElse { error ->
                log.w { "Govee $method $url failed: ${error.message}" }
                return GoveeResult.Failed(error.message ?: "could not reach Govee")
            }

        if (response.status == 401 || response.status == 403) {
            return GoveeResult.Unauthorized("HTTP ${response.status}")
        }
        if (response.status == 429) {
            log.w { "Govee rate limited on $url" }
            return GoveeResult.Failed("rate limited (HTTP 429)")
        }
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
        if (response.status !in 200..299) {
            val message = root?.string("message") ?: "HTTP ${response.status}"
            log.w { "Govee $method $url → HTTP ${response.status}: $message" }
            return GoveeResult.Failed(message)
        }
        if (root == null) return GoveeResult.Failed("unreadable response")
        // Govee wraps its own status inside a 200 as well.
        val code = root["code"]?.let { (it as? JsonPrimitive)?.intOrNull }
        if (code != null && code != 200) {
            val message = root.string("message") ?: "Govee code $code"
            log.w { "Govee $method $url → code $code: $message" }
            return if (code == 401 || code == 403) GoveeResult.Unauthorized(message) else GoveeResult.Failed(message)
        }
        return GoveeResult.Ok(root)
    }

    private inline fun <T, R> GoveeResult<T>.map(transform: (T) -> R): GoveeResult<R> = when (this) {
        is GoveeResult.Ok -> GoveeResult.Ok(transform(value))
        is GoveeResult.Unauthorized -> this
        is GoveeResult.Failed -> this
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Govee only requires the id to be present and unique per request; a UUID-shaped one is polite. */
    private fun requestId(): String {
        val hex = "0123456789abcdef"
        val chars = CharArray(36) { index ->
            when (index) {
                8, 13, 18, 23 -> '-'
                14 -> '4'
                else -> hex[Random.nextInt(16)]
            }
        }
        return chars.concatToString()
    }
}
