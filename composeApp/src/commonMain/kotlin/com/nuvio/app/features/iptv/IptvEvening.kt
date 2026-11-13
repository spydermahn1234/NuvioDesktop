package com.nuvio.app.features.iptv

import java.time.Instant
import java.time.ZoneId

/**
 * fork:live-tv-evening - Tonight's running order and Just ask: two ways to hand the whole decision over.
 * Rules only (no AI key needed); pure Kotlin, tested by IptvEveningTest. IptvOnNowScreen feeds them the guide.
 */
object IptvRunningOrder {
    const val SECTION = "__order__"
    const val MAX_SLOTS = 6

    /** Shorter than this is not worth a slot. */
    private const val MIN_LENGTH_MIN = 20

    /** The first slot is a programme already on only when this much of it is left. */
    private const val MIN_LEFT_MIN = 25
    private const val HOP_MS = 15 * 60_000L
    private const val MINUTE = 60_000L

    /** When bedtime is, as a time: today's, or tomorrow's when it is more than six hours behind the clock (as Tonight reads it). */
    fun bedtimeMs(now: Long, bedMinuteOfDay: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        var gap = bedMinuteOfDay - (local.hour * 60 + local.minute)
        if (gap < -6 * 60) gap += 24 * 60
        return now + gap * MINUTE
    }

    private fun score(p: IptvWallProgramme, follows: List<String>, usualKeys: Set<String>): Int {
        var s = 0
        if (IptvDecide.isFollowed(follows, p.entry.title)) s += 100
        if (p.entry.kind == IptvProgrammeKind.FILM) s += 30
        if (p.channel.primary.key in usualKeys) s += 20
        if (p.entry.kind == IptvProgrammeKind.SPORT) s += 15
        s += ((p.entry.endMs - p.entry.startMs) / MINUTE / 10).toInt().coerceIn(0, 6)
        return s
    }

    /**
     * One evening, in order: from now to [bedMs], each slot the best programme starting within a quarter of an hour of
     * the last one's end. Whatever would run past bedtime is left out (it is for tomorrow, not trimmed).
     */
    fun build(guide: List<IptvWallProgramme>, follows: List<String>, usualKeys: Set<String>, now: Long, bedMs: Long): List<IptvWallProgramme> {
        val pool = guide.filter {
            val length = (it.entry.endMs - it.entry.startMs) / MINUTE
            length >= MIN_LENGTH_MIN && it.entry.endMs <= bedMs && it.entry.endMs > now
        }
        val slots = ArrayList<IptvWallProgramme>()
        var cursor = now
        val seen = HashSet<String>()
        while (slots.size < MAX_SLOTS) {
            val first = slots.isEmpty()
            val options = pool.filter { p ->
                IptvDecide.normalise(p.entry.title) !in seen && if (first) {
                    val left = (p.entry.endMs - maxOf(now, p.entry.startMs)) / MINUTE
                    left >= MIN_LEFT_MIN && p.entry.startMs <= now + HOP_MS
                } else {
                    p.entry.startMs >= cursor && p.entry.startMs <= cursor + HOP_MS
                }
            }
            val pick = options.sortedWith(
                compareByDescending<IptvWallProgramme> { score(it, follows, usualKeys) }.thenBy { it.entry.startMs },
            ).firstOrNull() ?: break
            slots.add(pick)
            seen.add(IptvDecide.normalise(pick.entry.title))
            cursor = pick.entry.endMs
        }
        return slots
    }

    fun reminderId(playlistId: String, p: IptvWallProgramme) = "$playlistId:${p.channel.primary.key}:${p.entry.startMs}"

    /** Every slot has an auto-switch reminder: the order is approved. */
    fun approved(playlistId: String, slots: List<IptvWallProgramme>, reminders: List<IptvReminder>): Boolean =
        slots.isNotEmpty() && slots.all { s -> reminders.any { it.id == reminderId(playlistId, s) && it.autoSwitch } }
}

/** What a typed line asks for: how long, what kind, and the words a title or channel must contain. */
data class IptvAskQuery(val minutes: Int?, val kinds: Set<String>, val words: List<String>, val comedy: Boolean)

class IptvAskAnswer(val programme: IptvWallProgramme?, val why: String, val others: Int)

