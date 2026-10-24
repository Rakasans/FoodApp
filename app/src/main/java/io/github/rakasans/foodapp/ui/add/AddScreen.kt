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

private data class Method(val title: String, val description: String, val note: String?, val onClick: (() -> Unit)?)

@Composable
fun AddScreen(
    onManual: () -> Unit,
    onLink: () -> Unit,
    onScreenshot: () -> Unit,
    onText: () -> Unit,
    onPhoto: () -> Unit,
) {
    val methods = listOf(
        Method("Link do rolki lub strony", "Rolkę z Instagrama zapiszesz z notatką „od kogo”; przepis ze zrzutu opisu.", null, onLink),
        Method("Zrzut ekranu opisu z Instagrama", "Przepis rozpoznany na telefonie, także offline.", null, onScreenshot),
        Method("Wklej tekst", "Tekst zamieniony w szkic; oryginał zostaje przy przepisie.", null, onText),
        Method("Zdjęcie przepisu", "Np. strona z czasopisma — aparat albo galeria.", null, onPhoto),
        Method("Wpisz ręcznie", "Pusty formularz przepisu.", null, onManual),
        Method("Dyktowanie", "Nagraj, popraw transkrypcję, utwórz szkic.", "Wkrótce: v0.4", null),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Każdy przepis trafia najpierw do szkicu, który przejrzysz przed zatwierdzeniem. W innych aplikacjach możesz też wybrać „Udostępnij → Zapisz w FoodApp”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        methods.forEach { MethodCard(it) }
    }
}

@Composable
private fun MethodCard(method: Method) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(method.title, style = MaterialTheme.typography.titleMedium)
            Text(method.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            method.note?.let { Badge(BadgeUi(it, BadgeKind.NEUTRAL)) }
        }
    }
    val click = method.onClick
    if (click != null) {
        Card(onClick = click, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
    }
}
