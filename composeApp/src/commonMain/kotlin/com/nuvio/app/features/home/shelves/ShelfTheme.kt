// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * fork:shelf-home - phase 11, the shelf home's own look (design-notes/home-shelves.md). Dark is the
 * look the shelf home was built with (white type over a darkened backdrop); Paper is the mockup
 * "Films - Spotlight, Paper theme": warm paper, near-black ink, no backdrop picture, so the artwork
 * is the only colour and the accent is kept for slashes, bars and focus. Only the shelf home reads
 * this; the rest of the app keeps the app theme.
 */

enum class ShelfThemeMode { Dark, Paper }

/** Every colour the shelf home and its views paint with. */
data class ShelfPalette(
    /** Behind everything, under the backdrop picture. */
    val page: Color,
    /** How strongly the focused title's backdrop shows (0 = not drawn). */
    val backdropAlpha: Float,
    /** The left and bottom scrims' colour; ShelfHome keeps its own alpha stops and copies this. */
    val scrim: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkDim: Color,
    /** The right-hand panel with the clock and the shelf names. */
    val panel: Color,
    /** The hairline between shelf names. */
    val divider: Color,
    /** The focused shelf name's fill and its text. */
    val focusFill: Color,
    val focusText: Color,
    /** Bars, rings, markers and fills: the theme accent as it is. */
    val accent: Color,
    /** The accent used as text (slashes, the focused tab, the Ledger's focused title): readable on [page]. */
    val accentInk: Color,
    /** The Ledger's focused-row tint. */
    val rowFocus: Color,
    /** A poster with no picture, and its title. */
    val placeholder: Color,
    val placeholderInk: Color,
    /** The Wall's empty tile, and the wash over its unfocused posters. */
    val tile: Color,
    val posterDim: Color,
    /** The "View <shelf> as" card. */
    val pickerScrim: Color,
    val pickerFill: Color,
    val pickerBorder: Color,
    val pickerRow: Color,
    val pickerRowHover: Color,
    val pickerRowBorder: Color,
    val pickerTitle: Color,
    val pickerMuted: Color,
    /** The focused poster's outline: white, or the art's colour when "Colour from the art" is on (V3Art). */
    val ring: Color = ink,
)

internal object ShelfPaper {
    /** The mockup's paper; [WHITE] and [STONE] are the other shades it offered. */
    val PAGE = Color(0xFFF4F1EA)
    val WHITE = Color(0xFFFFFFFF)
    val STONE = Color(0xFFECE7DC)
    val INK = Color(0xFF1A1A1A)
    /** Darker than the mockup's muted #8A857C, which is only 3.2:1 on the paper; this one passes 4.5:1. */
    val INK_SOFT = Color(0xFF4A4740)
    val INK_DIM = Color(0xFF6B665E)
    /** The mockup's muted ink, kept for lines and edges only (never text). */
    val RULE = Color(0xFF8A857C)
}

/** WCAG contrast ratio of two opaque colours (1..21). */
internal fun shelfContrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * [color] darkened toward [ink] just enough to read at [ratio] on [background]: a gold or pale
 * accent stays its own hue but is legible as type on paper. Already-readable colours come back as they are.
 */
internal fun shelfReadableOn(color: Color, background: Color, ink: Color, ratio: Float = 4.5f): Color {
    val opaque = color.copy(alpha = 1f)
    if (shelfContrast(opaque, background) >= ratio) return opaque
    var step = 1
    while (step <= 20) {
        val mixed = lerp(opaque, ink, step / 20f)
        if (shelfContrast(mixed, background) >= ratio) return mixed
        step++
    }
    return ink
}

