package com.nuvio.app.features.iptv

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.NuvioTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** fork:live-tv-triage - the three questions, and what the Live TV tab shows after them. */
@OptIn(ExperimentalTestApi::class)
class IptvTriageScreenTest {

    @Test
    fun `answering the questions narrows the tab, and search still finds the rest`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/list.m3u" -> MockResponse().setBody(
                    """
                    #EXTM3U
                    #EXTINF:-1 group-title="UK | Entertainment",BBC One
                    http://h/bbc1.ts
                    #EXTINF:-1 group-title="UK | Entertainment",Dave
                    http://h/dave.ts
                    #EXTINF:-1 group-title="UK | Sports",Sky Sports Main Event
                    http://h/ssme.ts
                    #EXTINF:-1 group-title="FR | Info",BFM TV
                    http://h/bfm.ts
                    #EXTINF:-1 group-title="FR | Cinema",Canal Cinema
                    http://h/canal.ts
                    #EXTINF:-1 group-title="US | News",CNN
                    http://h/cnn.ts
                    #EXTINF:-1 group-title="US | Shopping",QVC
                    http://h/qvc.ts
                    #EXTINF:-1 group-title="24/7 Movies",Loop One
                    http://h/loop.ts
                    """.trimIndent(),
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        IptvRepository.onProfileChanged()
        IptvRepository.settings.value.playlists.forEach { IptvRepository.removePlaylist(it.id) }
        val playlist = IptvRepository.addPlaylist(
            IptvPlaylist(id = "", type = IptvPlaylistType.M3u, name = "Test TV", url = server.url("/list.m3u").toString()),
        )
        try {
            runBlocking { IptvRepository.refresh(playlist.id, force = true) }
            assertNotNull(IptvRepository.contentState(playlist.id).value.content)

            runComposeUiTest {
                setContent {
                    NuvioTheme(darkTheme = true, appTheme = AppTheme.OCEAN) {
                        IptvScreen(onOpenSettings = {})
                    }
                }
                fun shown(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
                fun click(text: String) {
                    onAllNodesWithText(text)[0].performClick()
                    waitForIdle()
                }
                fun openTriage() {
                    onAllNodesWithContentDescription("Triage: choose what Live TV shows")[0].performClick()
                    waitForIdle()
                }
                waitForIdle()
                assertTrue(shown("FR | Info"), "before the triage every category is in the sidebar")

                openTriage()
                assertTrue(shown("Which countries do you watch?"))
                assertTrue(shown("No country marked"))
                click("United Kingdom")
                assertTrue(shown("3"), "three UK channels left")
                saveScreenshot("iptv-triage-countries.png")
                click("Next: kinds")

                assertTrue(shown("Which of these would you ever watch?"))
                assertTrue(shown("Entertainment and general"))
                assertTrue(!shown("Shopping"), "a kind the chosen countries do not have is not asked about")
                click("Sport")
                saveScreenshot("iptv-triage-kinds.png")
                click("Next: your first ten")

                assertTrue(shown("Your first ten"))
                assertTrue(shown("Dave"))
                assertTrue(!shown("Sky Sports Main Event"))
                click("BBC One")
                saveScreenshot("iptv-triage-first-ten.png")
                click("Done")

                // Back on the tab: only what was kept, with BBC One starred.
                assertEquals(IptvTriage(setOf("GB"), setOf("Entertainment")), IptvRepository.settings.value.triage[playlist.id])
                assertTrue(IptvRepository.settings.value.favourites.any { it.startsWith(playlist.id) })
                assertTrue(shown("UK | Entertainment"))
                assertTrue(!shown("UK | Sports"))
                assertTrue(!shown("FR | Info"))
                assertTrue(shown("Dave"))
                assertTrue(!shown("CNN"))
                saveScreenshot("iptv-triage-after.png")

                // The search box still reaches a channel the triage left out.
                onAllNodesWithContentDescription("Filter channels")[0].performClick()
                waitForIdle()
                onAllNodes(hasSetTextAction())[0].performTextInput("cnn")
                waitForIdle()
                assertTrue(shown("CNN"))
                onRoot().performKeyInput { pressKey(Key.Escape) }
                onRoot().performKeyInput { pressKey(Key.Escape) }
                waitForIdle()

                // The keyboard drives the questions, and Back on the first one leaves without saving.
                openTriage()
                assertTrue(shown("Show everything"), "offered once a triage exists")
                listOf(Key.DirectionDown, Key.DirectionRight, Key.Enter).forEach { key -> onRoot().performKeyInput { pressKey(key) } }
                waitForIdle()
                saveScreenshot("iptv-triage-keyboard.png")
                onRoot().performKeyInput { pressKey(Key.Escape) }
                waitForIdle()
                assertTrue(shown("UK | Entertainment"), "back on the tab")
                assertEquals(setOf("GB"), IptvRepository.settings.value.triage[playlist.id]?.regions, "unsaved")

                // Show everything clears it.
                openTriage()
                click("Show everything")
                assertNull(IptvRepository.settings.value.triage[playlist.id])
                assertTrue(shown("FR | Info"))
            }
        } finally {
            IptvRepository.setTriage(playlist.id, null)
            IptvRepository.removePlaylist(playlist.id)
            server.shutdown()
        }
    }

    private fun ComposeUiTest.saveScreenshot(name: String) {
        runCatching {
            val dir = File("build/test-screenshots").apply { mkdirs() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, name))
        }
    }
}
