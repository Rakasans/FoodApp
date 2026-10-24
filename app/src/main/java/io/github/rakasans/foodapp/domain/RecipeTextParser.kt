package io.github.rakasans.foodapp.domain

data class ParsedIngredientLine(val raw: String, val group: String? = null)

/** Wynik rozpoznania tekstu przepisu. Puste pola = nie znaleziono (niczego nie dopowiadamy). */
data class ParsedRecipeText(
    val name: String,
    val servings: Int?,
    val ingredients: List<ParsedIngredientLine>,
    val steps: List<String>,
)

/**
 * Regułowe rozpoznawanie przepisu z tekstu: wklejonego, z OCR albo z opisu rolki.
 * Rozpoznaje nagłówki („Składniki”, „Przygotowanie”…), grupy („Ciasto:”), numerację kroków,
 * liczbę porcji. Linie, których nie umie przypisać, pomija — oryginalny tekst zostaje przy przepisie.
 */
object RecipeTextParser {

    private val INGREDIENT_HEADERS = Regex("""^(składniki|skladniki|ingredients|potrzebne będą|potrzebujesz)\b.*""", RegexOption.IGNORE_CASE)
    private val STEP_HEADERS = Regex(
        """^(przygotowanie|sposób przygotowania|sposob przygotowania|wykonanie|przepis|instrukcja|kroki|jak zrobić|jak przygotować|method|instructions)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    private val SERVINGS = listOf(
        Regex("""(?:porcj\w*|liczba porcji|dla)\s*[:\-]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,2})\s*(?:porcj\w*|osób|osoby|os\.)""", RegexOption.IGNORE_CASE),
    )
    private val STEP_NUMBER = Regex("""^(?:krok\s*)?(\d{1,2})\s*[.):\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("""^[-•*–·▪✓✔]\s*""")
    private val HASHTAGS_ONLY = Regex("""^(#\S+\s*)+$""")
    private val META_LINE = Regex("""^(porcj|czas|kcal|liczba porcji)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedRecipeText {
        val lines = text.lines().map { cleanLine(it) }.filter { it.isNotEmpty() && !HASHTAGS_ONLY.matches(it) }
        if (lines.isEmpty()) return ParsedRecipeText("", null, emptyList(), emptyList())

        val servings = SERVINGS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it > 0 }
        val hasHeaders = lines.any { INGREDIENT_HEADERS.matches(it) || STEP_HEADERS.matches(it) }

        val nameIndex = lines.indexOfFirst { !isHeader(it) && !META_LINE.containsMatchIn(it) }
        val name = if (nameIndex >= 0 && looksLikeTitle(lines[nameIndex])) lines[nameIndex] else ""

        val ingredients = mutableListOf<ParsedIngredientLine>()
        val steps = mutableListOf<String>()
        var mode = Mode.NONE
        var group: String? = null

        lines.forEachIndexed { index, rawLine ->
            if (index == nameIndex && name.isNotEmpty()) return@forEachIndexed
            val line = rawLine
            when {
                INGREDIENT_HEADERS.matches(line) -> { mode = Mode.INGREDIENTS; group = null; return@forEachIndexed }
                STEP_HEADERS.matches(line) -> { mode = Mode.STEPS; return@forEachIndexed }
                META_LINE.containsMatchIn(line) -> return@forEachIndexed
            }
            when (mode) {
                Mode.INGREDIENTS -> {
                    val stepMatch = STEP_NUMBER.matchEntire(line)
                    when {
                        // „Ciasto:” — nagłówek grupy składników
                        line.endsWith(":") && line.length <= 40 && IngredientParser.parse(line).quantity == null ->
                            group = line.removeSuffix(":").trim()
                        // numerowane długie zdanie w sekcji składników = kroki bez nagłówka
                        stepMatch != null && stepMatch.groupValues[2].length > 40 -> {
                            mode = Mode.STEPS; steps += stepMatch.groupValues[2].trim()
                        }
                        else -> ingredients += ParsedIngredientLine(line.replace(BULLET, "").trim(), group)
                    }
                }
                Mode.STEPS -> {
                    val m = STEP_NUMBER.matchEntire(line)
                    steps += (m?.groupValues?.get(2) ?: line).trim()
                }
                Mode.NONE -> if (!hasHeaders) {
                    // Bez nagłówków: numerowane linie to kroki, linie z ilością albo punktorem to składniki.
                    val m = STEP_NUMBER.matchEntire(line)
                    when {
                        m != null && !looksLikeIngredient(m.groupValues[2]) -> steps += m.groupValues[2].trim()
                        BULLET.containsMatchIn(line) || looksLikeIngredient(line) ->
                            ingredients += ParsedIngredientLine(line.replace(BULLET, "").trim())
                        line.length > 60 -> steps += line
                    }
                }
            }
        }
        return ParsedRecipeText(name, servings, ingredients, steps)
    }

    private enum class Mode { NONE, INGREDIENTS, STEPS }

    private fun isHeader(line: String) = INGREDIENT_HEADERS.matches(line) || STEP_HEADERS.matches(line)

    /** Tytuł: krótka linia bez ilości na początku. */
    private fun looksLikeTitle(line: String): Boolean =
        line.length <= 80 && IngredientParser.parse(line).quantity == null && !line.endsWith(":")

    private fun looksLikeIngredient(line: String): Boolean {
        val p = IngredientParser.parse(line)
        return p.quantity != null && line.length <= 60 || p.isRange
    }

    /** Usuwa emoji i ozdobniki z początku i końca linii (opisy z Instagrama). */
    private fun cleanLine(line: String): String {
        var s = line.trim()
        while (s.isNotEmpty() && isDecoration(s.codePointAt(0))) s = s.substring(Character.charCount(s.codePointAt(0))).trim()
        while (s.isNotEmpty() && isDecoration(s.codePointBefore(s.length))) {
            s = s.substring(0, s.length - Character.charCount(s.codePointBefore(s.length))).trim()
        }
        return s
    }

    private fun isDecoration(cp: Int): Boolean =
        Character.getType(cp) == Character.OTHER_SYMBOL.toInt() ||
            Character.getType(cp) == Character.SURROGATE.toInt() ||
            cp == 0xFE0F || cp == 0x200D
}
