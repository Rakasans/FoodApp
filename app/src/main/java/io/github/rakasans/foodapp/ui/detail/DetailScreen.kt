package io.github.rakasans.foodapp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.rakasans.foodapp.data.local.InfoOrigin
import io.github.rakasans.foodapp.data.local.RecipeDetails
import io.github.rakasans.foodapp.data.repository.PhotoStore
import io.github.rakasans.foodapp.data.repository.RecipeRepository
import io.github.rakasans.foodapp.ui.common.RecipePhoto
import io.github.rakasans.foodapp.domain.IngredientFacts
import io.github.rakasans.foodapp.domain.RecipeRules
import io.github.rakasans.foodapp.domain.formatQuantity
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.BadgeUi
import io.github.rakasans.foodapp.ui.common.NoticeCard
import io.github.rakasans.foodapp.ui.common.SectionLabel
import io.github.rakasans.foodapp.ui.library.sourceLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DetailViewModel(recipeId: String, repo: RecipeRepository) : ViewModel() {
    val details: StateFlow<RecipeDetails?> = repo.observeDetails(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(viewModel: DetailViewModel, photos: PhotoStore, onEdit: () -> Unit) {
    val d by viewModel.details.collectAsStateWithLifecycle()
    val details = d ?: return
    val r = details.recipe
    val check = RecipeRules.check(
        r.name,
        details.ingredients.map { IngredientFacts(it.name, it.quantity, it.noQuantity) },
        details.steps.map { it.text },
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        r.photoUri?.let { path ->
            RecipePhoto(path, photos, "Zdjęcie: ${r.name}", Modifier.fillMaxWidth().height(200.dp))
        }
        Text(r.name, style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            details.categories.forEach { Badge(BadgeUi(it.name, BadgeKind.OK)) }
            details.tags.forEach { Badge(BadgeUi("#${it.name}", BadgeKind.NEUTRAL)) }
            details.collections.forEach { Badge(BadgeUi("Kolekcja: ${it.name}", BadgeKind.INFO)) }
        }

        if (check.isIncomplete) {
            NoticeCard(BadgeKind.WARN) {
                Text("Przepis niekompletny", fontWeight = FontWeight.Bold)
                Text(check.reasons().joinToString(" · ") + ". Brakujących danych nie uzupełniamy automatycznie.")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile("Porcje", r.servings?.toString() ?: "brak danych", Modifier.weight(1f))
            InfoTile("Przygotowanie", r.prepTime ?: "brak danych", Modifier.weight(1f))
            InfoTile("Obróbka", r.cookTime ?: "brak danych", Modifier.weight(1f))
        }
        r.yieldText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        SectionLabel("Składniki (zapis oryginalny)")
        details.ingredients.sortedBy { it.position }.groupBy { it.groupName }.forEach { (group, items) ->
            SurfaceCard {
                group?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                items.forEach { i ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        val text = i.rawText.ifBlank { listOfNotNull(i.quantity?.let(::formatQuantity), i.unit, i.name).joinToString(" ") }
                        Text(text, modifier = Modifier.weight(1f))
                        if (RecipeRules.isQuantityMissing(IngredientFacts(i.name, i.quantity, i.noQuantity))) {
                            Badge(BadgeUi("brak ilości", BadgeKind.WARN))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        SectionLabel("Przygotowanie")
        val steps = details.steps.sortedBy { it.position }.filter { it.text.isNotBlank() }
        if (steps.isEmpty()) Text("Brak kroków — nie zostały podane.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        steps.forEachIndexed { index, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${index + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(s.text, modifier = Modifier.weight(1f))
            }
        }

        r.notes?.takeIf { it.isNotBlank() }?.let {
            SurfaceCard { SectionLabel("Moje notatki"); Text(it) }
        }

        SurfaceCard {
            SectionLabel("Źródło")
            Text(sourceLabel(r.sourceType), fontWeight = FontWeight.Bold)
            Text("Autor: ${r.sourceAuthor ?: "nie podano"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(r.sourceUrl ?: "Brak linku (źródło bez adresu).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
        SectionLabel("Informacje dodatkowe")
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wartości odżywcze", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Badge(originBadge(r.nutritionOrigin))
            }
            if (r.nutritionOrigin == InfoOrigin.SOURCE && details.nutrition.isNotEmpty()) {
                Text("Podstawa: ${r.nutritionBasis ?: "nie podano"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                details.nutrition.sortedBy { it.position }.forEach { n ->
                    Row { Text(n.label, modifier = Modifier.weight(1f)); Text(n.valueText, fontWeight = FontWeight.Bold) }
                }
            } else {
                Text("Brak danych o wartościach odżywczych. To nie oznacza wartości zerowych.")
            }
        }
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alergeny", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Badge(originBadge(r.allergensOrigin))
            }
            Text(
                if (r.allergensOrigin == InfoOrigin.SOURCE && !r.allergensText.isNullOrBlank()) r.allergensText.orEmpty()
                else "Brak informacji o alergenach. To nie oznacza, że przepis ich nie zawiera."
            )
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edytuj przepis") }
        Box(Modifier.padding(bottom = 24.dp))
    }
}

private fun originBadge(origin: InfoOrigin) = when (origin) {
    InfoOrigin.SOURCE -> BadgeUi("Ze źródła", BadgeKind.OK)
    InfoOrigin.NONE -> BadgeUi("Brak danych", BadgeKind.NEUTRAL)
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
