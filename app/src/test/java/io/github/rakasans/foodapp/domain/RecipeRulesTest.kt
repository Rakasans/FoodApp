package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeRulesTest {

    @Test
    fun `do smaku nie jest brakiem danych`() {
        val check = RecipeRules.check(
            name = "Owsianka",
            ingredients = listOf(
                IngredientFacts("płatki", 50.0, noQuantity = false),
                IngredientFacts("cynamon", null, noQuantity = true),
            ),
            stepTexts = listOf("Ugotuj."),
        )
        assertFalse(check.isIncomplete)
    }

    @Test
    fun `brak ilosci i krokow oznacza niekompletny`() {
        val check = RecipeRules.check(
            name = "Zupa",
            ingredients = listOf(IngredientFacts("mleko kokosowe", null, noQuantity = false)),
            stepTexts = listOf("  "),
        )
        assertTrue(check.isIncomplete)
        assertEquals(listOf("Brak ilości: 1 składnik", "Brak kroków przygotowania"), check.reasons())
    }

    @Test
    fun `formatowanie ilosci po polsku`() {
        assertEquals("1,5", formatQuantity(1.5))
        assertEquals("2", formatQuantity(2.0))
        assertEquals("0,33", formatQuantity(1.0 / 3))
    }
}
