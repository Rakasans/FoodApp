package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Import na wycinkach prawdziwych stron (test/resources/web), pobranych 25.10.2026. */
class RealPagesTest {

    private fun page(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/web/$name")) { "brak pliku $name" }.bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun `aniagotuje mikrodane grupy skladnikow i kroki bez porad`() {
        val r = WebRecipePage.parse(page("aniagotuje_zupa_ogorkowa_krem.html"))!!
        assertEquals("Zupa ogórkowa krem", r.name)
        assertEquals(12, r.ingredients.size)
        assertEquals("ogórki kiszone 500 g", r.ingredients[0])
        assertEquals("Składniki na zupę", r.groups[0])
        assertEquals("Dodatki opcjonalne", r.groups[9])
        // nazwa i ilość z osobnych pól strony
        val pieprz = r.hints[7]!!
        assertEquals("pieprz mielony", pieprz.name); assertEquals(0.5, pieprz.quantity!!, 1e-9); assertEquals("łyżeczka", pieprz.unit)
        val cebula = r.hints[2]!!
        assertEquals("cebula", cebula.name); assertEquals(200.0, cebula.quantity!!, 1e-9); assertEquals("g", cebula.unit)
        assertEquals(null, r.hints[11]) // „do dekoracji” — bez ilości, nie zgadujemy
        // kroki: tylko kroki przepisu, bez porad, pytań FAQ, ocen i przycisków
        assertEquals(3, r.steps.size)
        assertTrue(r.steps[0].startsWith("Podsmaż cebulę z listkami laurowymi\nCałą zupę"))
        assertTrue(r.steps.none { it.contains("Porada") || it.contains("Jakie ziemniaki") || it.contains("Kopiuj") || it.contains("Średnia ocen") })
        assertTrue(r.tips.any { it.startsWith("Porada: Masło klarowane") })
        assertEquals("około 1900 g zupy", r.yieldText)
        assertEquals(null, r.servings)
        assertEquals("1 godz.", r.prepTime)
        assertEquals("Ania Gotuje", r.author)
        assertEquals("100 g zupy", r.nutritionBasis)
        assertEquals("Energia" to "51 kcal", r.nutrition.first())
        assertTrue(r.originalText("u").contains("Porady ze strony:"))
    }

    @Test
    fun `rozkoszny porcje i sekcja do podania`() {
        val r = WebRecipePage.parse(page("rozkoszny_gulasz_soczewica.html"))!!
        assertEquals("Jesienny gulasz z soczewicy, dyni i mangoldu", r.name)
        assertEquals(4, r.servings)
        assertTrue(r.ingredients.none { it.contains("porcje") })
        assertEquals("1 biała cebula, obrana i pokrojona drobno", r.ingredients[0])
        val podanie = r.ingredients.indices.filter { r.groups[it] == "Do podania" }.map { r.ingredients[it] }
        assertEquals(listOf("chleb, masło", "ziemniaki", "szczypiorek zwykły lub czosnkowy"), podanie)
        assertEquals(null, r.groups[0])
        assertEquals(4, r.steps.size)
        assertTrue(r.steps.none { it.contains("botwiną") || it.contains("Instagram") || it.contains("Rozkoszny") || it.contains("Komentarze") })
    }

    @Test
    fun `rozkoszny grupy skladnikow bez dwukropka`() {
        val r = WebRecipePage.parse(page("rozkoszny_kopytka.html"))!!
        assertEquals(2, r.servings)
        assertEquals("Kopytka", r.groups[0])
        assertEquals("500 g ziemniaków", r.ingredients[0])
        assertEquals("Sos kurkowy + dodatki", r.groups.last())
        assertTrue(r.ingredients.none { it == "Kopytka" || it.startsWith("Sos kurkowy") })
        assertEquals(3, r.steps.size)
    }
}
