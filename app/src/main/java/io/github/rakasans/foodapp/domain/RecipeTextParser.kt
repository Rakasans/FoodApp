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
 *
 * - nagłówki („Składniki”, „Potrzebujemy”, „Przygotowanie”…) i grupy („Sos:”),
 * - kroki numerowane albo akapity; linie łamane przez OCR / szerokość ekranu są sklejane w jeden krok,
 * - opis bez nagłówka kroków: długi akapit po liście składników zaczyna kroki,
 * - wstęp przed listą składników i pożegnania („Smacznego”) są pomijane.
 * Linie, których nie umie przypisać, pomija — oryginalny tekst zostaje przy przepisie.
 */
object RecipeTextParser {

    private val INGREDIENT_HEADERS = Regex(
        """^(składniki|skladniki|ingredients|potrzebne będą|potrzebne beda|potrzebujesz|potrzebujemy|będziesz potrzebować|lista zakupów)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    private val STEP_HEADERS = Regex(
        """^(przygotowanie|sposób przygotowania|sposob przygotowania|sposób wykonania|wykonanie|instrukcja|kroki|jak zrobić|jak przygotować|method|instructions)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    private val SERVINGS = listOf(
        // tylko w tej samej linii („Porcje: 4”), nie „4 porcje” + następna linia „1 cebula”
        Regex("""\b(?:porcj\w*|liczba porcji|dla)[ \t]*[:\-]?[ \t]*(\d{1,2})\b""", RegexOption.IGNORE_CASE),
        // „2 – 3 porcje” to zakres — nie wybieramy za użytkownika jednej liczby
        Regex("""(?<!\d\s{0,2}[-–]\s{0,2})\b(\d{1,2})\s*(?:porcj\w*|osób|osoby|os\.)""", RegexOption.IGNORE_CASE),
    )
    private val STEP_NUMBER = Regex("""^(?:krok\s*)?(\d{1,2})\s*[.):\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("""^[-•*–·▪✓✔]\s*""")
    private val HASHTAGS_ONLY = Regex("""^(#\S+\s*)+$""")
    /** Linie z informacjami o przepisie, nie składniki: „Porcje: 4”, „4 porcje”, „2 – 3 porcje”, „Czas: 30 min”. */
    private val META_LINE = Regex(
        """^(porcj|czas|kcal|liczba porcji|\d+(\s*[-–]\s*\d+)?\s*(porcj\w*|osób|osoby|os\.)\s*$)""",
        RegexOption.IGNORE_CASE,
    )
    private val SIGN_OFF = Regex("""\s*\b(smacznego|enjoy|bon app[ée]tit)\b[!.\s]*$""", RegexOption.IGNORE_CASE)

    /** Długość, powyżej której linia bez ilości to już opis przygotowania, a nie składnik. */
    private const val PROSE_LENGTH = 70

    fun parse(text: String): ParsedRecipeText {
        val lines = joinHyphenated(text.lines().map { cleanLine(it) })
            .filter { it.isNotEmpty() && !HASHTAGS_ONLY.matches(it) }
        if (lines.isEmpty()) return ParsedRecipeText("", null, emptyList(), emptyList())

        val servings = SERVINGS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it > 0 }
        val hasHeaders = lines.any { INGREDIENT_HEADERS.matches(it) || STEP_HEADERS.matches(it) }

        val nameIndex = lines.indexOfFirst { !isHeader(it) && !META_LINE.containsMatchIn(it) }
        val name = if (nameIndex >= 0 && looksLikeTitle(lines[nameIndex])) lines[nameIndex] else ""

        val ingredients = mutableListOf<ParsedIngredientLine>()
        val steps = StepCollector()
        var mode = Mode.NONE
        var group: String? = null

        var lastWasIngredient = false
        lines.forEachIndexed { index, line ->
            if (index == nameIndex && name.isNotEmpty()) return@forEachIndexed
            val countBefore = ingredients.size

            when {
                INGREDIENT_HEADERS.matches(line) -> { mode = Mode.INGREDIENTS; group = null; return@forEachIndexed }
                STEP_HEADERS.matches(line) -> { mode = Mode.STEPS; return@forEachIndexed }
                META_LINE.containsMatchIn(line) -> return@forEachIndexed
            }
            when (mode) {
                Mode.INGREDIENTS -> {
                    val stepMatch = STEP_NUMBER.matchEntire(line)
                    when {
                        // „Sos:” — nagłówek grupy składników
                        line.endsWith(":") && line.length <= 40 && IngredientParser.parse(line).quantity == null ->
                            group = line.removeSuffix(":").trim()
                        // numerowane długie zdanie = kroki bez nagłówka
                        stepMatch != null && stepMatch.groupValues[2].length > 40 -> {
                            mode = Mode.STEPS; steps.add(stepMatch.groupValues[2], numbered = true)
                        }
                        // długi akapit bez ilości po liście składników = opis przygotowania
                        isProse(line) -> { mode = Mode.STEPS; steps.add(line, numbered = false) }
                        else -> ingredients += ParsedIngredientLine(line.replace(BULLET, "").trim(), group)
                    }
                }
                Mode.STEPS -> {
                    val m = STEP_NUMBER.matchEntire(line)
                    if (m != null) steps.add(m.groupValues[2], numbered = true) else steps.add(line, numbered = false)
                }
                Mode.NONE -> if (!hasHeaders) {
                    // Bez nagłówków: numerowane linie to kroki, linie z ilością albo punktorem to składniki.
                    // Krótka linia bez ilości („Mała dymka”) w środku listy składników też jest składnikiem —
                    // ilość zostaje pusta („brak danych”), niczego nie dopisujemy.
                    val m = STEP_NUMBER.matchEntire(line)
                    when {
                        m != null && !looksLikeIngredient(m.groupValues[2]) -> steps.add(m.groupValues[2], numbered = true)
                        BULLET.containsMatchIn(line) || looksLikeIngredient(line) ->
                            ingredients += ParsedIngredientLine(line.replace(BULLET, "").trim())
                        steps.isNotEmpty() || line.length > 60 -> steps.add(line, numbered = false)
                        isBareIngredient(line) && (lastWasIngredient || lines.getOrNull(index + 1)?.let(::looksLikeIngredient) == true) ->
                            ingredients += ParsedIngredientLine(line)
                    }
                }
                // z nagłówkami: tekst przed listą składników to wstęp — pomijamy
            }
            lastWasIngredient = ingredients.size > countBefore
        }
        return ParsedRecipeText(name, servings, ingredients, steps.result())
    }

    /**
     * Składa kroki z linii. Linia nienumerowana dokleja się do poprzedniego kroku, jeśli ten nie skończył
     * się kropką/wykrzyknikiem/pytajnikiem albo linia zaczyna się małą literą (łamanie wiersza w OCR).
     */
    private class StepCollector {
        private val steps = mutableListOf<String>()
        fun isNotEmpty() = steps.isNotEmpty()

        fun add(raw: String, numbered: Boolean) {
            val line = raw.trim()
            if (line.isEmpty()) return
            val last = steps.lastOrNull()
            val continues = !numbered && last != null &&
                (!last.trimEnd().endsWithAny('.', '!', '?') || line.first().isLowerCase())
            if (continues) steps[steps.lastIndex] = "$last $line" else steps += line
        }

        fun result(): List<String> = steps
            .map { it.replace(SIGN_OFF, "").trim() }
            .filter { it.isNotEmpty() }
    }

    private enum class Mode { NONE, INGREDIENTS, STEPS }

    private fun isHeader(line: String) = INGREDIENT_HEADERS.matches(line) || STEP_HEADERS.matches(line)

    private fun isProse(line: String): Boolean {
        if (line.length <= PROSE_LENGTH) return false
        val p = IngredientParser.parse(line)
        return p.quantity == null && !p.isRange
    }

    /** Tytuł: krótka linia bez ilości, nie zakończona kropką (zdania wstępu kończą się kropką). */
    private fun looksLikeTitle(line: String): Boolean =
        line.length <= 80 &&
            line.split(' ').size <= 12 &&
            IngredientParser.parse(line).quantity == null &&
            !line.endsWith(":") && !line.endsWith(".") && !line.endsWith("!") && !line.endsWith("?")

    /** Krótka nazwa bez ilości i bez kropki na końcu, np. „Mała dymka”, „Sól”. */
    private fun isBareIngredient(line: String): Boolean =
        line.length <= 40 && line.split(' ').size <= 5 && line.first().isLetter() &&
            !line.endsWithAny('.', '!', '?', ':') && IngredientParser.parse(line).quantity == null

    private fun String.endsWithAny(vararg chars: Char) = isNotEmpty() && last() in chars

    private fun looksLikeIngredient(line: String): Boolean {
        val p = IngredientParser.parse(line)
        return p.quantity != null && line.length <= 60 || p.isRange
    }

    /** OCR dzieli wyrazy na końcu linii („odrobi-” + „nę”) — sklejamy je. */
    private fun joinHyphenated(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (line in lines) {
            val prev = out.lastOrNull()
            if (prev != null && prev.length > 2 && prev.endsWith("-") && prev[prev.length - 2].isLetter() &&
                line.isNotEmpty() && line.first().isLowerCase()
            ) {
                out[out.lastIndex] = prev.dropLast(1) + line
            } else {
                out += line
            }
        }
        return out
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
