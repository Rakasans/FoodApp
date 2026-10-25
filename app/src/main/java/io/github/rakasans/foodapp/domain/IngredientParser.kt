package io.github.rakasans.foodapp.domain

/**
 * Wynik rozpoznania jednej linii składnika. Oryginalny zapis zawsze zostaje przy składniku —
 * parser tylko proponuje nazwę, ilość i jednostkę.
 */
data class ParsedIngredient(
    val name: String,
    /** null = ilość nieznana. Przy zakresie („300–350 g”) to dolna granica. */
    val quantity: Double?,
    val unit: String?,
    /** Jednostka przyjęta automatycznie („2 jajka” → szt.) — do sprawdzenia przez użytkownika. */
    val unitInferred: Boolean = false,
    /** Źródło podaje zakres — zapisujemy oba końce, nie wybieramy za użytkownika jednej wartości. */
    val isRange: Boolean = false,
    /** Górna granica zakresu („300–350 g” → 350); null, gdy to nie zakres. */
    val quantityMax: Double? = null,
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
        add("pałka", "pałka", "pałki", "pałek", "pałkę")
        add("laska", "laska", "laski", "lasek", "laskę")
        add("gałązka", "gałązka", "gałązki", "gałązek", "gałązkę")
        add("ziarenko", "ziarenko", "ziarenka", "ziarenek", "ziarno", "ziarna", "ziaren")
        add("gwiazdka", "gwiazdka", "gwiazdki", "gwiazdek", "gwiazdkę")
    }

    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)
    private val WORD_NUMBERS = mapOf("pół" to 0.5, "półtorej" to 1.5, "półtora" to 1.5)

    private const val NUM = """\d+(?:[.,]\d+)?"""
    private val RANGE = Regex("""^($NUM)\s*[-–—]\s*($NUM)\s*(.*)$""")
    private val MIXED = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)\s*(.*)$""")
    private val FRACTION = Regex("""^(\d+)\s*/\s*(\d+)\s*(.*)$""")
    private val DECIMAL = Regex("""^($NUM)\s*(.*)$""")
    /** „1 ¼ szklanki” — liczba całkowita i ułamek jako znak. */
    private val MIXED_UNICODE = Regex("""^(\d+)\s*([½¼¾⅓⅔])\s*(.*)$""")
    /**
     * Ilość na końcu: „Sezam - 10g”, „Liść laurowy 3”, „Cynamon 1 pałka”, „karczek wołowy 1 kg”,
     * „cebule 300 g - 2 sztuki” (dopisek po myślniku/przecinku/nawiasie zostaje tylko w zapisie oryginalnym).
     */
    private val TRAILING = Regex("""^(\p{L}[\p{L}\s.,]*?)\s*[-–—:]?\s*($NUM)\s*(\p{L}+\.?)?(\s*[-–—,(].*)?$""")
    /** Przymiotniki przed jednostką: „1 płaska łyżeczka”, „2 czubate łyżki”. */
    private val UNIT_ADJECTIVES = setOf(
        "płaska", "płaskie", "płaskich", "czubata", "czubate", "czubatych", "kopiasta", "kopiaste",
        "pełna", "pełne", "pełnych", "duża", "duże", "dużych", "mała", "małe", "małych", "średnia", "średnie", "średnich",
    )
    /** Liczba po tych słowach to oznaczenie, nie ilość („mąka typ 650”, „makaron nr 5”). */
    private val NOT_QUANTITY_BEFORE = setOf("typ", "typu", "nr", "numer", "rozmiar", "rozm")

    /** „około 300 ml”, „ok. 2 łyżki”, „~200 g” — przybliżenie z oryginału zostaje w rawText. */
    private val APPROX = Regex("""^(około|okolo|ok\.|ok(?=\s)|ca\.|circa|mniej więcej|~)\s*""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedIngredient {
        val text = raw.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim().replace(APPROX, "")
        if (text.isEmpty()) return ParsedIngredient(name = "", quantity = null, unit = null)

        RANGE.matchEntire(text)?.let { m ->
            val (unit, rest) = splitUnit(m.groupValues[3])
            val min = m.groupValues[1].replace(',', '.').toDouble()
            val max = m.groupValues[2].replace(',', '.').toDouble()
            return if (unit != null) ParsedIngredient(rest.ifBlank { text }, quantity = min, unit = unit, isRange = true, quantityMax = max)
            else ParsedIngredient(rest.ifBlank { text }, quantity = min, unit = "szt.", unitInferred = true, isRange = true, quantityMax = max)
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
        MIXED_UNICODE.matchEntire(text)?.let { m ->
            return withQuantity(m.groupValues[1].toDouble() + FRACTIONS.getValue(m.groupValues[2][0]), m.groupValues[3], text)
        }
        FRACTIONS[text.first()]?.let { q -> return withQuantity(q, text.drop(1), text) }
        DECIMAL.matchEntire(text)?.let { m ->
            return withQuantity(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2], text)
        }
        val firstWord = text.substringBefore(' ').lowercase()
        WORD_NUMBERS[firstWord]?.let { q -> return withQuantity(q, text.substringAfter(' ', ""), text) }
        trailing(text)?.let { return it }

        return ParsedIngredient(name = text, quantity = null, unit = null)
    }

    private fun trailing(text: String): ParsedIngredient? {
        val m = TRAILING.matchEntire(text) ?: return null
        val name = m.groupValues[1].trim().trimEnd(',', '.').trim()
        if (name.isEmpty()) return null
        val words = name.split(Regex("\\s+"))
        if (words.size > 4 || words.last().lowercase() in NOT_QUANTITY_BEFORE) return null
        val q = m.groupValues[2].replace(',', '.').toDouble()
        val unitToken = m.groupValues[3]
        if (unitToken.isEmpty()) return if (m.groupValues[4].isBlank()) ParsedIngredient(name, q, "szt.", unitInferred = true) else null
        val unit = UNITS[unitToken.lowercase().trimEnd('.')] ?: return null
        return ParsedIngredient(name, q, unit)
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
        if (unit != null) return unit to trimmed.substringAfter(' ', "").trim()
        if (token.lowercase() in UNIT_ADJECTIVES) {
            val afterAdj = trimmed.substringAfter(' ', "").trim()
            val next = afterAdj.substringBefore(' ')
            UNITS[next.lowercase().trimEnd('.')]?.let { return it to afterAdj.substringAfter(' ', "").trim() }
        }
        return null to trimmed
    }
}
