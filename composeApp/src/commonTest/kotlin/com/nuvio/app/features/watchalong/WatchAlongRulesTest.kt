package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchAlongRulesTest {
    private val track = AlongTrack(
        title = "Film",
        moments = listOf(
            AlongMoment(1_000, AlongKind.Trivia, "One", "a"),
            AlongMoment(5_000, AlongKind.Making, "Two", "b"),
            AlongMoment(9_000, AlongKind.Music, "Three", "c"),
        ),
        people = listOf(
            AlongPerson("Ellie", "Laura Dern", firstMs = 4_000),
            AlongPerson("Ian", "Jeff Goldblum", firstMs = 8_000),
            AlongPerson("Nobody", "Unknown", firstMs = -1),
        ),
    )

    @Test
    fun nothingIsShownBeforeTheFilmReachesIt() {
        assertEquals(listOf("One", "Two"), WatchAlongRules.revealed(track, 5_000).map { it.title })
        assertEquals(emptyList(), WatchAlongRules.revealed(track, 999))
        assertEquals(listOf("Ellie"), WatchAlongRules.peopleSoFar(track, 5_000).map { it.character })
        // A person with no known first time is never listed.
        assertEquals(listOf("Ellie", "Ian"), WatchAlongRules.peopleSoFar(track, 99_000_000).map { it.character })
    }

    @Test
    fun onlyNormalPlayPopsACard() {
        assertEquals("Two", WatchAlongRules.due(track, 4_000, 5_000, emptySet())?.title)
        assertNull(WatchAlongRules.due(track, 4_000, 20_000, emptySet()), "a seek fires nothing")
        assertNull(WatchAlongRules.due(track, 6_000, 5_000, emptySet()), "going back fires nothing")
        assertNull(WatchAlongRules.due(track, 4_000, 5_000, setOf(5_000L)), "a card is shown once")
    }

    @Test
    fun theTvGetsThinnedCardsCastCloserTogether() {
        val moments = listOf(
            AlongMoment(0, AlongKind.Cast, "A", "a"),
            AlongMoment(100_000, AlongKind.Cast, "B", "b"),
            AlongMoment(180_000, AlongKind.Trivia, "C", "c"),
            AlongMoment(8 * 60_000L, AlongKind.Trivia, "D", "d"),
        )
        assertEquals(listOf("A", "B", "D"), WatchAlongRules.forTv(moments, AlongDensity.Normal).map { it.title })
    }

    @Test
    fun aCardWaitsForAQuietGap() {
        val cues = listOf(
            AlongCue(0, "Hi"),
            AlongCue(2_000, "Hello there"),
            AlongCue(3_000, "x"),
            AlongCue(20_000, "y"),
        )
        assertEquals(4_555L, WatchAlongRules.placeInQuiet(2_500, cues))
        assertEquals(123L, WatchAlongRules.placeInQuiet(123, emptyList()))
    }

    @Test
    fun charactersAreCalledByTheirNames() {
        assertEquals(listOf("Ellie", "Sattler"), WatchAlongRules.callNames("Dr. Ellie Sattler"))
        assertEquals(listOf("Rose"), WatchAlongRules.callNames("Young Rose / Rose DeWitt Bukater"))
        assertEquals(emptyList(), WatchAlongRules.callNames("Himself"))
        val cues = listOf(AlongCue(1_000, "I rose early"), AlongCue(2_000, "Rose, come here"))
        assertEquals(listOf(2_000L), WatchAlongRules.mentionTimes(listOf("Rose"), cues))
    }

    @Test
    fun theOfflineTrackIntroducesTheCastAsTheyAreNamed() {
        val cues = listOf(
            AlongCue(10_000, "Alan, look!"),
            AlongCue(60_000, "Ellie is here"),
            AlongCue(200_000, "Alan again"),
        )
        val built = WatchAlongRules.offline(
            title = "Jurassic Park",
            cast = listOf(
                WatchAlongRules.CastEntry("Ellie Sattler", "Laura Dern", "p.jpg"),
                WatchAlongRules.CastEntry("Alan Grant", "Sam Neill", null),
                WatchAlongRules.CastEntry("Nobody Here", "X Y", null),
            ),
            cues = cues,
            facts = listOf("A fact."),
            durationMs = 1_000_000,
        )
        assertEquals(listOf("Ellie Sattler", "Alan Grant"), built.people.map { it.character })
        assertEquals(listOf(10_000L, 200_000L), built.people[1].mentions)
        assertEquals(3, built.moments.size)
        val first = built.moments.first()
        assertEquals("Played by Sam Neill.", first.text)
        assertEquals(12_105L, first.atMs, "moved into the quiet after the line")
        assertEquals(62_215L, built.moments[1].atMs, "an introduction never comes before the name")
        assertEquals(475_000L, built.moments.last().atMs)
        assertTrue(built.timedBySubtitles)
        assertEquals(AlongOrigin.Offline, built.origin)

        assertEquals(listOf("Alan Grant", "Ellie Sattler"), WatchAlongRules.aroundNow(built, 205_000).map { it.character })
        assertEquals(listOf("Alan Grant"), WatchAlongRules.aroundNow(built, 50_000).map { it.character })
    }

    @Test
    fun clocksReadBothWays() {
        assertEquals(3_723_000L, WatchAlongRules.parseClock("1:02:03"))
        assertEquals(3_723_000L, WatchAlongRules.parseClock("62:03"))
        assertEquals(90_000L, WatchAlongRules.parseClock("90"))
        assertNull(WatchAlongRules.parseClock("x"))
        assertEquals("1:02:03", WatchAlongRules.clock(3_723_000))
        assertEquals("1:05", WatchAlongRules.clock(65_000))
    }

    @Test
    fun theReplyDropsRiskyUnsureAndLateNotes() {
        val raw = """
            ```json
            {"people":[{"character":"Ellie Sattler","actor":"Laura Dern","first_seen":"0:12:30","note":"A palaeobotanist."}],
             "moments":[
              {"at":"0:20:00","kind":"making","title":"Real rain","text":"The storm was a rain rig.","people":["Ellie Sattler"],"spoiler_risk":"none","confidence":0.9},
              {"at":"0:10:00","kind":"music","title":"Theme","text":"John Williams.","confidence":0.95},
              {"at":"1:50:00","kind":"trivia","title":"Ending","text":"Spoiler","spoiler_risk":"high","confidence":0.9},
              {"at":"0:30:00","kind":"place","title":"Kauai","text":"Unsure","confidence":0.3},
              {"at":"9:00:00","kind":"trivia","title":"Past end","text":"x","confidence":1}
             ]}
            ```
        """.trimIndent()
        val parsed = parseWatchAlong(raw, "Jurassic Park", 7_200_000, timedBySubtitles = true)!!
        assertEquals(listOf("Theme", "Real rain"), parsed.moments.map { it.title })
        assertEquals(listOf(AlongKind.Music, AlongKind.Making), parsed.moments.map { it.kind })
        assertEquals(listOf("Ellie Sattler"), parsed.moments[1].people)
        assertEquals(750_000L, parsed.people.single().firstMs)
        assertEquals(AlongOrigin.Ai, parsed.origin)
        assertNull(parseWatchAlong("no json here", "x", 0, false))
        assertEquals(AlongKind.Music, AlongKind.fromWire("song"))
    }

    @Test
    fun subtitlesAreCondensedWithTimes() {
        val text = condenseSubtitles(listOf(AlongCue(0, "a"), AlongCue(1_000, "b"), AlongCue(10_000, "c")))
        assertEquals("0:00  a b\n0:10  c\n", text)
        assertTrue(condenseSubtitles(emptyList()).startsWith("(none available"))
    }

    @Test
    fun theSubtitleInTheWantedLanguageIsRead() {
        val subs = listOf("fre" to "a", "eng" to "b", "de" to "c")
        assertEquals("c", WatchAlongRules.pickSubtitle(subs, { it.first }, "de")?.second)
        assertEquals("b", WatchAlongRules.pickSubtitle(subs, { it.first }, "ja")?.second)
        assertNull(WatchAlongRules.pickSubtitle(emptyList<Pair<String, String>>(), { it.first }, "en"))
    }

    @Test
    fun theLiveSessionGreetsPopsAndFillsThePausePanel() {
        val scripts = mutableListOf<String>()
        WatchAlongLive.hud = { scripts += it }
        WatchAlongLive.begin("Film")
        WatchAlongLive.ready(track)
        WatchAlongLive.tick(500, playing = true)
        assertTrue(scripts.last().contains("spoiler-free"), "the first tick says hello")
        WatchAlongLive.tick(1_500, playing = true)
        assertTrue(scripts.last().contains("\"One\""))
        WatchAlongLive.tick(5_200, playing = false)
        assertTrue(scripts.last().startsWith("window.nuvioSetAlongPanel"))
        assertTrue(scripts.last().contains("Laura Dern"))
        assertEquals(2, WatchAlongLive.remote.value?.moments?.size)
        WatchAlongLive.end()
        assertNull(WatchAlongLive.remote.value)
        WatchAlongLive.hud = null
    }
}
