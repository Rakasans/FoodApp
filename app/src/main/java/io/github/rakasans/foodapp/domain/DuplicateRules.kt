package io.github.rakasans.foodapp.domain

/** Istniejący przepis widziany przez regułę duplikatów. */
data class ExistingRecipe(val id: String, val name: String, val sourceUrl: String? = null, val isMaterial: Boolean = false)

/**
 * Możliwy duplikat = przepis o tej samej nazwie (po normalizacji: wielkość liter, spacje).
 * Użytkownik zawsze decyduje, co zrobić — reguła tylko wskazuje kandydatów.
 * Od v0.4 także ten sam adres źródła (rolka / strona zapisana drugi raz).
 */
object DuplicateRules {

    /** Przepisy z tym samym źródłem (adres porównywany po [sourceKey]). */
    fun findSameSource(url: String, existing: List<ExistingRecipe>): List<ExistingRecipe> {
        val key = sourceKey(url) ?: return emptyList()
        return existing.filter { e -> e.sourceUrl?.let(::sourceKey) == key }
    }

    /**
     * Klucz adresu: bez protokołu, „www.”/„m.”, parametrów śledzących, kotwicy i końcowego „/”.
     * Instagram: tylko kod rolki/posta (reel/p/tv to ten sam materiał).
     */
    fun sourceKey(url: String): String? {
        val u = url.trim()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return null
        InstagramPage.shortcode(u)?.let { if (isInstagramUrl(u)) return "instagram:$it" }
        val noScheme = u.substringAfter("://")
        val hostPart = noScheme.substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
        val rest = noScheme.substringAfter('/', "").substringBefore('#')
        val path = rest.substringBefore('?').trimEnd('/')
        val query = rest.substringAfter('?', "").split('&')
            .filter { it.isNotBlank() && !TRACKING.containsMatchIn(it.substringBefore('=')) }
            .sorted().joinToString("&")
        return hostPart + "/" + path + (if (query.isNotEmpty()) "?$query" else "")
    }

    private val TRACKING = Regex("""^(utm_\w+|fbclid|gclid|igsh|igshid|mc_\w+|ref|si|share)$""", RegexOption.IGNORE_CASE)

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