fun shelfPalette(
    mode: ShelfThemeMode,
    accent: Color,
    onAccent: Color,
    /** The app theme's text colours, which the view picker uses in Dark (as it did before themes). */
    textPrimary: Color = Color.White,
    textMuted: Color = Color.White.copy(alpha = 0.55f),
): ShelfPalette = when (mode) {
    // Exactly the values ShelfHome.kt and the views used before phase 11.
    ShelfThemeMode.Dark -> ShelfPalette(
        page = Color.Black,
        backdropAlpha = 1f,
        scrim = Color.Black,
        ink = Color.White,
        inkSoft = Color.White.copy(alpha = 0.78f),
        inkDim = Color.White.copy(alpha = 0.55f),
        panel = Color.Black.copy(alpha = 0.72f),
        divider = Color.White.copy(alpha = 0.10f),
        focusFill = accent,
        focusText = onAccent,
        accent = accent,
        accentInk = accent,
        rowFocus = Color.White.copy(alpha = 0.06f),
        placeholder = Color.White.copy(alpha = 0.08f),
        placeholderInk = Color.White.copy(alpha = 0.85f),
        tile = Color(0xFF1B1B1F),
        posterDim = Color.Black.copy(alpha = 0.18f),
        pickerScrim = Color.Black.copy(alpha = 0.72f),
        pickerFill = Color(0xFF17171C),
        pickerBorder = Color.White.copy(alpha = 0.14f),
        pickerRow = Color.White.copy(alpha = 0.05f),
        pickerRowHover = Color.White.copy(alpha = 0.10f),
        pickerRowBorder = Color.White.copy(alpha = 0.10f),
        pickerTitle = textPrimary,
        pickerMuted = textMuted,
    )
    ShelfThemeMode.Paper -> ShelfPalette(
        page = ShelfPaper.PAGE,
        backdropAlpha = 0f,
        scrim = ShelfPaper.PAGE,
        ink = ShelfPaper.INK,
        inkSoft = ShelfPaper.INK_SOFT,
        inkDim = ShelfPaper.INK_DIM,
        panel = ShelfPaper.STONE,
        divider = ShelfPaper.INK.copy(alpha = 0.12f),
        focusFill = accent,
        focusText = onAccent,
        accent = accent,
        accentInk = shelfReadableOn(accent, ShelfPaper.STONE, ShelfPaper.INK),
        rowFocus = ShelfPaper.INK.copy(alpha = 0.05f),
        placeholder = ShelfPaper.STONE,
        placeholderInk = ShelfPaper.INK_SOFT,
        tile = ShelfPaper.STONE,
        posterDim = ShelfPaper.PAGE.copy(alpha = 0.25f),
        pickerScrim = ShelfPaper.PAGE.copy(alpha = 0.72f),
        pickerFill = ShelfPaper.WHITE,
        pickerBorder = ShelfPaper.RULE.copy(alpha = 0.45f),
        pickerRow = ShelfPaper.INK.copy(alpha = 0.03f),
        pickerRowHover = ShelfPaper.INK.copy(alpha = 0.07f),
        pickerRowBorder = ShelfPaper.INK.copy(alpha = 0.10f),
        pickerTitle = ShelfPaper.INK,
        pickerMuted = ShelfPaper.INK_DIM,
    )
}

// Not static: the art's colour eases over 600 ms, and only what reads the palette should recompose.
val LocalShelfPalette = compositionLocalOf {
    shelfPalette(ShelfThemeMode.Dark, accent = Color(0xFFFFD700), onAccent = Color.Black)
}

@Composable
fun ProvideShelfPalette(
    mode: ShelfThemeMode,
    /** fork:shelf-home v3 - the colour taken from the focused art (ShelfAccent.kt); Dark only, Paper keeps its own. */
    artAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val base = shelfPalette(mode, colors.accent, colors.onAccent, colors.textPrimary, colors.textMuted)
    val palette = if (artAccent != null && mode == ShelfThemeMode.Dark) {
        base.copy(accent = artAccent, accentInk = artAccent, ring = artAccent)
    } else {
        base
    }
    CompositionLocalProvider(LocalShelfPalette provides palette, content = content)
}

/** The shelf home's look, per profile, in the `nuvio_shelf_home` store under "theme". Dark by default. */
object ShelfThemeRepository {
    private val _mode = MutableStateFlow(ShelfThemeMode.Dark)
    val mode: StateFlow<ShelfThemeMode> = _mode.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _mode.value = runCatching {
            ShelfHomeStorage.getString("theme")
                ?.let { stored -> ShelfThemeMode.entries.firstOrNull { it.name == stored } }
        }.getOrNull() ?: ShelfThemeMode.Dark
    }

    /** Re-read after a profile switch (the store is profile-scoped). */
    fun reload() {
        loaded = false
        ensureLoaded()
    }

    fun set(mode: ShelfThemeMode) {
        ensureLoaded()
        if (_mode.value == mode) return
        _mode.value = mode
        ShelfHomeStorage.putString("theme", mode.name)
    }
}
