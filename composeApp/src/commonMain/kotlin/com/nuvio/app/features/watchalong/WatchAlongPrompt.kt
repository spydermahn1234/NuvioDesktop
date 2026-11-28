package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import com.nuvio.app.features.discover.DiscoverAiPromptText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * fork:watch-along - what the AI provider is asked, and how its reply becomes an [AlongTrack].
 *
 * The model is given the whole subtitle file (condensed) so it can place each note at the right scene, which means it
 * *reads the ending*. Everything below exists to keep that out of what is written: each note must be true and safe at
 * its own timestamp, and a character's note at their first appearance. The app then only ever shows a note once the
 * film reaches it (WatchAlongRules.revealed), so the two together keep the film unspoiled.
 *
 * Bump [WATCH_ALONG_PROMPT_VERSION] on any change; it is part of the cache key.
 */
const val WATCH_ALONG_PROMPT_VERSION = 1

/** About 15k tokens of dialogue: a whole film, condensed. */
private const val MAX_SUBTITLE_CHARS = 60_000
private const val MERGE_WITHIN_MS = 4_000L
private const val TEXT_MAX = 170
private const val MIN_CONFIDENCE = 0.6

private const val SYSTEM =
    "You write a watch-along companion for a film: short notes that pop up on a TV at the exact moment they relate " +
        "to, like a friend who knows the film leaning over to tell you something. You are given the film's details " +
        "and its subtitles with timestamps, so you can place each note at the right scene.\n\n" +
        "SPOILER RULES (the most important part):\n" +
        "- The viewer is watching for the FIRST TIME. A note is shown at its timestamp and must only refer to what the " +
        "viewer has already seen by then. Never hint at, foreshadow or set up anything that happens later: no twists, " +
        "deaths, betrayals, reveals, who survives, how it ends, or \"remember this, it matters later\".\n" +
        "- You have read the whole script. Pretend you have not read past each note's timestamp.\n" +
        "- A character's note describes them as the viewer meets them, in their own words: who they appear to be at " +
        "first sight. Never what they turn out to be.\n" +
        "- Behind-the-scenes facts about a scene are fine only if they do not reveal what happens next.\n\n" +
        "WHAT TO WRITE:\n" +
        "- \"making\": how a scene was shot, practical effects, improvised lines, deleted or changed bits.\n" +
        "- \"cast\": an actor fact when they first appear (an earlier role they are known for, an age, a casting " +
        "story). Not a biography.\n" +
        "- \"music\": a recognisable song or the score at that moment (title and artist, or the composer's idea).\n" +
        "- \"place\": where the scene was filmed, when you are sure.\n" +
        "- \"callback\": a nod to an EARLIER scene of this film, or a reference to another film.\n" +
        "- \"trivia\": anything else worth a smile.\n" +
        "- Only facts you are highly confident are true for THIS film (the year and director disambiguate). Never " +
        "invent a quote, an anecdote or a number. Fewer, true notes beat more.\n" +
        "- Each note is at most $TEXT_MAX characters, readable across a room in a few seconds; its title is 2 to 5 " +
        "words. Spread them through the whole film, not just the start. Aim for 25 to 40.\n" +
        "- Timestamps must be ones the subtitles support (the scene the note is about), as h:mm:ss.\n\n" +
        "Reply with a single JSON object and nothing else: no markdown fence, no preamble.\n" +
        "{\n" +
        "  \"people\": [ { \"character\": string, \"actor\": string, \"first_seen\": \"h:mm:ss\", " +
        "\"note\": one spoiler-free line about who they seem to be when we meet them } ],\n" +
        "  \"moments\": [ { \"at\": \"h:mm:ss\", \"kind\": \"making\"|\"cast\"|\"music\"|\"place\"|\"callback\"|" +
        "\"trivia\", \"title\": string, \"text\": string, \"people\": [character names it is about], " +
        "\"spoiler_risk\": \"none\"|\"low\"|\"high\", \"confidence\": number from 0 to 1 } ]\n" +
        "}\n" +
        "List the 6 to 12 characters who matter, using the names the cast list gives. Be honest in \"confidence\" and " +
        "\"spoiler_risk\": they are used to throw notes away."

/** The film as the prompt describes it. */
data class AlongSource(
    val title: String,
    val year: String?,
    val directors: List<String>,
    val cast: List<WatchAlongRules.CastEntry>,
    val synopsis: String?,
    val runtimeMs: Long,
    /** For an episode: "S2E5 · The Rains of Castamere"; null for a film. */
    val episode: String? = null,
)

