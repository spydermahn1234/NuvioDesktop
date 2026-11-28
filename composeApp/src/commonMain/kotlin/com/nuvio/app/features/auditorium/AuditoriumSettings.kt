package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium (a rendered cinema hall around Play), not in UmbraProjects. See CLAUDE.md.

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which hall is drawn. [Random] picks one per film, the same one every time for that film. */
@Serializable
enum class AuditoriumThemeChoice(val label: String) {
    Random("A different hall per film"),
    Palace("Picture palace"),
    Modern("Modern big screen"),
    DriveIn("Drive-in"),
}

/** How long the sequences take. */
@Serializable
enum class AuditoriumLength(val label: String, val scale: Float) {
    Short("Short", 0.55f),
    Normal("Normal", 1f),
}

/** fork:auditorium - Settings > Pre-show > The Auditorium. Kept by AuditoriumDesktop (store `nuvio_auditorium`). */
@Serializable
data class AuditoriumSettings(
    /** Off by default, like every fork feature upstream might merge. */
    val enabled: Boolean = false,
    val theme: AuditoriumThemeChoice = AuditoriumThemeChoice.Random,
    /** The pre-show (trailers, clips, trivia) plays on the hall's screen instead of full screen. */
    val preshowInHall: Boolean = true,
    /** When a film plays to its end, the camera pulls back out, the curtains close and the house lights come up. */
    val closing: Boolean = true,
    /** Films only by default; on, a fresh series episode gets the hall too. */
    val includeSeries: Boolean = false,
    val length: AuditoriumLength = AuditoriumLength.Normal,
)

object AuditoriumSettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(AuditoriumSettings())
    val state: StateFlow<AuditoriumSettings> = _state.asStateFlow()

    /** Set by the desktop side: where the settings are kept. */
    internal var persist: ((String) -> Unit)? = null

    internal fun load(payload: String?) {
        if (payload.isNullOrBlank()) return
        runCatching { json.decodeFromString(AuditoriumSettings.serializer(), payload) }.onSuccess { _state.value = it }
    }

    private fun change(edit: (AuditoriumSettings) -> AuditoriumSettings) {
        _state.update(edit)
        persist?.invoke(json.encodeToString(AuditoriumSettings.serializer(), _state.value))
    }

    fun setEnabled(value: Boolean) = change { it.copy(enabled = value) }
    fun setTheme(value: AuditoriumThemeChoice) = change { it.copy(theme = value) }
    fun setPreshowInHall(value: Boolean) = change { it.copy(preshowInHall = value) }
    fun setClosing(value: Boolean) = change { it.copy(closing = value) }
    fun setIncludeSeries(value: Boolean) = change { it.copy(includeSeries = value) }
    fun setLength(value: AuditoriumLength) = change { it.copy(length = value) }
}
