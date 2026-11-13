package com.nuvio.app.features.iptv

/**
 * fork:live-tv-brakes - the live side of [IptvBrakeRules]. The player calls [noteChange] on every
 * channel change and [tick] once a minute; a card is shown through [showChoice] (the desktop HUD)
 * and the answer comes back through [answer]. Nothing here runs unless the Brakes switch is on.
 */
object IptvBrakeEngine {
    /** Set by the desktop app: draws the card in the player (title, line, options, focused index). */
    var showChoice: ((String, String, List<String>, Int) -> Unit)? = null

    private enum class Kind { ZapBudget, EndCard }

    private var zaps = emptyList<Long>()
    private var pending: Kind? = null
    private var endShownFor: Long? = null
    private var stayKey: String? = null
    private var stayStartMs = 0L

    private fun on() = IptvRepository.settings.value.brakes
    private fun night(now: Long) = IptvDecide.nightOf(now)

    /** A channel change: counted in the tally, and the zap budget offered the brake once a night. */
    fun noteChange(now: Long = System.currentTimeMillis()) {
        if (!on()) return
        IptvRepository.tally(night(now)) { it.copy(changes = it.changes + 1) }
        zaps = IptvBrakeRules.zapped(zaps, now)
        stayKey = null
        val settings = IptvRepository.settings.value
        if (IptvBrakeRules.overBudget(zaps, settings.brakeOffered, night(now)) && pending == null) {
            IptvRepository.brakeOffered(night(now))
            zaps = emptyList()
            pending = Kind.ZapBudget
            showChoice?.invoke("That's a lot of flicking", "Nothing has held you for a while.", listOf("Keep flicking", "Turn Live TV off"), 0)
        }
    }

    /** Once a minute while a channel plays: the minute is counted, and the end card shown when a programme you stayed on ends. */
    fun tick(playlist: IptvPlaylist, channel: IptvChannel, now: Long = System.currentTimeMillis()) {
        if (!on()) return
        IptvRepository.tally(night(now)) { it.copy(minutes = it.minutes + 1) }
        val key = "${playlist.id}:${channel.uid}"
        if (stayKey != key) {
            stayKey = key
            stayStartMs = now
        }
        val entry = IptvEpgRepository.guideEntries(playlist, channel).firstOrNull { now >= it.startMs && now < it.endMs } ?: return
        if (pending != null) return
        // Stay counts from the later of tuning in and the programme's start.
        val stayed = now - maxOf(stayStartMs, entry.startMs)
        if (IptvBrakeRules.endCardDue(entry.endMs, now, stayed, endShownFor)) {
            endShownFor = entry.endMs
            pending = Kind.EndCard
            IptvRepository.tally(night(now)) { it.copy(finished = it.finished + 1) }
            showChoice?.invoke(entry.title, "That's the end of it.", listOf("Stop here", "One more"), 0)
        }
    }

    /** The viewer's answer to the card on screen: -1 is dismissed. */
    fun answer(index: Int, now: Long = System.currentTimeMillis()): IptvBrakeAnswer {
        val kind = pending ?: return IptvBrakeAnswer.Dismissed
        pending = null
        val stops = when (kind) {
            Kind.ZapBudget -> index == 1
            Kind.EndCard -> index == 0
        }
        if (!stops) return IptvBrakeAnswer.KeepGoing.takeIf { index >= 0 } ?: IptvBrakeAnswer.Dismissed
        IptvRepository.tally(night(now)) { it.copy(stopped = it.stopped + 1) }
        return IptvBrakeAnswer.TurnOff
    }
}
