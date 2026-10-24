package io.github.rakasans.foodapp.domain

private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** Pierwszy adres http(s) w tekście (np. z „Udostępnij”, gdzie link bywa poprzedzony opisem). */
fun extractUrl(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', ']')

/**
 * Czy udostępniony tekst to link (przeglądarka wysyła „Tytuł strony https://…”), a nie przepis,
 * w którym akurat jest adres. Długi tekst z linkiem traktujemy jako tekst przepisu.
 */
fun isLinkShare(text: String): Boolean {
    val url = extractUrl(text) ?: return false
    return text.replace(url, "").trim().length <= 200
}

fun isInstagramUrl(url: String): Boolean =
    Regex("""^https?://(www\.|m\.)?(instagram\.com|instagr\.am)/""", RegexOption.IGNORE_CASE).containsMatchIn(url)
