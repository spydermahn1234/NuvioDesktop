package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import kotlinx.serialization.Serializable

/**
 * fork:watch-along - a film's companion track: timed notes ([AlongMoment]) and its people ([AlongPerson]), built once
 * before the film starts (WatchAlongRepository) and cached, so playback never waits on a request.
 *
 * **Spoiler-safe by construction.** Nothing in a track is shown before its own time: a moment at 41:10 stays hidden
 * until the film has reached 41:10, and a person is only listed once the film has passed their [AlongPerson.firstMs].
 * Every moment's text is written to be true *at* its time (WatchAlongPrompt asks for that), so what has been shown can
 * never give away what comes later. [WatchAlongRules.revealed] is the only way anything reaches a screen.
 */
@Serializable
data class AlongTrack(
    val title: String = "",
    val moments: List<AlongMoment> = emptyList(),
    val people: List<AlongPerson> = emptyList(),
    val origin: AlongOrigin = AlongOrigin.Offline,
    /** The times came from a subtitle file, so they line up with the film; false means spread by running time only. */
    val timedBySubtitles: Boolean = false,
) {
    val isEmpty: Boolean get() = moments.isEmpty() && people.isEmpty()
}

@Serializable
enum class AlongOrigin { Ai, Offline }

/** What a moment is about; the label is what the TV card and the phone show above it. */
@Serializable
enum class AlongKind(val label: String) {
    Trivia("Trivia"),
    Making("Behind the scenes"),
    Cast("Cast"),
    Music("Music"),
    Place("Filmed at"),
    Callback("Callback"),
    ;

    companion object {
        fun fromWire(value: String?): AlongKind = when (value?.trim()?.lowercase()) {
            "making", "production", "behind the scenes", "behind_the_scenes" -> Making
            "cast", "actor", "person", "character" -> Cast
            "music", "song", "score", "soundtrack" -> Music
            "place", "location", "filmed at", "filmed_at" -> Place
            "callback", "reference", "easter egg", "easter_egg" -> Callback
            else -> Trivia
        }
    }
}

@Serializable
data class AlongMoment(
    val atMs: Long = 0L,
    val kind: AlongKind = AlongKind.Trivia,
    val title: String = "",
    val text: String = "",
    /** Characters the moment is about, by [AlongPerson.character]; they light up on the phone. */
    val people: List<String> = emptyList(),
)

@Serializable
data class AlongPerson(
    val character: String = "",
    val actor: String = "",
    val photo: String? = null,
    /** When the character first turns up (by name in the dialogue, or as the AI placed them); -1 when unknown. */
    val firstMs: Long = -1L,
    /** One line about who they are, written as of [firstMs] so it gives nothing away. */
    val note: String = "",
    /** When their name comes up in the dialogue, in order; drives "around now" on pause. */
    val mentions: List<Long> = emptyList(),
)

/** How many pop-ups the TV gets; the phone always has every moment. */
@Serializable
enum class AlongDensity(val label: String, val gapMs: Long) {
    Light("A few", 12 * 60_000L),
    Normal("Some", 6 * 60_000L),
    Lots("Lots", 150_000L),
}

/** A subtitle line with an estimated end, for finding the quiet gaps a card can sit in without covering dialogue. */
data class AlongCue(val startMs: Long, val text: String)
