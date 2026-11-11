package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.localaddons.LocalAddonHost
import com.nuvio.app.features.localaddons.LocalAddonKeys
import com.nuvio.app.features.localaddons.LocalAddonServer
import com.nuvio.app.features.localaddons.LocalAddonSettingsRepository
import com.nuvio.app.features.localaddons.LocalAddonStatus
import com.nuvio.app.features.localaddons.localAddonBaseUrl
import com.nuvio.app.features.localaddons.localAddonConfigureUrl
import kotlinx.coroutines.launch

/**
 * fork:local-addons - Settings > Addons > "AIOStreams and AIOMetadata on this PC": the switch,
 * each server's state with a button to its configure page, the shared API keys, and a field that
 * installs the address a configure page hands out. Off by default.
 */
internal fun LazyListScope.localAddonsSettingsContent(isTablet: Boolean) {
    item(key = "local-addons") { LocalAddonsSection(isTablet) }
}

@Composable
private fun LocalAddonsSection(isTablet: Boolean) {
    LaunchedEffect(Unit) { LocalAddonSettingsRepository.ensureLoaded() }
    val settings by LocalAddonSettingsRepository.state.collectAsStateWithLifecycle()
    val statuses by LocalAddonHost.statuses.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val bundleFound = remember { LocalAddonHost.bundleFound }

    SettingsSection(title = "AIOStreams and AIOMetadata on this PC", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = "Run them inside Nuvio",
                description = if (bundleFound) {
                    "Starts AIOStreams and AIOMetadata in the background whenever Nuvio is open, on this PC only " +
                        "(nothing is reachable from other devices). They use about 1.3 GB of memory together."
                } else {
                    "This build does not include the servers. The Windows Nightly does; a PC build gets them " +
                        "by running scripts/local-addons/build-bundle.sh."
                },
                checked = settings.enabled,
                enabled = bundleFound || settings.enabled,
                isTablet = isTablet,
                onCheckedChange = { on ->
                    LocalAddonSettingsRepository.setEnabled(on)
                    LocalAddonHost.apply()
                },
            )
            if (settings.enabled && bundleFound) {
                listOf(LocalAddonServer.AioStreams, LocalAddonServer.AioMetadata, LocalAddonServer.Redis).forEach { server ->
                    SettingsGroupDivider(isTablet = isTablet)
                    val status = statuses[server] ?: LocalAddonStatus.Off
                    val configure = localAddonConfigureUrl(server, settings)
                    SettingsActionRow(
                        title = server.label,
                        description = statusText(server, status, settings.portOf(server)),
                        isTablet = isTablet,
                    ) {
                        if (configure != null) {
                            SettingsGlassButton(
                                text = "Open configure page",
                                enabled = status is LocalAddonStatus.Running,
                                onClick = { runCatching { uriHandler.openUri(configure) } },
                            )
                        }
                    }
                }
            }
        }

        if (settings.enabled && bundleFound) {
            KeysGroup(isTablet)
            InstallGroup(isTablet, scope)
            LocalAddonHost.logFolder?.let { folder ->
                SettingsSectionNote(
                    text = "Debrid services, catalogues and sorting are set on each configure page, which then " +
                        "gives you an addon address to install below. Logs: $folder",
                    isTablet = isTablet,
                )
            }
        }
    }
}

@Composable
private fun KeysGroup(isTablet: Boolean) {
    val saved by LocalAddonSettingsRepository.state.collectAsStateWithLifecycle()
    var tmdb by remember { mutableStateOf(saved.keys.tmdb) }
    var tvdb by remember { mutableStateOf(saved.keys.tvdb) }
    var fanart by remember { mutableStateOf(saved.keys.fanart) }
    var mdblist by remember { mutableStateOf(saved.keys.mdblist) }
    var gemini by remember { mutableStateOf(saved.keys.gemini) }
    var rpdb by remember { mutableStateOf(saved.keys.rpdb) }
    val edited = LocalAddonKeys(tmdb, tvdb, fanart, mdblist, gemini, rpdb)
    val changed = edited != saved.keys

    SettingsGroup(isTablet = isTablet) {
        SettingsTextRow(
            title = "TMDB API key",
            description = "Needed by AIOMetadata for most catalogues; AIOStreams uses it for exact title matching.",
            value = tmdb, secret = true, isTablet = isTablet, onValueChange = { tmdb = it },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsTextRow(
            title = "TVDB API key",
            description = "Series and anime details (AIOMetadata and AIOStreams).",
            value = tvdb, secret = true, isTablet = isTablet, onValueChange = { tvdb = it },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsTextRow(
            title = "Fanart.tv API key", description = "Logos and backgrounds.",
            value = fanart, secret = true, isTablet = isTablet, onValueChange = { fanart = it },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsTextRow(
            title = "MDBList API key", description = "Ratings and MDBList catalogues.",
            value = mdblist, secret = true, isTablet = isTablet, onValueChange = { mdblist = it },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsTextRow(
            title = "Gemini API key", description = "AIOMetadata's AI search (optional).",
            value = gemini, secret = true, isTablet = isTablet, onValueChange = { gemini = it },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsTextRow(
            title = "RPDB API key", description = "Rating posters (optional).",
            value = rpdb, secret = true, isTablet = isTablet, onValueChange = { rpdb = it },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsActionRow(
            title = "Save keys",
            description = if (changed) "Saving restarts both servers so they pick the keys up." else "Keys saved.",
            isTablet = isTablet,
        ) {
            SettingsGlassButton(
                text = "Save and restart",
                enabled = changed,
                accent = true,
                onClick = {
                    LocalAddonSettingsRepository.setKeys(edited)
                    LocalAddonHost.apply()
                },
            )
        }
    }
}

@Composable
private fun InstallGroup(isTablet: Boolean, scope: kotlinx.coroutines.CoroutineScope) {
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    SettingsGroup(isTablet = isTablet) {
        SettingsTextRow(
            title = "Install an addon address",
            description = result ?: "Paste the manifest address a configure page gave you " +
                "(it starts with http://127.0.0.1).",
            value = address,
            placeholder = localAddonBaseUrl(LocalAddonServer.AioStreams.defaultPort) + "/stremio/…/manifest.json",
            isTablet = isTablet,
            onValueChange = { address = it; result = null },
        )
        SettingsGroupDivider(isTablet = isTablet)
        SettingsActionRow(title = "Add to Nuvio", description = null, isTablet = isTablet) {
            SettingsGlassButton(
                text = "Install",
                enabled = address.isNotBlank() && !busy,
                loading = busy,
                onClick = {
                    busy = true
                    scope.launch {
                        result = when (val added = AddonRepository.addAddon(address.trim())) {
                            is AddAddonResult.Success -> "Installed ${added.manifest.name}.".also { address = "" }
                            is AddAddonResult.Error -> added.message
                        }
                        busy = false
                    }
                },
            )
        }
    }
}

private fun statusText(server: LocalAddonServer, status: LocalAddonStatus, port: Int): String = when (status) {
    LocalAddonStatus.Off -> "Stopped"
    LocalAddonStatus.NotInBuild -> "Not in this build"
    LocalAddonStatus.Starting -> if (server == LocalAddonServer.AioMetadata) {
        "Starting on port $port (it fills its caches first, which can take a minute)"
    } else {
        "Starting on port $port"
    }
    is LocalAddonStatus.Running -> "Running at ${status.baseUrl}"
    is LocalAddonStatus.Restarting -> "Stopped unexpectedly, restarting in ${status.inSeconds} s" +
        status.reason.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
    is LocalAddonStatus.Failed -> "Could not keep it running. Last log lines:\n${status.reason}"
}
