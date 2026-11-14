package com.nuvio.app.features.iptv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * fork:live-tv-departure - fires the reminders the viewer set ("Tune in at"). A plain reminder shows a card two
 * minutes before the start (on the Compose overlay, and on the player's HUD pill over a playing video); a switching
 * reminder puts the channel on at the start. Reminders live in IptvSettings, so a restart keeps them.
 */
object IptvReminderEngine {
    /** A card on the screen. [id] changes with every card so the same text can show twice. */
    data class Card(val title: String, val line: String, val id: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var hide: Job? = null
    private var counter = 0L

    private val _showing = MutableStateFlow<Card?>(null)
    val showing: StateFlow<Card?> = _showing.asStateFlow()

    /** Set by the desktop app: draws a pill on the player's HUD (title, line, milliseconds). */
    @Volatile
    var playerPill: (String, String, Long) -> Unit = { _, _, _ -> }

    private const val CARD_MS = 20_000L
    private const val TICK_MS = 15_000L

    /** Starts the clock. Safe to call more than once. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                runCatching { tick(System.currentTimeMillis()) }
                delay(TICK_MS)
            }
        }
    }

    internal fun tick(now: Long) {
        IptvRepository.ensureLoaded()
        if (IptvRepository.settings.value.reminders.isEmpty()) return
        IptvRepository.takeDueReminders(now).forEach { fire(it, now) }
    }

    private fun fire(reminder: IptvReminder, now: Long) {
        if (reminder.autoSwitch && switchTo(reminder)) {
            // fork:live-tv-brakes - reached through a reminder: chosen, so its end gets the card.
            IptvBrakeEngine.chose(reminder.title, reminder.endMs)
            show(Card("Switching to ${reminder.title}", "${reminder.channelName} · starting now", 0L))
        } else {
            show(Card(reminder.title, IptvReminderRules.cardLine(reminder, now), 0L))
        }
    }

    private fun switchTo(reminder: IptvReminder): Boolean {
        val playlist = IptvRepository.settings.value.playlists.firstOrNull { it.id == reminder.playlistId } ?: return false
        val channel = IptvRepository.contentState(playlist.id).value.content?.byKey?.get(reminder.channelKey) ?: return false
        return IptvPlaybackRequests.play(playlist, channel)
    }

    private fun show(card: Card) {
        val shown = card.copy(id = ++counter)
        _showing.value = shown
        runCatching { playerPill(shown.title, shown.line, CARD_MS) }
        hide?.cancel()
        hide = scope.launch {
            delay(CARD_MS)
            if (_showing.value?.id == shown.id) _showing.value = null
        }
    }
}
