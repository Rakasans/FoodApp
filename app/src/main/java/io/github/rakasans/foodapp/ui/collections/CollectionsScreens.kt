package io.github.rakasans.foodapp.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.AppContainer
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.domain.recipesCount
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.NoticeCard
import io.github.rakasans.foodapp.ui.common.SectionLabel
import kotlinx.coroutines.launch

/** Lista kolekcji. Kolekcja ≠ kategoria ≠ etykieta: to Twój zestaw przepisów na okazję. */
@Composable
fun CollectionsScreen(container: AppContainer, onOpen: (String) -> Unit) {
    val collections by container.collections.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nowa kolekcja, np. „Święta”") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                Button(enabled = name.isNotBlank(), onClick = {
                    scope.launch { val c = container.collections.getOrCreate(name); name = ""; onOpen(c.id) }
                }) { Text("Utwórz") }
            }
        }
        if (collections.isEmpty()) {
            item {
                NoticeCard(BadgeKind.INFO) {
                    Text("Czym jest kolekcja?", fontWeight = FontWeight.Bold)
                    Text("Kategoria mówi, czym jest danie (np. zupa), etykieta — jakie jest (np. #wege). Kolekcja to Twój zestaw na okazję, np. „Wigilia 2026” — przepis może być w wielu kolekcjach.")
                }
            }
        }
        items(collections, key = { it.collection.id }) { c ->
            Surface(
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(c.collection.id) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(c.collection.name, fontWeight = FontWeight.Bold)
                    Text(
                        if (c.recipes.isEmpty()) "Pusta" else c.recipes.joinToString(", ") { it.name.ifBlank { "Bez nazwy" } }.take(90),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(recipesCount(c.recipes.size), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun CollectionDetailScreen(container: AppContainer, id: String, onOpenRecipe: (String) -> Unit, onDeleted: () -> Unit) {
    val data by container.collections.observeOne(id).collectAsState(initial = null)
    val all by container.recipes.observeSummaries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = data ?: return

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.collection.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { renaming = true }) { Text("Zmień nazwę") }
        }
        SectionLabel("Przepisy (${c.recipes.size})")
        if (c.recipes.isEmpty()) Text("Kolekcja jest pusta — dodaj przepisy poniżej.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        c.recipes.sortedBy { it.name.lowercase() }.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenRecipe(r.id) }) {
                Text(r.name.ifBlank { "Bez nazwy" }, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                IconButton(onClick = { scope.launch { container.collections.setMembership(r.id, id, false) } }) {
                    Icon(Icons.Default.Close, contentDescription = "Usuń z kolekcji")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        SectionLabel("Dodaj przepisy")
        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("Szukaj w moich przepisach") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        val inside = c.recipes.map { it.id }.toSet()
        val q = query.trim().lowercase()
        all.map { it.recipe }
            .filter { it.status != RecipeStatus.MATERIAL && it.id !in inside && (q.isEmpty() || it.name.lowercase().contains(q)) }
            .take(if (q.isEmpty()) 8 else 30)
            .forEach { r ->
                Text(
                    "+ ${r.name.ifBlank { "Bez nazwy" }}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch { container.collections.setMembership(r.id, id, true) }
                    }.padding(vertical = 10.dp),
                )
            }

        TextButton(onClick = { confirmDelete = true }) { Text("Usuń kolekcję", color = MaterialTheme.colorScheme.error) }
    }

    if (renaming) {
        var newName by remember { mutableStateOf(c.collection.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nazwa kolekcji") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    scope.launch { container.collections.rename(id, newName) }; renaming = false
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Anuluj") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć kolekcję?") },
            text = { Text("Przepisy zostaną w „Moich przepisach” — usuwamy tylko zestaw.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { container.collections.delete(id); onDeleted() }; confirmDelete = false }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}

/** Okno „Kolekcje” w szczegółach przepisu: zaznacz kolekcje, wyszukaj albo utwórz nową. */
@Composable
fun CollectionPickerDialog(container: AppContainer, recipeId: String, onDismiss: () -> Unit) {
    val collections by container.collections.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var member by remember { mutableStateOf<Set<String>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(recipeId) { member = container.collections.collectionIdsFor(recipeId) }
    val m = member ?: emptySet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kolekcje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Szukaj lub wpisz nową") }, modifier = Modifier.fillMaxWidth(),
                )
                val q = query.trim().lowercase()
                val shown = collections.filter { q.isEmpty() || it.collection.name.lowercase().contains(q) }
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    shown.forEach { c ->
                        val cid = c.collection.id
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            val on = cid !in m
                            member = if (on) m + cid else m - cid
                            scope.launch { container.collections.setMembership(recipeId, cid, on) }
                        }) {
                            Checkbox(checked = cid in m, onCheckedChange = null)
                            Text(c.collection.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (q.isNotEmpty() && collections.none { it.collection.name.trim().lowercase() == q }) {
                    TextButton(onClick = {
                        scope.launch {
                            val c = container.collections.getOrCreate(query)
                            container.collections.setMembership(recipeId, c.id, true)
                            member = m + c.id
                            query = ""
                        }
                    }) { Text("Utwórz „${query.trim()}” i dodaj") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}
