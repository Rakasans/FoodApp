package io.github.rakasans.foodapp.domain

private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** Pierwszy adres http(s) w tekście (np. z „Udostępnij”, gdzie link bywa poprzedzony opisem). */
fun extractUrl(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', ']')

fun isInstagramUrl(url: String): Boolean =
    Regex("""^https?://(www\.|m\.)?(instagram\.com|instagr\.am)/""", RegexOption.IGNORE_CASE).containsMatchIn(url)