fun buildWatchAlongPrompt(source: AlongSource, cues: List<AlongCue>, languageTag: String): DiscoverAiPromptText {
    val user = buildString {
        appendLine("Film: ${source.title}${source.year?.let { " ($it)" }.orEmpty()}")
        source.episode?.let { appendLine("Episode: $it (write about this episode only)") }
        source.directors.takeIf { it.isNotEmpty() }?.let { appendLine("Directed by: ${it.joinToString(", ")}") }
        if (source.cast.isNotEmpty()) {
            appendLine("Cast (character — actor):")
            source.cast.take(25).forEach { appendLine("- ${it.character.ifBlank { "?" }} — ${it.actor}") }
        }
        source.synopsis?.let { appendLine("Premise: $it") }
        if (source.runtimeMs > 0) appendLine("Running time: ${WatchAlongRules.clock(source.runtimeMs)}")
        appendLine()
        appendLine("Subtitles (h:mm:ss  line):")
        append(condenseSubtitles(cues))
        appendLine()
        append("Write the titles, notes and character notes in the language with tag \"$languageTag\".")
    }
    return DiscoverAiPromptText(system = SYSTEM, user = user)
}

/**
 * The subtitles as the prompt carries them: lines within a few seconds of each other joined, every line stamped, and
 * the whole thinned evenly (every other block, then every third...) until it fits, so the end is never just cut off.
 */
fun condenseSubtitles(cues: List<AlongCue>, maxChars: Int = MAX_SUBTITLE_CHARS): String {
    if (cues.isEmpty()) return "(none available: place notes by the running time and the premise only)\n"
    val blocks = mutableListOf<Pair<Long, StringBuilder>>()
    var lastStart = Long.MIN_VALUE / 2
    cues.forEach { cue ->
        val text = cue.text.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return@forEach
        val current = blocks.lastOrNull()
        if (current != null && cue.startMs - lastStart <= MERGE_WITHIN_MS && current.second.length < 220) {
            current.second.append(' ').append(text)
        } else {
            blocks += cue.startMs to StringBuilder(text)
        }
        lastStart = cue.startMs
    }
    val lines = blocks.map { (at, text) -> "${WatchAlongRules.clock(at)}  $text" }
    var step = 1
    while (true) {
        val kept = lines.filterIndexed { i, _ -> i % step == 0 }
        val size = kept.sumOf { it.length + 1 }
        if (size <= maxChars || step > 12) return kept.joinToString("\n", postfix = "\n")
        step++
    }
}

private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * The reply as a track, or null when nothing usable came back. Drops notes the model itself marked as a high
 * spoiler risk or unsure, notes past the film's end, and empty ones; keeps everything in time order.
 */
fun parseWatchAlong(raw: String, title: String, runtimeMs: Long, timedBySubtitles: Boolean): AlongTrack? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val root = runCatching { lenient.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
    val limit = if (runtimeMs > 0) runtimeMs else Long.MAX_VALUE
    val people = (root["people"] as? JsonArray).orEmpty().mapNotNull { element ->
        val o = element as? JsonObject ?: return@mapNotNull null
        val character = o.string("character")?.take(60) ?: return@mapNotNull null
        AlongPerson(
            character = character,
            actor = o.string("actor").orEmpty().take(60),
            firstMs = WatchAlongRules.parseClock(o.string("first_seen"))?.takeIf { it <= limit } ?: -1L,
            note = o.string("note").orEmpty().take(TEXT_MAX),
        )
    }.distinctBy { it.character.lowercase() }
    val moments = (root["moments"] as? JsonArray).orEmpty().mapNotNull { element ->
        val o = element as? JsonObject ?: return@mapNotNull null
        val at = WatchAlongRules.parseClock(o.string("at"))?.takeIf { it <= limit } ?: return@mapNotNull null
        val text = o.string("text")?.take(TEXT_MAX) ?: return@mapNotNull null
        if (o.string("spoiler_risk")?.lowercase() == "high") return@mapNotNull null
        val confidence = (o["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 1.0
        if (confidence < MIN_CONFIDENCE) return@mapNotNull null
        AlongMoment(
            atMs = at,
            kind = AlongKind.fromWire(o.string("kind")),
            title = o.string("title")?.take(48).orEmpty(),
            text = text,
            people = runCatching { o["people"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) } }
                .getOrNull().orEmpty(),
        )
    }.sortedBy { it.atMs }
    if (people.isEmpty() && moments.isEmpty()) return null
    return AlongTrack(
        title = title,
        moments = moments,
        people = people,
        origin = AlongOrigin.Ai,
        timedBySubtitles = timedBySubtitles,
    )
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
