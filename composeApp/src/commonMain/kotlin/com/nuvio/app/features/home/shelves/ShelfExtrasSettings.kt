package com.nuvio.app.features.home.shelves

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * fork:shelf-home - the v3 switches (plan in design-notes/home-shelves-v3.md), per profile in the shelf home's own
 * store (`nuvio_shelf_home`, key "extras"). Defaults as Nick chose on Nov 27: the art, its colour and the evening home
 * on; trailers, theme tunes and lights off until he has tried them.
 */
data class ShelfExtrasSettings(
    /** Phase 1: backdrops without the title burned in first, several per title. */
    val textlessArt: Boolean = true,
    /** Phase 2: the accent colour taken from each backdrop. */
    val artAccent: Boolean = true,
    /** Phase 4: "Fits before bed" leads Home from [eveningStartMinute]. */
    val eveningHome: Boolean = true,
    /** Minutes after midnight the evening home starts (21:00 by default, changeable). */
    val eveningStartMinute: Int = 21 * 60,
    /** Phase 5: a muted trailer behind the art after 4 s still on a title. */
    val trailers: Boolean = false,
    /** Phase 8: a show's theme quietly while it is focused. */
    val themeTunes: Boolean = false,
    /** Phase 7: the chosen bulbs take the art's colour while browsing. */
    val artLights: Boolean = false,
    /** fork:shelf-home live-tv: a Live TV favourite plays, muted, beside the art after 0.7 s on it (one stream). */
    val livePreview: Boolean = false,
)

/** The stored form: one `key=value` per line; unknown keys and bad values are skipped (defaults kept). */
internal object ShelfExtrasCodec {
    fun encode(s: ShelfExtrasSettings): String = listOf(
        "textlessArt=${s.textlessArt}",
        "artAccent=${s.artAccent}",
        "eveningHome=${s.eveningHome}",
        "eveningStartMinute=${s.eveningStartMinute}",
        "trailers=${s.trailers}",
        "themeTunes=${s.themeTunes}",
        "artLights=${s.artLights}",
        "livePreview=${s.livePreview}",
    ).joinToString("\n")

    fun decode(text: String?): ShelfExtrasSettings {
        var s = ShelfExtrasSettings()
        if (text.isNullOrBlank()) return s
        for (line in text.lines()) {
            val key = line.substringBefore('=', "").trim()
            val value = line.substringAfter('=', "").trim()
            val bool = value.toBooleanStrictOrNull()
            s = when (key) {
                "textlessArt" -> bool?.let { s.copy(textlessArt = it) } ?: s
                "artAccent" -> bool?.let { s.copy(artAccent = it) } ?: s
                "eveningHome" -> bool?.let { s.copy(eveningHome = it) } ?: s
                "eveningStartMinute" -> value.toIntOrNull()?.takeIf { it in 0 until 24 * 60 }
                    ?.let { s.copy(eveningStartMinute = it) } ?: s
                "trailers" -> bool?.let { s.copy(trailers = it) } ?: s
                "themeTunes" -> bool?.let { s.copy(themeTunes = it) } ?: s
                "artLights" -> bool?.let { s.copy(artLights = it) } ?: s
                "livePreview" -> bool?.let { s.copy(livePreview = it) } ?: s
                else -> s
            }
        }
        return s
    }
}

object ShelfExtrasRepository {
    private val _state = MutableStateFlow(ShelfExtrasSettings())
    val state: StateFlow<ShelfExtrasSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _state.value = runCatching { ShelfExtrasCodec.decode(ShelfHomeStorage.getString(KEY)) }
            .getOrDefault(ShelfExtrasSettings())
    }

    /** Re-read after a profile switch (the store is profile-scoped). */
    fun reload() {
        loaded = false
        ensureLoaded()
    }

    fun update(change: (ShelfExtrasSettings) -> ShelfExtrasSettings) {
        ensureLoaded()
        val next = change(_state.value)
        if (next == _state.value) return
        _state.value = next
        ShelfHomeStorage.putString(KEY, ShelfExtrasCodec.encode(next))
    }

    private const val KEY = "extras"
}
