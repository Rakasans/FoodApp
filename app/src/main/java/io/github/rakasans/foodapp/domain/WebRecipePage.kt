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
    /** Rozpoznanie składnika z osobnych pól strony (nazwa / ilość); null = rozpoznaj z zapisu. */
    val hints: List<ParsedIngredient?> = emptyList(),
    /** Porady autora przy krokach — nie są krokami; zostają tylko w oryginale. */
    val tips: List<String> = emptyList(),
) {
    /** Oryginał do zachowania przy przepisie: to, co strona podała, bez zmian w treści. */
    fun originalText(url: String): String = pageText?.let { "Tekst ze strony (bez danych strukturalnych):\n\n$it\n\nŹródło: $url" } ?: buildString {
        if (name.isNotBlank()) appendLine(name).appendLine()
        (servings?.let { "Porcje: $it" } ?: yieldText?.let { "Wydajność: $it" })?.let { appendLine(it) }
        prepTime?.let { appendLine("Przygotowanie: $it") }
        cookTime?.let { appendLine("Gotowanie: $it") }
        if (ingredients.isNotEmpty()) {
            appendLine().appendLine("Składniki:")
            var lastGroup: String? = null
            ingredients.forEachIndexed { i, it ->
                val g = groups.getOrNull(i)
                if (g != null && g != lastGroup) appendLine("$g:")
                lastGroup = g
                appendLine("- $it")
            }
        }
        if (steps.isNotEmpty()) {
            appendLine().appendLine("Sposób przygotowania:")
            steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        }
        if (nutrition.isNotEmpty()) {
            appendLine().appendLine("Wartości odżywcze (${nutritionBasis ?: "1 porcja"}):")
            nutrition.forEach { (k, v) -> appendLine("$k: $v") }
        }
        if (tips.isNotEmpty()) {
            appendLine().appendLine("Porady ze strony:")
            tips.forEach { appendLine("- $it") }
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
    fun parse(html: String): WebRecipe? = parseStructured(html) ?: parseMicrodata(html) ?: parseText(html)

    // ---------- mikrodane (itemprop), np. AniaGotuje

    fun parseMicrodata(html: String): WebRecipe? {
        if (!html.contains("schema.org/Recipe", ignoreCase = true)) return null
        val root = MiniHtml.parse(html)
        val recipe = root.descendants().firstOrNull {
            it.has("itemscope") && it.attr("itemtype")?.substringAfterLast('/')?.equals("Recipe", ignoreCase = true) == true
        } ?: return null
        val p = props(recipe)

        val ingredientNodes = p["recipeIngredient"].orEmpty() + p["ingredients"].orEmpty()
        val ingredients = ingredientNodes.map { it.text().replace('\n', ' ').trim() }
        val groups = ingredientNodes.map { groupOf(it, recipe) }
        val hints = ingredientNodes.map(::hintOf)

        val tips = mutableListOf<String>()
        val steps = p["recipeInstructions"].orEmpty().flatMap { microSteps(it, tips) }
        val name = p["name"]?.firstOrNull()?.let(::value).orEmpty()
        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val (servings, yieldText) = yieldOf(p["recipeYield"]?.map(::value))
        val nutritionNode = p["nutrition"]?.firstOrNull()
        val (nutrition, basis) = nutritionNode?.let(::microNutrition) ?: (emptyList<Pair<String, String>>() to null)
        val author = p["author"]?.firstOrNull()?.let { a ->
            if (a.has("itemscope")) props(a)["name"]?.firstOrNull()?.let(::value) else value(a)
        }?.ifBlank { null }
        return WebRecipe(
            name = name,
            servings = servings,
            yieldText = yieldText,
            ingredients = ingredients,
            steps = steps,
            imageUrl = p["image"]?.map(::value)?.firstOrNull { it.startsWith("http") } ?: meta(html, "og:image"),
            author = author,
            prepTime = p["prepTime"]?.firstOrNull()?.let(::value)?.let(::duration),
            cookTime = p["cookTime"]?.firstOrNull()?.let(::value)?.let(::duration),
            nutrition = nutrition,
            nutritionBasis = basis,
            groups = groups,
            hints = hints,
            tips = tips,
        )
    }

    /** Właściwości należące do danego zakresu itemscope (bez zagnieżdżonych zakresów). */
    private fun props(scope: HtmlNode): Map<String, List<HtmlNode>> {
        val out = LinkedHashMap<String, MutableList<HtmlNode>>()
        fun walk(n: HtmlNode) {
            for (c in n.elements) {
                c.attr("itemprop")?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.forEach { out.getOrPut(it) { mutableListOf() } += c }
                if (!c.has("itemscope")) walk(c)
            }
        }
        walk(scope)
        return out
    }

    private fun value(n: HtmlNode): String = when (n.tag) {
        "meta" -> n.attr("content").orEmpty()
        "link", "a" -> n.attr("href") ?: n.text()
        "img" -> n.attr("src").orEmpty()
        "time" -> n.attr("datetime") ?: n.text()
        "data" -> n.attr("value") ?: n.text()
        else -> n.attr("content") ?: n.text()
    }.trim()

    private fun isTip(n: HtmlNode): Boolean =
        n.classes.any { c -> TIP_CLASS.any { c.contains(it, ignoreCase = true) } } ||
            (n.tag == "p" && TIP_TEXT.containsMatchIn(n.text().take(20)))

    private val TIP_CLASS = listOf("tip", "porad", "hint", "note", "uwag", "wskazow")
    private val TIP_TEXT = Regex("""^(porada|wskazówka|uwaga|tip)\b""", RegexOption.IGNORE_CASE)

    private fun microSteps(node: HtmlNode, tips: MutableList<String>): List<String> {
        if (!node.has("itemscope")) {
            collectTips(node, tips)
            return node.text(::isTip).lines().filter { it.isNotBlank() }
        }
        val sp = props(node)
        sp["itemListElement"]?.let { items -> return items.flatMap { microSteps(it, tips) } } // HowToSection
        val textNode = sp["text"]?.firstOrNull()
        val text = (textNode ?: node).let { collectTips(it, tips); it.text(::isTip) }
            .let { t -> if (textNode == null) t.lines().filterNot { l -> sp["name"]?.any { value(it) == l } == true }.joinToString("\n") else t }
            .trim()
        val name = sp["name"]?.firstOrNull()?.let(::value)?.trim().orEmpty()
        return when {
            text.isEmpty() && name.isEmpty() -> emptyList()
            text.isEmpty() -> listOf(name)
            name.isEmpty() || text.startsWith(name) -> listOf(text)
            else -> listOf("$name\n$text")
        }
    }

    private fun collectTips(n: HtmlNode, tips: MutableList<String>) {
        n.descendants().filter(::isTip).forEach { t -> t.text().replace('\n', ' ').trim().takeIf { it.isNotEmpty() }?.let { tips += it } }
    }

    /** Nazwa grupy składników: nagłówek najbliższego kontenera „grupy”. Sam nagłówek „Składniki” = brak grupy. */
    private fun groupOf(ing: HtmlNode, recipe: HtmlNode): String? {
        val container = ing.ancestors().takeWhile { it !== recipe }
            .firstOrNull { a -> a.classes.any { it.contains("group", ignoreCase = true) } } ?: return null
        val header = container.descendants().firstOrNull { d ->
            d.tag in setOf("h2", "h3", "h4", "h5", "strong", "b", "legend") ||
                d.classes.any { it.contains("header", true) || it.contains("title", true) }
        } ?: return null
        if (header.descendants().any { it.has("itemprop") }) return null
        val text = header.text().replace('\n', ' ').trim().trimEnd(':').trim()
        return text.takeUnless { it.isEmpty() || it.equals("Składniki", ignoreCase = true) }
    }

    /** Strona podaje nazwę i ilość w osobnych polach („pieprz mielony” + „pół płaskiej łyżeczki”). */
    private fun hintOf(ing: HtmlNode): ParsedIngredient? {
        val nameNode = ing.descendants().firstOrNull { d -> d.classes.any { it.contains("name", true) } } ?: return null
        val qtyNode = ing.descendants().firstOrNull { d ->
            d.classes.any { it.contains("qty", true) || it.contains("amount", true) || it.contains("quantity", true) }
        } ?: return null
        val name = nameNode.text().replace('\n', ' ').trim()
        val q = IngredientParser.parse(qtyNode.text().replace('\n', ' ').trim())
        if (name.isEmpty() || q.quantity == null) return null
        return q.copy(name = name)
    }

    private fun microNutrition(n: HtmlNode): Pair<List<Pair<String, String>>, String?> {
        val np = props(n)
        val values = NUTRITION_LABELS.mapNotNull { (key, label) ->
            np[key]?.firstOrNull()?.let(::value)?.takeIf { it.isNotBlank() }?.let { label to it }
        }
        if (values.isEmpty()) return values to null
        // „W 100 g zupy:” — podstawa podana na stronie ma pierwszeństwo przed domyślną porcją
        val basis = Regex("""(?:^|\s)(?:w|na)\s+(\d+\s*(?:g|ml|porcj\w*)[^:\n]{0,30}):""", RegexOption.IGNORE_CASE)
            .find(n.text())?.groupValues?.get(1)?.trim()
        return values to (basis ?: "1 porcja")
    }

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
        if (v is List<*> && v.isEmpty()) return null to null
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
