package com.nuvio.app.features.localaddons

/*
 * fork:local-addons - AIOStreams and AIOMetadata run inside Nuvio (Nov 11, 2026).
 *
 * The Windows build ships Node 24, Redis 8 and both servers under <install>\resources\local-addons
 * (built by scripts/local-addons/build-bundle.sh). While the switch is on, Nuvio starts them as
 * child processes on 127.0.0.1 and stops them when it closes. This file is the pure part: which
 * command, folder and environment each server gets, how long to wait before a restart, and the
 * addresses the settings page shows. LocalAddonServers.desktop.kt runs it.
 */

enum class LocalAddonServer(
    val label: String,
    val defaultPort: Int,
    /** Path that answers 200 once the server is ready; null = Redis (checked with PING). */
    val healthPath: String?,
    /** The page where each person configures their addon and gets its manifest address. */
    val configurePath: String?,
) {
    Redis("Cache (Redis)", 47342, null, null),
    AioMetadata("AIOMetadata", 47341, "/health/live", "/configure"),
    AioStreams("AIOStreams", 47340, "/api/v1/status", "/stremio/configure"),
    ;

    /** Start order: Redis before AIOMetadata, which refuses to start without it. */
    val dependsOn: LocalAddonServer? get() = if (this == AioMetadata) Redis else null
}

/** Server-wide keys entered in Nuvio's Settings and handed to the servers at start. */
data class LocalAddonKeys(
    val tmdb: String = "",
    val tvdb: String = "",
    val fanart: String = "",
    val mdblist: String = "",
    val gemini: String = "",
    val rpdb: String = "",
)

data class LocalAddonSettings(
    /** Off by default: new features stay behind a switch until the user is happy with them. */
    val enabled: Boolean = false,
    val keys: LocalAddonKeys = LocalAddonKeys(),
    /** Ports chosen on first start and then kept, because installed addon addresses contain them. */
    val ports: Map<LocalAddonServer, Int> = emptyMap(),
    /** AIOStreams' SECRET_KEY (64 hex characters), made once and kept: it encrypts saved configs. */
    val aioStreamsSecret: String = "",
) {
    fun portOf(server: LocalAddonServer): Int = ports[server] ?: server.defaultPort
}

sealed interface LocalAddonStatus {
    data object Off : LocalAddonStatus
    /** This build has no bundle (a PC build that skipped the bundle script, or macOS). */
    data object NotInBuild : LocalAddonStatus
    data object Starting : LocalAddonStatus
    data class Running(val baseUrl: String) : LocalAddonStatus
    data class Restarting(val reason: String, val inSeconds: Int) : LocalAddonStatus
    data class Failed(val reason: String) : LocalAddonStatus
}

/** Where the bundle's pieces sit, from the folder that holds node/, redis/, aiostreams/, aiometadata/. */
data class LocalAddonBundle(val root: String, val windows: Boolean) {
    val node: String get() = joinPath(root, "node", if (windows) "node.exe" else "node")
    val redis: String get() = joinPath(root, "redis", if (windows) "redis-server.exe" else "redis-server")
    val loopback: String get() = joinPath(root, "loopback.cjs")
    val versions: String get() = joinPath(root, "versions.json")
    fun appDir(server: LocalAddonServer): String = when (server) {
        LocalAddonServer.Redis -> joinPath(root, "redis")
        LocalAddonServer.AioMetadata -> joinPath(root, "aiometadata")
        LocalAddonServer.AioStreams -> joinPath(root, "aiostreams")
    }
}

data class LocalAddonLaunch(
    val command: List<String>,
    val workingDir: String,
    val env: Map<String, String>,
)

/** One server's own data folder under Nuvio's data folder (`<data>\local-addons\<name>`). */
fun localAddonDataDir(dataRoot: String, server: LocalAddonServer): String = joinPath(
    dataRoot,
    when (server) {
        LocalAddonServer.Redis -> "redis"
        LocalAddonServer.AioMetadata -> "aiometadata"
        LocalAddonServer.AioStreams -> "aiostreams"
    },
)

fun localAddonBaseUrl(port: Int): String = "http://127.0.0.1:$port"

fun localAddonConfigureUrl(server: LocalAddonServer, settings: LocalAddonSettings): String? =
    server.configurePath?.let { localAddonBaseUrl(settings.portOf(server)) + it }

/**
 * The command, working folder and environment for [server].
 *
 * - Redis keeps its files in its data folder, listens on loopback only, and is capped at 512 MB
 *   (AIOMetadata gives its cache keys lifetimes, so `volatile-lru` drops the stalest first).
 * - AIOMetadata finds its web pages through the working folder (package.json, dist/client and
 *   public must sit there) and writes its caches to `<working folder>\addon\data`, so it runs from
 *   its data folder, where the runner keeps a copy of those web files; its code still loads from
 *   the install folder.
 * - AIOStreams keeps everything beside its database, and a Windows drive letter does not survive
 *   its `sqlite://` parsing, so it also runs from its data folder with `sqlite://./db.sqlite`.
 * - Both Node servers load loopback.cjs, which pins listen() to 127.0.0.1 (no firewall prompt, not
 *   reachable from other devices).
 */
