package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateRulesTest {

    private val existing = listOf(
        ExistingRecipe("1", "Szakszuka z papryką"),
        ExistingRecipe("2", "Zupa krem z dyni"),
    )

    @Test
    fun `ta sama nazwa niezaleznie od wielkosci liter i spacji`() {
        val found = DuplicateRules.findSameName("  szakszuka   Z papryką ", existing)
        assertEquals(listOf("1"), found.map { it.id })
    }

    @Test
    fun `podobna nazwa nie jest duplikatem`() {
        assertTrue(DuplicateRules.findSameName("Szakszuka", existing).isEmpty())
    }

    @Test
    fun `pusta nazwa nie jest duplikatem`() {
        assertTrue(DuplicateRules.findSameName("   ", existing).isEmpty())
    }

    @Test
    fun `podzial na istniejace i nowe`() {
        val (dup, fresh) = DuplicateRules.partition(listOf("Zupa krem z dyni", "Pierniki świąteczne"), existing)
        assertEquals(listOf("Zupa krem z dyni"), dup)
        assertEquals(listOf("Pierniki świąteczne"), fresh)
    }

    @Test
    fun `ten sam adres zrodla mimo roznic w zapisie`() {
        val existing = listOf(
            ExistingRecipe("1", "Gulasz", "https://aniagotuje.pl/przepis/gulasz-wolowy-z-dynia"),
            ExistingRecipe("2", "Kluski", "https://www.instagram.com/reel/DPx1AbCdEfG/"),
            ExistingRecipe("3", "Ręczny", null),
        )
        assertEquals(listOf("1"), DuplicateRules.findSameSource("https://www.aniagotuje.pl/przepis/gulasz-wolowy-z-dynia/?utm_source=fb#skladniki", existing).map { it.id })
        assertEquals(listOf("2"), DuplicateRules.findSameSource("https://instagram.com/p/DPx1AbCdEfG/?igsh=abc123", existing).map { it.id })
        assertTrue(DuplicateRules.findSameSource("https://aniagotuje.pl/przepis/gulasz-z-dynia", existing).isEmpty())
        assertTrue(DuplicateRules.findSameSource("https://www.instagram.com/reel/INNYKOD123/", existing).isEmpty())
    }

    @Test
    fun `parametry inne niz sledzace odrozniaja strony`() {
        val a = DuplicateRules.sourceKey("https://example.pl/przepis?id=5&utm_medium=x")
        val b = DuplicateRules.sourceKey("https://example.pl/przepis?id=6")
        assertEquals("example.pl/przepis?id=5", a)
        assertTrue(a != b)
    }
}
