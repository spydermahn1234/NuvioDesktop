package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** fork:watch-along - Settings > Pre-show > Watch-along. Kept by WatchAlongDesktop (store `nuvio_watch_along`). */
@Serializable
data class WatchAlongSettings(
    /** Off by default, like every fork feature upstream might merge. */
    val enabled: Boolean = false,
    /** Cards over the film on the TV; off leaves the TV clean and the phone (and the pause panel) still have it all. */
    val tvCards: Boolean = true,
    /** The "around now" panel beside the pause screen. */
    val pausePanel: Boolean = true,
    val density: AlongDensity = AlongDensity.Normal,
    /** Ask the AI provider (Discover > AI) to write the track; off, or with no provider, the offline track is built. */
    val useAi: Boolean = true,
)

object WatchAlongSettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(WatchAlongSettings())
    val state: StateFlow<WatchAlongSettings> = _state.asStateFlow()

    /** Set by the desktop side: where the settings are kept. */
    internal var persist: ((String) -> Unit)? = null

    internal fun load(payload: String?) {
        if (payload.isNullOrBlank()) return
        runCatching { json.decodeFromString(WatchAlongSettings.serializer(), payload) }.onSuccess { _state.value = it }
    }

    private fun change(edit: (WatchAlongSettings) -> WatchAlongSettings) {
        _state.update(edit)
        persist?.invoke(json.encodeToString(WatchAlongSettings.serializer(), _state.value))
    }

    fun setEnabled(value: Boolean) = change { it.copy(enabled = value) }
    fun setTvCards(value: Boolean) = change { it.copy(tvCards = value) }
    fun setPausePanel(value: Boolean) = change { it.copy(pausePanel = value) }
    fun setDensity(value: AlongDensity) = change { it.copy(density = value) }
    fun setUseAi(value: Boolean) = change { it.copy(useAi = value) }
}
