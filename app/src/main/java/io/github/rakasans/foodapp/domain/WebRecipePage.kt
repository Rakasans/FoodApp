package io.github.rakasans.foodapp.domain

/** Przepis odczytany z danych strukturalnych strony (schema.org/Recipe). Puste = strona nie podała. */
data class WebRecipe(
    val name: String,
    val servings: Int?,
    /** Wydajność, gdy nie jest liczbą porcji (np. „1 blacha”). */
    val yieldText: String?,
    val ingredients: List<String>,
    val steps: List<String>,
    val imageUrl: String?,
    val author: String?,
    val prepTime: String?,
    val cookTime: String?,
    /** Wartości odżywcze dokładnie tak, jak podała strona (etykieta → wartość). */
    val nutrition: List<Pair<String, String>>,
    /** Podstawa wartości odżywczych; schema.org podaje je na porcję. */
    val nutritionBasis: String?,
    /** Grupy składników (równolegle do [ingredients]); puste, gdy strona ich nie podała. */
    val groups: List<String?> = emptyList(),
    /** Fragment tekstu strony, gdy przepis odczytano z treści (brak danych strukturalnych). */
    val pageText: String? = null,
) {
    /** Oryginał do zachowania przy przepisie: to, co strona podała, bez zmian w treści. */
    fun originalText(url: String): String = pageText?.let { "Tekst ze strony (bez danych strukturalnych):\n\n$it\n\nŹródło: $url" } ?: buildString {
        if (name.isNotBlank()) appendLine(name).appendLine()
        (servings?.let { "Porcje: $it" } ?: yieldText?.let { "Wydajność: $it" })?.let { appendLine(it) }
        prepTime?.let { appendLine("Przygotowanie: $it") }
        cookTime?.let { appendLine("Gotowanie: $it") }
        if (ingredients.isNotEmpty()) {
            appendLine().appendLine("Składniki:")
            ingredients.forEach { appendLine("- $it") }
        }
        if (steps.isNotEmpty()) {
            appendLine().appendLine("Sposób przygotowania:")
            steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        }
        if (nutrition.isNotEmpty()) {
            appendLine().appendLine("Wartości odżywcze (${nutritionBasis ?: "1 porcja"}):")
            nutrition.forEach { (k, v) -> appendLine("$k: $v") }
        }
        appendLine().append("Źródło: $url")
    }.trim()
}

/**
 * Import ze stron (decyzja 3): najpierw dane strukturalne schema.org/Recipe w blokach JSON-LD —
 * tak publikuje przepisy większość serwisów kulinarnych i blogów (WordPress z wtyczkami przepisów).
 * Nie zgadujemy niczego, czego strona nie podała. Brak danych strukturalnych → null.
 */
object WebRecipePage {

    private val LD_JSON = Regex(
        """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Najpierw dane strukturalne, potem treść strony. */
    fun parse(html: String): WebRecipe? = parseStructured(html) ?: parseText(html)

    /** Plan B: przepis z widocznej treści strony (nagłówek „Składniki” albo lista z ilościami). */
    fun parseText(html: String): WebRecipe? {
        val e = HtmlRecipeText.extract(html) ?: return null
        val p = RecipeTextParser.parse(e.text)
        if (p.ingredients.size < 2) return null
        return WebRecipe(
            name = e.title.ifBlank { p.name },
            servings = p.servings,
            yieldText = null,
            ingredients = p.ingredients.map { it.raw },
            steps = p.steps,
            imageUrl = meta(html, "og:image"),
            author = null,
            prepTime = null,
            cookTime = null,
            nutrition = emptyList(),
            nutritionBasis = null,
            groups = p.ingredients.map { it.group },
            pageText = e.text,
        )
    }

    fun parseStructured(html: String): WebRecipe? {
        val node = LD_JSON.findAll(html)
            .mapNotNull { MiniJson.parseOrNull(it.groupValues[1].trim()) }
            .flatMap { findRecipes(it) }
            .sortedByDescending { (it["recipeIngredient"] ?: it["ingredients"]) != null }
            .firstOrNull() ?: return null

        val name = text(node["name"]).orEmpty()
        val ingredients = strings(node["recipeIngredient"] ?: node["ingredients"]).map(::clean).filter { it.isNotEmpty() }
        val steps = instructions(node["recipeInstructions"])
        if (name.isBlank() && ingredients.isEmpty() && steps.isEmpty()) return null

        val (servings, yieldText) = yieldOf(node["recipeYield"])
        val (nutrition, basis) = nutritionOf(node["nutrition"])
        return WebRecipe(
            name = name,
            servings = servings,
            yieldText = yieldText,
            ingredients = ingredients,
            steps = steps,
            imageUrl = imageOf(node["image"]) ?: meta(html, "og:image"),
            author = authorOf(node["author"]),
            prepTime = text(node["prepTime"])?.let(::duration),
            cookTime = text(node["cookTime"])?.let(::duration) ?: text(node["totalTime"])?.let(::duration)?.let { "$it (łącznie)" },
            nutrition = nutrition,
            nutritionBasis = basis,
        )
    }

    // ---------- wyszukiwanie węzła Recipe (także w @graph i tablicach)

    private fun findRecipes(v: Any?): List<Map<String, Any?>> = when (v) {
        is List<*> -> v.flatMap { findRecipes(it) }
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val m = v as Map<String, Any?>
            val self = if (isRecipe(m["@type"])) listOf(m) else emptyList()
            self + m.filterKeys { it == "@graph" || it == "mainEntity" || it == "mainEntityOfPage" }.values.flatMap { findRecipes(it) }
        }
        else -> emptyList()
    }

    private fun isRecipe(type: Any?): Boolean = when (type) {
        is String -> type.substringAfterLast('/').equals("Recipe", ignoreCase = true)
        is List<*> -> type.any { isRecipe(it) }
        else -> false
    }

    // ---------- pola

    private fun instructions(v: Any?): List<String> = when (v) {
        null -> emptyList()
        is String -> splitInstructionText(v)
        is List<*> -> v.flatMap { instructions(it) }
        is Map<*, *> -> {
            val items = v["itemListElement"]
            if (items != null) instructions(items)
            else (text(v["text"]) ?: text(v["name"]))?.let { splitInstructionText(it) } ?: emptyList()
        }
        else -> emptyList()
    }

    /** Tekst kroków bywa jednym blokiem HTML (<p>, <li>, <br>) albo z nowymi liniami. */
    private fun splitInstructionText(raw: String): List<String> {
        val withBreaks = raw.replace(Regex("""</(p|li|div|h\d)>|<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        return InstagramPage.decodeEntities(withBreaks.replace(Regex("<[^>]+>"), ""))
            .lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
    }

