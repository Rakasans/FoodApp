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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.rakasans.foodapp.data.local.RecipeDetails
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.repository.RecipeRepository
import io.github.rakasans.foodapp.data.repository.ShoppingRepository
import io.github.rakasans.foodapp.domain.IngredientForShopping
import io.github.rakasans.foodapp.domain.polishPlural
import io.github.rakasans.foodapp.domain.recipesCount
import io.github.rakasans.foodapp.domain.Scaling
import io.github.rakasans.foodapp.domain.ShoppingBuilder
import io.github.rakasans.foodapp.domain.ShoppingMerge
import io.github.rakasans.foodapp.domain.formatQuantity
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.NoticeCard
import io.github.rakasans.foodapp.ui.common.SectionLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wybrany przepis z przeliczeniem: docelowe porcje (gdy znane) albo mnożnik całego przepisu. */
data class PortionChoice(
    val details: RecipeDetails,
    val targetServings: Int? = details.recipe.servings,
    val multiplier: Double = 1.0,
) {
    val scaling: Scaling
        get() {
            val s = details.recipe.servings
            return if (s != null && s > 0 && targetServings != null) Scaling.ByServings(s, targetServings)
            else Scaling.ByMultiplier(multiplier)
        }
}

class PortionsViewModel(
    initialIds: List<String>,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
) : ViewModel() {
    private val _chosen = MutableStateFlow<List<PortionChoice>>(emptyList())
    val chosen: StateFlow<List<PortionChoice>> = _chosen.asStateFlow()

    /** Zatwierdzone przepisy do wyszukiwarki „Dodaj przepis”. */
    val approved = recipes.observeSummaries()
        .map { list -> list.filter { it.recipe.status == RecipeStatus.APPROVED }.map { it.recipe.id to it.recipe.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { initialIds.forEach(::add) }

    fun add(id: String) {
        if (_chosen.value.any { it.details.recipe.id == id }) return
        viewModelScope.launch {
            val d = recipes.getDetails(id) ?: return@launch
            _chosen.update { if (it.any { c -> c.details.recipe.id == id }) it else it + PortionChoice(d) }
        }
    }

    fun remove(id: String) = _chosen.update { list -> list.filterNot { it.details.recipe.id == id } }

    fun setServings(id: String, target: Int) = _chosen.update { list ->
        list.map { if (it.details.recipe.id == id) it.copy(targetServings = target.coerceIn(1, 99)) else it }
    }

    fun setMultiplier(id: String, m: Double) = _chosen.update { list ->
        list.map { if (it.details.recipe.id == id) it.copy(multiplier = m) else it }
    }

    suspend fun addAll(): Int = _chosen.value.sumOf { shopping.addRecipe(it.details, it.scaling) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortionsScreen(viewModel: PortionsViewModel, onDone: (Int) -> Unit) {
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    val approved by viewModel.approved.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Przeliczenie dotyczy tylko listy zakupów — przepis się nie zmienia.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            chosen.forEach { c ->
                val r = c.details.recipe
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name.ifBlank { "Bez nazwy" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.remove(r.id) }) { Icon(Icons.Default.Close, contentDescription = "Usuń z wyboru") }
                        }
                        val servings = r.servings
                        if (servings != null && servings > 0) {
                            val target = c.targetServings ?: servings
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Porcje:", modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { viewModel.setServings(r.id, target - 1) }, enabled = target > 1) { Text("−") }
                                Text(target.toString(), style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { viewModel.setServings(r.id, target + 1) }) { Text("+") }
                            }
                            Text("W przepisie: $servings ${polishPlural(servings, "porcja", "porcje", "porcji")}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Liczba porcji nieznana — możesz tylko pomnożyć cały przepis.", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShoppingBuilder.MULTIPLIERS.forEach { m ->
                                    FilterChip(
                                        selected = c.multiplier == m, onClick = { viewModel.setMultiplier(r.id, m) },
                                        label = { Text("×${formatQuantity(m)}") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (chosen.isNotEmpty()) {
                val parts = chosen.flatMap { c ->
                    ShoppingBuilder.parts(
                        c.details.recipe.name,
                        c.details.ingredients.sortedBy { it.position }.map {
                            IngredientForShopping(it.name, it.rawText, it.quantity, it.quantityMax, it.unit, it.noQuantity)
                        },
                        c.scaling,
                    )
                }
                val lines = ShoppingMerge.merge(parts)
                SectionLabel("Podgląd (${lines.size} pozycji)")
                if (lines.any { it.differentUnits || it.hasUnknownQuantity }) {
                    NoticeCard(BadgeKind.WARN) {
                        Text("Część pozycji ma różne jednostki albo nieznaną ilość — nie łączymy ich na siłę.")
                    }
                }
                lines.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(l.name, modifier = Modifier.weight(1f))
                        Text(l.quantityText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionLabel("Dodaj przepis")
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Szukaj w moich przepisach") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val chosenIds = chosen.map { it.details.recipe.id }.toSet()
            val q = query.trim().lowercase()
            approved.filter { (id, name) -> id !in chosenIds && (q.isEmpty() || name.lowercase().contains(q)) }
                .take(if (q.isEmpty()) 5 else 20)
                .forEach { (id, name) ->
                    Text(
                        "+ $name",
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.add(id); query = "" }.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                enabled = chosen.isNotEmpty(),
                onClick = { scope.launch { onDone(viewModel.addAll()) } },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(if (chosen.size > 1) "Dodaj do listy (${recipesCount(chosen.size)})" else "Dodaj do listy zakupów") }
        }
    }
}
