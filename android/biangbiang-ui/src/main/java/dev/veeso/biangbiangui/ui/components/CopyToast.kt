package dev.veeso.biangbiangui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.veeso.biangbiangui.ui.AppDesign

/**
 * Brief confirmation toast shown after copying recognized text.
 *
 * Ported verbatim from the reference BiangBiang Hanzi Android
 * `ui/components/CopyToast.kt`. Mirrors iOS `CopyToast`. The caller supplies
 * the `message` string (e.g. from `config.strings.textCopied`).
 */
@Composable
fun CopyToast(visible: Boolean, message: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Text(
            text = message,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(AppDesign.cornerRadius))
                .clip(RoundedCornerShape(AppDesign.cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