    private fun yieldOf(v: Any?): Pair<Int?, String?> {
        val all = strings(v).map(::clean).filter { it.isNotEmpty() }
        if (all.isEmpty()) return null to null
        all.forEach { s ->
            SERVINGS.matchEntire(s)?.let { m -> m.groupValues[1].toIntOrNull()?.takeIf { it > 0 }?.let { return it to null } }
        }
        return null to all.first()
    }

    private val SERVINGS = Regex(
        """^(\d{1,3})(?:[.,]0)?\s*(?:porcj\w*|osób|osoby|os\.|serving\w*|portion\w*|people|persons?)?\.?$""",
        RegexOption.IGNORE_CASE,
    )

    private val NUTRITION_LABELS = linkedMapOf(
        "calories" to "Energia",
        "fatContent" to "Tłuszcz",
        "saturatedFatContent" to "w tym kwasy nasycone",
        "carbohydrateContent" to "Węglowodany",
        "sugarContent" to "w tym cukry",
        "fiberContent" to "Błonnik",
        "proteinContent" to "Białko",
        "sodiumContent" to "Sód",
        "cholesterolContent" to "Cholesterol",
    )

    private fun nutritionOf(v: Any?): Pair<List<Pair<String, String>>, String?> {
        val m = v as? Map<*, *> ?: return emptyList<Pair<String, String>>() to null
        val values = NUTRITION_LABELS.mapNotNull { (key, label) ->
            text(m[key])?.takeIf { it.isNotBlank() }?.let { label to it }
        }
        if (values.isEmpty()) return values to null
        val size = text(m["servingSize"])?.takeIf { it.isNotBlank() }
        return values to (size?.let { "1 porcja ($it)" } ?: "1 porcja")
    }

    private fun imageOf(v: Any?): String? = when (v) {
        is String -> v.takeIf { it.startsWith("http") }
        is List<*> -> v.firstNotNullOfOrNull { imageOf(it) }
        is Map<*, *> -> imageOf(v["url"]) ?: imageOf(v["contentUrl"])
        else -> null
    }

    private fun authorOf(v: Any?): String? = when (v) {
        is String -> clean(v).ifBlank { null }
        is List<*> -> v.firstNotNullOfOrNull { authorOf(it) }
        is Map<*, *> -> text(v["name"])?.ifBlank { null }
        else -> null
    }

    /** ISO 8601 („PT1H30M”) → „1 godz. 30 min”; nieznany format zostaje bez zmian. */
    fun duration(raw: String): String? {
        val m = Regex("""^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:\d+S)?)?$""", RegexOption.IGNORE_CASE).matchEntire(raw.trim())
            ?: return raw.trim().ifBlank { null }
        val days = m.groupValues[1].toIntOrNull() ?: 0
        val hours = (m.groupValues[2].toIntOrNull() ?: 0) + days * 24
        var minutes = m.groupValues[3].toIntOrNull() ?: 0
        var h = hours
        if (minutes >= 60) { h += minutes / 60; minutes %= 60 }
        return when {
            h > 0 && minutes > 0 -> "$h godz. $minutes min"
            h > 0 -> "$h godz."
            minutes > 0 -> "$minutes min"
            else -> null // „PT0M” — strona nie podała czasu
        }
    }

    // ---------- narzędzia

    private fun text(v: Any?): String? = when (v) {
        is String -> clean(v)
        is Double -> formatQuantity(v)
        is List<*> -> v.firstNotNullOfOrNull { text(it) }
        is Map<*, *> -> text(v["name"]) ?: text(v["text"])
        else -> null
    }

    private fun strings(v: Any?): List<String> = when (v) {
        is String -> listOf(v)
        is Double -> listOf(formatQuantity(v))
        is List<*> -> v.flatMap { strings(it) }
        else -> emptyList()
    }

    private fun clean(s: String): String =
        InstagramPage.decodeEntities(s.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim()

    private fun meta(html: String, property: String): String? =
        Regex("""<meta[^>]+(?:property|name)=["']${Regex.escape(property)}["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(InstagramPage::decodeEntities)
            ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["']${Regex.escape(property)}["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.let(InstagramPage::decodeEntities)
}
