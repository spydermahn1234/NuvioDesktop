package com.nuvio.app.features.home

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.IptvPlaylist
import com.nuvio.app.features.iptv.IptvPlaylistContent
import com.nuvio.app.features.iptv.IptvPlaylistType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTvHomeRowTest {
    private val playlist = IptvPlaylist(id = "p1", type = IptvPlaylistType.M3u, name = "Home")
    private val bbc = IptvChannel(uid = 1, key = "bbc1", num = 1, name = "BBC One", logo = "https://logo/bbc.png", categoryId = "uk")
    private val itv = IptvChannel(uid = 2, key = "itv1", num = 2, name = "", categoryId = "uk")
    private val content = IptvPlaylistContent(playlist = playlist, categories = emptyList(), channels = listOf(bbc, itv))
    private val now = 1_000_000L

    @Test
    fun cardsFollowTheFavouritesOrderAndSkipWhatIsNotLoaded() {
        val cards = LiveTvHomeRules.cards(
            favourites = listOf("p1:itv1", "p2:sky", "p1:gone", "p1:bbc1", "broken"),
            contents = mapOf("p1" to content),
            guide = { _, _ -> emptyList() },
            now = now,
        )
        assertEquals(listOf("p1:itv1", "p1:bbc1"), cards.map { it.ref })
        assertEquals("Channel 2", cards[0].name)
        assertNull(cards[0].logo)
        assertEquals("https://logo/bbc.png", cards[1].logo)
    }

    @Test
    fun theProgrammeOnNowIsTheOneAiringAtThisMoment() {
        val guide = listOf(
            IptvEpgEntry(title = "Earlier", startMs = now - 2_000, endMs = now - 1),
            IptvEpgEntry(title = "News", startMs = now - 1_000, endMs = now + 60_000, image = "https://pic"),
            IptvEpgEntry(title = "Later", startMs = now + 60_000, endMs = now + 120_000),
        )
        val card = LiveTvHomeRules.cards(listOf("p1:bbc1"), mapOf("p1" to content), { _, _ -> guide }, now).single()
        assertEquals("News", card.nowTitle)
        assertEquals(now + 60_000, card.nowEndMs)
        assertEquals("https://pic", card.nowImage)
        assertTrue(LiveTvHomeRules.nowLine(card)!!.startsWith("News · until "))
    }

    @Test
    fun noCardsNoRowAndCardsPlayAsLiveTv() {
        assertNull(LiveTvHomeRules.section(emptyList(), "Live TV", ""))
        val card = LiveTvHomeCard("p1:bbc1", "BBC One", null, null, null, null)
        val section = LiveTvHomeRules.section(listOf(card), "Live TV", "")!!
        assertEquals(LIVE_TV_HOME_SECTION_KEY, section.key)
        val item = section.items.single()
        assertTrue(item.isLiveTvHomeCard())
        assertTrue(item.isOffCatalogHeroItem())
        assertEquals("p1:bbc1", item.id)
        assertNull(item.description)
    }

    @Test
    fun theRowLeadsTheEnabledRows() {
        val card = LiveTvHomeCard("p1:bbc1", "BBC One", null, null, null, null)
        val section = LiveTvHomeRules.section(listOf(card), "Live TV", "")!!
        val items = buildEnabledHomeItems(
            settingsItems = listOf(HomeCatalogSettingsItem(key = "addon:top", defaultTitle = "Top", addonName = "A")),
            effectiveSections = listOf(section),
        )
        assertEquals(listOf(LIVE_TV_HOME_SECTION_KEY, "addon:top"), items.map { it.key })
    }
}
