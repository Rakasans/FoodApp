package io.github.rakasans.foodapp.domain

/*
 * Kopia zapasowa (decyzja 9): plik ZIP z `recipes.json` i zdjęciami w `photos/`.
 * Format jest jawny i opisany wersją — można go otworzyć w każdym edytorze.
 * Zapisujemy wszystko, co użytkownik ma, łącznie z oryginałem materiału; nic nie przeliczamy.
 */

data class BackupIngredient(
    val rawText: String,
    val name: String,
    val quantity: Double?,
    val quantityMax: Double?,
    val unit: String?,
    val noQuantity: Boolean,
    val unitInferred: Boolean,
    val groupName: String?,
)

data class BackupRecipe(
    val id: String,
    /** Treść rekordu przepisu jako pary pole → wartość (String/Double/Boolean/null). */
    val fields: Map<String, Any?>,
    val ingredients: List<BackupIngredient>,
    val steps: List<String>,
    val nutrition: List<Pair<String, String>>,
    val categories: List<String>,
    val tags: List<String>,
    val collections: List<String>,
    /** Nazwa pliku zdjęcia w katalogu `photos/` archiwum. */
    val photoFile: String?,
)

data class BackupFile(val version: Int, val exportedAt: Long, val recipes: List<BackupRecipe>)

object Backup {
    const val VERSION = 1

    fun toJson(file: BackupFile): String = Json.write(
        mapOf(
            "app" to "FoodApp",
            "version" to file.version,
            "exportedAt" to file.exportedAt,
            "recipes" to file.recipes.map { r ->
                mapOf(
                    "id" to r.id,
                    "recipe" to r.fields,
                    "ingredients" to r.ingredients.map {
                        mapOf(
                            "rawText" to it.rawText, "name" to it.name, "quantity" to it.quantity,
                            "quantityMax" to it.quantityMax, "unit" to it.unit, "noQuantity" to it.noQuantity,
                            "unitInferred" to it.unitInferred, "groupName" to it.groupName,
                        )
                    },
                    "steps" to r.steps,
                    "nutrition" to r.nutrition.map { mapOf("label" to it.first, "value" to it.second) },
                    "categories" to r.categories,
                    "tags" to r.tags,
                    "collections" to r.collections,
                    "photoFile" to r.photoFile,
                )
            },
        )
    )

    /** Odczyt kopii. Rzuca IllegalArgumentException, gdy plik nie jest kopią FoodApp. */
    fun fromJson(text: String): BackupFile {
        val root = MiniJson.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("To nie jest kopia Przepisarni.")
        require(root["app"] == "FoodApp") { "To nie jest kopia Przepisarni." }
        val version = (root["version"] as? Double)?.toInt() ?: 0
        require(version in 1..VERSION) { "Kopia pochodzi z nowszej wersji aplikacji — zaktualizuj Przepisarnię." }
        val recipes = (root["recipes"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.map { r ->
            BackupRecipe(
                id = r["id"] as String,
                fields = (r["recipe"] as? Map<*, *>).orEmpty().entries.associate { (k, v) -> k as String to v },
                ingredients = (r["ingredients"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.map {
                    BackupIngredient(
                        rawText = it["rawText"] as? String ?: "",
                        name = it["name"] as? String ?: "",
                        quantity = it["quantity"] as? Double,
                        quantityMax = it["quantityMax"] as? Double,
                        unit = it["unit"] as? String,
                        noQuantity = it["noQuantity"] == true,
                        unitInferred = it["unitInferred"] == true,
                        groupName = it["groupName"] as? String,
                    )
                },
                steps = (r["steps"] as? List<*>).orEmpty().filterIsInstance<String>(),
                nutrition = (r["nutrition"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                    .mapNotNull { n -> (n["label"] as? String)?.let { l -> l to (n["value"] as? String ?: "") } },
                categories = (r["categories"] as? List<*>).orEmpty().filterIsInstance<String>(),
                tags = (r["tags"] as? List<*>).orEmpty().filterIsInstance<String>(),
                collections = (r["collections"] as? List<*>).orEmpty().filterIsInstance<String>(),
                photoFile = r["photoFile"] as? String,
            )
        }
        return BackupFile(version, (root["exportedAt"] as? Double)?.toLong() ?: 0L, recipes)
    }
}

/** Prosty zapis JSON (para do [MiniJson]). Liczby całkowite bez „.0”. */
object Json {
    fun write(v: Any?): String = StringBuilder().also { append(it, v, 0) }.toString()

    private fun append(sb: StringBuilder, v: Any?, indent: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> string(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null")
                else if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) sb.append(v.toLong()) else sb.append(v)
            is Float -> append(sb, v.toDouble(), indent)
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append("{\n")
                v.entries.forEachIndexed { i, (k, value) ->
                    pad(sb, indent + 1); string(sb, k.toString()); sb.append(": "); append(sb, value, indent + 1)
                    sb.append(if (i < v.size - 1) ",\n" else "\n")
                }
                pad(sb, indent); sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append("[\n")
                v.forEachIndexed { i, value ->
                    pad(sb, indent + 1); append(sb, value, indent + 1)
                    sb.append(if (i < v.size - 1) ",\n" else "\n")
                }
                pad(sb, indent); sb.append(']')
            }
            else -> string(sb, v.toString())
        }
    }

    private fun pad(sb: StringBuilder, n: Int) { repeat(n) { sb.append("  ") } }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        s.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}
