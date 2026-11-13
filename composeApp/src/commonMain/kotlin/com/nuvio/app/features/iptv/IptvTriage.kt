package com.nuvio.app.features.iptv

import kotlinx.serialization.Serializable

/*
 * fork:live-tv-triage - cutting a 12,000-channel playlist down once, by answering questions instead of scrolling
 * (the Triage button in the Live TV header; design-notes/live-tv-less-to-choose.md, screen 1). The answers pick
 * countries and kinds of channel; categories outside them leave the sidebar, All channels and the On Now wall.
 * Nothing is deleted: the search box still finds every channel, and favourites and recents are never hidden.
 * This file is the rules, pure Kotlin (IptvTriageTest); IptvTriageScreen.kt asks the questions.
 */

/**
 * What a playlist's triage kept: [regions] are ISO country codes plus [IptvTriageRules.NO_REGION], [kinds] are
 * [IptvChannelKind] names. A category is shown when both its country and its kind are in.
 */
@Serializable
data class IptvTriage(val regions: Set<String> = emptySet(), val kinds: Set<String> = emptySet())

/** What a provider category is for, read off its name. */
enum class IptvChannelKind(val label: String) {
    Entertainment("Entertainment and general"),
    Sport("Sport"),
    Films("Films"),
    News("News"),
    Documentaries("Documentaries"),
    Kids("Kids"),
    Music("Music"),
    Radio("Radio"),
    Shopping("Shopping"),
    Religious("Religious"),
    Adult("Adult"),
    Loops("24/7 loops"),
    ;

    companion object {
        /** Ticked when the kinds question is first asked. */
        val DEFAULT_ON: Set<IptvChannelKind> = setOf(Entertainment, Sport, Films, News, Documentaries, Kids, Music)

        fun byName(name: String): IptvChannelKind? = entries.firstOrNull { it.name == name }
    }
}

object IptvTriageRules {
    /** The "country" of categories that name none ("VIP Sports", "24/7 Shows"). */
    const val NO_REGION = "none"

    /** One choice of a question, with how many channels it stands for. */
    data class Option(val id: String, val channels: Int)

    private val adult = listOf("adult", "xxx", "18+", "+18", "porn")
    private val loops = listOf("24/7", "24-7", "24 7", "247")
    private val shopping = listOf("shop")
    private val religious = listOf("relig", "christian", "islam", "gospel", "church", "quran", "faith", "bible")
    private val kids = listOf("kid", "child", "cartoon", "junior", "enfant", "kinder", "bambini", "infantil")
    private val documentaries = listOf("docu", "nature", "history", "science", "discovery", "knowledge", "wildlife")
    private val sport = Regex("""\b(sports?|ppv|football|futbol|soccer|rugby|cricket|tennis|golf|boxing|ufc|wwe|nba|nfl|nhl|mlb|f1|motogp|racing|espn|dazn|bein|eurosport|fight|deportes?)\b""")
    private val films = Regex("""\b(movies?|films?|filme|cinema|cine|kino)\b""")
    private val news = Regex("""\b(news|noticias|nachrichten|info|actualit\w*)\b""")
    private val music = Regex("""\b(music|musik|musique|musica|mtv)\b""")
    private val radio = Regex("""\bradios?\b""")

    /**
     * The kind a category name points to. The narrow kinds are tried first, so "24/7 Movies" is a loop and
     * "Kids Movies" is for kids; a name that says nothing is Entertainment and general.
     */
    fun kindOf(categoryName: String): IptvChannelKind {
        val text = categoryName.lowercase()
        return when {
            adult.any { it in text } -> IptvChannelKind.Adult
            loops.any { it in text } -> IptvChannelKind.Loops
            shopping.any { it in text } -> IptvChannelKind.Shopping
            religious.any { it in text } -> IptvChannelKind.Religious
            kids.any { it in text } -> IptvChannelKind.Kids
            sport.containsMatchIn(text) -> IptvChannelKind.Sport
            films.containsMatchIn(text) -> IptvChannelKind.Films
            news.containsMatchIn(text) -> IptvChannelKind.News
            documentaries.any { it in text } -> IptvChannelKind.Documentaries
            music.containsMatchIn(text) -> IptvChannelKind.Music
            radio.containsMatchIn(text) -> IptvChannelKind.Radio
            else -> IptvChannelKind.Entertainment
        }
    }

    private fun regionOf(category: IptvCategory, categoryRegions: Map<String, String>): String = categoryRegions[category.id] ?: NO_REGION

    private fun size(content: IptvPlaylistContent, category: IptvCategory): Int = content.byCategory[category.id]?.size ?: 0

    /** The countries question: every country the categories name, the biggest first, then "no country". */
    fun regionOptions(content: IptvPlaylistContent, categoryRegions: Map<String, String>): List<Option> =
        content.categories.groupBy { regionOf(it, categoryRegions) }
            .map { (region, categories) -> Option(region, categories.sumOf { size(content, it) }) }
            .sortedWith(compareBy<Option> { it.id == NO_REGION }.thenByDescending { it.channels }.thenBy { it.id })

    /** The kinds question, counted inside the chosen [regions]; a kind with no channels there is not asked about. */
    fun kindOptions(content: IptvPlaylistContent, categoryRegions: Map<String, String>, regions: Set<String>): List<Option> {
        val counts = content.categories.filter { regionOf(it, categoryRegions) in regions }
            .groupBy { kindOf(it.name) }
            .mapValues { (_, categories) -> categories.sumOf { size(content, it) } }
        return IptvChannelKind.entries.mapNotNull { kind -> counts[kind]?.takeIf { it > 0 }?.let { Option(kind.name, it) } }
    }

    /** The ids of the categories [triage] keeps. */
    fun keptCategories(content: IptvPlaylistContent, triage: IptvTriage, categoryRegions: Map<String, String>): Set<String> =
        content.categories.filter { regionOf(it, categoryRegions) in triage.regions && kindOf(it.name).name in triage.kinds }
            .mapTo(HashSet()) { it.id }

    /** How many channels sit in [categories]. */
    fun channelCount(content: IptvPlaylistContent, categories: Set<String>): Int = categories.sumOf { content.byCategory[it]?.size ?: 0 }

    /**
     * The channels offered for "your first ten": what is left after the questions, the ones already watched most
     * first, then the starred, then the playlist's own order. [plays] and [favourites] are channel refs.
     */
    fun firstTenCandidates(
        channels: List<IptvMergedChannel>,
        refOf: (IptvChannel) -> String,
        plays: List<String>,
        favourites: Collection<String>,
        limit: Int = 30,
    ): List<IptvMergedChannel> {
        val watched = plays.groupingBy { it }.eachCount()
        val starred = favourites.toHashSet()
        fun playCount(channel: IptvMergedChannel): Int = channel.sources.sumOf { watched[refOf(it)] ?: 0 }
        fun isStarred(channel: IptvMergedChannel): Boolean = channel.sources.any { refOf(it) in starred }
        // sortedWith is stable: channels that tie keep the playlist's order.
        return channels.sortedWith(compareByDescending<IptvMergedChannel> { playCount(it) }.thenByDescending { isStarred(it) }).take(limit)
    }
}
