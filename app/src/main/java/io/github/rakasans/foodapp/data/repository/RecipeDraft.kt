package io.github.rakasans.foodapp.data.repository

import io.github.rakasans.foodapp.core.Ids
import io.github.rakasans.foodapp.data.local.RecipeDetails
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.domain.IngredientFacts
import io.github.rakasans.foodapp.domain.IngredientParser

/** Składnik w edytorze. `rawText` to zapis użytkownika; pozostałe pola — rozpoznane lub poprawione. */
data class DraftIngredient(
    val id: String = Ids.new(),
    val rawText: String = "",
    val name: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val noQuantity: Boolean = false,
    val unitInferred: Boolean = false,
    val isRange: Boolean = false,
    /** true = użytkownik poprawił nazwę/ilość/jednostkę ręcznie; zmiana zapisu nie nadpisze poprawek. */
    val manuallyCorrected: Boolean = false,
    val groupName: String? = null,
) {
    val facts: IngredientFacts get() = IngredientFacts(name, quantity, noQuantity)

    /** Nowy zapis → ponowne rozpoznanie (chyba że użytkownik poprawił pola ręcznie). */
    fun withRawText(text: String): DraftIngredient {
        if (manuallyCorrected) return copy(rawText = text)
        val p = IngredientParser.parse(text)
        return copy(
            rawText = text, name = p.name, quantity = p.quantity, unit = p.unit,
            unitInferred = p.unitInferred, isRange = p.isRange,
        )
    }
}

data class DraftStep(val id: String = Ids.new(), val text: String = "")

/** Stan przepisu w edytorze — niezależny od encji Room. */
data class RecipeDraft(
    val id: String = Ids.new(),
    val status: RecipeStatus = RecipeStatus.DRAFT,
    val name: String = "",
    val servings: Int? = null,
    val prepTime: String = "",
    val cookTime: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val sourceUrl: String? = null,
    val sourceAuthor: String? = null,
    val ingredients: List<DraftIngredient> = listOf(DraftIngredient()),
    val steps: List<DraftStep> = listOf(DraftStep()),
    val categoryIds: Set<String> = emptySet(),
    val tagNames: List<String> = emptyList(),
) {
    /** Czy użytkownik cokolwiek wpisał — pusty szkic nie jest zapisywany. */
    val hasContent: Boolean
        get() = name.isNotBlank() || notes.isNotBlank() || photoPath != null ||
            ingredients.any { it.rawText.isNotBlank() } || steps.any { it.text.isNotBlank() }

    companion object {
        fun from(d: RecipeDetails): RecipeDraft = RecipeDraft(
            id = d.recipe.id,
            status = d.recipe.status,
            name = d.recipe.name,
            servings = d.recipe.servings,
            prepTime = d.recipe.prepTime.orEmpty(),
            cookTime = d.recipe.cookTime.orEmpty(),
            notes = d.recipe.notes.orEmpty(),
            photoPath = d.recipe.photoUri,
            sourceType = d.recipe.sourceType,
            sourceUrl = d.recipe.sourceUrl,
            sourceAuthor = d.recipe.sourceAuthor,
            ingredients = d.ingredients.sortedBy { it.position }.map {
                DraftIngredient(
                    id = it.id, rawText = it.rawText, name = it.name, quantity = it.quantity, unit = it.unit,
                    noQuantity = it.noQuantity, unitInferred = it.unitInferred, manuallyCorrected = true,
                    groupName = it.groupName,
                )
            }.ifEmpty { listOf(DraftIngredient()) },
            steps = d.steps.sortedBy { it.position }.map { DraftStep(it.id, it.text) }.ifEmpty { listOf(DraftStep()) },
            categoryIds = d.categories.map { it.id }.toSet(),
            tagNames = d.tags.map { it.name },
        )
    }
}
