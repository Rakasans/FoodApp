package io.github.rakasans.foodapp.domain

/** Minimalny opis składnika potrzebny regułom (niezależny od Room). */
data class IngredientFacts(
    val name: String,
    val quantity: Double?,
    val noQuantity: Boolean,
)

data class CompletenessCheck(
    val missingName: Boolean,
    val missingQuantities: Int,
    val missingSteps: Boolean,
) {
    val isIncomplete: Boolean get() = missingName || missingQuantities > 0 || missingSteps

    fun reasons(): List<String> = buildList {
        if (missingName) add("Brak nazwy")
        if (missingQuantities > 0) add("Brak ilości: $missingQuantities ${if (missingQuantities == 1) "składnik" else "składniki"}")
        if (missingSteps) add("Brak kroków przygotowania")
    }
}

object RecipeRules {
    /** Brak ilości = ilość nieznana i składnik nie jest oznaczony jako „bez ilości / do smaku”. */
    fun isQuantityMissing(i: IngredientFacts): Boolean = i.quantity == null && !i.noQuantity && i.name.isNotBlank()

    fun check(name: String, ingredients: List<IngredientFacts>, stepTexts: List<String>): CompletenessCheck =
        CompletenessCheck(
            missingName = name.isBlank(),
            missingQuantities = ingredients.count(::isQuantityMissing),
            missingSteps = stepTexts.none { it.isNotBlank() },
        )
}
