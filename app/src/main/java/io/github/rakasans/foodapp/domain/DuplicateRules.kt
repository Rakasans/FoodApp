package io.github.rakasans.foodapp.domain

/** Istniejący przepis widziany przez regułę duplikatów. */
data class ExistingRecipe(val id: String, val name: String)

/**
 * Możliwy duplikat = przepis o tej samej nazwie (po normalizacji: wielkość liter, spacje).
 * Użytkownik zawsze decyduje, co zrobić — reguła tylko wskazuje kandydatów.
 * Później: także ten sam adres źródła (v0.4).
 */
object DuplicateRules {

    fun findSameName(name: String, existing: List<ExistingRecipe>): List<ExistingRecipe> {
        val key = normalizeName(name)
        if (key.isEmpty()) return emptyList()
        return existing.filter { normalizeName(it.name) == key }
    }

    /** Dzieli nowe nazwy na te, które już istnieją, i te, które są nowe. */
    fun partition(newNames: List<String>, existing: List<ExistingRecipe>): Pair<List<String>, List<String>> {
        val existingKeys = existing.map { normalizeName(it.name) }.toSet()
        return newNames.partition { normalizeName(it) in existingKeys }
    }
}