object IptvAsk {
    const val SECTION = "__ask__"

    private val NUMBER_WORDS = mapOf("five" to 5, "ten" to 10, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "fortyfive" to 45, "sixty" to 60, "ninety" to 90)
    private val STOP = setOf(
        "something", "anything", "to", "watch", "a", "an", "the", "for", "of", "on", "with", "me", "some", "about", "i", "want", "need",
        "have", "has", "got", "half", "hour", "hours", "minute", "minutes", "min", "mins", "and", "or", "that", "is", "short", "quick", "long",
        "tonight", "now", "good", "nice", "any", "please", "just", "show", "program", "programme",
    )
    private val COMEDY = listOf("comedy", "sitcom", "funny", "laugh", "stand-up", "standup", "comedian", "mock the week", "friends", "frasier")

    /** Reads "something funny, half an hour" into a [IptvAskQuery]. */
    fun parse(text: String): IptvAskQuery {
        val lower = text.lowercase().replace(Regex("[^a-z0-9\\s-]"), " ")
        var minutes: Int? = null
        Regex("(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m)\\b").find(lower)?.let {
            val n = it.groupValues[1].toInt()
            minutes = if (it.groupValues[2].startsWith("h")) n * 60 else n
        }
        if (minutes == null) {
            when {
                Regex("\\bhalf an? hour\\b").containsMatchIn(lower) -> minutes = 30
                Regex("\\ban hour and a half\\b").containsMatchIn(lower) -> minutes = 90
                Regex("\\ban hour\\b|\\bone hour\\b").containsMatchIn(lower) -> minutes = 60
                Regex("\\bquarter of an hour\\b").containsMatchIn(lower) -> minutes = 15
                else -> NUMBER_WORDS.entries.firstOrNull { Regex("\\b${it.key}\\s*(minutes?|mins?)\\b").containsMatchIn(lower) }?.let { minutes = it.value }
            }
        }
        val kinds = buildSet {
            if (Regex("\\b(film|films|movie|movies)\\b").containsMatchIn(lower)) add(IptvProgrammeKind.FILM)
            if (Regex("\\b(sport|sports|match|game)\\b").containsMatchIn(lower)) add(IptvProgrammeKind.SPORT)
            if (Regex("\\bnews\\b").containsMatchIn(lower)) add(IptvProgrammeKind.NEWS)
            if (Regex("\\b(kids|children|cartoons?)\\b").containsMatchIn(lower)) add(IptvProgrammeKind.KIDS)
        }
        val comedy = Regex("\\b(funny|comedy|laugh|laughs|sitcom)\\b").containsMatchIn(lower)
        val kindWords = setOf("film", "films", "movie", "movies", "sport", "sports", "match", "game", "news", "kids", "children", "cartoon", "cartoons", "funny", "comedy", "laugh", "laughs", "sitcom")
        val words = lower.split(Regex("\\s+")).filter {
            it.length >= 3 && it !in STOP && it !in kindWords && it.toIntOrNull() == null && it !in NUMBER_WORDS && !Regex("\\d+(h|m|hr|hrs|min|mins)").matches(it)
        }
        return IptvAskQuery(minutes, kinds, words, comedy)
    }

    private fun matches(p: IptvWallProgramme, q: IptvAskQuery, now: Long): Boolean {
        val title = p.entry.title.lowercase()
        val channel = p.channel.primary.name.lowercase()
        if (q.kinds.isNotEmpty() && p.entry.kind !in q.kinds) return false
        if (q.comedy && COMEDY.none { title.contains(it) } && p.entry.kind != "comedy") return false
        if (q.words.any { !title.contains(it) && !channel.contains(it) }) return false
        q.minutes?.let { limit ->
            val length = (p.entry.endMs - maxOf(now, p.entry.startMs)) / 60_000L
            if (length > limit + IptvDecide.MAX_OVER_MIN || length < 5) return false
        }
        return true
    }

