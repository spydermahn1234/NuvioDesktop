package com.nuvio.app.features.localaddons

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * fork:local-addons - runs the bundled Redis, AIOMetadata and AIOStreams as child processes.
 *
 * Bundle: `<install>\resources\local-addons` (or `-Dnuvio.localAddons.dir=<folder>`). Data and
 * logs: `<Nuvio data>\local-addons\`. Each server is started, checked until its health address
 * answers, and restarted with a growing wait if it stops on its own (see [localAddonRestartDelayMs]).
 * Everything stops when Nuvio closes; a process left behind by a crash is stopped on the next start
 * (its pid is kept in `<server>.pid`).
 */
internal actual object LocalAddonHost {
    private val log = Logger.withTag("LocalAddons")
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val bundle: LocalAddonBundle? by lazy {
        val candidates = listOfNotNull(
            System.getProperty("nuvio.localAddons.dir")?.takeIf(String::isNotBlank)?.let(::File),
            System.getProperty("jpackage.app-path")?.takeIf(String::isNotBlank)
                ?.let { File(it).parentFile?.resolve("resources")?.resolve("local-addons") },
        )
        candidates.firstOrNull { dir ->
            val b = LocalAddonBundle(dir.absolutePath, windows)
            File(b.node).isFile && File(b.redis).isFile &&
                File(b.appDir(LocalAddonServer.AioMetadata)).isDirectory &&
                File(b.appDir(LocalAddonServer.AioStreams)).isDirectory
        }?.let { LocalAddonBundle(it.absolutePath, windows) }
            .also { log.i { if (it == null) "No bundle in this build" else "Bundle at ${it.root}" } }
    }

    private val dataRoot: File by lazy { DesktopStorage.rootDir.resolve("local-addons").toFile().apply { mkdirs() } }
    private val logDir: File by lazy { dataRoot.resolve("logs").apply { mkdirs() } }

    actual val bundleFound: Boolean get() = bundle != null
    actual val logFolder: String? get() = runCatching { logDir.absolutePath }.getOrNull()

    private val _statuses = MutableStateFlow(LocalAddonServer.entries.associateWith { LocalAddonStatus.Off as LocalAddonStatus })
    actual val statuses: StateFlow<Map<LocalAddonServer, LocalAddonStatus>> = _statuses.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val jobs = mutableMapOf<LocalAddonServer, Job>()
    private val processes = java.util.concurrent.ConcurrentHashMap<LocalAddonServer, Process>()
    @Volatile private var hookInstalled = false

    private val http: HttpClient by lazy {
        // HTTP/1.1: the default HTTP/2 client sends an h2c upgrade on plain http, which these servers mishandle.
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build()
    }

    actual fun apply() {
        scope.launch {
            lock.withLock {
                stopAll()
                LocalAddonSettingsRepository.ensureLoaded()
                val settings = LocalAddonSettingsRepository.state.value
                val found = bundle
                when {
                    !settings.enabled -> setAll(LocalAddonStatus.Off)
                    found == null -> setAll(LocalAddonStatus.NotInBuild)
                    else -> startAll(found)
                }
            }
        }
    }

    /** Called from Main on startup: starts the servers only when the switch is on. */
    fun startIfEnabled() {
        LocalAddonSettingsRepository.ensureLoaded()
        if (LocalAddonSettingsRepository.state.value.enabled) apply()
    }

    /** Stops everything now (Nuvio is closing). */
    fun shutdown() {
        runCatching { runBlocking { withTimeoutOrNull(8_000) { lock.withLock { stopAll() } } } }
    }

    private suspend fun startAll(bundle: LocalAddonBundle) {
        installShutdownHook()
        val settings = prepareSettings()
        LocalAddonServer.entries.forEach { prepareDataDir(it, bundle) }
        LocalAddonServer.entries.forEach { stopLeftover(it, bundle) }
        val redisUp = scope.launch { superviseServer(LocalAddonServer.Redis, bundle) }
        jobs[LocalAddonServer.Redis] = redisUp
        // AIOStreams needs nothing else; AIOMetadata waits for Redis to answer.
        jobs[LocalAddonServer.AioStreams] = scope.launch { superviseServer(LocalAddonServer.AioStreams, bundle) }
        jobs[LocalAddonServer.AioMetadata] = scope.launch {
            val waited = withTimeoutOrNull(localAddonStartTimeoutMs(LocalAddonServer.Redis) + 5_000) {
                while (_statuses.value[LocalAddonServer.Redis] !is LocalAddonStatus.Running) delay(250)
            }
            if (waited == null) log.w { "Redis is not up; starting AIOMetadata anyway (it will retry)" }
            superviseServer(LocalAddonServer.AioMetadata, bundle)
        }
        log.i { "Started: ports ${LocalAddonServer.entries.joinToString { "${it.label} ${settings.portOf(it)}" }}" }
    }

    /** Saves a port for each server and AIOStreams' secret the first time, so addresses never change. */
    private fun prepareSettings(): LocalAddonSettings {
        val repo = LocalAddonSettingsRepository
        val taken = mutableSetOf<Int>()
        LocalAddonServer.entries.forEach { server ->
            val saved = repo.state.value.ports[server]
            if (saved != null) {
                taken += saved
            } else {
                val port = pickLocalAddonPort(server.defaultPort, taken, ::portIsFree) ?: server.defaultPort
                taken += port
                repo.savePort(server, port)
            }
        }
        if (!isValidAioStreamsSecret(repo.state.value.aioStreamsSecret)) {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            repo.saveSecret(bytes.joinToString("") { "%02x".format(it) })
        }
        return repo.state.value
    }

    private fun prepareDataDir(server: LocalAddonServer, bundle: LocalAddonBundle) {
        val dir = File(localAddonDataDir(dataRoot.absolutePath, server)).apply { mkdirs() }
        if (server != LocalAddonServer.AioMetadata) return
        dir.resolve("addon").resolve("data").mkdirs()
        // AIOMetadata reads its web pages from its working folder: copy them when the bundle changes.
        val version = runCatching { File(bundle.versions).readText() }.getOrDefault("")
        val marker = dir.resolve(".bundle-version")
        if (marker.isFile && marker.readText() == version && dir.resolve("package.json").isFile) return
        val app = File(bundle.appDir(server))
        aioMetadataWorkingCopy.forEach { relative ->
            val from = app.resolve(relative)
            val to = dir.resolve(relative)
            to.deleteRecursively()
            to.parentFile?.mkdirs()
            if (from.isDirectory) from.copyRecursively(to, overwrite = true) else from.copyTo(to, overwrite = true)
        }
        marker.writeText(version)
        log.i { "AIOMetadata web files copied to $dir" }
    }

    private suspend fun superviseServer(server: LocalAddonServer, bundle: LocalAddonBundle) {
        var failuresInRow = 0
        while (scope.isActive) {
            val settings = LocalAddonSettingsRepository.state.value
            val launch = localAddonLaunch(server, bundle, dataRoot.absolutePath, settings)
            val logFile = rotatedLog(server)
            setStatus(server, LocalAddonStatus.Starting)
            val startedAt = System.currentTimeMillis()
            val process = try {
                ProcessBuilder(launch.command)
                    .directory(File(launch.workingDir))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    .apply {
                        environment().remove("NODE_OPTIONS")
                        environment().putAll(launch.env)
                    }
                    .start()
            } catch (e: Exception) {
                log.e(e) { "${server.label} could not start" }
                setStatus(server, LocalAddonStatus.Failed(e.message ?: "could not start"))
                return
            }
            processes[server] = process
            runCatching { pidFile(server).writeText("${process.pid()}\n${launch.command.first()}") }
            log.i { "${server.label} started (pid ${process.pid()}, port ${settings.portOf(server)})" }

            val ready = withTimeoutOrNull(localAddonStartTimeoutMs(server)) {
                while (process.isAlive && !isHealthy(server, settings.portOf(server))) delay(500)
                process.isAlive
            } ?: false
            if (ready) {
                setStatus(server, LocalAddonStatus.Running(localAddonBaseUrl(settings.portOf(server))))
                log.i { "${server.label} ready in ${System.currentTimeMillis() - startedAt} ms" }
            } else if (process.isAlive) {
                log.w { "${server.label} did not answer within ${localAddonStartTimeoutMs(server) / 1000} s; still waiting" }
            }

            val exit = runCatching { waitFor(process) }.getOrDefault(-1)
            processes.remove(server, process)
            if (!scope.isActive || _statuses.value[server] == LocalAddonStatus.Off) return
            val ranFor = System.currentTimeMillis() - startedAt
            failuresInRow = if (ranFor >= LOCAL_ADDON_STABLE_MS) 1 else failuresInRow + 1
            val tail = runCatching { lastLogLines(logFile.readText().takeLast(8_000), max = 12) }.getOrDefault("")
            log.w { "${server.label} stopped (exit $exit after ${ranFor / 1000} s, ${failuresInRow} in a row):\n$tail" }
            val wait = localAddonRestartDelayMs(failuresInRow)
            if (wait == null) {
                setStatus(server, LocalAddonStatus.Failed(tail.ifBlank { "stopped (exit $exit)" }))
                return
            }
            setStatus(server, LocalAddonStatus.Restarting(likelyCause(tail), (wait / 1000).toInt()))
            delay(wait)
        }
    }

    private suspend fun waitFor(process: Process): Int {
        while (!process.waitFor(1, TimeUnit.SECONDS)) {
            if (!scope.isActive) return -1
            delay(0)
        }
        return process.exitValue()
    }

    private fun stopAll() {
        if (processes.isEmpty() && jobs.isEmpty()) return
        setAll(LocalAddonStatus.Off)
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        // Redis first asked to save and shut down (it keeps AIOMetadata's cache across restarts).
        val redisPort = LocalAddonSettingsRepository.state.value.portOf(LocalAddonServer.Redis)
        listOf(LocalAddonServer.AioMetadata, LocalAddonServer.AioStreams, LocalAddonServer.Redis).forEach { server ->
            val process = processes.remove(server) ?: return@forEach
            if (server == LocalAddonServer.Redis) redisCommand(redisPort, "SHUTDOWN")
            if (!process.waitFor(if (server == LocalAddonServer.Redis) 3 else 0, TimeUnit.SECONDS)) {
                process.descendants().forEach { it.destroy() }
                process.destroy()
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            runCatching { pidFile(server).delete() }
            log.i { "${server.label} stopped" }
        }
    }

    /** A process from a run that ended in a crash would hold the port: stop it if it is ours. */
    private fun stopLeftover(server: LocalAddonServer, bundle: LocalAddonBundle) {
        val file = pidFile(server)
        if (!file.isFile) return
        runCatching {
            val lines = file.readLines()
            val pid = lines.firstOrNull()?.trim()?.toLongOrNull() ?: return@runCatching
            val handle = ProcessHandle.of(pid).orElse(null) ?: return@runCatching
            val command = handle.info().command().orElse("")
            if (handle.isAlive && command.isNotEmpty() && File(command).parentFile?.parentFile?.absolutePath == File(bundle.root).absolutePath) {
                log.w { "Stopping ${server.label} left over from the last run (pid $pid)" }
                handle.descendants().forEach { it.destroy() }
                handle.destroy()
                handle.onExit().get(3, TimeUnit.SECONDS)
            }
        }
        file.delete()
    }

    private fun installShutdownHook() {
        if (hookInstalled) return
        hookInstalled = true
        Runtime.getRuntime().addShutdownHook(Thread({ shutdown() }, "nuvio-local-addons-shutdown"))
    }

    private fun isHealthy(server: LocalAddonServer, port: Int): Boolean {
        val path = server.healthPath ?: return redisCommand(port, "PING")?.startsWith("+PONG") == true
        return runCatching {
            val request = HttpRequest.newBuilder(URI.create(localAddonBaseUrl(port) + path))
                .timeout(Duration.ofSeconds(3)).GET().build()
            http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..299
        }.getOrDefault(false)
    }

    private fun redisCommand(port: Int, command: String): String? = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 1_000)
            socket.soTimeout = 1_500
            socket.getOutputStream().apply { write("$command\r\n".toByteArray()); flush() }
            socket.getInputStream().bufferedReader().readLine()
        }
    }.getOrNull()

    private fun portIsFree(port: Int): Boolean = runCatching {
        ServerSocket(port, 0, InetAddress.getByName("127.0.0.1")).use { true }
    }.getOrDefault(false)

    /** One log per server, started fresh past 5 MB (the previous one kept as `.old`). */
    private fun rotatedLog(server: LocalAddonServer): File {
        val file = logDir.resolve("${server.name.lowercase()}.log")
        if (file.length() > 5L * 1024 * 1024) {
            val old = logDir.resolve("${server.name.lowercase()}.old.log")
            old.delete()
            file.renameTo(old)
        }
        return file
    }

    private fun pidFile(server: LocalAddonServer): File = dataRoot.resolve("${server.name.lowercase()}.pid")

    private fun setStatus(server: LocalAddonServer, status: LocalAddonStatus) {
        _statuses.update { it + (server to status) }
    }

    private fun setAll(status: LocalAddonStatus) {
        _statuses.value = LocalAddonServer.entries.associateWith { status }
    }
}
