package dev.veeso.biangbiangui.examples.arabic.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.veeso.biangbiangui.ui.AppDesign

/**
 * Ported faithfully from the Harakat-Lens iOS app and the already-complete
 * iOS `ArabicExample/Quran/QuranMatchView`. Two adaptations for the library
 * plugin slot (mirroring iOS): the audio service is injected as an
 * [EveryAyahAudioProvider] reference, and the brand-green colour is a local
 * constant (the library's `AppDesign` doesn't vend it). Layout and behaviour
 * follow the SwiftUI view.
 */
/** Brand green `#006C35` (the library's `AppDesign` doesn't vend it). */
private val brandGreen = Color(0xFF006C35)

@Composable
fun QuranMatchView(
    match: QuranMatch,
    surahName: SurahName?,
    audio: EveryAyahAudioProvider,
) {
    val state by audio.state.collectAsState()
    val surah = match.ayah.surah
    val ayah = match.ayah.ayah
    val isLoading = state is EveryAyahAudioProvider.State.LoadingAyah &&
        (state as EveryAyahAudioProvider.State.LoadingAyah).let { it.surah == surah && it.ayah == ayah }
    val isPlaying = state is EveryAyahAudioProvider.State.PlayingAyah &&
        (state as EveryAyahAudioProvider.State.PlayingAyah).let { it.surah == surah && it.ayah == ayah }

    val nameLabel = surahName
        ?.let { "${it.transliteration} (${it.english})" }
        ?: "Surah ${match.ayah.surah}"
    val headerLine = "Surah ${match.ayah.surah} · $nameLabel · Ayah ${match.ayah.ayah}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = brandGreen.copy(alpha = 0.08f),
                shape = RoundedCornerShape(AppDesign.cornerRadius),
            )
            .border(
                width = 1.dp,
                color = brandGreen.copy(alpha = 0.6f),
                shape = RoundedCornerShape(AppDesign.cornerRadius),
            )
            .padding(AppDesign.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(AppDesign.stackSpacing),
    ) {
        Text(text = "📖  $headerLine", color = brandGreen)
        Text(
            text = match.ayah.text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        Text(
            text = match.ayah.transliteration,
            fontStyle = FontStyle.Italic,
        )
        Text(text = match.ayah.translationEn)
        Row(horizontalArrangement = Arrangement.spacedBy(AppDesign.stackSpacing)) {
            OutlinedButton(onClick = {
                if (isPlaying || isLoading) {
                    audio.stop()
                } else {
                    audio.playAyah(surah, ayah)
                }
            }) {
                Text(
                    when {
                        isLoading -> "Loading"
                        isPlaying -> "Stop"
                        else -> "Listen"
                    },
                )
            }
        }
    }
}