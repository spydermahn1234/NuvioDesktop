package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

/**
 * fork:watch-along - the pure part: what may be shown at a position, which moment pops up now, where a card fits
 * between lines of dialogue, and the offline track (no AI) made from the cast list and the subtitles.
 * Tested by WatchAlongRulesTest.
 */
object WatchAlongRules {
    /** A tick further than this from the last one is a seek: nothing in between pops up. */
    const val SEEK_JUMP_MS = 6_000L

    /** How long a TV card stays up; a quiet gap must hold most of it. */
    const val CARD_MS = 9_000L
    private const val MIN_GAP_MS = 4_000L
    private const val LOOK_AHEAD_MS = 150_000L
    private const val LOOK_BEHIND_MS = 20_000L

    /** Who counts as "around now" on pause: named in the last few minutes. */
    const val AROUND_NOW_MS = 180_000L
    private const val AROUND_NOW_MAX = 4

    /** Everything at or before [positionMs], in order: the only list any screen draws from. */
    fun revealed(track: AlongTrack, positionMs: Long): List<AlongMoment> =
        track.moments.filter { it.atMs <= positionMs }.sortedBy { it.atMs }

    /** The people the film has reached, in the order they turned up. Unknown first times stay hidden. */
    fun peopleSoFar(track: AlongTrack, positionMs: Long): List<AlongPerson> =
        track.people.filter { it.firstMs in 0..positionMs }.sortedBy { it.firstMs }

