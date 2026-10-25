package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationTextTest {

    @Test
    fun `nowa linia dzieli tekst a liczebniki staja sie cyframi`() {
        val text = DictationText.append("", "składniki nowa linia dwie łyżki oliwy nowa linia 200 g mąki")
        assertEquals("Składniki\n2 łyżki oliwy\n200 g mąki", text)
        assertEquals(2.0, IngredientParser.parse(text.lines()[1]).quantity!!, 1e-9)
    }

    @Test
    fun `kolejny fragment od nowej linii`() {
        val text = DictationText.append("Placki\n2 jajka", "przygotowanie")
        assertEquals("Placki\n2 jajka\nPrzygotowanie", text)
    }

    @Test
    fun `slowo bez dalszego tekstu i zwykle zdanie bez zmian`() {
        assertEquals(listOf("Dwa"), DictationText.normalize("dwa"))
        assertEquals(listOf("Smaż na złoto"), DictationText.normalize("smaż na złoto"))
    }
}
