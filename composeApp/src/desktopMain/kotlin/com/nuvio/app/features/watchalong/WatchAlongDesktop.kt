package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.desktop.NativeAudioDuck
import com.nuvio.app.features.player.desktop.NativePlayerBridge

internal actual object WatchAlongCacheStorage {
    private const val KEY = "tracks"
    private val store by lazy { DesktopStorage.store("nuvio_watch_along_cache") }

    actual fun load(): String? = store.getString(KEY)

    actual fun save(json: String?) {
        store.putString(KEY, json)
    }
}

/**
 * fork:watch-along - the desktop half: loads the settings and draws on the player. Over a playing video the mpv
 * surface covers the Compose layer, so the cards and the pause panel are drawn by the player's HUD
 * (`window.nuvioShowAlongCard` / `window.nuvioSetAlongPanel` in controls.js), as the live-score cards are.
 * Started from Main.kt beside the live scores.
 */
object WatchAlongDesktop {
    fun start() {
        WatchAlongLive.hud = { script ->
            val handles = runCatching { NativeAudioDuck.liveHandles() }.getOrDefault(emptyList())
            handles.forEach { handle -> runCatching { NativePlayerBridge.runJavaScript(handle, script) } }
        }
        // Off the UI thread: a store's first open can block for seconds on this PC (see DesktopStorage.preload).
        Thread({
            val store = DesktopStorage.store("nuvio_watch_along")
            WatchAlongSettingsRepository.load(store.getString("settings"))
            WatchAlongSettingsRepository.persist = { store.putString("settings", it) }
        }, "watch-along-settings").apply { isDaemon = true }.start()
    }
}
