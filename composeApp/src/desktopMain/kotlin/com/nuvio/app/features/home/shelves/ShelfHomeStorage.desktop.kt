package com.nuvio.app.features.home.shelves

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

// fork:shelf-home
internal actual object ShelfHomeStorage {
    private val store = DesktopStorage.store("nuvio_shelf_home")

    actual fun getString(key: String): String? = store.getString(ProfileScopedKey.of(key))

    actual fun putString(key: String, value: String) {
        store.putString(ProfileScopedKey.of(key), value)
    }
}
