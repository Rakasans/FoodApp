package io.github.rakasans.foodapp.domain

/**
 * Wynik rozpoznania jednej linii składnika. Oryginalny zapis zawsze zostaje przy składniku —
 * parser tylko proponuje nazwę, ilość i jednostkę.
 */
data class ParsedIngredient(
    val name: String,
    /** null = ilość nieznana (brak liczby albo zakres, np. „2–3 łyżki”). */
    val quantity: Double?,
    val unit: String?,
    /** Jednostka przyjęta automatycznie („2 jajka” → szt.) — do sprawdzenia przez użytkownika. */
    val unitInferred: Boolean = false,
    /** Źródło podaje zakres — nie wybieramy za użytkownika jednej wartości. */
    val isRange: Boolean = false,
)

/**
 * Parser regułowy (bez AI): „2 łyżki oliwy”, „1,5 kg dyni”, „1/2 szklanki mleka”, „½ cebuli”,
 * „pół kostki masła”, „2–3 ząbki czosnku”, „sól do smaku”.
 * Zasada wiarygodności: niczego nie zgadujemy — przy wątpliwości ilość = null.
 */
object IngredientParser {

    private val UNITS: Map<String, String> = buildMap {
        fun add(canonical: String, vararg forms: String) { forms.forEach { form -> this[form] = canonical } }
        add("g", "g", "gr", "gram", "gramy", "gramów")
        add("dag", "dag", "dkg")
        add("kg", "kg", "kilogram", "kilogramy", "kilogramów")
        add("ml", "ml", "mililitr", "mililitry", "mililitrów")
        add("l", "l", "litr", "litry", "litrów")
        add("łyżka", "łyżka", "łyżki", "łyżek", "łyżkę", "łyż")
        add("łyżeczka", "łyżeczka", "łyżeczki", "łyżeczek", "łyżeczkę", "łyżecz")
        add("szklanka", "szklanka", "szklanki", "szklanek", "szklankę")
        add("szt.", "szt", "sztuka", "sztuki", "sztuk", "sztukę")
        add("ząbek", "ząbek", "ząbki", "ząbków")
        add("puszka", "puszka", "puszki", "puszek", "puszkę")
        add("opak.", "op", "opak", "opakowanie", "opakowania", "opakowań")
        add("szczypta", "szczypta", "szczypty", "szczyptę")
        add("garść", "garść", "garści")
        add("plaster", "plaster", "plastry", "plastrów")
        add("pęczek", "pęczek", "pęczki", "pęczków")
        add("kostka", "kostka", "kostki", "kostek", "kostkę")
    }

    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)
    private val WORD_NUMBERS = mapOf("pół" to 0.5, "półtorej" to 1.5, "półtora" to 1.5)

    private const val NUM = """\d+(?:[.,]\d+)?"""
    private val RANGE = Regex("""^($NUM)\s*[-–—]\s*($NUM)\s*(.*)$""")
    private val MIXED = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)\s*(.*)$""")
    private val FRACTION = Regex("""^(\d+)\s*/\s*(\d+)\s*(.*)$""")
    private val DECIMAL = Regex("""^($NUM)\s*(.*)$""")

    /** „około 300 ml”, „ok. 2 łyżki”, „~200 g” — przybliżenie z oryginału zostaje w rawText. */
    private val APPROX = Regex("""^(około|okolo|ok\.|ok(?=\s)|ca\.|circa|mniej więcej|~)\s*""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedIngredient {
        val text = raw.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim().replace(APPROX, "")
        if (text.isEmpty()) return ParsedIngredient(name = "", quantity = null, unit = null)

        RANGE.matchEntire(text)?.let { m ->
            val (unit, rest) = splitUnit(m.groupValues[3])
            return ParsedIngredient(rest.ifBlank { text }, quantity = null, unit = unit, isRange = true)
        }
        MIXED.matchEntire(text)?.let { m ->
            val denominator = m.groupValues[3].toDouble()
            if (denominator > 0) {
                val q = m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / denominator
                return withQuantity(q, m.groupValues[4], text)
            }
        }
        FRACTION.matchEntire(text)?.let { m ->
            val denominator = m.groupValues[2].toDouble()
            if (denominator > 0) return withQuantity(m.groupValues[1].toDouble() / denominator, m.groupValues[3], text)
        }
        FRACTIONS[text.first()]?.let { q -> return withQuantity(q, text.drop(1), text) }
        DECIMAL.matchEntire(text)?.let { m ->
            return withQuantity(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2], text)
        }
        val firstWord = text.substringBefore(' ').lowercase()
        WORD_NUMBERS[firstWord]?.let { q -> return withQuantity(q, text.substringAfter(' ', ""), text) }

        return ParsedIngredient(name = text, quantity = null, unit = null)
    }

    private fun withQuantity(q: Double, remainder: String, original: String): ParsedIngredient {
        val (unit, rest) = splitUnit(remainder)
        val name = rest.ifBlank { original }
        return if (unit != null) ParsedIngredient(name, q, unit)
        else ParsedIngredient(name, q, "szt.", unitInferred = true)
    }

    /** Odcina jednostkę z początku tekstu, jeśli jest znana. */
    private fun splitUnit(text: String): Pair<String?, String> {
        val trimmed = text.trim()
        val token = trimmed.substringBefore(' ')
        val unit = UNITS[token.lowercase().trimEnd('.')]
        return if (unit != null) unit to trimmed.substringAfter(' ', "").trim() else null to trimmed
    }
}
