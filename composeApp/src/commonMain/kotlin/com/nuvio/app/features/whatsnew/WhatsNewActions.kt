package com.nuvio.app.features.whatsnew

import com.nuvio.app.features.preroll.PreshowSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Connects a card's `TurnOn` key to the real setting. The choice of what to draw is the pure
 * `whatsNewButtonFor`; this only reads and flips the switch.
 */
internal object WhatsNewActions {
    /** Whether the feature is on, as a flow the card can watch; null for a key not known here. */
    fun enabledFlow(key: String): StateFlow<Boolean>? = when (key) {
        WHATS_NEW_KEY_PREVIOUSLY_ON -> PreshowSettings.previouslyOnEnabled
        WHATS_NEW_KEY_POST_SHOW -> PreshowSettings.postShowEnabled
        else -> null
    }

    /** Switches the feature on. The caller has already ruled out the consent step. */
    fun turnOn(key: String) {
        when (key) {
            WHATS_NEW_KEY_PREVIOUSLY_ON -> PreshowSettings.setPreviouslyOnEnabled(true)
            WHATS_NEW_KEY_POST_SHOW -> PreshowSettings.setPostShowEnabled(true)
        }
    }
}
