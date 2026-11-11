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
    fun aReadAccessTokenGoesToAioStreamsAsATokenAndNotToAioMetadata() {
        val token = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.c2lnbmF0dXJl"
        val withToken = settings.copy(keys = LocalAddonKeys(tmdb = token))
        val streams = localAddonLaunch(LocalAddonServer.AioStreams, bundle, data, withToken).env
        assertEquals(token, streams["TMDB_ACCESS_TOKEN"])
        assertFalse("TMDB_API_KEY" in streams, "AIOStreams tries TMDB_API_KEY first, so a token there gets 401")
        val meta = localAddonLaunch(LocalAddonServer.AioMetadata, bundle, data, withToken).env
        assertFalse("BUILT_IN_TMDB_API_KEY" in meta, "AIOMetadata sends ?api_key=, which a token cannot pass")
    }

    @Test
    fun tmdbCredentialKinds() {
        assertEquals(TmdbCredential.Empty, tmdbCredentialKind("  "))
        assertEquals(TmdbCredential.ApiKey, tmdbCredentialKind(" 0123456789abcdef0123456789ABCDEF "))
        assertEquals(TmdbCredential.ReadAccessToken, tmdbCredentialKind("eyJa.eyJb.sig"))
        assertEquals(TmdbCredential.Unknown, tmdbCredentialKind("tmdb-key"))
        val apiKey = settings.copy(keys = LocalAddonKeys(tmdb = "0123456789abcdef0123456789abcdef"))
        assertEquals("0123456789abcdef0123456789abcdef", localAddonLaunch(LocalAddonServer.AioStreams, bundle, data, apiKey).env["TMDB_API_KEY"])
        assertEquals("0123456789abcdef0123456789abcdef", localAddonLaunch(LocalAddonServer.AioMetadata, bundle, data, apiKey).env["BUILT_IN_TMDB_API_KEY"])
    }

    @Test
    fun localAddonPorts() {
        assertEquals(47340, localAddonPortOf("http://127.0.0.1:47340/stremio/abc/def/manifest.json"))
        assertEquals(47341, localAddonPortOf(" http://LOCALHOST:47341/x/manifest.json"))
        assertNull(localAddonPortOf("https://aiostreams.elfhosted.com/stremio/manifest.json"))
        assertNull(localAddonPortOf("http://127.0.0.1.evil.example:47340/manifest.json"))
        assertNull(localAddonPortOf("http://192.168.1.5:47340/manifest.json"))
    }

    @Test
    fun unavailableLocalAddonsAreRetriedOnceWhenTheirServerIsReady() {
        val streams = "http://127.0.0.1:47340/stremio/u/p/manifest.json"
        val meta = "http://127.0.0.1:47341/stremio/c/manifest.json"
        val remote = "https://torrentsdb.com/x/manifest.json"
        val addons = listOf(
            InstalledAddonState(streams, loaded = false, refreshing = false, enabled = true),
            InstalledAddonState(meta, loaded = false, refreshing = false, enabled = true),
            InstalledAddonState(remote, loaded = false, refreshing = false, enabled = true),
        )
        // Only AIOMetadata is up yet: only its addon is fetched again; a remote addon never is.
        assertEquals(listOf(meta), localAddonsToRetry(addons, readyPorts = setOf(47341), alreadyRetried = emptySet()))
        // Both up, AIOMetadata's already retried this run: only AIOStreams' now.
        assertEquals(listOf(streams), localAddonsToRetry(addons, setOf(47340, 47341), alreadyRetried = setOf(meta)))
        // Loaded, mid-fetch or switched off: left alone.
        val settled = listOf(
            InstalledAddonState(streams, loaded = true, refreshing = false, enabled = true),
            InstalledAddonState(meta, loaded = false, refreshing = true, enabled = true),
            InstalledAddonState("http://127.0.0.1:47340/other/manifest.json", loaded = false, refreshing = false, enabled = false),
        )
        assertEquals(emptyList(), localAddonsToRetry(settled, setOf(47340, 47341), emptySet()))
    }

    @Test
    fun aioMetadataIsReadyOnlyAfterItsStartUpFinishes() {
        assertEquals("/health/ready", LocalAddonServer.AioMetadata.healthPath)
        val starting = """{"status":"starting","ready":false,"components":{}}"""
        val degraded = """{"status":"degraded","ready":true,"dependencies":{"redis":{"state":"slow"}}}"""
        assertFalse(localAddonHealthIsReady(LocalAddonServer.AioMetadata, 503, starting))
        assertTrue(localAddonHealthIsReady(LocalAddonServer.AioMetadata, 503, degraded))
        assertTrue(localAddonHealthIsReady(LocalAddonServer.AioMetadata, 200, """{"status":"ready","ready":true}"""))
        assertFalse(localAddonHealthIsReady(LocalAddonServer.AioStreams, 503, degraded), "only AIOMetadata's 503 can mean ready")
        assertTrue(localAddonHealthIsReady(LocalAddonServer.AioStreams, 200, ""))
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
