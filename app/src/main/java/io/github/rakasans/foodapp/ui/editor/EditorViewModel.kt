package io.github.rakasans.foodapp.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rakasans.foodapp.data.local.CategoryEntity
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.repository.CategoryRepository
import io.github.rakasans.foodapp.data.repository.DraftIngredient
import io.github.rakasans.foodapp.data.repository.DraftStep
import io.github.rakasans.foodapp.data.repository.PhotoStore
import io.github.rakasans.foodapp.data.repository.RecipeDraft
import io.github.rakasans.foodapp.data.repository.RecipeRepository
import io.github.rakasans.foodapp.data.repository.TagRepository
import io.github.rakasans.foodapp.domain.DuplicateRules
import io.github.rakasans.foodapp.domain.ExistingRecipe
import io.github.rakasans.foodapp.domain.RecipeRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EditorDialog {
    data class Incomplete(val reasons: List<String>) : EditorDialog
    data class Duplicate(val existing: List<ExistingRecipe>) : EditorDialog
    data object DiscardChanges : EditorDialog
    data object DiscardDraft : EditorDialog
    /** Wyjście z edytora, gdy szkic ma nazwę istniejącego przepisu. */
    data class DuplicateOnExit(val existing: List<ExistingRecipe>) : EditorDialog
}

sealed interface EditorEvent {
    data class Approved(val id: String) : EditorEvent
    data class OpenRecipe(val id: String) : EditorEvent
    data object Close : EditorEvent
}

data class EditorUiState(
    val loading: Boolean = true,
    val draft: RecipeDraft = RecipeDraft(),
    /** Tekst pola „Porcje” — osobno, żeby dało się swobodnie pisać. */
    val servingsText: String = "",
    /** true = edycja zatwierdzonego przepisu (zapis przyciskiem, bez autozapisu). */
    val editingApproved: Boolean = false,
    val dirty: Boolean = false,
    val savedOnce: Boolean = false,
    val sameName: List<ExistingRecipe> = emptyList(),
    val dialog: EditorDialog? = null,
    val correcting: Set<String> = emptySet(),
    val photoError: Boolean = false,
)

