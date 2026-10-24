package io.github.rakasans.foodapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.SectionLabel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenRecipe: (String) -> Unit,
    onAddRecipe: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Szukaj po nazwie, składniku, etykiecie") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
        }
        if (state.categories.isNotEmpty()) {
            item {
                Column {
                    SectionLabel("Kategoria")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        item {
                            FilterChip(selected = state.filters.categoryId == null, onClick = { viewModel.onCategory(null) }, label = { Text("Wszystkie") })
                        }
                        items(state.categories, key = { it.id }) { c ->
                            FilterChip(selected = state.filters.categoryId == c.id, onClick = { viewModel.onCategory(c.id) }, label = { Text(c.name) })
                        }
                    }
                }
            }
        }
        if (state.tags.isNotEmpty()) {
            item {
                Column {
                    SectionLabel("Etykieta")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(state.tags, key = { it.id }) { t ->
                            FilterChip(selected = state.filters.tagId == t.id, onClick = { viewModel.onTag(t.id) }, label = { Text("#${t.name}") })
                        }
                    }
                }
            }
        }
        when {
            state.libraryEmpty -> item { EmptyLibrary(onAddRecipe) }
            state.noResults -> item {
                Text(
                    "Brak przepisów pasujących do wyszukiwania i filtrów.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.items, key = { it.id }) { card -> RecipeCard(card, onClick = { onOpenRecipe(card.id) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(card: RecipeCardUi, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    card.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                Text(card.meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card.tags.isNotEmpty()) {
                    Text(card.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
                }
                if (card.badges.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        card.badges.forEach { Badge(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAddRecipe: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Twoja biblioteka jest pusta", style = MaterialTheme.typography.titleLarge)
            Text(
                "Dodaj przepis z linku, zdjęcia, tekstu lub dyktowania. Możesz też udostępnić link z innej aplikacji przez „Udostępnij”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAddRecipe) { Text("Dodaj pierwszy przepis") }
        }
    }
}
