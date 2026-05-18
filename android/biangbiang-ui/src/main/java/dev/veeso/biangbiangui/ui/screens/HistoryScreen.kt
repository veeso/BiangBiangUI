package dev.veeso.biangbiangui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.veeso.biangbiangui.services.HistoryEntry
import dev.veeso.biangbiangui.ui.AppDesign
import dev.veeso.biangbiangui.ui.LocalBiangBiangContext
import kotlinx.coroutines.launch

/**
 * Saved history, newest-first, with an Original / Transliterated segmented
 * toggle, per-row tap-to-expand, per-row TTS, swipe-to-delete and Clear All
 * with a confirmation dialog. Config-driven port of the reference BiangBiang
 * Hanzi Android `HistoryModeView.kt`; mirrors iOS Phase 3 `HistoryScreen`.
 *
 * Generalisations vs. the reference:
 * - All strings from `config.strings`.
 * - Per-row TTS language resolved via `ctx.variant(forId = entry.variantId)`
 *   and its `ttsLanguageCode`; the row TTS button is hidden when that is
 *   `null` (data-driven; no `HistoryVariant` enum). Driven through
 *   `ctx.audio` (plugin override else system TTS).
 * - Preserves reference issue #22 (consecutive-duplicate dedup is in
 *   `HistoryStore`; newest-first ordering and stable `id` key kept here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val ctx = LocalBiangBiangContext.current
    val scope = rememberCoroutineScope()

    val history by ctx.settings.history.collectAsStateWithLifecycle(initialValue = emptyList())

    val isPlaying = ctx.audio.isPlaying
    var speakingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) speakingId = null
    }

    var showTransliterated by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateListOf<String>() }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(ctx.config.strings.clearAllConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { ctx.settings.clearHistory() }
                        expanded.clear()
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
        topBar = { TopAppBar(title = { Text(ctx.config.strings.tabHistory) }) },
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = AppDesign.horizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ctx.config.strings.historyEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = AppDesign.horizontalPadding,
                                vertical = AppDesign.stackSpacing,
                            ),
                        verticalArrangement = Arrangement.spacedBy(AppDesign.stackSpacing),
                    ) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SegmentedButton(
                                selected = !showTransliterated,
                                onClick = { showTransliterated = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text(ctx.config.strings.original) },
                            )
                            SegmentedButton(
                                selected = showTransliterated,
                                onClick = { showTransliterated = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text(ctx.config.strings.transliterated) },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { showClearConfirm = true },
                                enabled = history.isNotEmpty(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    ctx.config.strings.clearAll,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                items(history, key = { it.id }) { entry ->
                    val variant = ctx.variant(forId = entry.variantId)
                    val ttsCode = variant?.ttsLanguageCode
                    val rowSpeaking = isPlaying && speakingId == entry.id
                    HistoryRow(
                        entry = entry,
                        showTransliterated = showTransliterated,
                        isExpanded = expanded.contains(entry.id),
                        isSpeaking = rowSpeaking,
                        ttsLanguageCode = ttsCode,
                        listenLabel = ctx.config.strings.listen,
                        stopLabel = ctx.config.strings.stop,
                        onToggleExpand = {
                            if (expanded.contains(entry.id)) {
                                expanded.remove(entry.id)
                            } else {
                                expanded.add(entry.id)
                            }
                        },
                        onSpeak = {
                            if (rowSpeaking) {
                                ctx.audio.stop()
                                speakingId = null
                            } else {
                                speakingId = entry.id
                                ctx.audio.play(entry.original, ttsCode)
                            }
                        },
                        onDelete = {
                            scope.launch { ctx.settings.deleteHistory(entry.id) }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    showTransliterated: Boolean,
    isExpanded: Boolean,
    isSpeaking: Boolean,
    ttsLanguageCode: String?,
    listenLabel: String,
    stopLabel: String,
    onToggleExpand: () -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = AppDesign.horizontalPadding),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(
                    horizontal = AppDesign.horizontalPadding,
                    vertical = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (showTransliterated) entry.transliteration else entry.original,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleExpand() },
            )
            if (ttsLanguageCode != null) {
                IconButton(onClick = onSpeak) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) stopLabel else listenLabel,
                    )
                }
            }
        }
    }
}
