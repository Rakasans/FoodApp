package io.github.rakasans.foodapp.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.BadgeUi

private data class Method(val title: String, val description: String, val version: String?)

private val upcoming = listOf(
    Method("Link do strony lub rolki", "Wklej adres albo udostępnij go z przeglądarki lub Instagrama.", "v0.2–v0.3"),
    Method("Zrzut ekranu opisu z Instagrama", "Przepis rozpoznany na urządzeniu.", "v0.2"),
    Method("Wklej tekst", "Tekst zamieniony w szkic; oryginał zostaje zachowany.", "v0.2"),
    Method("Zdjęcie przepisu", "Np. strona z czasopisma — rozpoznawanie tekstu na urządzeniu.", "v0.2"),
    Method("Dyktowanie", "Nagraj, popraw transkrypcję, utwórz szkic.", "v0.4"),
)

@Composable
fun AddScreen(onManual: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Każdy przepis trafia najpierw do szkicu, który przejrzysz przed zatwierdzeniem.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MethodCard(Method("Wpisz ręcznie", "Formularz przepisu. Działa bez internetu.", null), onClick = onManual)
        upcoming.forEach { MethodCard(it, onClick = null) }
    }
}

@Composable
private fun MethodCard(method: Method, onClick: (() -> Unit)?) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
    )
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(method.title, style = MaterialTheme.typography.titleMedium)
            Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            method.version?.let { Badge(BadgeUi("Wkrótce: $it", BadgeKind.NEUTRAL)) }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
    }
}
