package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientMentionsTest {

    private fun ings(vararg raws: String) = raws.mapIndexed { i, raw ->
        MentionIngredient("i$i", IngredientParser.parse(raw).name, raw)
    }

    private fun found(step: String, list: List<MentionIngredient>): List<Pair<String, List<String>>> =
        IngredientMentions.find(step, list).map { step.substring(it.start, it.end) to it.ingredientIds }

    @Test
    fun `nazwy w krokach mimo odmiany`() {
        val list = ings(
            "2 średnie cebule", "5 ząbków czosnku", "1 czubata łyżka pasty gochujang",
            "3 duże łychy jasnej pasty miso", "2 czubate łyżki masła orzechowego", "3 liście limonki kaffir",
            "3 łyżki sosu sojowego", "2 łyżki sosu rybnego",
        )
        val step = "Dodaj gochujang, miso, masło orzechowe i liście limonki kaffir. Cebulę pokrój, czosnek zetrzyj. Dopraw sosem sojowym."
        val f = found(step, list).toMap()
        assertEquals(listOf("i4"), f["masło orzechowe"])
        assertEquals(listOf("i5"), f["liście limonki kaffir"])
        assertEquals(listOf("i0"), f["Cebulę"])
        assertEquals(listOf("i1"), f["czosnek"])
        assertEquals(listOf("i6"), f["sosem sojowym"])
    }

    @Test
    fun `przymiotnik na poczatku nie jest slowem kluczowym`() {
        assertEquals(listOf("cebule"), IngredientMentions.keywords("średnie cebule"))
        assertEquals(listOf("dyni"), IngredientMentions.keywords("dyni - fajnie sprawdzi się hokkaido"))
    }

    @Test
    fun `brak pewnosci to brak podpowiedzi`() {
        val list = ings("200 g mąki", "1 jajko")
        assertTrue(found("Wymieszaj wszystko i odstaw na 10 minut.", list).isEmpty())
    }
}
