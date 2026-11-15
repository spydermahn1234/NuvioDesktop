package com.spydermahn.nuvioremote

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/*
 * The app updates itself from the rolling NuvioRemote release on GitHub (android-remote.yml publishes it on every
 * push). On start, at most every few hours, it reads the release, and when its build number is above the installed
 * one it downloads the APK in the background. A card then offers Install; Android shows its own confirm screen the
 * first time (and asks once to let Nuvio Remote install apps). From Android 12, once the app has installed itself,
 * later updates can go through without the confirm screen. Every build is signed with the same key (the repository's
 * keystore secrets), so an update installs over the app and keeps the paired PCs.
 */

/** The release's build number, from the line the workflow writes into its notes ("Build 57."). */
internal fun releaseBuild(notes: String?): Int? =
    notes?.let { Regex("""\bBuild (\d+)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

/** An update is worth fetching when the release is a later build than the one installed. */
internal fun isNewerBuild(release: Int?, installed: Long): Boolean = release != null && release > installed

/** "sha256:ab12..." (GitHub's asset digest) to the hex alone, or null when it is some other kind or missing. */
internal fun sha256Of(digest: String?): String? =
    digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()?.takeIf { it.length == 64 }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val build: Long) : UpdateState
    data class Downloading(val build: Int, val percent: Int) : UpdateState
    data class Ready(val build: Int, val apk: File) : UpdateState
    data class Installing(val build: Int) : UpdateState
    /** Android has to be told once that Nuvio Remote may install apps; Install again after allowing it. */
    data class NeedsPermission(val build: Int, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

object AppUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/spydermahn1234/NuvioDesktop/releases/tags/NuvioRemote"
    private const val APK_NAME = "NuvioRemote.apk"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val PREFS = "nuvio_remote_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_DISMISSED = "dismissed_build"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun installedBuild(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    fun installedName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    /** On start: checks when the last check is a few hours old. [force] (More › Update) checks now. */
    fun check(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val busy = _state.value.let { it is UpdateState.Checking || it is UpdateState.Downloading || it is UpdateState.Installing }
        if (busy) return
        if (!force) {
            // A download already waiting is offered again rather than fetched again.
            if (_state.value is UpdateState.Ready) return
            if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_EVERY_MS) return
        }
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = runCatching { checkNow(app) }.getOrElse { UpdateState.Failed("Couldn't reach GitHub: ${it.message ?: it.javaClass.simpleName}") }
            if (_state.value !is UpdateState.Failed) prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        }
    }

    private fun checkNow(context: Context): UpdateState {
        val installed = installedBuild(context)
        val release = JSONObject(get(RELEASE_API))
        val build = releaseBuild(release.optString("body")) ?: return UpdateState.Failed("The release doesn't say its build number yet.")
        if (!isNewerBuild(build, installed)) {
            cacheDir(context).listFiles()?.forEach { it.delete() }
            return UpdateState.UpToDate(installed)
        }
        val assets = release.optJSONArray("assets")
        val asset = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }.firstOrNull { it.optString("name") == APK_NAME }
            ?: return UpdateState.Failed("The release has no $APK_NAME.")
        val apk = File(cacheDir(context), "NuvioRemote-$build.apk")
        val size = asset.optLong("size", -1L)
        if (!(apk.exists() && apk.length() == size)) {
            cacheDir(context).listFiles()?.forEach { it.delete() }
            download(asset.getString("browser_download_url"), apk, size, build)
        }
        sha256Of(asset.optString("digest").takeIf { it.isNotBlank() })?.let { expected ->
            if (sha256(apk) != expected) {
                apk.delete()
                return UpdateState.Failed("The download was damaged; try again.")
            }
        }
        return UpdateState.Ready(build, apk)
    }

    /** The card shows once per build; Later hides it until the next one (More › Update still offers it). */
    fun dismissed(context: Context, build: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DISMISSED, 0) >= build

    fun dismiss(context: Context, build: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_DISMISSED, build).apply()
    }

    /** Hands the downloaded APK to Android's installer. */
    fun install(context: Context) {
        val ready = when (val s = _state.value) {
            is UpdateState.Ready -> s.build to s.apk
            is UpdateState.NeedsPermission -> s.build to s.apk
            else -> return
        }
        val (build, apk) = ready
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(build, apk)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        _state.value = UpdateState.Installing(build)
        scope.launch {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite(APK_NAME, 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val callback = Intent(context, UpdateInstallReceiver::class.java).putExtra(EXTRA_BUILD, build)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context.applicationContext, id, callback, flags).intentSender)
                }
            }.onFailure { _state.value = UpdateState.Failed("Couldn't start the install: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    internal const val EXTRA_BUILD = "build"

    internal fun installResult(context: Context, intent: Intent) {
        val build = intent.getIntExtra(EXTRA_BUILD, 0)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.UpToDate(build.toLong())
            else -> {
                val apk = File(cacheDir(context), "NuvioRemote-$build.apk")
                _state.value = when (status) {
                    // Cancelled on the confirm screen: offer it again.
                    PackageInstaller.STATUS_FAILURE_ABORTED -> if (apk.exists()) UpdateState.Ready(build, apk) else UpdateState.Idle
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        UpdateState.Failed("This build is signed differently from the one installed. Uninstall Nuvio Remote once and install NuvioRemote.apk from the release; later updates then install by themselves.")
                    PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Not enough space on the phone to update.")
                    else -> UpdateState.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update didn't install.")
                }
            }
        }
    }

    private fun cacheDir(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "NuvioRemote")
        }
        try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, into: File, size: Long, build: Int) {
        _state.value = UpdateState.Downloading(build, 0)
        val part = File(into.parentFile, into.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NuvioRemote")
        }
        try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            val total = if (size > 0) size else connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(build, (done * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (size > 0 && part.length() != size) error("download cut short")
            if (!part.renameTo(into)) error("couldn't save the download")
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Android's answer to an install: its confirm screen to show, or how it went. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AppUpdater.installResult(context, intent)
}

/** The card over the app when an update has downloaded: Install, or Later (until the next build). */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    var hiddenBuild by remember { mutableIntStateOf(0) }
    val (build, line) = when (val s = state) {
        is UpdateState.Ready -> s.build to "Update ready"
        is UpdateState.NeedsPermission -> s.build to "Allow Nuvio Remote to install apps, then tap Install"
        is UpdateState.Installing -> s.build to "Installing…"
        else -> return
    }
    if (hiddenBuild >= build || (state is UpdateState.Ready && AppUpdater.dismissed(context, build))) return
    val g = LocalGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(18.dp), fill = Color(0xF0181A20), edge = g.tintEdge)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = g.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(line, color = g.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Nuvio Remote build $build", color = g.textMuted, fontSize = 12.sp)
        }
        if (state !is UpdateState.Installing) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassTag("Later") {
                    hiddenBuild = build
                    AppUpdater.dismiss(context, build)
                }
                GlassTag("Install", accent = true) { AppUpdater.install(context) }
            }
        }
    }
}

/** More › Update: the version installed, and what checking found. */
fun updateLine(context: Context, state: UpdateState): String {
    val installed = "Build ${AppUpdater.installedBuild(context)}"
    return when (state) {
        UpdateState.Idle -> installed
        UpdateState.Checking -> "$installed · checking…"
        is UpdateState.UpToDate -> "$installed · up to date"
        is UpdateState.Downloading -> "$installed · downloading build ${state.build} (${state.percent}%)"
        is UpdateState.Ready -> "$installed · build ${state.build} ready to install"
        is UpdateState.NeedsPermission -> "$installed · allow installing apps, then Install"
        is UpdateState.Installing -> "$installed · installing build ${state.build}"
        is UpdateState.Failed -> "$installed · ${state.message}"
    }
}

/** More › Device › Update: checks now, or installs what has downloaded. */
@Composable
fun UpdateKey() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    val ready = state is UpdateState.Ready || state is UpdateState.NeedsPermission
    GlassKey(Icons.Rounded.SystemUpdate, if (ready) "Install" else "Update", active = ready) {
        if (ready) AppUpdater.install(context) else AppUpdater.check(context, force = true)
    }
}

/** Under the Device keys: the build installed and what the last check found. */
@Composable
fun UpdateStatusLine() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    val g = LocalGlass.current
    Text(
        updateLine(context, state),
        color = if (state is UpdateState.Failed) g.warning else g.textMuted,
        fontSize = 12.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
