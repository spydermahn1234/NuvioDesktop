package com.nuvio.app.features.localaddons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalAddonPlanTest {
    private val bundle = LocalAddonBundle("""C:\Nuvio\resources\local-addons""", windows = true)
    private val data = """C:\Users\nick\AppData\Roaming\Nuvio\local-addons"""
    private val settings = LocalAddonSettings(
        enabled = true,
        keys = LocalAddonKeys(tmdb = " tmdb-key ", tvdb = "tvdb-key"),
        ports = mapOf(LocalAddonServer.Redis to 47350),
        aioStreamsSecret = "a".repeat(64),
    )

    @Test
    fun redisListensOnLoopbackAndKeepsItsFilesInItsDataFolder() {
        val launch = localAddonLaunch(LocalAddonServer.Redis, bundle, data, settings)
        assertEquals("""C:\Nuvio\resources\local-addons\redis\redis-server.exe""", launch.command.first())
        val args = launch.command.drop(1)
        assertEquals("47350", args[args.indexOf("--port") + 1])
        assertEquals("127.0.0.1", args[args.indexOf("--bind") + 1])
        assertEquals("C:/Users/nick/AppData/Roaming/Nuvio/local-addons/redis", args[args.indexOf("--dir") + 1])
        assertEquals("volatile-lru", args[args.indexOf("--maxmemory-policy") + 1])
        assertEquals("""$data\redis""", launch.workingDir)
    }

    @Test
    fun aioMetadataRunsFromItsDataFolderWithItsCodeFromTheInstall() {
        val launch = localAddonLaunch(LocalAddonServer.AioMetadata, bundle, data, settings)
        assertEquals(
            listOf(
                """C:\Nuvio\resources\local-addons\node\node.exe""",
                "--require", """C:\Nuvio\resources\local-addons\loopback.cjs""",
                """C:\Nuvio\resources\local-addons\aiometadata\dist\server\server.js""",
            ),
            launch.command,
        )
        assertEquals("""$data\aiometadata""", launch.workingDir)
        assertEquals("47341", launch.env["PORT"])
        assertEquals("http://127.0.0.1:47341", launch.env["HOST_NAME"])
        assertEquals("redis://127.0.0.1:47350", launch.env["REDIS_URL"])
        assertEquals("sqlite://C:/Users/nick/AppData/Roaming/Nuvio/local-addons/aiometadata/addon/data/db.sqlite", launch.env["DATABASE_URI"])
        assertEquals("tmdb-key", launch.env["BUILT_IN_TMDB_API_KEY"])
        assertEquals("tvdb-key", launch.env["BUILT_IN_TVDB_API_KEY"])
        assertFalse("BUILT_IN_FANART_API_KEY" in launch.env, "a blank key is left out, not passed empty")
    }

    @Test
    fun aioStreamsUsesARelativeDatabaseBecauseDriveLettersBreakItsParser() {
        val launch = localAddonLaunch(LocalAddonServer.AioStreams, bundle, data, settings)
        assertEquals("""$data\aiostreams""", launch.workingDir)
        assertEquals("sqlite://./db.sqlite", launch.env["DATABASE_URI"])
        assertEquals("http://127.0.0.1:47340", launch.env["BASE_URL"])
        assertEquals("a".repeat(64), launch.env["SECRET_KEY"])
        assertEquals("tmdb-key", launch.env["TMDB_API_KEY"])
        assertTrue(launch.command.last().endsWith("""aiostreams\packages\server\dist\server.js"""))
        assertTrue("--require" in launch.command)
    }

    @Test
    fun linuxBundlePathsUseForwardSlashes() {
        val linux = LocalAddonBundle("/opt/nuvio/local-addons", windows = false)
        assertEquals("/opt/nuvio/local-addons/node/node", linux.node)
        assertEquals("/opt/nuvio/local-addons/redis/redis-server", linux.redis)
    }

    @Test
    fun restartWaitsGrowThenGiveUp() {
        assertEquals(listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L), (1..5).map { localAddonRestartDelayMs(it) })
        assertNull(localAddonRestartDelayMs(LOCAL_ADDON_MAX_FAILURES))
    }

    @Test
    fun portsSkipTakenAndBusyOnes() {
        assertEquals(47341, pickLocalAddonPort(47340, setOf(47340)) { true })
        assertEquals(47342, pickLocalAddonPort(47340, emptySet()) { it == 47342 })
        assertNull(pickLocalAddonPort(47340, emptySet()) { false })
    }

    @Test
    fun configurePagesAndSecrets() {
        assertEquals("http://127.0.0.1:47340/stremio/configure", localAddonConfigureUrl(LocalAddonServer.AioStreams, settings))
        assertEquals("http://127.0.0.1:47341/configure", localAddonConfigureUrl(LocalAddonServer.AioMetadata, settings))
        assertNull(localAddonConfigureUrl(LocalAddonServer.Redis, settings))
        assertTrue(isValidAioStreamsSecret("0123456789abcdef".repeat(4)))
        assertFalse(isValidAioStreamsSecret("xyz"))
        assertFalse(isValidAioStreamsSecret("g".repeat(64)))
    }

    @Test
    fun lastLogLinesKeepsTheEnd() {
        assertEquals("c\nd", lastLogLines("a\n\nb\nc\n  \nd\n", max = 2))
    }

    @Test
    fun likelyCauseSkipsStackFramesAndTheNodeVersionLine() {
        val tail = """
            Error [ERR_MODULE_NOT_FOUND]: Cannot find package '@aiostreams/core' imported from /x/user.js
                at #resolve (node:internal/modules/esm/loader:687:17)
                at ModuleJob.syncLink (node:internal/modules/esm/module_job:276:33) {
              code: 'ERR_MODULE_NOT_FOUND'
            }
            Node.js v24.22.2
        """.trimIndent()
        assertEquals("Error [ERR_MODULE_NOT_FOUND]: Cannot find package '@aiostreams/core' imported from /x/user.js", likelyCause(tail))
        assertEquals("Error [ERR_MODULE_NOT_FOUND]: Cannot find package '@aiostreams/core' imported from /x/user.js\n  code: 'ERR_MODULE_NOT_FOUND'", lastLogLines(tail))
        assertEquals("listening", likelyCause("starting\nlistening\n"))
    }
}
