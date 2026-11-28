package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Where the track for what is playing has got to. */
enum class AlongStatus(val wire: String) { Building("building"), Ready("ready"), None("none") }

/** What the phone's Along tab shows; only ever what the film has already reached. */
data class RemoteWatchAlong(
    val title: String,
    val status: AlongStatus,
    val positionMs: Long,
    /** How many notes the track holds in all, so the phone can say "12 of 34 so far". */
    val total: Int,
    val moments: List<AlongMoment>,
    val people: List<AlongPerson>,
    /** Of [people], the characters "around now". */
    val aroundNow: Set<String>,
    val fromAi: Boolean,
)

/**
 * fork:watch-along - the track for the film playing now, and what has been shown of it. The player feeds it
 * ([begin], [ready], [tick], [end]); it pops cards on the TV through [hud] and keeps [remote] up to date for the phone.
 * The phone's copy only changes when something new is revealed, not on every tick, so the remote's line stays quiet.
 */
object WatchAlongLive {
    /** Runs a script on every playing video's HUD (set by WatchAlongDesktop; controls.js draws the cards). */
    @kotlin.concurrent.Volatile
    var hud: ((String) -> Unit)? = null

    private val _remote = MutableStateFlow<RemoteWatchAlong?>(null)
    val remote: StateFlow<RemoteWatchAlong?> = _remote.asStateFlow()

    private val lock = Any()
    private var title = ""
    private var status = AlongStatus.None
    private var track: AlongTrack? = null
    private var tvMoments: List<AlongMoment> = emptyList()
    private var lastMs = -1L
    private var lastPlaying = false
    private val shown = mutableSetOf<Long>()
    private var greeted = false
    private var panelKey = ""

    fun begin(title: String) = synchronized(lock) {
        this.title = title
        status = AlongStatus.Building
        track = null
        tvMoments = emptyList()
        lastMs = -1L
        shown.clear()
        greeted = false
        panelKey = ""
        publish(0L)
    }

    fun ready(built: AlongTrack?) = synchronized(lock) {
        track = built?.takeUnless { it.isEmpty }
        status = if (track != null) AlongStatus.Ready else AlongStatus.None
        tvMoments = track?.let { WatchAlongRules.forTv(it.moments, WatchAlongSettingsRepository.state.value.density) }.orEmpty()
        publish(lastMs.coerceAtLeast(0L))
    }

    /** Every position update from the player (about once a second, and on seeks). */
    fun tick(positionMs: Long, playing: Boolean) {
        val script = synchronized(lock) {
            val current = track
            val settings = WatchAlongSettingsRepository.state.value
            val next = when {
                current == null -> null
                !playing -> pausePanel(current, positionMs, settings)
                !greeted && positionMs < GREET_BEFORE_MS && settings.tvCards -> {
                    greeted = true
                    card(
                        kind = "Watch-along",
                        title = "${current.moments.size} notes, spoiler-free",
                        text = "Each one turns up at its scene. Pause for who's who, or follow along on your phone.",
                        holdMs = 7_000L,
                    )
                }
                else -> {
                    val due = if (settings.tvCards) {
                        WatchAlongRules.due(current.copy(moments = tvMoments), lastMs, positionMs, shown)
                    } else {
                        null
                    }
                    due?.let { moment ->
                        shown += moment.atMs
                        card(moment.kind.label, moment.title, moment.text, WatchAlongRules.CARD_MS)
                    } ?: if (lastPlaying != playing) "window.nuvioSetAlongPanel&&window.nuvioSetAlongPanel(null)" else null
                }
            }
            if (playing) panelKey = ""
            lastMs = positionMs
            lastPlaying = playing
            publish(positionMs)
            next
        }
        script?.let { s -> runCatching { hud?.invoke(s) } }
    }

    fun end() = synchronized(lock) {
        track = null
        status = AlongStatus.None
        _remote.value = null
        runCatching { hud?.invoke("window.nuvioShowAlongCard&&window.nuvioShowAlongCard(null);window.nuvioSetAlongPanel&&window.nuvioSetAlongPanel(null)") }
    }

    private fun publish(positionMs: Long) {
        val current = track
        val next = RemoteWatchAlong(
            title = title,
            status = status,
            positionMs = 0L,
            total = current?.moments?.size ?: 0,
            moments = current?.let { WatchAlongRules.revealed(it, positionMs).takeLast(PHONE_MOMENTS) }.orEmpty(),
            people = current?.let { WatchAlongRules.peopleSoFar(it, positionMs).take(PHONE_PEOPLE) }.orEmpty(),
            aroundNow = current?.let { WatchAlongRules.aroundNow(it, positionMs).map(AlongPerson::character).toSet() }.orEmpty(),
            fromAi = current?.origin == AlongOrigin.Ai,
        )
        // Position is left out of the comparison on purpose (it is 0 above): the phone's copy changes on a reveal.
        if (_remote.value != next) _remote.value = next
    }

    /** The pause panel's contents, or null when nothing changed since it was last sent. */
    private fun pausePanel(current: AlongTrack, positionMs: Long, settings: WatchAlongSettings): String? {
        if (!settings.pausePanel) return null
        val around = WatchAlongRules.aroundNow(current, positionMs)
        val sofar = WatchAlongRules.revealed(current, positionMs).takeLast(3).reversed()
        val key = around.joinToString { it.character } + "|" + sofar.joinToString { it.atMs.toString() }
        if (key == panelKey) return null
        panelKey = key
        if (around.isEmpty() && sofar.isEmpty()) return "window.nuvioSetAlongPanel&&window.nuvioSetAlongPanel(null)"
        val panel = JsonObject(
            mapOf(
                "people" to JsonArray(
                    around.map { p ->
                        JsonObject(
                            mapOf(
                                "character" to JsonPrimitive(p.character),
                                "actor" to JsonPrimitive(p.actor),
                                "note" to JsonPrimitive(p.note),
                                "photo" to (p.photo?.let { JsonPrimitive(it) } ?: JsonNull),
                            ),
                        )
                    },
                ),
                "sofar" to JsonArray(
                    sofar.map { m ->
                        JsonObject(
                            mapOf(
                                "at" to JsonPrimitive(WatchAlongRules.clock(m.atMs)),
                                "kind" to JsonPrimitive(m.kind.label),
                                "title" to JsonPrimitive(m.title),
                                "text" to JsonPrimitive(m.text),
                            ),
                        )
                    },
                ),
            ),
        )
        return "window.nuvioSetAlongPanel&&window.nuvioSetAlongPanel($panel)"
    }

    private fun card(kind: String, title: String, text: String, holdMs: Long): String {
        val card = JsonObject(
            mapOf(
                "kind" to JsonPrimitive(kind),
                "title" to JsonPrimitive(title),
                "text" to JsonPrimitive(text),
                "durationMs" to JsonPrimitive(holdMs),
            ),
        )
        return "window.nuvioShowAlongCard&&window.nuvioShowAlongCard($card)"
    }

    private const val GREET_BEFORE_MS = 10 * 60_000L
    private const val PHONE_MOMENTS = 40
    private const val PHONE_PEOPLE = 20
}
