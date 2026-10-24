package io.github.rakasans.foodapp.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rakasans.foodapp.data.repository.DraftIngredient
import io.github.rakasans.foodapp.domain.RecipeRules
import io.github.rakasans.foodapp.domain.formatQuantity
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.BadgeUi
import io.github.rakasans.foodapp.ui.common.NoticeCard
import io.github.rakasans.foodapp.ui.common.RecipePhoto
import io.github.rakasans.foodapp.ui.common.SectionLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onApproved: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val knownTags by viewModel.knownTags.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is EditorEvent.Approved -> onApproved(e.id)
                is EditorEvent.OpenRecipe -> onOpenRecipe(e.id)
                EditorEvent.Close -> onClose()
            }
        }
    }
    BackHandler { viewModel.onBack() }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::setPhoto)
    }

    if (state.loading) return
    val d = state.draft

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(
                if (state.editingApproved) BadgeUi("Zatwierdzony — edycja", BadgeKind.OK) else BadgeUi("Szkic", BadgeKind.WARN),
                Modifier.weight(1f, fill = false),
            )
            Text(
                text = when {
                    state.editingApproved && state.dirty -> "Niezapisane zmiany"
                    state.editingApproved -> ""
                    state.savedOnce && !state.dirty -> "Szkic zapisany na urządzeniu"
                    state.dirty -> "Zapisywanie…"
                    else -> "Szkic zapisze się sam po wpisaniu treści"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (state.sameName.isNotEmpty()) {
            NoticeCard(BadgeKind.WARN) {
                Text("Możliwy duplikat", fontWeight = FontWeight.Bold)
                Text("W bibliotece jest już przepis „${state.sameName.first().name}”.")
                TextButton(onClick = { viewModel.openExisting(state.sameName.first().id) }) { Text("Otwórz istniejący") }
            }
        }

        // Zdjęcie
        SectionLabel("Zdjęcie potrawy")
        val photo = d.photoPath
        if (photo != null) {
            RecipePhoto(photo, viewModel.photos, "Zdjęcie potrawy", Modifier.fillMaxWidth().height(180.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Zmień zdjęcie")
                }
                TextButton(onClick = viewModel::removePhoto) { Text("Usuń zdjęcie", color = MaterialTheme.colorScheme.error) }
            }
        } else {
            OutlinedButton(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dodaj zdjęcie z galerii (opcjonalne)") }
        }
        if (state.photoError) Text("Nie udało się wczytać zdjęcia. Spróbuj wybrać inne.", color = MaterialTheme.colorScheme.error)

        OutlinedTextField(
            value = d.name,
            onValueChange = viewModel::setName,
            label = { Text("Nazwa") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.servingsText,
                onValueChange = viewModel::setServings,
                label = { Text("Porcje") },
                placeholder = { Text("brak") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = d.prepTime, onValueChange = viewModel::setPrep, label = { Text("Przygot.") },
                placeholder = { Text("np. 15 min") }, singleLine = true, modifier = Modifier.weight(1.2f),
            )
            OutlinedTextField(
                value = d.cookTime, onValueChange = viewModel::setCook, label = { Text("Obróbka") },
                placeholder = { Text("np. 30 min") }, singleLine = true, modifier = Modifier.weight(1.2f),
            )
        }
        if (d.servings == null) {
            Text(
                "Liczba porcji nieznana — lista zakupów użyje mnożnika całego przepisu zamiast przeliczania porcji.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Składniki
        SectionLabel("Składniki")
        Text(
            "Wpisz składnik tak jak w przepisie, np. „2 łyżki oliwy”. Możesz wkleić całą listę — każda linia stanie się osobnym składnikiem.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        d.ingredients.forEach { ing ->
            IngredientRow(
                ing = ing,
                correcting = ing.id in state.correcting,
                onText = { viewModel.setIngredientText(ing.id, it) },
                onToggleNoQuantity = { viewModel.toggleNoQuantity(ing.id) },
                onToggleCorrect = { viewModel.toggleCorrecting(ing.id) },
                onCorrect = { name, qty, unit -> viewModel.correctIngredient(ing.id, name, qty, unit) },
                onRemove = { viewModel.removeIngredient(ing.id) },
            )
        }
        OutlinedButton(onClick = viewModel::addIngredient, modifier = Modifier.fillMaxWidth()) { Text("+ Dodaj składnik") }

        // Kroki
        SectionLabel("Kroki przygotowania")
        d.steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${index + 1}.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 18.dp).width(28.dp),
                )
                OutlinedTextField(
                    value = step.text,
                    onValueChange = { viewModel.setStep(step.id, it) },
                    placeholder = { Text("Opis kroku") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.removeStep(step.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Usuń krok ${index + 1}")
                }
            }
        }
        OutlinedButton(onClick = viewModel::addStep, modifier = Modifier.fillMaxWidth()) { Text("+ Dodaj krok") }

        // Kategorie
        SectionLabel("Kategorie (można kilka)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c ->
                FilterChip(selected = c.id in d.categoryIds, onClick = { viewModel.toggleCategory(c.id) }, label = { Text(c.name) })
            }
        }
        AddRow(placeholder = "Nowa kategoria, np. Lunchbox", onAdd = viewModel::addCategory)

        // Etykiety
        SectionLabel("Etykiety")
        val allTags = (knownTags + d.tagNames).distinctBy { it.lowercase() }
        if (allTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.forEach { t ->
                    FilterChip(
                        selected = d.tagNames.any { it.equals(t, ignoreCase = true) },
                        onClick = { viewModel.toggleTag(t) },
                        label = { Text("#$t") },
                    )
                }
            }
        }
        AddRow(placeholder = "Nowa etykieta, np. wegańskie", onAdd = viewModel::addTag)

        OutlinedTextField(
            value = d.notes,
            onValueChange = viewModel::setNotes,
            label = { Text("Własne notatki") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = viewModel::approve, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.editingApproved) "Zapisz zmiany" else "Zatwierdź przepis")
        }
        if (!state.editingApproved) {
            TextButton(onClick = viewModel::askDiscardDraft, modifier = Modifier.fillMaxWidth()) {
                Text("Odrzuć szkic", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    EditorDialogs(state.dialog, viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientRow(
    ing: DraftIngredient,
    correcting: Boolean,
    onText: (String) -> Unit,
    onToggleNoQuantity: () -> Unit,
    onToggleCorrect: () -> Unit,
    onCorrect: (name: String?, qty: String?, unit: String?) -> Unit,
    onRemove: () -> Unit,
) {
    val missing = ing.rawText.isNotBlank() && RecipeRules.isQuantityMissing(ing.facts)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (missing) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ing.rawText,
                    onValueChange = onText,
                    placeholder = { Text("np. 2 łyżki oliwy") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Usuń składnik") }
            }
            if (ing.rawText.isNotBlank()) {
                val summary = buildString {
                    append("Rozpoznano: ")
                    if (ing.noQuantity) append("bez ilości")
                    else append(ing.quantity?.let(::formatQuantity) ?: "ilość nieznana")
                    ing.unit?.takeIf { !ing.noQuantity }?.let { append(" ").append(it) }
                    append(" · ").append(ing.name.ifBlank { ing.rawText })
                }
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (missing) Badge(BadgeUi(if (ing.isRange) "Zakres — wpisz ilość" else "Brak ilości", BadgeKind.WARN))
                    if (ing.unitInferred && !ing.noQuantity) Badge(BadgeUi("Jednostka przyjęta — sprawdź", BadgeKind.INFO))
                    TextButton(onClick = onToggleNoQuantity) { Text(if (ing.noQuantity) "Ma ilość" else "Bez ilości") }
                    TextButton(onClick = onToggleCorrect) { Text(if (correcting) "Gotowe" else "Popraw") }
                }
                if (correcting) {
                    var qtyText by remember(ing.id) { mutableStateOf(ing.quantity?.let(::formatQuantity).orEmpty()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = ing.name, onValueChange = { onCorrect(it, null, null) },
                            label = { Text("Nazwa") }, singleLine = true, modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it; onCorrect(null, it, null) },
                            label = { Text("Ilość") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = ing.unit.orEmpty(), onValueChange = { onCorrect(null, null, it) },
                            label = { Text("Jedn.") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRow(placeholder: String, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, placeholder = { Text(placeholder) },
            singleLine = true, modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank()) { Text("Dodaj") }
    }
}

@Composable
private fun EditorDialogs(dialog: EditorDialog?, vm: EditorViewModel) {
    when (dialog) {
        null -> Unit
        is EditorDialog.Incomplete -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Przepis jest niekompletny") },
            text = {
                Text(
                    dialog.reasons.joinToString("\n") { "• $it" } +
                        "\n\nMożesz go zatwierdzić — zostanie oznaczony jako „Niekompletny”. Brakujących danych nie uzupełniamy automatycznie."
                )
            },
            confirmButton = { TextButton(onClick = vm::acceptIncomplete) { Text("Zatwierdź mimo braków") } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Wróć do edycji") } },
        )
        is EditorDialog.Duplicate -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Taki przepis już istnieje") },
            text = { Text("W bibliotece jest już „${dialog.existing.first().name}”. Co chcesz zrobić?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.openExisting(dialog.existing.first().id) }) { Text("Otwórz istniejący") }
                    TextButton(onClick = vm::keepBoth) { Text("Zachowaj oba") }
                    TextButton(onClick = vm::dismissDialog) { Text("Anuluj") }
                }
            },
        )
        EditorDialog.DiscardChanges -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Odrzucić zmiany?") },
            text = { Text("Zmiany w przepisie nie zostały zapisane.") },
            confirmButton = { TextButton(onClick = vm::confirmDiscardChanges) { Text("Odrzuć zmiany", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Wróć do edycji") } },
        )
        EditorDialog.DiscardDraft -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Odrzucić szkic?") },
            text = { Text("Szkic zniknie z biblioteki.") },
            confirmButton = { TextButton(onClick = vm::confirmDiscardDraft) { Text("Odrzuć", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Anuluj") } },
        )
    }
}
