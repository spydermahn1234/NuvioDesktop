package com.nuvio.app.features.whatsnew

import com.nuvio.app.isDesktop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns whether the "What's new" cards are up and which one. The rules (who sees what, what counts
 * as seen) are the pure functions in `WhatsNewPlan.kt`; this only connects them to storage and to
 * the screen.
 *
 * Closing in any way records everything as seen. That is deliberate: unlike the setup wizard,
 * closing the cards is a decision, not an interruption to resume.
 */
internal object WhatsNewController {
    private val _uiState = MutableStateFlow(WhatsNewUiState())
    val uiState: StateFlow<WhatsNewUiState> = _uiState.asStateFlow()

    private var hasEvaluated = false

    /**
     * Once per launch. Seeds a fresh account, or prepares the cards for an update. Preparing is not
     * showing: the host decides when the screen is free for it (after the wizard, the key prompt and
     * the updater, and only over the tabs), and nothing is recorded as seen until the cards close.
     */
    fun evaluateOnLaunch() {
        if (hasEvaluated) return
        hasEvaluated = true
        if (!isDesktop) return
        when (
            val launch = decideWhatsNewOnLaunch(
                entries = whatsNewEntries,
                lastSeen = WhatsNewStorage.lastSeen(),
                freshAccount = WhatsNewStorage.isFreshAccount(),
            )
        ) {
            is WhatsNewLaunch.Seed -> WhatsNewStorage.setLastSeen(launch.lastSeen)
            WhatsNewLaunch.NothingNew -> Unit
            is WhatsNewLaunch.Show -> _uiState.value = whatsNewStateFor(launch.plan)
        }
    }

    /** Reopened from Settings: the newest cards, whether or not they were seen. */
    fun openFromSettings() {
        val plan = planWhatsNewHistory(whatsNewEntries) ?: return
        _uiState.value = whatsNewStateFor(plan, fromSettings = true)
    }

    fun next() {
        _uiState.update { it.next() }
    }

    fun back() {
        _uiState.update { it.back() }
    }

    fun close() {
        if (!_uiState.value.visible) return
        if (isDesktop) {
            WhatsNewStorage.setLastSeen(lastSeenAfterClosing(whatsNewEntries, WhatsNewStorage.lastSeen()))
        }
        _uiState.value = WhatsNewUiState()
    }
}
