package com.nuvio.app.features.localaddons

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/* fork:local-addons - settings for the built-in servers. One set per computer (store
 * `nuvio_local_addons`, not per profile): the servers belong to the PC, not to whoever is signed in. */

internal expect object LocalAddonStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** The running servers; the desktop build implements it, other builds have no bundle. */
internal expect object LocalAddonHost {
    /** True when this build carries the bundle (node, redis and both servers). */
    val bundleFound: Boolean
    val statuses: StateFlow<Map<LocalAddonServer, LocalAddonStatus>>
    /** Start or stop the servers to match the settings; restarts running ones so new keys apply. */
    fun apply()
    /** Folder holding each server's log, to show in Settings. */
    val logFolder: String?
}

object LocalAddonSettingsRepository {
    private val _state = MutableStateFlow(LocalAddonSettings())
    val state: StateFlow<LocalAddonSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _state.value = runCatching {
            LocalAddonSettings(
                enabled = LocalAddonStorage.getString("enabled") == "true",
                keys = LocalAddonKeys(
                    tmdb = LocalAddonStorage.getString("apikey.tmdb").orEmpty(),
                    tvdb = LocalAddonStorage.getString("apikey.tvdb").orEmpty(),
                    fanart = LocalAddonStorage.getString("apikey.fanart").orEmpty(),
                    mdblist = LocalAddonStorage.getString("apikey.mdblist").orEmpty(),
                    gemini = LocalAddonStorage.getString("apikey.gemini").orEmpty(),
                    rpdb = LocalAddonStorage.getString("apikey.rpdb").orEmpty(),
                ),
                ports = LocalAddonServer.entries.mapNotNull { server ->
                    LocalAddonStorage.getString("port.${server.name}")?.toIntOrNull()
                        ?.takeIf { it in 1024..65535 }
                        ?.let { server to it }
                }.toMap(),
                aioStreamsSecret = LocalAddonStorage.getString("aiostreamsSecret")
                    ?.takeIf(::isValidAioStreamsSecret)
                    .orEmpty(),
            )
        }.getOrDefault(LocalAddonSettings())
    }

    fun setEnabled(on: Boolean) {
        update { it.copy(enabled = on) }
        LocalAddonStorage.putString("enabled", on.toString())
    }

    fun setKeys(keys: LocalAddonKeys) {
        update { it.copy(keys = keys) }
        LocalAddonStorage.putString("apikey.tmdb", keys.tmdb.trim())
        LocalAddonStorage.putString("apikey.tvdb", keys.tvdb.trim())
        LocalAddonStorage.putString("apikey.fanart", keys.fanart.trim())
        LocalAddonStorage.putString("apikey.mdblist", keys.mdblist.trim())
        LocalAddonStorage.putString("apikey.gemini", keys.gemini.trim())
        LocalAddonStorage.putString("apikey.rpdb", keys.rpdb.trim())
    }

    internal fun savePort(server: LocalAddonServer, port: Int) {
        update { it.copy(ports = it.ports + (server to port)) }
        LocalAddonStorage.putString("port.${server.name}", port.toString())
    }

    internal fun saveSecret(secret: String) {
        require(isValidAioStreamsSecret(secret))
        update { it.copy(aioStreamsSecret = secret) }
        LocalAddonStorage.putString("aiostreamsSecret", secret)
    }

    private fun update(change: (LocalAddonSettings) -> LocalAddonSettings) {
        ensureLoaded()
        _state.value = change(_state.value)
    }
}