class EditorViewModel(
    private val recipeId: String?,
    private val recipes: RecipeRepository,
    private val categoryRepo: CategoryRepository,
    private val tagRepo: TagRepository,
    val photos: PhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> =
        categoryRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val knownTags: StateFlow<List<String>> =
        tagRepo.observeAll().map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var existing: List<ExistingRecipe> = emptyList()
    private var saveJob: Job? = null
    private var acceptedIncomplete = false
    private var acceptedDuplicate = false

    init {
        viewModelScope.launch {
            val loaded = recipeId?.let { recipes.loadDraft(it) }
            val draft = loaded ?: RecipeDraft()
            existing = recipes.existingRecipes().filter { it.id != draft.id }
            _state.value = EditorUiState(
                loading = false,
                draft = draft,
                servingsText = draft.servings?.toString().orEmpty(),
                editingApproved = draft.status == RecipeStatus.APPROVED,
                savedOnce = loaded != null,
                sameName = DuplicateRules.findSameName(draft.name, existing),
            )
        }
    }

    // ---------- zmiany treści ----------

    private fun edit(transform: (RecipeDraft) -> RecipeDraft) {
        _state.update { s ->
            val d = transform(s.draft)
            s.copy(draft = d, dirty = true, sameName = DuplicateRules.findSameName(d.name, existing))
        }
        acceptedDuplicate = false
        scheduleAutosave()
    }

    fun setName(v: String) = edit { it.copy(name = v) }
    fun setPrep(v: String) = edit { it.copy(prepTime = v) }
    fun setCook(v: String) = edit { it.copy(cookTime = v) }
    fun setNotes(v: String) = edit { it.copy(notes = v) }

    fun setServings(text: String) {
        val clean = text.filter { it.isDigit() }.take(3)
        _state.update { it.copy(servingsText = clean) }
        edit { it.copy(servings = clean.toIntOrNull()?.takeIf { n -> n > 0 }) }
    }

    /** Wklejenie kilku linii naraz rozbija je na osobne składniki. */
    fun setIngredientText(id: String, text: String) = edit { d ->
        val lines = text.split('\n')
        val list = d.ingredients.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@edit d
        list[index] = list[index].withRawText(lines.first())
        lines.drop(1).filter { it.isNotBlank() }.forEachIndexed { offset, line ->
            list.add(index + 1 + offset, DraftIngredient().withRawText(line))
        }
        d.copy(ingredients = list)
    }

    fun toggleNoQuantity(id: String) = edit { d ->
        d.copy(ingredients = d.ingredients.map {
            if (it.id == id) it.copy(noQuantity = !it.noQuantity) else it
        })
    }

    fun toggleCorrecting(id: String) = _state.update {
        it.copy(correcting = if (id in it.correcting) it.correcting - id else it.correcting + id)
    }

    fun correctIngredient(id: String, name: String? = null, quantityText: String? = null, unit: String? = null) = edit { d ->
        d.copy(ingredients = d.ingredients.map { i ->
            if (i.id != id) i else i.copy(
                name = name ?: i.name,
                quantity = if (quantityText != null) parseRange(quantityText).first else i.quantity,
                quantityMax = if (quantityText != null) parseRange(quantityText).second else i.quantityMax,
                unit = unit ?: i.unit,
                unitInferred = if (unit != null) false else i.unitInferred,
                isRange = if (quantityText != null) parseRange(quantityText).second != null else i.isRange,
                manuallyCorrected = true,
            )
        })
    }

    fun addIngredient() = edit { it.copy(ingredients = it.ingredients + DraftIngredient()) }
    fun removeIngredient(id: String) = edit { d ->
        d.copy(ingredients = d.ingredients.filterNot { it.id == id }.ifEmpty { listOf(DraftIngredient()) })
    }

    fun setStep(id: String, text: String) = edit { d ->
        d.copy(steps = d.steps.map { if (it.id == id) it.copy(text = text) else it })
    }
    fun addStep() = edit { it.copy(steps = it.steps + DraftStep()) }
    fun removeStep(id: String) = edit { d ->
        d.copy(steps = d.steps.filterNot { it.id == id }.ifEmpty { listOf(DraftStep()) })
    }

    fun toggleCategory(id: String) = edit { d ->
        d.copy(categoryIds = if (id in d.categoryIds) d.categoryIds - id else d.categoryIds + id)
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val c = categoryRepo.getOrCreate(name)
            edit { it.copy(categoryIds = it.categoryIds + c.id) }
        }
    }

    fun toggleTag(name: String) = edit { d ->
        val has = d.tagNames.any { it.equals(name, ignoreCase = true) }
        d.copy(tagNames = if (has) d.tagNames.filterNot { it.equals(name, ignoreCase = true) } else d.tagNames + name)
    }

    fun addTag(name: String) {
        val clean = name.trim().removePrefix("#").trim()
        if (clean.isEmpty()) return
        edit { d -> if (d.tagNames.any { it.equals(clean, true) }) d else d.copy(tagNames = d.tagNames + clean) }
    }

    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            val path = photos.import(uri)
            if (path == null) _state.update { it.copy(photoError = true) }
            else {
                _state.update { it.copy(photoError = false) }
                edit { it.copy(photoPath = path) }
            }
        }
    }

    fun removePhoto() = edit { it.copy(photoPath = null) }

    // ---------- zapis ----------

    private fun scheduleAutosave() {
        val s = _state.value
        if (s.editingApproved || !s.draft.hasContent) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            recipes.saveDraft(_state.value.draft, RecipeStatus.DRAFT)
            _state.update { it.copy(dirty = false, savedOnce = true) }
        }
    }

    /** Zatwierdzenie (szkic) albo zapis zmian (zatwierdzony przepis). */
    fun approve() {
        val d = _state.value.draft
        val check = RecipeRules.check(d.name, d.ingredients.filter { it.rawText.isNotBlank() }.map { it.facts }, d.steps.map { it.text })
        if (check.isIncomplete && !acceptedIncomplete) {
            _state.update { it.copy(dialog = EditorDialog.Incomplete(check.reasons())) }
            return
        }
        val dups = DuplicateRules.findSameName(d.name, existing)
        if (dups.isNotEmpty() && !acceptedDuplicate) {
            _state.update { it.copy(dialog = EditorDialog.Duplicate(dups)) }
            return
        }
        saveJob?.cancel()
        viewModelScope.launch {
            recipes.saveDraft(d.copy(name = d.name.ifBlank { "Przepis bez nazwy" }), RecipeStatus.APPROVED)
            _state.update { it.copy(dirty = false, dialog = null) }
            _events.send(EditorEvent.Approved(d.id))
        }
    }

    fun acceptIncomplete() {
        acceptedIncomplete = true
        dismissDialog()
        approve()
    }

    fun keepBoth() {
        acceptedDuplicate = true
        dismissDialog()
        approve()
    }

    fun openExisting(id: String) {
        dismissDialog()
        viewModelScope.launch {
            flush()
            _events.send(EditorEvent.OpenRecipe(id))
        }
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    /** „Wstecz”: szkic jest już zapisany; przy edycji zatwierdzonego pytamy o niezapisane zmiany. */
    fun onBack() {
        val s = _state.value
        if (s.editingApproved && s.dirty) {
            _state.update { it.copy(dialog = EditorDialog.DiscardChanges) }
        } else if (!s.editingApproved && s.draft.hasContent && s.sameName.isNotEmpty()) {
            // Nie zostawiamy po cichu drugiego przepisu o tej samej nazwie — decyduje użytkownik.
            _state.update { it.copy(dialog = EditorDialog.DuplicateOnExit(s.sameName)) }
        } else {
            viewModelScope.launch {
                flush()
                _events.send(EditorEvent.Close)
            }
        }
    }

    /** Świadomie zostawiamy szkic mimo tej samej nazwy. */
    fun keepDraftAndClose() {
        dismissDialog()
        viewModelScope.launch {
            flush()
            _events.send(EditorEvent.Close)
        }
    }

    fun confirmDiscardChanges() {
        dismissDialog()
        viewModelScope.launch { _events.send(EditorEvent.Close) }
    }

    fun askDiscardDraft() = _state.update { it.copy(dialog = EditorDialog.DiscardDraft) }

    fun confirmDiscardDraft() {
        dismissDialog()
        saveJob?.cancel()
        viewModelScope.launch {
            val s = _state.value
            if (s.savedOnce) recipes.softDelete(listOf(s.draft.id))
            _events.send(EditorEvent.Close)
        }
    }

    /** Dokończ oczekujący autozapis przed wyjściem. */
    private suspend fun flush() {
        val s = _state.value
        if (!s.editingApproved && s.draft.hasContent && (s.dirty || saveJob?.isActive == true)) {
            saveJob?.cancel()
            recipes.saveDraft(s.draft, RecipeStatus.DRAFT)
            _state.update { it.copy(dirty = false, savedOnce = true) }
        }
    }
}

/** „300” → (300, null); „300-350” / „300–350” → (300, 350). */
private fun parseRange(text: String): Pair<Double?, Double?> {
    val parts = text.split('-', '–', '—').map { it.trim().replace(',', '.') }
    val min = parts.getOrNull(0)?.toDoubleOrNull()
    val max = if (parts.size == 2) parts[1].toDoubleOrNull() else null
    return min to max
}
