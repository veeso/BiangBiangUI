package dev.veeso.biangbiangui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.veeso.biangbiangui.protocols.ProcessedText
import dev.veeso.biangbiangui.ui.AppDesign
import dev.veeso.biangbiangui.ui.LocalBiangBiangContext
import dev.veeso.biangbiangui.ui.components.SectionView
import dev.veeso.biangbiangui.ui.screens.textmode.TextModeViewModel
import kotlinx.coroutines.launch

/**
 * The primary text-input / transliteration screen. Config-driven port of
 * `TextModeView` from the reference BiangBiang Hanzi Android app; mirrors iOS
 * Phase 3 `TextScreen`.
 *
 * Generalisations vs. the reference:
 * - All strings from `config.strings`; branding from `config.branding`.
 * - Logo resolved by name from `config.branding.logoAssetName` (the consuming
 *   app owns the drawable), the Android equivalent of iOS
 *   `Image(config.branding.logoAssetName)`.
 * - Processing via `ctx.engine` (no Mandarin/Cantonese mode enum).
 * - TTS button shown only when active variant `ttsLanguageCode != null`,
 *   driven through `ctx.audio` (plugin override else system TTS).
 * - Translation section shown only when active variant `translatable == true`
 *   (data-driven; no hard-coded Chinese check).
 * - Plugin hook: after output is produced a `ProcessedText(source=TEXT)` is
 *   dispatched to every plugin `onProcessedText`; the first plugin
 *   `inlineResultView` replaces the plain output box.
 * - Save -> `settings.addHistory(...)` with the active variant id.
 */
@Composable
fun TextScreen(viewModel: TextModeViewModel = viewModel()) {
    val ctx = LocalBiangBiangContext.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val outputText by viewModel.outputText.collectAsStateWithLifecycle()
    val translatedText by viewModel.translatedText.collectAsStateWithLifecycle()

    val activeVariant = ctx.activeVariant
    val engine = ctx.engine

    // Push the active engine; re-processes on variant change (rebuildEngine()).
    LaunchedEffect(engine) {
        viewModel.setEngine(engine)
    }

    // Plugin TEXT seam: dispatch onProcessedText after each successful process.
    LaunchedEffect(viewModel, ctx) {
        viewModel.onProcessed = { original, transliteration ->
            val pt = ProcessedText(
                original = original,
                transliteration = transliteration,
                variantId = ctx.activeVariant?.id ?: "",
                source = ProcessedText.Source.TEXT,
            )
            ctx.config.plugins.forEach { it.onProcessedText(pt) }
        }
    }

    // First plugin inline view for the current output (replaces plain box).
    val pluginInline: (@Composable () -> Unit)? = run {
        if (outputText.isEmpty()) {
            null
        } else {
            val pt = ProcessedText(
                original = inputText,
                transliteration = outputText,
                variantId = activeVariant?.id ?: "",
                source = ProcessedText.Source.TEXT,
            )
            ctx.config.plugins.asSequence()
                .mapNotNull { it.inlineResultView(pt) }
                .firstOrNull()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = AppDesign.horizontalPadding,
                    vertical = 5.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDesign.sectionSpacing),
            horizontalAlignment = Alignment.Start,
        ) {
            LogoHeader(
                appName = ctx.config.branding.appName,
                logoAssetName = ctx.config.branding.logoAssetName,
            )

            SectionView(
                title = ctx.config.strings.inputTitle,
                actionLabel = ctx.config.strings.paste,
                actionIcon = Icons.Default.ContentPaste,
                onActionClick = {
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        viewModel.onInputChanged(
                            clip.getItemAt(0).coerceToText(context).toString(),
                        )
                    }
                },
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::onInputChanged,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                )
            }

            val isPlaying = ctx.audio.isPlaying
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (activeVariant?.ttsLanguageCode != null) {
                    OutlinedButton(
                        onClick = {
                            if (ctx.audio.isPlaying) {
                                ctx.audio.stop()
                            } else {
                                ctx.audio.play(inputText, activeVariant.ttsLanguageCode)
                            }
                        },
                        enabled = isPlaying || inputText.trim().isNotEmpty(),
                        shape = CircleShape,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isPlaying) ctx.config.strings.stop
                            else ctx.config.strings.listen,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            ctx.settings.addHistory(
                                original = inputText,
                                transliteration = outputText,
                                variantId = ctx.activeVariant?.id ?: "",
                            )
                            snackbarHostState.showSnackbar(
                                ctx.config.strings.savedToHistory,
                            )
                        }
                    },
                    enabled = outputText.trim().isNotEmpty(),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(ctx.config.strings.save)
                }
            }

            SectionView(
                title = ctx.config.strings.outputTitle,
                actionLabel = ctx.config.strings.copy,
                actionIcon = Icons.Default.ContentCopy,
                onActionClick = { copyToClipboard(context, outputText) },
            ) {
                if (pluginInline != null) {
                    pluginInline()
                } else {
                    OutlinedTextField(
                        value = outputText,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                    )
                }
            }

            if (activeVariant?.translatable == true) {
                SectionView(
                    title = ctx.config.strings.translationTitle,
                    actionLabel = ctx.config.strings.copy,
                    actionIcon = Icons.Default.ContentCopy,
                    onActionClick = { copyToClipboard(context, translatedText) },
                ) {
                    OutlinedTextField(
                        value = translatedText,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                    )
                }

                val userLanguage by ctx.settings.userLanguage.collectAsStateWithLifecycle(initialValue = "")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = {
                            viewModel.translate(
                                activeVariant.ttsLanguageCode,
                                userLanguage,
                            )
                        },
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ctx.config.strings.translate)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoHeader(appName: String, logoAssetName: String) {
    val context = LocalContext.current
    val logoResId = remember(logoAssetName) {
        context.resources.getIdentifier(
            logoAssetName,
            "drawable",
            context.packageName,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (logoResId != 0) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(AppDesign.cornerRadius)),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
}
