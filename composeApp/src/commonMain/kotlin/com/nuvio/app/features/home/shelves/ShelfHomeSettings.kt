package com.nuvio.app.features.home.shelves

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * fork:shelf-home - the Kodi-style home's settings, per profile (store `nuvio_shelf_home`).
 * Kept out of HomeCatalogSettings on purpose: that payload is upstream's and is synced, so the
 * fork's switch lives in its own store and upstream's home never sees it. Plan in
 * design-notes/home-shelves.md.
 */

internal expect object ShelfHomeStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

data class ShelfHomeSettings(
    /** Off by default: with it off the classic home runs exactly as upstream's. */
    val enabled: Boolean = false,
)

object ShelfHomeSettingsRepository {
    private val _state = MutableStateFlow(ShelfHomeSettings())
    val state: StateFlow<ShelfHomeSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _state.value = runCatching {
            ShelfHomeSettings(
                enabled = ShelfHomeStorage.getString("enabled") == "true",
            )
        }.getOrDefault(ShelfHomeSettings())
    }

    /** Re-read after a profile switch (the store is profile-scoped). */
    fun reload() {
        loaded = false
        ensureLoaded()
    }

    fun setEnabled(on: Boolean) {
        ensureLoaded()
        if (_state.value.enabled == on) return
        _state.value = _state.value.copy(enabled = on)
        ShelfHomeStorage.putString("enabled", on.toString())
    }
}
