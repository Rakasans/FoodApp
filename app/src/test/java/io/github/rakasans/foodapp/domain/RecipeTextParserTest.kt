package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTextParserTest {

    @Test
    fun `tekst z naglowkami`() {
        val r = RecipeTextParser.parse(
            """
            Racuchy z jabłkami
            Porcje: 4

            Składniki:
            - 500 ml kefiru
            - 2 jajka
            - cukier waniliowy

            Przygotowanie:
            1. Jajka roztrzep z kefirem.
            2. Dodaj mąkę i wymieszaj.
            """.trimIndent()
        )
        assertEquals("Racuchy z jabłkami", r.name)
        assertEquals(4, r.servings)
        assertEquals(listOf("500 ml kefiru", "2 jajka", "cukier waniliowy"), r.ingredients.map { it.raw })
        assertEquals(listOf("Jajka roztrzep z kefirem.", "Dodaj mąkę i wymieszaj."), r.steps)
    }

    @Test
    fun `grupy skladnikow`() {
        val r = RecipeTextParser.parse(
            """
            Pierniki
            Składniki
            Ciasto:
            500 g mąki
            200 g miodu
            Lukier:
            150 g cukru pudru
            Wykonanie
            Wszystko połącz.
            """.trimIndent()
        )
        assertEquals(listOf("Ciasto", "Ciasto", "Lukier"), r.ingredients.map { it.group })
        assertEquals(listOf("Wszystko połącz."), r.steps)
    }

    @Test
    fun `opis rolki z emoji i hashtagami bez naglowkow`() {
        val r = RecipeTextParser.parse(
            """
            🎃 Makaron z dynią 🎃
            • 250 g makaronu
            • 300 g dyni
            • parmezan
            1. Dynię podsmaż na maśle z czosnkiem do miękkości.
            2. Wymieszaj z makaronem i posyp parmezanem.
            #przepis #dynia #obiad
            """.trimIndent()
        )
        assertEquals("Makaron z dynią", r.name)
        assertEquals(listOf("250 g makaronu", "300 g dyni", "parmezan"), r.ingredients.map { it.raw })
        assertEquals(2, r.steps.size)
        assertEquals(null, r.servings)
    }

    @Test
    fun `brak przepisu daje puste pola`() {
        val r = RecipeTextParser.parse("   \n  \n")
        assertEquals("", r.name)
        assertTrue(r.ingredients.isEmpty() && r.steps.isEmpty())
    }

    @Test
    fun `porcje w formie dla osob`() {
        assertEquals(6, RecipeTextParser.parse("Gulasz\nDla 6 osób\nSkładniki\n1 kg wołowiny").servings)
    }
}
