package com.nuvio.app.features.iptv

/**
 * fork:live-tv-on-demand (Settings > Live TV > From the start, on demand): a programme already under way on live TV,
 * looked up on TMDB, can be played from its first minute through the viewer's add-ons (their TorBox cache and the
 * like) instead of joining it live. A film by its title and year; an episode by the series' exact name and the
 * guide's season and episode numbers; a series without numbers opens on its page to pick the episode.
 */
data class IptvOnDemandQuery(val title: String, val film: Boolean, val year: Int?, val season: Int?, val episode: Int?)

/** What TMDB matched: Stremio ids ("tt0111161", "tt0903747:2:5"). */
data class IptvOnDemandTarget(
    /** "movie" or "series". */
    val type: String,
    val imdbId: String,
    val name: String,
    val season: Int?,
    val episode: Int?,
    val poster: String?,
    val backdrop: String?,
) {
    /** True when it can play straight away: a film, or an episode with its numbers. */
    val playable: Boolean get() = type == "movie" || (season != null && episode != null)
    val videoId: String get() = if (type == "series" && season != null && episode != null) "$imdbId:$season:$episode" else imdbId
}

object IptvOnDemandRules {
    /** Minutes in before joining live is worth a question. */
    const val LATE_MIN = 3
    /** Shorter programmes are not worth a look-up. */
    const val MIN_LENGTH_MIN = 20

    /**
     * Worth offering from the start: under way for [LATE_MIN] minutes or more (or over, and not in the provider's
     * catch-up), long enough, and not something that is only ever live (news, sport, shopping, a Live mark).
     */
    fun worth(entry: IptvEpgEntry, now: Long, catchup: Boolean = false): Boolean {
        if (entry.kind == IptvProgrammeKind.NEWS || entry.kind == IptvProgrammeKind.SPORT || entry.isLive) return false
        if (IptvProgrammeKind.of(entry.title) == IptvProgrammeKind.NEWS || IptvGuideText.isShopping(entry.title)) return false
        if ((entry.endMs - entry.startMs) / 60_000L < MIN_LENGTH_MIN) return false
        val late = entry.startMs <= now - LATE_MIN * 60_000L && entry.endMs > now
        val missed = entry.endMs <= now && !catchup
        return late || missed
    }

    /** What to look up for [entry]; null for a title too short to match safely. */
    fun query(entry: IptvEpgEntry): IptvOnDemandQuery? {
        val film = entry.kind == IptvProgrammeKind.FILM
        val (title, yearInTitle) = if (film) IptvOnNow.filmQuery(entry.title) else entry.title.trim() to null
        if (IptvDecide.normalise(title).length < 3) return null
        val (season, episode) = episodeNumbers(entry.episode)
        return IptvOnDemandQuery(title, film, entry.year ?: yearInTitle, if (film) null else season, if (film) null else episode)
    }

    /** "S2 E5" to (2, 5); anything else to nulls. */
    fun episodeNumbers(label: String?): Pair<Int?, Int?> {
        val match = label?.let { Regex("""S(\d{1,2})\s*E(\d{1,3})""", RegexOption.IGNORE_CASE).find(it) } ?: return null to null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    /** "Started 23 min ago" / "Ended at 21:00". */
    fun lateLine(entry: IptvEpgEntry, now: Long, clock: (Long) -> String): String =
        if (entry.endMs <= now) "Ended at ${clock(entry.endMs)}" else "Started ${((now - entry.startMs) / 60_000L).coerceAtLeast(1)} min ago"

    /** The button that plays it from the start. */
    fun playLabel(target: IptvOnDemandTarget): String = when {
        target.type == "movie" -> "From the start, on demand"
        target.playable -> "S${target.season} E${target.episode} from the start, on demand"
        else -> "Pick the episode, on demand"
    }
}
