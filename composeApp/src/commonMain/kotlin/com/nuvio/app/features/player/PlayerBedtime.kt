package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * fork:bedtime - "Stop after this episode", set from Nuvio Remote's More sheet.
 *
 * While armed, the player does not advance on its own (no Binge Mode advance at the credits, no end-of-file advance,
 * no playlist advance): when the episode ends the player closes and Lights out switches the room off instead of
 * bringing it back up. A next episode the viewer asks for (the card, the remote's Next) still plays. Disarmed when
 * it fires and whenever the player closes, so it never carries over to another evening.
 */
object PlayerBedtime {
    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    fun set(on: Boolean) {
        _armed.value = on
    }
}
