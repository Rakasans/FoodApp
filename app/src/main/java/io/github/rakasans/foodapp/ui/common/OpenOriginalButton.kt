package io.github.rakasans.foodapp.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        Text(if (isInstagramUrl(link)) "Otwórz w Instagramie" else "Otwórz stronę")
    }
}