fun localAddonLaunch(
    server: LocalAddonServer,
    bundle: LocalAddonBundle,
    dataRoot: String,
    settings: LocalAddonSettings,
): LocalAddonLaunch {
    val port = settings.portOf(server)
    val dataDir = localAddonDataDir(dataRoot, server)
    val keys = settings.keys
    return when (server) {
        LocalAddonServer.Redis -> LocalAddonLaunch(
            command = listOf(
                bundle.redis,
                "--port", port.toString(),
                "--bind", "127.0.0.1",
                "--protected-mode", "yes",
                "--dir", forwardSlashes(dataDir),
                "--appendonly", "yes",
                "--save", "3600", "1",
                "--maxmemory", "512mb",
                "--maxmemory-policy", "volatile-lru",
            ),
            workingDir = dataDir,
            env = emptyMap(),
        )
        LocalAddonServer.AioMetadata -> LocalAddonLaunch(
            command = listOf(
                bundle.node,
                "--require", bundle.loopback,
                joinPath(bundle.appDir(server), "dist", "server", "server.js"),
            ),
            workingDir = dataDir,
            env = buildMap {
                put("PORT", port.toString())
                put("HOST_NAME", localAddonBaseUrl(port))
                put("NODE_ENV", "production")
                put("LOG_LEVEL", "info")
                put("DOTENV_CONFIG_QUIET", "true")
                put("UV_THREADPOOL_SIZE", "16")
                put("DATABASE_URI", "sqlite://" + forwardSlashes(joinPath(dataDir, "addon", "data", "db.sqlite")))
                put("REDIS_URL", "redis://127.0.0.1:${settings.portOf(LocalAddonServer.Redis)}")
                // AIOMetadata sends TMDB's key as ?api_key=, which only takes the short API key.
                if (tmdbCredentialKind(keys.tmdb) != TmdbCredential.ReadAccessToken) putKey("BUILT_IN_TMDB_API_KEY", keys.tmdb)
                putKey("BUILT_IN_TVDB_API_KEY", keys.tvdb)
                putKey("BUILT_IN_FANART_API_KEY", keys.fanart)
                putKey("BUILT_IN_MDBLIST_API_KEY", keys.mdblist)
                putKey("BUILT_IN_GEMINI_API_KEY", keys.gemini)
                putKey("BUILT_IN_RPDB_API_KEY", keys.rpdb)
            },
        )
        LocalAddonServer.AioStreams -> LocalAddonLaunch(
            command = listOf(
                bundle.node,
                "--require", bundle.loopback,
                "--max-semi-space-size=8",
                joinPath(bundle.appDir(server), "packages", "server", "dist", "server.js"),
            ),
            workingDir = dataDir,
            env = buildMap {
                put("PORT", port.toString())
                put("BASE_URL", localAddonBaseUrl(port))
                put("NODE_ENV", "production")
                put("SECRET_KEY", settings.aioStreamsSecret)
                put("DATABASE_URI", "sqlite://./db.sqlite")
                // AIOStreams takes either; it tries TMDB_API_KEY first, so a Read Access Token must go in its own variable.
                when (tmdbCredentialKind(keys.tmdb)) {
                    TmdbCredential.ReadAccessToken -> putKey("TMDB_ACCESS_TOKEN", keys.tmdb)
                    TmdbCredential.ApiKey, TmdbCredential.Unknown -> putKey("TMDB_API_KEY", keys.tmdb)
                    TmdbCredential.Empty -> Unit
                }
                putKey("TVDB_API_KEY", keys.tvdb)
            },
        )
    }
}

/**
 * TMDB's API settings page shows two credentials: the **API key** (v3, 32 hex characters) and the much
 * longer **API Read Access Token** (v4, a JWT starting "eyJ"). Either can be pasted into Nuvio's one
 * field, so the kind decides where it goes (see [localAddonLaunch]).
 */
enum class TmdbCredential { Empty, ApiKey, ReadAccessToken, Unknown }

fun tmdbCredentialKind(value: String): TmdbCredential {
    val v = value.trim()
    return when {
        v.isEmpty() -> TmdbCredential.Empty
        v.startsWith("eyJ") && v.count { it == '.' } == 2 -> TmdbCredential.ReadAccessToken
        v.length == 32 && v.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> TmdbCredential.ApiKey
        else -> TmdbCredential.Unknown
    }
}

