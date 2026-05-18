package dev.veeso.biangbiangui.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.veeso.biangbiangui.config.BiangBiangConfig
import dev.veeso.biangbiangui.protocols.AudioProvider
import dev.veeso.biangbiangui.services.SettingsStore
import dev.veeso.biangbiangui.services.SystemTtsAudioProvider
import dev.veeso.biangbiangui.ui.components.ReviewPrompt
import dev.veeso.biangbiangui.ui.screens.CameraScreen
import dev.veeso.biangbiangui.ui.screens.HistoryScreen
import dev.veeso.biangbiangui.ui.screens.SettingsScreen
import dev.veeso.biangbiangui.ui.screens.TextScreen
import dev.veeso.biangbiangui.ui.theme.BiangBiangTheme

/**
 * The library's public entry point. Config-driven port of the reference
 * BiangBiang Hanzi Android `MainActivity` + `ui/MainScreen.kt`; mirrors iOS
 * Phase 3 `BiangBiangRootView`.
 *
 * Responsibilities:
 * - Owns the dependency graph: builds the [SettingsStore] (seeded from the
 *   active profile's variants + extra descriptors), resolves the
 *   [AudioProvider] (plugin override else [SystemTtsAudioProvider]),
 *   constructs the [BiangBiangContext] and provides it via
 *   [LocalBiangBiangContext] so every screen resolves it.
 * - Calls `settings.registerLaunch()` and seeds descriptor defaults once on
 *   launch (preserves issue #20).
 * - Hosts the bottom-nav: Text, Camera, History (only when
 *   `config.features.history`), Settings, plus one tab per
 *   `plugin.tabs` (the generic plugin seam). Labels from `config.strings`.
 * - Hosts [ReviewPrompt] (Play In-App Review + the 800ms heuristic), only
 *   when `config.features.ratePrompt` (preserves issue #20).
 *
 * The consuming app makes this its activity content:
 *
 *     setContent { BiangBiangRoot(config = myConfig) }
 */
@Composable
fun BiangBiangRoot(config: BiangBiangConfig) {
    val appContext = LocalContext.current.applicationContext

    val settings = remember(config) {
        SettingsStore(
            context = appContext,
            variants = config.languages.firstOrNull()?.variants ?: emptyList(),
            descriptors = config.extraSettings,
        )
    }

    val audio: AudioProvider = remember(config) {
        config.plugins.asSequence()
            .mapNotNull { it.audioProvider }
            .firstOrNull()
            ?: SystemTtsAudioProvider(appContext)
    }

    // Register this cold launch + seed descriptor defaults once (issue #20).
    LaunchedEffect(settings) {
        settings.registerLaunch()
        settings.seedDescriptorDefaults()
    }

    val ctx = rememberBiangBiangContext(config, settings, audio)

    BiangBiangTheme {
        CompositionLocalProvider(LocalBiangBiangContext provides ctx) {
            RootScaffold(config)
        }
    }
}

private data class RootTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

@Composable
private fun RootScaffold(config: BiangBiangConfig) {
    val strings = config.strings

    val tabs = buildList {
        add(RootTab("text", strings.tabText, Icons.Default.TextFields) { TextScreen() })
        add(RootTab("camera", strings.tabCamera, Icons.Default.CameraAlt) { CameraScreen() })
        if (config.features.history) {
            add(RootTab("history", strings.tabHistory, Icons.Default.History) { HistoryScreen() })
        }
        add(RootTab("settings", strings.tabSettings, Icons.Default.Settings) { SettingsScreen() })
        config.plugins.flatMap { it.tabs }.forEach { pluginTab ->
            add(
                RootTab(
                    id = "plugin:${pluginTab.id}",
                    label = pluginTab.title,
                    icon = Icons.Default.Extension,
                    content = pluginTab.content,
                ),
            )
        }
    }

    var selectedTabId by rememberSaveable { mutableStateOf(tabs.first().id) }
    val selectedTab = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.first()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = selectedTab.id == tab.id,
                        onClick = { selectedTabId = tab.id },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            selectedTab.content()
            if (config.features.ratePrompt) {
                ReviewPrompt(LocalBiangBiangContext.current.settings)
            }
        }
    }
}
