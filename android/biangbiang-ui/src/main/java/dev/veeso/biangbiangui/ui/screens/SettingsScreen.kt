package dev.veeso.biangbiangui.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.veeso.biangbiangui.config.SettingDescriptor
import dev.veeso.biangbiangui.services.ReviewPromptPolicy
import dev.veeso.biangbiangui.ui.AppDesign
import dev.veeso.biangbiangui.ui.BiangBiangContext
import dev.veeso.biangbiangui.ui.LocalBiangBiangContext
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The settings screen, restructured into the three spec layers. Config-driven
 * port of the reference BiangBiang Hanzi Android `SettingsModeView.kt`;
 * mirrors iOS Phase 3 `SettingsScreen`.
 *
 * 1. Shared core — translation-target picker (disabled as data when the active
 *    variant `translatable == false`), History Clear All, bug-report (GitHub
 *    issue URL from `branding.githubRepo`, email `branding.supportEmail`,
 *    subject `[Android] Bug report – <appName>`).
 * 2. Data-driven variant segmented picker — only when the active profile has
 *    more than one variant; binds `settings.selectedVariantId`. Changing it
 *    rebuilds the engine automatically via `BiangBiangContext` (the Android
 *    equivalent of iOS `rebuildEngine()` — the engine is derived from the
 *    selected-variant Flow).
 * 3. Injectable descriptors — one toggle/picker per `config.extraSettings`,
 *    bound to `settings.value`/`setValue`; footer from the descriptor.
 *
 * Keeps the DEBUG-only review-reset (developer-facing, never shipped).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalBiangBiangContext.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val translatable = ctx.activeVariant?.translatable ?: true
    val history by ctx.settings.history.collectAsStateWithLifecycle(initialValue = emptyList())
    val userLanguage by ctx.settings.userLanguage
        .collectAsStateWithLifecycle(initialValue = "")
    val selectedVariantId by ctx.settings.selectedVariantId
        .collectAsStateWithLifecycle(
            initialValue = ctx.activeProfile.variants.firstOrNull()?.id ?: "",
        )

    var languageSelectExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val allLanguages = remember {
        Locale.getAvailableLocales()
            .filter { it.displayLanguage.isNotBlank() }
            .distinctBy { it.displayLanguage }
            .sortedBy { it.displayLanguage }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(ctx.config.strings.clearAllConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { ctx.settings.clearHistory() }
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(ctx.config.strings.clearAll)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(ctx.config.strings.tabSettings) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = AppDesign.horizontalPadding,
                    vertical = AppDesign.sectionSpacing,
                )
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AppDesign.sectionSpacing),
        ) {
            // --- Layer 1: shared core ---
            SettingsSection(title = ctx.config.strings.translationLanguage) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { languageSelectExpanded = true },
                        enabled = translatable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(currentLanguageName(allLanguages, userLanguage))
                    }

                    DropdownMenu(
                        expanded = languageSelectExpanded,
                        onDismissRequest = { languageSelectExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f),
                    ) {
                        allLanguages.forEach { locale ->
                            DropdownMenuItem(
                                text = { Text(locale.displayLanguage) },
                                onClick = {
                                    scope.launch {
                                        ctx.settings.setUserLanguage(locale.language)
                                    }
                                    languageSelectExpanded = false
                                },
                            )
                        }
                    }
                }
                if (!translatable) {
                    Text(
                        "Translation is unavailable for the selected variant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Layer 2: data-driven variant picker ---
            val variants = ctx.activeProfile.variants
            if (variants.size > 1) {
                SettingsSection(title = ctx.activeProfile.displayName) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        variants.forEachIndexed { index, variant ->
                            SegmentedButton(
                                selected = selectedVariantId == variant.id,
                                onClick = {
                                    scope.launch {
                                        ctx.settings.setSelectedVariantId(variant.id)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = variants.size,
                                ),
                                label = { Text(variant.displayName) },
                            )
                        }
                    }
                }
            }

            // --- Layer 3: injectable descriptors ---
            ctx.config.extraSettings.forEach { descriptor ->
                SettingsSection(title = descriptor.label) {
                    DescriptorControl(ctx, descriptor)
                    descriptor.footer?.let { footer ->
                        Text(
                            footer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // --- Library-owned History + support, after app-defined settings ---
            SettingsSection(title = ctx.config.strings.tabHistory) {
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    enabled = history.isNotEmpty(),
                ) {
                    Text(ctx.config.strings.clearAll)
                }
            }

            SettingsSection(title = ctx.config.strings.reportBug) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = {
                        openGitHubIssues(context, ctx.config.branding.githubRepo)
                    }) {
                        Text(ctx.config.strings.openGithubIssues)
                    }
                    Button(onClick = {
                        sendBugEmail(
                            context,
                            ctx.config.branding.supportEmail,
                            ctx.config.branding.appName,
                        )
                    }) {
                        Text(ctx.config.strings.sendEmail)
                    }
                }
            }

            // --- DEBUG-only review reset ---
            if (isDebugBuild(context)) {
                val reviewLaunchCount by ctx.settings.reviewLaunchCount
                    .collectAsStateWithLifecycle(initialValue = 0)
                val reviewDismissed by ctx.settings.reviewPromptDismissed
                    .collectAsStateWithLifecycle(initialValue = false)
                val reviewAttempts by ctx.settings.reviewAttempts
                    .collectAsStateWithLifecycle(initialValue = 0)
                SettingsSection(title = "Debug") {
                    Text(
                        "review launchCount=$reviewLaunchCount dismissed=$reviewDismissed " +
                            "attempts=$reviewAttempts/${ReviewPromptPolicy.MAX_ATTEMPTS} " +
                            "(fires at ${ReviewPromptPolicy.LAUNCH_THRESHOLD})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { scope.launch { ctx.settings.resetReviewPrompt() } }) {
                        Text("Reset review prompt")
                    }
                }
            }
        }
    }
}

@Composable
private fun DescriptorControl(
    ctx: BiangBiangContext,
    descriptor: SettingDescriptor,
) {
    val scope = rememberCoroutineScope()
    val current by produceState(initialValue = descriptor.defaultValue, descriptor.key) {
        value = ctx.settings.value(descriptor.key) ?: descriptor.defaultValue
    }
    when (val kind = descriptor.kind) {
        is SettingDescriptor.Kind.Toggle -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(descriptor.label)
                Switch(
                    checked = current == "true",
                    onCheckedChange = { checked ->
                        scope.launch {
                            ctx.settings.setValue(
                                descriptor.key,
                                if (checked) "true" else "false",
                            )
                        }
                    },
                )
            }
        }

        is SettingDescriptor.Kind.Picker -> {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kind.options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = current == option,
                        onClick = {
                            scope.launch { ctx.settings.setValue(descriptor.key, option) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = kind.options.size,
                        ),
                        label = { Text(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDesign.stackSpacing),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

private fun currentLanguageName(locales: List<Locale>, current: String): String =
    locales.find { it.language == current }?.displayLanguage ?: current

private fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

private fun openGitHubIssues(context: Context, repo: String) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        "https://github.com/$repo/issues/new".toUri(),
    )
    context.startActivity(intent)
}

private fun sendBugEmail(context: Context, email: String, appName: String) {
    val subject = "[Android] Bug report – $appName"
    val body = """
Description:

Steps to reproduce:

Device:
Android version:
"""
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$email?subject=$subject&body=$body".toUri()
    }
    context.startActivity(Intent.createChooser(intent, "Send bug report"))
}
