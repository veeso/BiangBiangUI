package dev.veeso.biangbiangui.ui.screens.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.veeso.biangbiangui.config.UiStrings
import dev.veeso.biangbiangui.services.camera.CameraSessionLog
import dev.veeso.biangbiangui.ui.AppDesign
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CameraSessionLogPane(
    log: CameraSessionLog,
    strings: UiStrings,
    accent: Color,
    highlightedIDs: Set<UUID>,
    onCopy: (CameraSessionLog.Entry) -> Unit,
    onSave: (CameraSessionLog.Entry) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDesign.horizontalPadding, vertical = AppDesign.stackSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDesign.stackSpacing),
        ) {
            Text(
                text = "${strings.cameraLogTitle} · ${log.entries.size}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text(strings.cameraLogClear, color = accent)
            }
        }

        if (log.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(AppDesign.horizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.cameraLogEmpty,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDesign.stackSpacing),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppDesign.horizontalPadding,
                    vertical = AppDesign.stackSpacing,
                ),
            ) {
                items(log.entries, key = CameraSessionLog.Entry::id) { entry ->
                    CameraSessionLogRow(
                        entry = entry,
                        accent = accent,
                        isHighlighted = entry.id in highlightedIDs,
                        strings = strings,
                        onCopy = { onCopy(entry) },
                        onSave = { onSave(entry) },
                    )
                }
            }
        }

        Text(
            text = strings.tapToCopyLongPressToSave,
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDesign.stackSpacing),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CameraSessionLogRow(
    entry: CameraSessionLog.Entry,
    accent: Color,
    isHighlighted: Boolean,
    strings: UiStrings,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isHighlighted) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
        animationSpec = tween(durationMillis = AppDesign.TOAST_ANIMATION_MS),
        label = "cameraLogHighlight",
    )
    val copyDescription = "${entry.original}, ${entry.transliteration}; ${strings.tapToCopyLongPressToSave}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .combinedClickable(onClick = onCopy, onLongClick = onSave)
            .semantics {
                contentDescription = copyDescription
                customActions = listOf(
                    CustomAccessibilityAction(strings.copy) {
                        onCopy()
                        true
                    },
                    CustomAccessibilityAction(strings.save) {
                        onSave()
                        true
                    },
                )
            }
            .padding(AppDesign.stackSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDesign.stackSpacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.original,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = entry.transliteration,
                color = accent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = strings.copy,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp),
        )
    }
}
