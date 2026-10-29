package io.github.rakasans.foodapp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rakasans.foodapp.data.local.CategoryEntity
import io.github.rakasans.foodapp.data.local.ProcessingState
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.local.RecipeSummary
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.data.local.TagEntity
import io.github.rakasans.foodapp.data.repository.CategoryRepository
import io.github.rakasans.foodapp.data.repository.RecipeRepository
import io.github.rakasans.foodapp.data.repository.TagRepository
import io.github.rakasans.foodapp.domain.IngredientFacts
import io.github.rakasans.foodapp.domain.RecipeRules
import io.github.rakasans.foodapp.domain.formatUpdated
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.BadgeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeCardUi(
    val id: String,
    val name: String,
    val meta: String,
    val tags: List<String>,
    val badges: List<BadgeUi>,
    val target: OpenTarget = OpenTarget.DETAIL,
    val updatedText: String = "",
    val photoUri: String? = null,
    val favorite: Boolean = false,
)

/** Dokąd prowadzi stuknięcie karty. */
enum class OpenTarget { DETAIL, EDITOR, MATERIAL }

data class LibraryFilters(
    val query: String = "",
    val categoryId: String? = null,
    val tagId: String? = null,
    val favoritesOnly: Boolean = false,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val filters: LibraryFilters = LibraryFilters(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val items: List<RecipeCardUi> = emptyList(),
    val libraryEmpty: Boolean = false,
    /** Zaznaczone przepisy (tryb zaznaczania po długim przytrzymaniu). */
    val selected: Set<String> = emptySet(),
) {
    val noResults: Boolean get() = !loading && !libraryEmpty && items.isEmpty()
    val selectionMode: Boolean get() = selected.isNotEmpty()
}

class LibraryViewModel(
    private val recipes: RecipeRepository,
    categories: CategoryRepository,
    tags: TagRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(LibraryFilters())
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<LibraryUiState> = combine(
        recipes.observeSummaries(), categories.observeAll(), tags.observeAll(), filters, selected,
    ) { all, cats, tagList, f, sel ->
        val visible = all.filter { matches(it, f) }
        LibraryUiState(
            loading = false,
            filters = f,
            categories = cats,
            tags = tagList,
            items = visible.map(::toCard),
            libraryEmpty = all.isEmpty(),
            // zaznaczenie obejmuje tylko widoczne przepisy
            selected = sel.intersect(visible.map { it.recipe.id }.toSet()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQuery(q: String) = filters.update { it.copy(query = q) }
    fun onCategory(id: String?) = filters.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    fun onTag(id: String?) = filters.update { it.copy(tagId = if (it.tagId == id) null else id) }
    fun onFavoritesOnly() = filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun toggleSelection(id: String) = selected.update { if (id in it) it - id else it + id }
    fun selectAllVisible() = selected.update { state.value.items.map { it.id }.toSet() }
    fun clearSelection() = selected.update { emptySet() }

    /** Usuwa zaznaczone (miękko) i zwraca ich identyfikatory — do „Cofnij”. */
    fun deleteSelected(onDone: (List<String>) -> Unit) {
        val ids = state.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            recipes.softDelete(ids)
            selected.update { emptySet() }
            onDone(ids)
        }
    }

    fun restore(ids: List<String>) {
        viewModelScope.launch { recipes.restore(ids) }
    }

    private fun matches(s: RecipeSummary, f: LibraryFilters): Boolean {
        val q = f.query.trim().lowercase()
        if (q.isNotEmpty()) {
            val haystack = buildString {
                append(s.recipe.name).append(' ')
                s.ingredients.forEach { append(it.name).append(' ') }
                s.tags.forEach { append(it.name).append(' ') }
                append(s.recipe.notes.orEmpty()).append(' ')
                append(s.recipe.materialNote.orEmpty())
            }.lowercase()
            if (!haystack.contains(q)) return false
        }
        if (f.categoryId != null && s.categories.none { it.id == f.categoryId }) return false
        if (f.tagId != null && s.tags.none { it.id == f.tagId }) return false
        if (f.favoritesOnly && !s.recipe.favorite) return false
        return true
    }

    private fun toCard(s: RecipeSummary): RecipeCardUi {
        val r = s.recipe
        val badges = buildList {
            when (r.status) {
                RecipeStatus.MATERIAL -> when (r.processing) {
                    ProcessingState.WAITING_NETWORK -> add(BadgeUi("Czeka na internet", BadgeKind.INFO))
                    ProcessingState.RUNNING -> add(BadgeUi("Pobieranie opisu…", BadgeKind.INFO))
                    ProcessingState.ERROR -> add(BadgeUi("Import nieudany — materiał zachowany", BadgeKind.DANGER))
                    ProcessingState.NEEDS_SCREENSHOT -> add(BadgeUi("Rolka — opis nie pobrany", BadgeKind.INFO))
                    ProcessingState.NONE -> add(BadgeUi("Link zapisany — do uzupełnienia", BadgeKind.INFO))
                }
                RecipeStatus.DRAFT -> add(BadgeUi("Szkic — do przejrzenia", BadgeKind.WARN))
                RecipeStatus.APPROVED -> {
                    val check = RecipeRules.check(
                        r.name,
                        s.ingredients.map { IngredientFacts(it.name, it.quantity, it.noQuantity) },
                        s.steps.map { it.text },
                    )
                    if (check.isIncomplete) add(BadgeUi("Niekompletny", BadgeKind.WARN))
                }
            }
        }
        val categoryText = s.categories.joinToString(", ") { it.name }.ifEmpty { "Bez kategorii" }
        return RecipeCardUi(
            id = r.id,
            name = r.name.ifBlank { "Szkic bez nazwy" },
            meta = listOf(categoryText, sourceLabel(r.sourceType)).joinToString(" · "),
            tags = s.tags.map { it.name },
            badges = badges,
            target = when (r.status) {
                RecipeStatus.DRAFT -> OpenTarget.EDITOR
                RecipeStatus.MATERIAL -> OpenTarget.MATERIAL
                RecipeStatus.APPROVED -> OpenTarget.DETAIL
            },
            updatedText = "Zaktualizowano: " + formatUpdated(r.updatedAt, System.currentTimeMillis()),
            photoUri = r.photoUri,
            favorite = r.favorite,
        )
    }
}

fun sourceLabel(type: SourceType): String = when (type) {
    SourceType.MANUAL -> "Wpisany ręcznie"
    SourceType.PASTED_TEXT -> "Wklejony tekst"
    SourceType.DICTATION -> "Dyktowanie"
    SourceType.PHOTO -> "Zdjęcie"
    SourceType.WEB -> "Strona WWW"
    SourceType.INSTAGRAM_REEL -> "Instagram — rolka"
    SourceType.INSTAGRAM_SCREENSHOT -> "Instagram — zrzut opisu"
}
