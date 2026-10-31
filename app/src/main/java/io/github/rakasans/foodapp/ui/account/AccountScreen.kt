package io.github.rakasans.foodapp.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import io.github.rakasans.foodapp.ui.common.SectionLabel
import io.github.rakasans.foodapp.domain.formatUpdated
import io.github.rakasans.foodapp.data.sync.SyncStatus
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
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

/**
 * „Moje dane”: logowanie, synchronizacja, kopia zapasowa, informacje o aplikacji.
 * Zasada: etykiety bez opisów — znaczenie niesie sama etykieta i jej wartość.
 */
@Composable
fun AccountScreen(container: AppContainer, onAbout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var duplicates by remember { mutableStateOf<List<String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroup("Konto") {
            SettingRow("Logowanie", value = signInLabel(container))
        }
        CloudSection(container)
        BackupSection(container)
        SettingsGroup("Aplikacja") {
            SettingRow("O aplikacji", onClick = onAbout, trailing = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            })
            SettingRow("Język", value = "polski")
        }

        if (BuildConfig.DEBUG) {
            SettingsGroup("Dla dewelopera") {
                SettingRow("Wczytaj przepisy demonstracyjne", onClick = {
                    scope.launch {
                        val dups = container.recipes.demoDuplicates()
                        if (dups.isEmpty()) {
                            val added = container.recipes.insertDemoData(skipExisting = false)
                            message = "Dodano ${recipesCount(added)}."
                        } else {
                            duplicates = dups
                        }
                    }
                })
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
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

    SettingsGroup("Kopia zapasowa") {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                enabled = !working,
                onClick = { exportLauncher.launch("Przepisarnia-kopia-${java.time.LocalDate.now()}.zip") },
                modifier = Modifier.weight(1f),
            ) { Text("Zapisz do pliku") }
            OutlinedButton(
                enabled = !working,
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.weight(1f),
            ) { Text("Przywróć") }
        }
        val info = if (working) "Pracuję…" else status
        info?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        }
    }
}

/** Chmura (Supabase): włącznik, ostatnia synchronizacja i mała ikona „synchronizuj teraz”. */
@Composable
private fun CloudSection(container: AppContainer) {
    val status by container.sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(container.sync.enabled) }

    SettingsGroup("Synchronizacja") {
        SettingRow("Kopia w chmurze", trailing = {
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                container.sync.enabled = it
                if (it) scope.launch { container.sync.sync() }
            })
        })
        if (enabled) {
            val failed = status is SyncStatus.Failed
            SettingRow(
                label = "Ostatnia",
                value = when (val s = status) {
                    SyncStatus.Disabled -> "wyłączona"
                    SyncStatus.Never -> "jeszcze nie"
                    SyncStatus.Running -> "trwa…"
                    SyncStatus.WaitingForNetwork -> "czeka na internet"
                    is SyncStatus.Done -> formatUpdated(s.at, System.currentTimeMillis())
                    is SyncStatus.Failed -> "błąd"
                },
                valueColor = if (failed) MaterialTheme.colorScheme.error else null,
                trailing = {
                    if (status is SyncStatus.Running) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { scope.launch { container.sync.sync() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Synchronizuj teraz")
                        }
                    }
                },
            )
            (status as? SyncStatus.Failed)?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            }
        }
    }
}

/** Sposób logowania, np. „Google, E-mail”; bez konta — gdzie są dane. */
private fun signInLabel(container: AppContainer): String {
    val cloud = container.supabase
    if (cloud.userId == null) return "brak — dane na telefonie"
    val names = cloud.providers.map {
        when (it) {
            "google" -> "Google"
            "facebook" -> "Facebook"
            "email" -> "E-mail"
            else -> it.replaceFirstChar(Char::uppercase)
        }
    }
    return if (cloud.isAnonymous || names.isEmpty()) "anonimowe — tylko ten telefon" else names.joinToString(", ")
}

/** Grupa ustawień: mały nagłówek + karta z wierszami. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) { Column(content = content) }
    }
}

/** Wiersz: etykieta po lewej, wartość lub kontrolka po prawej. */
@Composable
private fun SettingRow(
    label: String,
    value: String? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant) }
        trailing?.invoke()
    }
}
