package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrCorrectorTest {

    @Test
    fun `brakujace ogonki w slowach kulinarnych`() {
        assertEquals("500 g mąki i 2 łyżki masła", OcrCorrector.correct("500 g maki i 2 lyzki masla"))
        assertEquals("Szczyptę soli", OcrCorrector.correct("Szczypte soli"))
        // „cieplej” to też poprawne słowo („cieplej się ubierz”) — nie zgadujemy; „goracej” już tak
        assertEquals("około 300 ml gorącej wody", OcrCorrector.correct("okolo 300 ml goracej wody"))
    }

    @Test
    fun `pomylka l i t w jednostkach`() {
        assertEquals("2 łyżki cukru", OcrCorrector.correct("2 tyzki cukru"))
    }

    @Test
    fun `poprawne slowa bez ogonkow zostaja`() {
        // „cebule” to poprawna liczba mnoga — nie zamieniamy na „cebulę”
        assertEquals("3 cebule, dodaj sól", OcrCorrector.correct("3 cebule, dodaj sól"))
        // „woda”, „oliwa” to poprawne słowa — nie zamieniamy na „wodą”, „oliwą”
        assertEquals("zimna woda i oliwa", OcrCorrector.correct("zimna woda i oliwa"))
        // niejednoznaczne „ciepla” (ciepła / ciepłą) zostaje bez zmian
        assertEquals("ciepla", OcrCorrector.correct("ciepla"))
    }

    @Test
    fun `wielkie litery sa zachowane`() {
        assertEquals("SKŁADNIKI:", OcrCorrector.correct("SKLADNIKI:"))
        assertEquals("Przygotowanie", OcrCorrector.correct("Przygotowanie"))
    }
}
