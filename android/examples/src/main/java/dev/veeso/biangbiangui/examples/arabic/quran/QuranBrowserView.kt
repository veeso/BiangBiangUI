package dev.veeso.biangbiangui.examples.arabic.quran

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Minimal Quran browser backing the plugin's tab: a searchable list of ayat
 * loaded from [QuranDataset]. Not a Harakat port — Harakat has no standalone
 * browser screen — but kept deliberately small so the plugin's `tabs` hook
 * has real content to render. Mirrors iOS
 * `ArabicExample/Quran/QuranBrowserView`.
 */
@Composable
fun QuranBrowserView() {
    val context = LocalContext.current
    val dataset = remember { QuranDataset(context.applicationContext) }
    var ayat by remember { mutableStateOf<List<QuranAyah>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        dataset.loadIfNeeded()
        ayat = dataset.all
    }

    val q = query.trim()
    val filtered = if (q.isEmpty()) {
        ayat.take(200)
    } else {
        ayat.asSequence()
            .filter {
                it.normalized.contains(q) ||
                    it.translationEn.contains(q, ignoreCase = true)
            }
            .take(200)
            .toList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Quran")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { ayah ->
                Column {
                    Text(text = "Surah ${ayah.surah} · Ayah ${ayah.ayah}")
                    Text(
                        text = ayah.text,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                    Text(text = ayah.translationEn)
                }
            }
        }
    }
}
