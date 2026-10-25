package io.github.rakasans.foodapp.domain

/** Wkład jednego przepisu (albo ręcznego wpisu) w listę zakupów. */
data class ShoppingPart(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    /** Górna granica zakresu — taki wkład nie jest sumowany z innymi, pokazujemy go jako „300–350 g”. */
    val quantityMax: Double? = null,
    val noQuantity: Boolean = false,
    val manual: Boolean = false,
    val recipeName: String? = null,
    val rawText: String? = null,
    val factorLabel: String? = null,
)

data class ShoppingLine(
    val key: String,
    val name: String,
    /** Np. „250 ml + 2 łyżka + ilość nieznana”. Pusty dla pozycji ręcznej bez ilości. */
    val quantityText: String,
    val differentUnits: Boolean,
    val hasUnknownQuantity: Boolean,
    val recipeNames: List<String>,
    val parts: List<ShoppingPart>,
)

/**
 * Reguły listy zakupów (specyfikacja, sekcja 6):
 * 1. łączymy tylko identyczne znormalizowane nazwy,
 * 2. sumujemy tylko tę samą jednostkę; dokładne konwersje g↔kg i ml↔l,
 * 3. różne jednostki → osobne segmenty (bez sumowania),
 * 4. brak ilości → segment „ilość nieznana”, „bez ilości” → „do smaku”.
 */
object ShoppingMerge {

    fun merge(parts: List<ShoppingPart>): List<ShoppingLine> {
        val groups = LinkedHashMap<String, MutableList<ShoppingPart>>()
        parts.forEach { groups.getOrPut(normalizeName(it.name)) { mutableListOf() }.add(it) }
        return groups.map { (key, group) -> toLine(key, group) }
    }

    private fun toLine(key: String, group: List<ShoppingPart>): ShoppingLine {
        val sums = LinkedHashMap<String, Double>()
        var unknown = 0
        var toTaste = 0
        val ranges = mutableListOf<String>()
        group.filterNot { it.manual }.forEach { p ->
            val q = p.quantity
            if (q == null) {
                if (p.noQuantity) toTaste++ else unknown++
                return@forEach
            }
            val qMax = p.quantityMax
            if (qMax != null) {
                val unit = p.unit.orEmpty().trim()
                ranges += "${formatQuantity(q)}–${formatQuantity(qMax)}" + if (unit.isEmpty()) "" else " $unit"
                return@forEach
            }
            val (unit, value) = baseUnit(p.unit.orEmpty().trim(), q)
            sums[unit] = (sums[unit] ?: 0.0) + value
        }
        val segments = sums.map { (unit, value) -> displaySegment(unit, value) }.toMutableList()
        segments += ranges
        if (unknown > 0) segments += if (unknown > 1) "ilość nieznana (×$unknown)" else "ilość nieznana"
        if (toTaste > 0) segments += "do smaku"

        return ShoppingLine(
            key = key,
            name = group.first().name.trim(),
            quantityText = segments.joinToString(" + "),
            differentUnits = sums.size > 1,
            hasUnknownQuantity = unknown > 0,
            recipeNames = group.map { if (it.manual) "dodane ręcznie" else it.recipeName.orEmpty() }.distinct(),
            parts = group,
        )
    }

    /** Dokładne konwersje do jednostki bazowej; reszta bez zmian (łyżka, szt., puszka…). */
    private fun baseUnit(unit: String, value: Double): Pair<String, Double> = when (unit.lowercase()) {
        "kg" -> "g" to value * 1000
        "l" -> "ml" to value * 1000
        else -> unit to value
    }

    private fun displaySegment(unit: String, value: Double): String = when {
        unit == "g" && value >= 1000 -> "${formatQuantity(value / 1000)} kg"
        unit == "ml" && value >= 1000 -> "${formatQuantity(value / 1000)} l"
        unit.isEmpty() -> formatQuantity(value)
        else -> "${formatQuantity(value)} $unit"
    }
}

/** Składnik przepisu w postaci potrzebnej do listy zakupów. */
data class IngredientForShopping(
    val name: String,
    val rawText: String,
    val quantity: Double?,
    val quantityMax: Double?,
    val unit: String?,
    val noQuantity: Boolean,
)

/**
 * Tworzy wkłady do listy zakupów z przepisu i przeliczenia (specyfikacja, sekcja 6, pkt 5–6):
 * - przeliczamy tylko znane ilości (także oba końce zakresu); nieznana zostaje nieznana,
 * - przepis się nie zmienia — przeliczenie dotyczy wyłącznie listy,
 * - sztuk nie zamieniamy na gramy ani łyżek na mililitry.
 */
object ShoppingBuilder {

    /**
     * Grupy, które są propozycją, a nie częścią dania („Dodatki opcjonalne”, „Do podania”, „Do dekoracji”).
     * Na liście zakupów są domyślnie pominięte — użytkownik może je włączyć.
     */
    fun isOptionalGroup(group: String?): Boolean =
        group != null && OPTIONAL.containsMatchIn(group)

    private val OPTIONAL = Regex("""opcjonaln|do podania|do dekoracji|do serwowania|^dodatki$|propozycj""", RegexOption.IGNORE_CASE)

    /** Mnożniki całego przepisu, gdy liczba porcji jest nieznana — bez pozornej precyzji. */
    val MULTIPLIERS = listOf(0.5, 1.0, 2.0, 3.0)

    fun parts(recipeName: String, items: List<IngredientForShopping>, scaling: Scaling): List<ShoppingPart> {
        val f = scaling.factor
        val label = if (f == 1.0) null else scaling.label
        return items.filter { it.name.isNotBlank() || it.rawText.isNotBlank() }.map { i ->
            ShoppingPart(
                name = i.name.ifBlank { i.rawText }.trim(),
                quantity = if (i.noQuantity) null else i.quantity?.times(f),
                unit = i.unit,
                quantityMax = if (i.noQuantity) null else i.quantityMax?.times(f),
                noQuantity = i.noQuantity,
                recipeName = recipeName,
                rawText = i.rawText,
                factorLabel = label,
            )
        }
    }
}