    /**
     * One answer. [skips] steps to the next best ("Not that"). Candidates: on now, or starting within the next half hour;
     * best are followed things, then films, then your usual channels, then the one that fits the time most closely.
     */
    fun answer(
        text: String,
        guide: List<IptvWallProgramme>,
        follows: List<String>,
        usualKeys: Set<String>,
        now: Long,
        skips: Int,
    ): IptvAskAnswer {
        val q = parse(text)
        val pool = guide.filter { (it.onNow(now) || (it.entry.startMs > now && it.entry.startMs <= now + IptvOnNow.SOON_MS)) && matches(it, q, now) }
        if (pool.isEmpty()) return IptvAskAnswer(null, "Nothing on now or starting soon fits that.", 0)
        val ranked = pool.sortedWith(
            compareByDescending<IptvWallProgramme> { IptvDecide.isFollowed(follows, it.entry.title) }
                .thenByDescending { it.entry.kind == IptvProgrammeKind.FILM }
                .thenByDescending { it.channel.primary.key in usualKeys }
                .thenBy { kotlin.math.abs(((it.entry.endMs - maxOf(now, it.entry.startMs)) / 60_000L) - (q.minutes ?: 0)) }
                .thenBy { it.entry.startMs },
        )
        val pick = ranked[skips.mod(ranked.size)]
        val left = (pick.entry.endMs - maxOf(now, pick.entry.startMs)) / 60_000L
        val timing = if (pick.onNow(now)) "On now, $left min left" else "Starts in ${(pick.entry.startMs - now) / 60_000L} min, $left min long"
        val because = buildList {
            if (IptvDecide.isFollowed(follows, pick.entry.title)) add("you follow it")
            if (pick.entry.kind == IptvProgrammeKind.FILM) add("a film")
            if (pick.channel.primary.key in usualKeys) add("a channel you usually watch")
            q.minutes?.let { add("fits $it minutes") }
        }
        val why = if (because.isEmpty()) timing else "$timing; ${because.joinToString(", ")}"
        return IptvAskAnswer(pick, why, ranked.size - 1)
    }
}

/** fork:live-tv-evening - the wall rows of the two sections. */
object IptvEveningRows {
    private fun clock(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(ms).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

    fun order(slots: List<IptvWallProgramme>, approved: Boolean, bedMs: Long): List<IptvWallRow> {
        if (slots.isEmpty()) {
            return listOf(
                IptvWallRow(
                    "Tonight's running order", "to bedtime ${clock(bedMs)}", 3,
                    listOf(IptvWallItem.Note("order:none", "Nothing to line up", "No programme of your lineup (or usual channels) fits before bedtime.")),
                ),
            )
        }
        val rows = slots.mapIndexed { i, p -> IptvWallItem.Programme(p, film = false, tag = "${i + 1}  ${clock(p.entry.startMs)}") }
            .chunked(3).mapIndexed { index, chunk ->
                IptvWallRow(if (index == 0) "Tonight's running order" else null, if (index == 0) "ends before bedtime ${clock(bedMs)}" else null, 3, chunk)
            }
        val button = IptvWallItem.Action(
            if (approved) "order:cancel" else "order:approve",
            if (approved) "Approved" else "Approve",
            if (approved) "select to cancel" else "Nuvio changes channel at each start; any key takes over",
            selected = approved, enabled = true,
        )
        return rows + IptvWallRow(null, null, 3, listOf(button))
    }

    fun ask(text: String, answer: IptvAskAnswer?): List<IptvWallRow> {
        if (answer == null) {
            return listOf(
                IptvWallRow(
                    "Just ask", "one answer", 3,
                    listOf(IptvWallItem.Note("ask:help", "Type in the search box", "For example: something funny, half an hour. Or: a film, an hour. You get one answer.")),
                ),
            )
        }
        val p = answer.programme
        return if (p == null) {
            listOf(IptvWallRow("Just ask", "\"${text.trim()}\"", 3, listOf(IptvWallItem.Note("ask:none", "Nothing fits", answer.why))))
        } else {
            listOf(
                IptvWallRow("Just ask", "\"${text.trim()}\"", 3, listOf(IptvWallItem.Programme(p, film = false, tag = "THE ANSWER"))),
                IptvWallRow(
                    null, null, 3,
                    listOf(
                        IptvWallItem.Note("ask:why", "Why this", answer.why),
                        IptvWallItem.Action("ask:not", "Not that", if (answer.others > 0) "${answer.others} more" else "that was the only one", selected = false, enabled = answer.others > 0),
                    ),
                ),
            )
        }
    }
}
