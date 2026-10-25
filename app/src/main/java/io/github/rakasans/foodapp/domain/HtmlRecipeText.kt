package io.github.rakasans.foodapp.domain

/**
 * Plan B importu ze stron: strona nie ma danych strukturalnych (schema.org), ale przepis jest w treści.
 * Wyciągamy widoczny tekst (bez menu, stopki, skryptów), szukamy listy składników — po nagłówku
 * „Składniki” albo po najdłuższym ciągu linii z ilościami — i oddajemy fragment parserowi tekstu.
 * Niczego nie dopisujemy; wycięty fragment strony zostaje jako oryginał przy przepisie.
 */
object HtmlRecipeText {

    data class Extract(val title: String, val text: String)

    private val REMOVE_BLOCKS = listOf(
        "script", "style", "noscript", "svg", "template", "iframe", "header", "footer", "nav", "aside",
        "form", "button", "select", "figure",
    ).map { Regex("""<$it\b[^>]*>.*?</$it\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)) }
    private val COMMENTS = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val BLOCK_TAGS = Regex(
        """</?(p|div|li|ul|ol|h[1-6]|br|tr|table|section|article|main|dt|dd|dl|blockquote)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val ANY_TAG = Regex("""<[^>]+>""")

    private val INGREDIENT_HEADER = Regex("""^(składniki|skladniki|potrzebne składniki|lista składników)\b.{0,40}$""", RegexOption.IGNORE_CASE)
    /** Tu kończy się przepis, a zaczynają komentarze, polecane, stopka. */
    private val END = Regex(
        """^(komentarz|komentarze|dodaj komentarz|skomentuj|podobne przepisy|zobacz (też|również|także)|polecane|""" +
            """przeczytaj (też|również)|może cię zainteresować|może ci się spodobać|newsletter|udostępnij|tagi\b|""" +
            """oceń przepis|twoja ocena|©|copyright|zostaw komentarz|inne przepisy|rady\b|rady/porady|porady\b|wskazówki\b)""",
        RegexOption.IGNORE_CASE,
    )
    private const val MAX_LINES = 150

    /** Widoczny tekst strony, linia po linii. */
    fun visibleLines(html: String): List<String> {
        val body = html.substringAfter("<body", html)
        var s = COMMENTS.replace(body, " ")
        REMOVE_BLOCKS.forEach { s = it.replace(s, "\n") }
        s = BLOCK_TAGS.replace(s, "\n")
        s = ANY_TAG.replace(s, " ")
        val out = mutableListOf<String>()
        InstagramPage.decodeEntities(s).replace(' ', ' ').lines().forEach { raw ->
            val line = raw.replace(Regex("""[ \t]+"""), " ").trim()
            if (line.isNotEmpty() && line != out.lastOrNull()) out += line
        }
        return out
    }

    fun extract(html: String): Extract? {
        val lines = visibleLines(html)
        if (lines.isEmpty()) return null

        val headerAt = lines.indexOfFirst { INGREDIENT_HEADER.matches(it) }
        val start: Int
        val prefix: List<String>
        if (headerAt >= 0) {
            start = headerAt
            prefix = emptyList()
        } else {
            val run = longestIngredientRun(lines) ?: return null
            start = run
            prefix = listOf("Składniki:")
        }
        val tail = lines.drop(start)
        val endAt = tail.indexOfFirst { END.containsMatchIn(it) }.let { if (it <= 0) tail.size else it }
        val slice = prefix + tail.take(minOf(endAt, MAX_LINES))
        if (slice.size < 3) return null
        return Extract(title(html).orEmpty(), slice.joinToString("\n"))
    }

    /** Początek najdłuższego ciągu linii z ilością (dopuszczamy jedną „dziurę”, np. nazwę grupy). Min. 4 linie. */
    private fun longestIngredientRun(lines: List<String>): Int? {
        var bestStart = -1
        var bestLen = 0
        var i = 0
        while (i < lines.size) {
            if (!isIngredientLine(lines[i])) { i++; continue }
            val s = i
            var count = 0
            var gap = 0
            var j = i
            while (j < lines.size) {
                if (isIngredientLine(lines[j])) { count++; gap = 0 }
                else if (++gap > 1) break
                j++
            }
            if (count > bestLen) { bestLen = count; bestStart = s }
            i = j
        }
        return if (bestLen >= 4) bestStart else null
    }

    private fun isIngredientLine(line: String): Boolean =
        line.length <= 90 && !line.endsWith(".") && IngredientParser.parse(line).quantity != null

    private fun title(html: String): String? {
        // h1 z treści (bez nagłówka strony, gdzie bywa logo serwisu), potem og:title bez nazwy serwisu
        var cleaned = html
        REMOVE_BLOCKS.forEach { cleaned = it.replace(cleaned, " ") }
        val h1 = Regex("""<h1\b[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(cleaned)?.groupValues?.get(1)?.let { ANY_TAG.replace(it, " ") }?.let(::normalize)
        if (!h1.isNullOrBlank()) return h1
        val og = Regex("""<meta[^>]+property=["']og:title["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::normalize) ?: return null
        // „Gulasz wołowy z dynią | AniaGotuje.pl” → „Gulasz wołowy z dynią”
        val sep = Regex("""\s[|–—-]\s""").findAll(og).lastOrNull() ?: return og
        val suffix = og.substring(sep.range.last + 1)
        return if (suffix.length <= 30) og.substring(0, sep.range.first).trim() else og
    }

    private fun normalize(s: String) = InstagramPage.decodeEntities(s).replace(Regex("\\s+"), " ").trim()
}
