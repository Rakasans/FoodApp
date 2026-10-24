package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingMergeTest {

    @Test
    fun `sumuje te same jednostki z roznych przepisow`() {
        val lines = ShoppingMerge.merge(
            listOf(
                ShoppingPart("masło", 20.0, "g", recipeName = "Zupa"),
                ShoppingPart("Masło", 100.0, "g", recipeName = "Pierniki"),
            )
        )
        assertEquals(1, lines.size)
        assertEquals("120 g", lines[0].quantityText)
        assertEquals(listOf("Zupa", "Pierniki"), lines[0].recipeNames)
    }

    @Test
    fun `nie sumuje niezgodnych jednostek`() {
        val line = ShoppingMerge.merge(
            listOf(ShoppingPart("mleko", 250.0, "ml"), ShoppingPart("mleko", 2.0, "łyżka"))
        ).single()
        assertEquals("250 ml + 2 łyżka", line.quantityText)
        assertTrue(line.differentUnits)
    }

    @Test
    fun `nie laczy podobnych nazw`() {
        val lines = ShoppingMerge.merge(
            listOf(ShoppingPart("mleko", 250.0, "ml"), ShoppingPart("mleko kokosowe", 400.0, "ml"))
        )
        assertEquals(2, lines.size)
    }

    @Test
    fun `brak ilosci zostaje widoczny`() {
        val line = ShoppingMerge.merge(
            listOf(ShoppingPart("mleko kokosowe", 400.0, "ml"), ShoppingPart("mleko kokosowe", null, null))
        ).single()
        assertEquals("400 ml + ilość nieznana", line.quantityText)
        assertTrue(line.hasUnknownQuantity)
    }

    @Test
    fun `dokladna konwersja kg i l`() {
        val g = ShoppingMerge.merge(listOf(ShoppingPart("dynia", 1.0, "kg"), ShoppingPart("dynia", 300.0, "g"))).single()
        assertEquals("1,3 kg", g.quantityText)
        val ml = ShoppingMerge.merge(listOf(ShoppingPart("bulion", 0.5, "l"), ShoppingPart("bulion", 250.0, "ml"))).single()
        assertEquals("750 ml", ml.quantityText)
        assertFalse(ml.differentUnits)
    }

    @Test
    fun `sztuki nie sa przeliczane na gramy`() {
        val line = ShoppingMerge.merge(listOf(ShoppingPart("cebula", 1.0, "szt."), ShoppingPart("cebula", 200.0, "g"))).single()
        assertEquals("1 szt. + 200 g", line.quantityText)
    }

    @Test
    fun `skalowanie po porcjach i mnoznikiem`() {
        assertEquals(2.0, Scaling.ByServings(original = 2, target = 4).factor, 1e-9)
        assertEquals("×0,5 przepisu", Scaling.ByMultiplier(0.5).label)
    }
}
