package io.github.rakasans.foodapp.ui.account

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import io.github.rakasans.foodapp.domain.recipesCount
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.AppContainer
import io.github.rakasans.foodapp.BuildConfig
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.NoticeCard
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@Composable
fun AccountScreen(container: AppContainer, onAbout: () -> Unit) {
    val user by container.auth.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    var duplicates by remember { mutableStateOf<List<String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(user.displayName, fontWeight = FontWeight.Bold)
                Text(
                    if (user.isDummy) "Konto testowe (atrapa)" else "Konto",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NoticeCard(BadgeKind.INFO) {
            Text("Logowanie jest atrapą.", fontWeight = FontWeight.Bold)
            Text("Wszystkie dane zapisują się tylko na tym urządzeniu. Prawdziwe logowanie i synchronizacja — późniejszy etap.")
        }
        BackupSection(container)
        OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text("O aplikacji Przepisarnia") }
        Text("Język aplikacji: polski", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (BuildConfig.DEBUG) {
            Text("Narzędzia testowe (tylko wersja debug)", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = {
                scope.launch {
                    val dups = container.recipes.demoDuplicates()
                    if (dups.isEmpty()) {
                        val added = container.recipes.insertDemoData(skipExisting = false)
                        message = "Dodano ${recipesCount(added)}."
                    } else {
                        duplicates = dups
                    }
                }
            }) {
                Text("Wczytaj przepisy demonstracyjne")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    duplicates?.let { dups ->
        AlertDialog(
            onDismissRequest = { duplicates = null },
            title = { Text("Takie przepisy już istnieją") },
            text = {
                Text(
                    "W bibliotece są już przepisy o tych nazwach:\n" +
                        dups.joinToString("\n") { "• $it" } +
                        "\n\nCo chcesz zrobić?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    duplicates = null
                    scope.launch {
                        val added = container.recipes.insertDemoData(skipExisting = true)
                        message = if (added == 0) "Wszystkie przepisy demonstracyjne już są w bibliotece." else "Dodano ${recipesCount(added)}, pominięto istniejące."
                    }
                }) { Text("Pomiń istniejące") }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        duplicates = null
                        scope.launch {
                            val added = container.recipes.insertDemoData(skipExisting = false)
                            message = "Dodano ${recipesCount(added)} (z duplikatami)."
                        }
                    }) { Text("Dodaj mimo to") }
                    TextButton(onClick = { duplicates = null }) { Text("Anuluj") }
                }
            },
        )
    }
}

/** Kopia zapasowa: plik ZIP z przepisami i zdjęciami, zapisany tam, gdzie wskażesz (np. Dysk Google). */
@Composable
private fun BackupSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            status = try {
                val n = context.contentResolver.openOutputStream(uri)?.use { container.backup.export(it) }
                if (n == null) "Nie udało się zapisać pliku." else "Zapisano kopię: ${recipesCount(n)}."
            } catch (e: Exception) {
                "Nie udało się zapisać kopii: ${e.message ?: "nieznany błąd"}"
            }
            working = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            status = try {
                val r = context.contentResolver.openInputStream(uri)?.use { container.backup.restore(it) }
                when {
                    r == null -> "Nie udało się otworzyć pliku."
                    r.skipped > 0 -> "Przywrócono ${recipesCount(r.added)}. Pominięto ${r.skipped} — już są na telefonie."
                    else -> "Przywrócono ${recipesCount(r.added)}."
                }
            } catch (e: IllegalArgumentException) {
                e.message ?: "To nie jest kopia Przepisarni."
            } catch (e: Exception) {
                "Nie udało się przywrócić kopii: ${e.message ?: "nieznany błąd"}"
            }
            working = false
        }
    }

    NoticeCard(BadgeKind.INFO) {
        Text("Kopia zapasowa", fontWeight = FontWeight.Bold)
        Text("Dane są tylko na tym telefonie. Zapisz kopię (przepisy, oryginały i zdjęcia) do pliku — np. na Dysku Google. Przywrócenie dodaje brakujące przepisy, niczego nie nadpisuje.")
    }
    OutlinedButton(
        enabled = !working,
        onClick = { exportLauncher.launch("Przepisarnia-kopia-${java.time.LocalDate.now()}.zip") },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Zapisz kopię do pliku") }
    OutlinedButton(
        enabled = !working,
        onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Przywróć z kopii") }
    if (working) Text("Pracuję…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    status?.let { Text(it) }
}
