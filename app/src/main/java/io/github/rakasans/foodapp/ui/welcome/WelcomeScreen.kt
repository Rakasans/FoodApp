package io.github.rakasans.foodapp.ui.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.ui.common.SectionLabel

/**
 * Ekran powitalny — treść landingu przepisarnia.pl w aplikacji. Pokazywany przy pierwszym uruchomieniu
 * i dostępny w „Konto → O aplikacji”. Docelowo tu trafi też logowanie (konta + Supabase).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(onStart: () -> Unit, firstRun: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLogo(Modifier.size(40.dp))
                Text("Przepisarnia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "Wersja testowa · Android",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(
                "Przepis z rolki, strony czy zdjęcia. Zawsze pod ręką.",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Udostępnij link albo zrób zdjęcie, a przepis trafi do Twojej prywatnej biblioteki. Bez przepisywania, bez zgubionych zrzutów ekranu i wiadomości.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Twoje przepisy, tak jak je zapisał autor")
            Feature(Icons.Default.Lock, "Oryginał bez zmian",
                "Składniki i kroki zostają w brzmieniu autora. Nic nie tłumaczymy, nie przeliczamy i nie dopisujemy. Brakującą ilość pokazujemy jako brak, a nie zgadywankę.")
            Feature(Icons.Default.Home, "Działa offline",
                "Biblioteka mieszka na Twoim telefonie. Przeglądasz i dodajesz przepisy bez zasięgu, a linki pobiorą się same, gdy wróci internet.")
            Feature(Icons.Default.Refresh, "Nic nie ginie",
                "Każdy link, tekst i zdjęcie zapisujemy od razu. Gdy import się nie uda, materiał zostaje. Kopię całej biblioteki zapiszesz do pliku.")

            SectionLabel("Skąd dodasz przepis")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "„Udostępnij” z innej aplikacji", "Rolka z Instagrama", "Strona z przepisem",
                    "Zdjęcie lub zrzut ekranu", "Wklejony tekst", "Dyktowanie", "Wpisanie ręczne",
                ).forEach { Chip(it) }
            }
            Text(
                "Wskazówka: w Instagramie, przeglądarce czy notatkach wybierz „Udostępnij → Zapisz w Przepisarni”.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp),
            ) { Text(if (firstRun) "Zaczynamy" else "Wróć do przepisów", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
}

/** Znak Przepisarni (jak na przepisarnia.pl): zaokrąglony kwadrat z kartką przepisu. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    val green = MaterialTheme.colorScheme.primary
    val onGreen = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier) {
        val u = size.width / 34f
        drawRoundRect(green, cornerRadius = CornerRadius(9 * u, 9 * u))
        val stroke = Stroke(width = 2 * u, cap = StrokeCap.Round)
        drawRoundRect(onGreen, topLeft = Offset(10 * u, 9.5f * u), size = Size(13 * u, 15 * u), cornerRadius = CornerRadius(3.5f * u, 3.5f * u), style = stroke)
        drawLine(onGreen, Offset(14 * u, 14 * u), Offset(19 * u, 14 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
        drawLine(onGreen, Offset(14 * u, 18 * u), Offset(18 * u, 18 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
    }
}
