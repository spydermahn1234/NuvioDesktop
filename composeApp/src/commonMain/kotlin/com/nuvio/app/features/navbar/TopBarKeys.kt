package com.nuvio.app.features.navbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.nuvio.app.core.ui.requestFocusLogged

/*
 * fork:shelf-home - the floating top bar by keyboard and remote (Nick, Nov 27: "add the top bar to Shelves").
 *
 * The bar is mouse-only upstream: it fades until hovered and nothing sends focus to it. A screen that owns every key
 * (the fan-art shelf home) calls [enter] to hand the keyboard to the bar: the bar's selected entry takes focus, the bar
 * stays shown while it has focus, Left/Right walk its entries and OK opens one. Down, Back or Escape call the screen's
 * [enter] `onDown` so it takes the keyboard back; focus leaving the bar any other way (OK on another tab, a click)
 * calls `onLost`.
 */
object TopBarKeys {
    /** On the bar's selected entry (NavQuadrant in App.kt). */
    val entry = FocusRequester()

    /** True while focus is inside the bar's row of entries; the bar is drawn fully while it is. */
    var focused by mutableStateOf(false)
        private set

    private var onDown: (() -> Unit)? = null
    private var onLost: (() -> Unit)? = null

    /** Moves focus to the bar. False when no bar is on screen (the sidebar layout, or the bar hidden). */
    fun enter(onDown: () -> Unit, onLost: () -> Unit): Boolean {
        this.onDown = onDown
        this.onLost = onLost
        val moved = entry.requestFocusLogged()
        if (!moved) {
            this.onDown = null
            this.onLost = null
        }
        return moved
    }

    /** Down, Back or Escape on the bar: back to the screen that sent focus up. False when nobody did. */
    internal fun leave(): Boolean {
        val down = onDown ?: return false
        onDown = null
        onLost = null
        down()
        return true
    }

    internal fun focusChanged(hasFocus: Boolean) {
        val was = focused
        focused = hasFocus
        if (was && !hasFocus) {
            val lost = onLost
            onDown = null
            onLost = null
            lost?.invoke()
        }
    }
}
