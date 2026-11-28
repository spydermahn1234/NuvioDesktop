package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import com.nuvio.app.core.storage.DesktopStorage

/** fork:auditorium - loads and keeps the settings (store `nuvio_auditorium`, key `settings`). Started in Main.kt. */
object AuditoriumDesktop {
    fun start() {
        // Off the UI thread: a store's first open can block for seconds on this PC (see DesktopStorage.preload).
        Thread({
            val store = DesktopStorage.store("nuvio_auditorium")
            AuditoriumSettingsRepository.load(store.getString("settings"))
            AuditoriumSettingsRepository.persist = { store.putString("settings", it) }
        }, "auditorium-settings").apply { isDaemon = true }.start()
    }
}
