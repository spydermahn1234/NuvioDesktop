package com.nuvio.app.features.localaddons

import com.nuvio.app.core.storage.DesktopStorage

// fork:local-addons - one store per computer (not ProfileScopedKey): the servers belong to the PC.
internal actual object LocalAddonStorage {
    private val store = DesktopStorage.store("nuvio_local_addons")

    actual fun getString(key: String): String? = store.getString(key)

    actual fun putString(key: String, value: String) {
        store.putString(key, value)
    }
}
