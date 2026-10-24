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
}
