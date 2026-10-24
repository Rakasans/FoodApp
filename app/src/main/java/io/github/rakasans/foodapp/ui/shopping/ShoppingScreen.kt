package io.github.rakasans.foodapp.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.rakasans.foodapp.data.repository.ShoppingRepository
import io.github.rakasans.foodapp.data.repository.toDomain
import io.github.rakasans.foodapp.domain.ShoppingLine
import io.github.rakasans.foodapp.domain.ShoppingMerge
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.BadgeUi
import io.github.rakasans.foodapp.ui.common.SectionLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pozycja listy z jej stanem (odhaczenie, ilość wpisana ręcznie). */
data class ShoppingRow(val line: ShoppingLine, val checked: Boolean, val override: String?)

class ShoppingViewModel(private val repo: ShoppingRepository) : ViewModel() {
    val rows: StateFlow<List<ShoppingRow>> = combine(repo.observeParts(), repo.observeStates()) { parts, states ->
        ShoppingMerge.merge(parts.map { it.toDomain() }).map { line ->
            val st = states[line.key]
            ShoppingRow(line, st?.checked == true, st?.quantityOverride)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addManual(text: String) = viewModelScope.launch { repo.addManual(text) }
    fun toggle(row: ShoppingRow) = viewModelScope.launch { repo.setChecked(row.line.key, !row.checked) }
    fun setOverride(key: String, text: String?) = viewModelScope.launch { repo.setOverride(key, text) }
    fun remove(key: String) = viewModelScope.launch { repo.removeLines(listOf(key)) }
    fun removeChecked() = viewModelScope.launch { repo.removeLines(rows.value.filter { it.checked }.map { it.line.key }) }
    fun clearAll() = viewModelScope.launch { repo.clearAll() }
}

@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel, onAddFromRecipes: () -> Unit) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var manual by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun submitManual() {
        if (manual.isNotBlank()) { viewModel.addManual(manual); manual = "" }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it },
                        label = { Text("Dodaj pozycję, np. „chleb”") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitManual() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::submitManual, enabled = manual.isNotBlank()) { Text("Dodaj") }
                }
            }
            item {
                OutlinedButton(onClick = onAddFromRecipes, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Dodaj składniki z przepisów")
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        "Lista jest pusta. Dodaj składniki z przepisu (w przepisie: „Do listy zakupów”) albo wpisz pozycję ręcznie.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            val (todo, done) = rows.partition { !it.checked }
            if (todo.isNotEmpty()) item { SectionLabel("Do kupienia (${todo.size})") }
            items(todo, key = { "t-" + it.line.key }) { row ->
                ShoppingRowItem(row, expanded == row.line.key, onExpand = {
                    expanded = if (expanded == row.line.key) null else row.line.key
                }, viewModel)
            }
            if (done.isNotEmpty()) item { SectionLabel("W koszyku (${done.size})") }
            items(done, key = { "d-" + it.line.key }) { row ->
                ShoppingRowItem(row, expanded == row.line.key, onExpand = {
                    expanded = if (expanded == row.line.key) null else row.line.key
                }, viewModel)
            }
            if (rows.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        if (done.isNotEmpty()) OutlinedButton(onClick = viewModel::removeChecked) { Text("Usuń kupione") }
                        TextButton(onClick = { confirmClear = true }) { Text("Wyczyść listę", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Wyczyścić listę?") },
            text = { Text("Usuniemy wszystkie pozycje z listy zakupów. Przepisy zostaną bez zmian.") },
            confirmButton = { TextButton(onClick = { viewModel.clearAll(); confirmClear = false }) { Text("Wyczyść") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShoppingRowItem(row: ShoppingRow, expanded: Boolean, onExpand: () -> Unit, viewModel: ShoppingViewModel) {
    val line = row.line
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = row.checked, onCheckedChange = { viewModel.toggle(row) })
            Column(Modifier.weight(1f)) {
                Text(
                    line.name, fontWeight = FontWeight.Medium,
                    textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                    color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                // Ręczne pozycje: ilość zostaje w zapisie („2 l mleka”) — pokazujemy go, ale nie sumujemy.
                val manualText = line.parts.filter { it.manual }.mapNotNull { it.rawText }
                    .filter { it.trim().lowercase() != line.name.trim().lowercase() }.joinToString(" + ")
                val qty = row.override?.let { "$it (wpisane przez Ciebie)" }
                    ?: listOf(line.quantityText, manualText).filter { it.isNotBlank() }.joinToString(" + ")
                if (qty.isNotBlank()) Text(qty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (line.differentUnits) Badge(BadgeUi("różne jednostki — nie sumujemy", BadgeKind.WARN))
                    if (line.hasUnknownQuantity && row.override == null) Badge(BadgeUi("ilość nieznana", BadgeKind.WARN))
                }
            }
        }
        if (expanded) {
            var overrideText by remember(line.key) { mutableStateOf(row.override.orEmpty()) }
            Column(Modifier.padding(start = 48.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                line.parts.forEach { p ->
                    val source = if (p.manual) "dodane ręcznie" else p.recipeName.orEmpty().ifBlank { "przepis bez nazwy" }
                    Text(
                        listOfNotNull(source, p.rawText?.takeIf { it.isNotBlank() }, p.factorLabel).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = overrideText, onValueChange = { overrideText = it },
                        label = { Text("Ilość do kupienia") }, placeholder = { Text("np. 400 ml") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.setOverride(line.key, overrideText) }) { Text("Zapisz") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.override != null) TextButton(onClick = { overrideText = ""; viewModel.setOverride(line.key, null) }) { Text("Przywróć z przepisów") }
                    TextButton(onClick = { viewModel.remove(line.key) }) { Text("Usuń pozycję", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
