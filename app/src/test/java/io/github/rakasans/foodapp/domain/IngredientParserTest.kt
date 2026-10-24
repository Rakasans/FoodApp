package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientParserTest {

    private fun p(s: String) = IngredientParser.parse(s)

    @Test
    fun `liczba i jednostka w odmianie`() {
        val r = p("2 łyżki oliwy")
        assertEquals(2.0, r.quantity!!, 1e-9)
        assertEquals("łyżka", r.unit)
        assertEquals("oliwy", r.name)
        assertFalse(r.unitInferred)
    }

    @Test
    fun `ulamek dziesietny z przecinkiem`() {
        val r = p("1,5 kg dyni")
        assertEquals(1.5, r.quantity!!, 1e-9)
        assertEquals("kg", r.unit)
    }

    @Test
    fun `ulamki zwykle i unicode`() {
        assertEquals(0.5, p("1/2 szklanki mleka").quantity!!, 1e-9)
        assertEquals(1.5, p("1 1/2 szklanki mąki").quantity!!, 1e-9)
        assertEquals(0.5, p("½ cebuli").quantity!!, 1e-9)
        assertEquals(0.5, p("pół kostki masła").quantity!!, 1e-9)
        assertEquals("kostka", p("pół kostki masła").unit)
    }

    @Test
    fun `liczba bez jednostki to sztuki do sprawdzenia`() {
        val r = p("2 jajka")
        assertEquals(2.0, r.quantity!!, 1e-9)
        assertEquals("szt.", r.unit)
        assertTrue(r.unitInferred)
        assertEquals("jajka", r.name)
    }

    @Test
    fun `zakres zapisuje oba konce`() {
        val r = p("2–3 ząbki czosnku")
        assertEquals(2.0, r.quantity!!, 1e-9)
        assertEquals(3.0, r.quantityMax!!, 1e-9)
        assertTrue(r.isRange)
        assertEquals("ząbek", r.unit)
        assertEquals("czosnku", r.name)
    }

    @Test
    fun `zakres w gramach z myslnikiem`() {
        val r = p("300-350 g mąki pszennej")
        assertEquals(300.0, r.quantity!!, 1e-9)
        assertEquals(350.0, r.quantityMax!!, 1e-9)
        assertEquals("g", r.unit)
        assertEquals("mąki pszennej", r.name)
    }

    @Test
    fun `ilosc na koncu linii`() {
        val sezam = p("Sezam - 10g")
        assertEquals("Sezam", sezam.name); assertEquals(10.0, sezam.quantity!!, 1e-9); assertEquals("g", sezam.unit)
        val lisc = p("Liść laurowy 3")
        assertEquals("Liść laurowy", lisc.name); assertEquals(3.0, lisc.quantity!!, 1e-9); assertTrue(lisc.unitInferred)
        val cynamon = p("Cynamon 1 pałka")
        assertEquals("Cynamon", cynamon.name); assertEquals("pałka", cynamon.unit)
        val kardamon = p("Kardamon 6 ziarenek")
        assertEquals("Kardamon", kardamon.name); assertEquals(6.0, kardamon.quantity!!, 1e-9); assertEquals("ziarenko", kardamon.unit)
        val anyz = p("Anyż gwiazdki 2 sztuki")
        assertEquals("Anyż gwiazdki", anyz.name); assertEquals("szt.", anyz.unit); assertFalse(anyz.unitInferred)
    }

    @Test
    fun `oznaczenie to nie ilosc`() {
        assertEquals(null, p("Mąka typ 650").quantity)
        assertEquals(null, p("Makaron nr 5").quantity)
        assertEquals(null, p("Śmietana 18%").quantity)
        assertEquals(null, p("Piecz 20 minut").quantity)
    }

    @Test
    fun `brak liczby oznacza brak ilosci`() {
        val r = p("sól do smaku")
        assertEquals(null, r.quantity)
        assertEquals(null, r.unit)
        assertEquals("sól do smaku", r.name)
    }

    @Test
    fun `myslnik listy jest pomijany`() {
        val r = p("- 300 g mąki pszennej")
        assertEquals(300.0, r.quantity!!, 1e-9)
        assertEquals("g", r.unit)
        assertEquals("mąki pszennej", r.name)
    }

    @Test
    fun `okolo i ok przed iloscia`() {
        val r = p("około 300ml ciepłej wody")
        assertEquals(300.0, r.quantity!!, 1e-9)
        assertEquals("ml", r.unit)
        assertEquals("ciepłej wody", r.name)
        assertEquals(2.0, p("ok. 2 łyżki cukru").quantity!!, 1e-9)
        assertEquals("g", p("~200 g mąki").unit)
        // „ok” tylko jako osobne słowo — nie obcina nazw typu „okra”
        assertEquals("okra", p("okra").name)
    }

    @Test
    fun `jednostka z kropka`() {
        assertEquals("szt.", p("3 szt. jabłek").unit)
        assertEquals("opak.", p("1 op. drożdży").unit)
    }
}
