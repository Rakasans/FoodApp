package io.github.rakasans.foodapp.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.rakasans.foodapp.domain.isInstagramUrl

/**
 * Otwiera oryginalny przepis: rolkę w aplikacji Instagram (albo w przeglądarce, gdy jej nie ma)
 * lub stronę w przeglądarce. Pokazujemy tylko dla linków http(s).
 */
@Composable
fun OpenOriginalButton(url: String?, modifier: Modifier = Modifier) {
    val link = url?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) } ?: return
    val context = LocalContext.current
    OutlinedButton(
        modifier = modifier,
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Brak aplikacji, która otworzy ten link", Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        Text(if (isInstagramUrl(link)) "Otwórz rolkę" else "Otwórz stronę", maxLines = 1)
    }
}

/**
 * Rząd przycisków źródła: „Otwórz rolkę/stronę” i „Pokaż oryginał” — ten sam styl, jeden poziom.
 * Oryginał (tekst materiału) rozwija się pod spodem, tylko do odczytu.
 */
@Composable
fun SourceActions(url: String?, original: String?) {
    val hasLink = url?.trim()?.let { it.startsWith("https://", true) || it.startsWith("http://", true) } == true
    val hasOriginal = !original.isNullOrBlank()
    if (!hasLink && !hasOriginal) return
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (hasLink) OpenOriginalButton(url, Modifier.weight(1f))
        if (hasOriginal) {
            OutlinedButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.weight(1f)) {
                Text(if (showOriginal) "Ukryj oryginał" else "Pokaż oryginał", maxLines = 1)
            }
        }
    }
    if (showOriginal && hasOriginal) {
        SelectionContainer {
            Text(original.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
