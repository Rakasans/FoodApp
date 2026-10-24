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

    @Test
    fun `zakres to osobny segment bez sumowania`() {
        val line = ShoppingMerge.merge(
            listOf(
                ShoppingPart("mąka", 300.0, "g", quantityMax = 350.0, recipeName = "Kluski"),
                ShoppingPart("mąka", 200.0, "g", recipeName = "Naleśniki"),
            )
        ).single()
        assertEquals("200 g + 300–350 g", line.quantityText)
    }

    private fun ing(name: String, q: Double?, unit: String?, max: Double? = null, noQ: Boolean = false) =
        IngredientForShopping(name, "$q $unit $name", q, max, unit, noQ)

    @Test
    fun `przeliczenie porcji obejmuje zakres a nieznana ilosc zostaje nieznana`() {
        val parts = ShoppingBuilder.parts(
            "Zupa",
            listOf(ing("dynia", 1.0, "kg"), ing("ziemniaki", 300.0, "g", max = 350.0), ing("mleko kokosowe", null, null), ing("sól", null, null, noQ = true)),
            Scaling.ByServings(original = 4, target = 6),
        )
        assertEquals(1.5, parts[0].quantity!!, 1e-9)
        assertEquals(450.0, parts[1].quantity!!, 1e-9)
        assertEquals(525.0, parts[1].quantityMax!!, 1e-9)
        assertEquals(null, parts[2].quantity)
        assertTrue(parts[3].noQuantity)
        assertEquals("6 porc. zamiast 4", parts[0].factorLabel)
        val lines = ShoppingMerge.merge(parts).associateBy { it.key }
        assertEquals("1,5 kg", lines.getValue("dynia").quantityText)
        assertEquals("450–525 g", lines.getValue("ziemniaki").quantityText)
        assertEquals("ilość nieznana", lines.getValue("mleko kokosowe").quantityText)
        assertEquals("do smaku", lines.getValue("sól").quantityText)
    }

    @Test
    fun `mnoznik 1 nie dodaje etykiety a sztuki zostaja sztukami`() {
        val parts = ShoppingBuilder.parts("Placki", listOf(ing("jajka", 2.0, "szt.")), Scaling.ByMultiplier(1.0))
        assertEquals(null, parts.single().factorLabel)
        assertEquals("szt.", parts.single().unit)
        assertEquals(2.0, parts.single().quantity!!, 1e-9)
    }
}