/**
 * The port of an installed addon that lives on this PC (`http://127.0.0.1:<port>/...` or
 * `http://localhost:<port>/...`), else null.
 */
fun localAddonPortOf(manifestUrl: String): Int? {
    val match = Regex("""^http://(127\.0\.0\.1|localhost):(\d{1,5})(/|$)""", RegexOption.IGNORE_CASE)
        .find(manifestUrl.trim()) ?: return null
    return match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 }
}

/** What the retry rule needs to know about one installed addon. */
data class InstalledAddonState(val manifestUrl: String, val loaded: Boolean, val refreshing: Boolean, val enabled: Boolean)

/**
 * Nuvio loads its addons as soon as it opens, before the servers answer (AIOStreams takes 4 to 20
 * seconds), so those addons come up Unavailable. Once a server is ready, its addons that did not load
 * are fetched again: enabled, not loaded, not mid-fetch, on a ready server's port, and not already
 * retried during this run of that server ([alreadyRetried]), so a config the server rejects is
 * tried once, not in a loop.
 */
fun localAddonsToRetry(
    addons: List<InstalledAddonState>,
    readyPorts: Set<Int>,
    alreadyRetried: Set<String>,
): List<String> = addons
    .filter { it.enabled && !it.loaded && !it.refreshing && it.manifestUrl !in alreadyRetried }
    .filter { localAddonPortOf(it.manifestUrl) in readyPorts }
    .map { it.manifestUrl }
    .distinct()

/** Files AIOMetadata reads from its working folder; the runner copies them there when the bundle changes. */
val aioMetadataWorkingCopy: List<String> = listOf("package.json", "dist/client", "public")

/**
 * Wait before restarting a server that stopped on its own: 2, 5, 15, 30, then 60 seconds. After
 * [LOCAL_ADDON_MAX_FAILURES] stops in a row (each within [LOCAL_ADDON_STABLE_MS] of starting) it is
 * left stopped and shown as failed, so a server that cannot start does not loop all evening.
 */
fun localAddonRestartDelayMs(failuresInRow: Int): Long? = when {
    failuresInRow >= LOCAL_ADDON_MAX_FAILURES -> null
    failuresInRow <= 1 -> 2_000L
    failuresInRow == 2 -> 5_000L
    failuresInRow == 3 -> 15_000L
    failuresInRow == 4 -> 30_000L
    else -> 60_000L
}

/** A run that lasted this long resets the failure count. */
const val LOCAL_ADDON_STABLE_MS = 120_000L
const val LOCAL_ADDON_MAX_FAILURES = 6

/** How long a server may take to answer its health check after starting (AIOMetadata warms caches first). */
fun localAddonStartTimeoutMs(server: LocalAddonServer): Long = when (server) {
    LocalAddonServer.Redis -> 15_000L
    LocalAddonServer.AioMetadata -> 120_000L
    LocalAddonServer.AioStreams -> 90_000L
}

/** First free port at or after [preferred] (up to 20 tries), for a port not yet saved. */
fun pickLocalAddonPort(preferred: Int, taken: Set<Int>, isFree: (Int) -> Boolean): Int? =
    (preferred until preferred + 20).firstOrNull { it !in taken && isFree(it) }

/** A valid AIOStreams SECRET_KEY: exactly 64 hex characters. */
fun isValidAioStreamsSecret(secret: String): Boolean =
    secret.length == 64 && secret.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/**
 * The last [max] useful lines of a server's log, for the settings page when something fails:
 * blank lines, stack frames ("    at ...") and Node's closing "Node.js v24..." line are skipped.
 */
fun lastLogLines(text: String, max: Int = 6): String =
    text.lineSequence()
        .map(String::trimEnd)
        .filter { it.isNotBlank() && !isNoiseLine(it) }
        .toList()
        .takeLast(max)
        .joinToString("\n")

/** The one line most likely to say why a server stopped: the last error line, else the last line. */
fun likelyCause(tail: String): String {
    val lines = tail.lines().map(String::trim).filter { it.isNotEmpty() && !isNoiseLine(it) }
    return lines.lastOrNull { line ->
        listOf("Error", "error", "Cannot ", "cannot ", "EADDRINUSE", "refused", "failed").any(line::contains)
    } ?: lines.lastOrNull().orEmpty()
}

private fun isNoiseLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("at ") || trimmed.startsWith("Node.js v") || trimmed == "}" || trimmed == "^"
}

private fun MutableMap<String, String>.putKey(name: String, value: String) {
    val trimmed = value.trim()
    if (trimmed.isNotEmpty()) put(name, trimmed)
}

private fun joinPath(first: String, vararg rest: String): String {
    val separator = if (first.contains('\\')) '\\' else '/'
    return rest.fold(first.trimEnd('/', '\\')) { acc, part -> acc + separator + part }
}

private fun forwardSlashes(path: String): String = path.replace('\\', '/')
