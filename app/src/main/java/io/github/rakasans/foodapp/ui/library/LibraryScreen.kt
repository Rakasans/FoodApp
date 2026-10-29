package io.github.rakasans.foodapp.ui.library

import io.github.rakasans.foodapp.ui.common.RecipeThumbnail
import io.github.rakasans.foodapp.data.repository.PhotoStore
import androidx.compose.material.icons.filled.Favorite
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rakasans.foodapp.domain.recipesCount
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.SectionLabel
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    photos: PhotoStore,
    onOpenRecipe: (id: String, target: OpenTarget) -> Unit,
    onAddRecipe: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    // „Wstecz” w trybie zaznaczania tylko kończy zaznaczanie.
    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.selectionMode) {
                SelectionBar(
                    count = state.selected.size,
                    onCancel = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAllVisible,
                    onDelete = { confirmDelete = true },
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
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
                                item {
                                    FilterChip(
                                        selected = state.filters.favoritesOnly, onClick = viewModel::onFavoritesOnly,
                                        label = { Text("Ulubione") },
                                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    )
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
                    !state.selectionMode && state.items.isNotEmpty() -> item {
                        Text(
                            "Przytrzymaj przepis, aby go zaznaczyć i usunąć.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(state.items, key = { it.id }) { card ->
                    val isSelected = card.id in state.selected
                    RecipeCard(
                        photos = photos,
                        card = card,
                        selected = isSelected,
                        onClick = {
                            if (state.selectionMode) viewModel.toggleSelection(card.id) else onOpenRecipe(card.id, card.target)
                        },
                        onLongClick = { viewModel.toggleSelection(card.id) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDelete) {
        val count = state.selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć ${recipesCount(count)}?") },
            text = { Text("Zaznaczone przepisy znikną z biblioteki. Zaraz po usunięciu możesz to cofnąć.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelected { ids ->
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = "Usunięto ${recipesCount(ids.size)}",
                                    actionLabel = "Cofnij",
                                    withDismissAction = true,
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.restore(ids)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onCancel: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Zakończ zaznaczanie") }
        Text(
            "Zaznaczono: $count",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) { Text("Zaznacz wszystkie") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Usuń zaznaczone", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RecipeCard(photos: PhotoStore, card: RecipeCardUi, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (selected) contentDescription = "Zaznaczony: ${card.name}" }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Zaznacz"),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Miniatura: zdjęcie przepisu; bez zdjęcia — stonowane pole z pierwszą literą
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                RecipeThumbnail(card.photoUri, photos, card.name, Modifier.size(72.dp))
                if (selected) {
                    Box(
                        Modifier.size(72.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (card.favorite) {
                        Icon(
                            Icons.Default.Favorite, contentDescription = "Ulubiony",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp).size(18.dp),
                        )
                    }
                }
                Text(card.meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(card.updatedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Nie masz jeszcze przepisów", style = MaterialTheme.typography.titleLarge)
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