    /**
     * Who the pause panel shows: those named most recently (within [AROUND_NOW_MS]), else the latest to turn up.
     * Never anyone the film has not reached.
     */
    fun aroundNow(track: AlongTrack, positionMs: Long): List<AlongPerson> {
        val reached = peopleSoFar(track, positionMs)
        val recent = reached
            .mapNotNull { person ->
                val last = (person.mentions.filter { it <= positionMs } + person.firstMs).maxOrNull() ?: return@mapNotNull null
                if (positionMs - last <= AROUND_NOW_MS) person to last else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
        return (recent + reached.reversed()).distinctBy { it.character }.take(AROUND_NOW_MAX)
    }

    /**
     * The moment to pop up for a tick from [lastMs] to [nowMs], if any: the latest one crossed in normal play that has
     * not been shown yet. A seek (a jump over [SEEK_JUMP_MS], or backwards) crosses nothing, so skipping ahead never
     * fires a burst of old cards.
     */
    fun due(track: AlongTrack, lastMs: Long, nowMs: Long, shown: Set<Long>): AlongMoment? {
        if (nowMs <= lastMs || nowMs - lastMs > SEEK_JUMP_MS) return null
        return track.moments
            .filter { it.atMs in (lastMs + 1)..nowMs && it.atMs !in shown }
            .maxByOrNull { it.atMs }
    }

    /** The moments that become TV cards at [density]: at least its gap apart, cast introductions a little closer. */
    fun forTv(moments: List<AlongMoment>, density: AlongDensity): List<AlongMoment> {
        var lastAny = Long.MIN_VALUE / 2
        val kept = mutableListOf<AlongMoment>()
        moments.sortedBy { it.atMs }.forEach { moment ->
            val gap = if (moment.kind == AlongKind.Cast) minOf(density.gapMs, 90_000L) else density.gapMs
            if (moment.atMs - lastAny >= gap) {
                kept += moment
                lastAny = moment.atMs
            }
        }
        return kept
    }

    /** Each cue's end, estimated: subtitle start times are all the parser keeps, so the length is read from the text. */
    fun cueEnds(cues: List<AlongCue>): List<Long> = cues.mapIndexed { i, cue ->
        val spoken = 1_200L + cue.text.length * 55L
        val next = cues.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
        minOf(cue.startMs + spoken.coerceAtMost(6_000L), next)
    }

    /**
     * Where a card meant for [atMs] actually goes: the start of the first quiet stretch (no dialogue for at least
     * [MIN_GAP_MS]) from a little before [atMs] to [LOOK_AHEAD_MS] after it, so it never sits over someone talking.
     * Never earlier than [LOOK_BEHIND_MS] before [atMs] (it may only move to a scene the note is already about), and
     * [atMs] itself when there is no gap, or no subtitles.
     */
    fun placeInQuiet(atMs: Long, cues: List<AlongCue>, lookBehindMs: Long = LOOK_BEHIND_MS): Long {
        if (cues.isEmpty()) return atMs
        val ends = cueEnds(cues)
        val from = atMs - lookBehindMs
        val to = atMs + LOOK_AHEAD_MS
        // Before the first line of dialogue counts as quiet too.
        if (cues.first().startMs > atMs && cues.first().startMs - atMs >= MIN_GAP_MS) return atMs
        for (i in cues.indices) {
            val gapStart = ends[i]
            val gapEnd = cues.getOrNull(i + 1)?.startMs ?: (gapStart + LOOK_AHEAD_MS)
            if (gapEnd < from) continue
            if (gapStart > to) break
            val start = maxOf(gapStart, from)
            if (gapEnd - start >= MIN_GAP_MS) return start + 300L
        }
        return atMs
    }

    /**
     * Moves every moment into a quiet gap and keeps them in time order. A cast introduction never moves earlier than
     * its time: before it, the character has not turned up yet.
     */
    fun settle(track: AlongTrack, cues: List<AlongCue>): AlongTrack = track.copy(
        moments = track.moments
            .map { it.copy(atMs = placeInQuiet(it.atMs, cues, if (it.kind == AlongKind.Cast) 0L else LOOK_BEHIND_MS)) }
            .sortedBy { it.atMs },
    )

    // ------------------------------------------------------------------------------------------- Offline track

    /** One cast member as the details page lists them. */
    data class CastEntry(val character: String, val actor: String, val photo: String?)

    private val NOT_NAMES = setOf(
        "the", "and", "young", "old", "older", "younger", "little", "big", "voice", "uncredited", "himself", "herself",
        "themselves", "narrator", "man", "woman", "boy", "girl", "mr", "mrs", "ms", "miss", "dr", "doctor", "sir",
        "lady", "lord", "captain", "officer", "agent", "detective", "sergeant", "general", "king", "queen", "prince",
        "princess", "father", "mother", "uncle", "aunt", "guard", "soldier", "nurse", "waiter", "waitress", "driver",
        "reporter", "police", "cop", "kid", "baby", "additional", "voices", "self", "archive", "footage",
    )

    /** The words a character is called by in dialogue: "Dr. Ellie Sattler" is "Ellie" and "Sattler". */
    fun callNames(character: String): List<String> = character
        .substringBefore(" / ")
        .substringBefore("(")
        .split(' ', '-', '.', ',', '"', '\'')
        .map { it.trim() }
        .filter { it.length >= 3 && it[0].isUpperCase() && it.lowercase() !in NOT_NAMES }
        .distinct()

    /** When any of [names] is said, as a whole word with its capital: "Rose" the name, not "rose" the verb. */
    fun mentionTimes(names: List<String>, cues: List<AlongCue>): List<Long> {
        if (names.isEmpty()) return emptyList()
        val pattern = Regex("\\b(" + names.joinToString("|") { Regex.escape(it) } + ")\\b")
        return cues.filter { pattern.containsMatchIn(it.text) }.map { it.startMs }
    }

    /**
     * The no-AI track: the top-billed characters, each introduced the first time their name is said ("That's Ellie
     * Sattler, played by Laura Dern"), and the pre-show's facts (when it has some) spread through the film's quiet
     * stretches between 10% and 85% of the running time. Without subtitles there are no times, so only the facts.
     */
    fun offline(
        title: String,
        cast: List<CastEntry>,
        cues: List<AlongCue>,
        facts: List<String>,
        durationMs: Long,
        castLimit: Int = 10,
    ): AlongTrack {
        val people = cast
            .filter { it.character.isNotBlank() && it.actor.isNotBlank() }
            .take(castLimit)
            .mapNotNull { entry ->
                val mentions = mentionTimes(callNames(entry.character), cues)
                if (mentions.isEmpty()) return@mapNotNull null
                AlongPerson(
                    character = entry.character.substringBefore(" / ").trim(),
                    actor = entry.actor,
                    photo = entry.photo,
                    firstMs = mentions.first(),
                    note = "Played by ${entry.actor}.",
                    mentions = mentions.take(MAX_MENTIONS),
                )
            }
        val intros = people.map { person ->
            AlongMoment(
                atMs = person.firstMs,
                kind = AlongKind.Cast,
                title = person.character,
                text = "Played by ${person.actor}.",
                people = listOf(person.character),
            )
        }
        val spread = if (durationMs > 0 && facts.isNotEmpty()) {
            val from = durationMs / 10
            val span = durationMs * 85 / 100 - from
            facts.mapIndexed { i, fact ->
                AlongMoment(
                    atMs = from + span * (i + 1) / (facts.size + 1),
                    kind = AlongKind.Trivia,
                    title = "Did you know?",
                    text = fact,
                )
            }
        } else {
            emptyList()
        }
        return settle(
            AlongTrack(
                title = title,
                moments = intros + spread,
                people = people,
                origin = AlongOrigin.Offline,
                timedBySubtitles = cues.isNotEmpty(),
            ),
            cues,
        )
    }

    /** Fills in photos (and any missing mentions) for the AI's people from the details page's cast, by actor. */
    fun withCast(track: AlongTrack, cast: List<CastEntry>, cues: List<AlongCue>): AlongTrack {
        val byActor = cast.associateBy { it.actor.lowercase() }
        return track.copy(
            people = track.people.map { person ->
                val match = byActor[person.actor.lowercase()]
                val mentions = person.mentions.ifEmpty { mentionTimes(callNames(person.character), cues).take(MAX_MENTIONS) }
                person.copy(photo = person.photo ?: match?.photo, mentions = mentions)
            },
        )
    }

    /** Picks the subtitle file to read: one in the wanted language first, then English, then any. */
    fun <T> pickSubtitle(items: List<T>, language: (T) -> String, wanted: String): T? {
        fun matches(item: T, code: String) = language(item).lowercase().let { it == code || it.startsWith("$code-") || it == iso3(code) }
        return items.firstOrNull { matches(it, wanted) } ?: items.firstOrNull { matches(it, "en") } ?: items.firstOrNull()
    }

    private fun iso3(code: String): String = when (code) {
        "en" -> "eng"; "fr" -> "fre"; "de" -> "ger"; "es" -> "spa"; "it" -> "ita"; "pt" -> "por"; "nl" -> "dut"
        else -> code
    }

    /** "1:02:03" or "62:03" or "3723" (seconds) as milliseconds; null when unreadable. */
    fun parseClock(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = text.split(':').map { it.trim().toDoubleOrNull() ?: return null }
        val seconds = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return if (seconds < 0) null else (seconds * 1000).toLong()
    }

    /** "1:02:03" for the prompt and the phone. */
    fun clock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "$m:${s.pad()}"
    }

    private fun Long.pad() = toString().padStart(2, '0')

    private const val MAX_MENTIONS = 60
}
