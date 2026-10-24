package io.github.rakasans.foodapp.data.repository

import io.github.rakasans.foodapp.core.Ids
import io.github.rakasans.foodapp.data.local.IngredientEntity
import io.github.rakasans.foodapp.data.local.InfoOrigin
import io.github.rakasans.foodapp.data.local.MaterialKind
import io.github.rakasans.foodapp.data.local.NutritionValueEntity
import io.github.rakasans.foodapp.data.local.RecipeEntity
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.data.local.StepEntity

/** Te same przepisy demonstracyjne co w prototypie: kompletny, bez porcji, z brakiem ilości. */
object DemoData {

    class DemoRecipe(
        val recipe: RecipeEntity,
        val ingredients: List<IngredientEntity>,
        val steps: List<StepEntity>,
        val nutrition: List<NutritionValueEntity>,
        val categoryNames: List<String>,
        val tagNames: List<String>,
    )

    private data class Ing(
        val raw: String, val name: String, val qty: Double?, val unit: String?,
        val group: String? = null, val noQty: Boolean = false,
    )

    fun recipes(owner: String, now: Long): List<DemoRecipe> = listOf(
        build(
            owner, now,
            RecipeTemplate(
                name = "Szakszuka z papryką", servings = 2, prep = "10 min", cook = "20 min",
                sourceType = SourceType.WEB, sourceUrl = "https://example.com/szakszuka-z-papryka", author = "Autor bloga (dane demo)",
                notes = "Następnym razem więcej kuminu.",
                nutritionBasis = "1 porcja", allergens = "Jaja.",
            ),
            listOf(
                Ing("4 jajka", "jajka", 4.0, "szt."),
                Ing("400 g pomidorów krojonych z puszki", "pomidory krojone", 400.0, "g"),
                Ing("1 czerwona papryka", "papryka czerwona", 1.0, "szt."),
                Ing("1 cebula", "cebula", 1.0, "szt."),
                Ing("2 łyżki oliwy", "oliwa", 2.0, "łyżka"),
                Ing("1 łyżeczka kuminu", "kumin", 1.0, "łyżeczka"),
            ),
            listOf(
                "Cebulę i paprykę pokrój w kostkę, zeszklij na oliwie (ok. 5 minut).",
                "Dodaj kumin i pomidory, duś 10 minut.",
                "Zrób w sosie 4 wgłębienia i wbij jajka.",
                "Przykryj i gotuj na małym ogniu 5–7 minut, aż białka się zetną.",
            ),
            nutrition = listOf("Energia" to "310 kcal", "Białko" to "17 g", "Tłuszcze" to "20 g", "Węglowodany" to "16 g"),
            categories = listOf("Śniadanie", "Kolacja"), tags = listOf("wegetariańskie", "szybkie"),
        ),
        build(
            owner, now - 1000,
            RecipeTemplate(
                name = "Pierniki świąteczne", servings = null, yieldText = "Źródło podaje „ok. 1 blacha” — bez liczby porcji.",
                prep = "40 min", cook = "10 min", sourceType = SourceType.PHOTO, author = null,
            ),
            listOf(
                Ing("500 g mąki pszennej", "mąka pszenna", 500.0, "g", "Ciasto"),
                Ing("200 g miodu", "miód", 200.0, "g", "Ciasto"),
                Ing("100 g masła", "masło", 100.0, "g", "Ciasto"),
                Ing("2 jajka", "jajka", 2.0, "szt.", "Ciasto"),
                Ing("2 łyżki mleka", "mleko", 2.0, "łyżka", "Ciasto"),
                Ing("150 g cukru pudru", "cukier puder", 150.0, "g", "Lukier"),
            ),
            listOf(
                "Miód podgrzej z masłem i przyprawą, przestudź.",
                "Połącz z mąką, jajkami i mlekiem; wyrób ciasto.",
                "Rozwałkuj na grubość 4 mm i wykrawaj pierniki.",
                "Piecz 8–10 minut w 180°C.",
            ),
            categories = listOf("Deser"), tags = listOf("święta"),
        ),
        build(
            owner, now - 2000,
            RecipeTemplate(
                name = "Zupa krem z dyni", servings = 4, prep = "15 min", cook = "25 min",
                sourceType = SourceType.INSTAGRAM_REEL, sourceUrl = "https://www.instagram.com/reel/DEMO123/", author = "@kuchnia_demo",
            ),
            listOf(
                Ing("1 kg dyni (np. hokkaido)", "dynia", 1.0, "kg"),
                Ing("1 cebula", "cebula", 1.0, "szt."),
                Ing("20 g masła", "masło", 20.0, "g"),
                Ing("1 l bulionu warzywnego", "bulion warzywny", 1.0, "l"),
                Ing("mleko kokosowe", "mleko kokosowe", null, null),
            ),
            listOf(
                "Cebulę zeszklij na maśle.",
                "Dodaj pokrojoną dynię, zalej bulionem.",
                "Gotuj ok. 20 minut do miękkości.",
                "Zmiksuj, dodaj mleko kokosowe i dopraw.",
            ),
            categories = listOf("Obiad", "Kolacja"), tags = listOf("wegetariańskie", "jesień"),
        ),
    )

    private data class RecipeTemplate(
        val name: String,
        val servings: Int?,
        val yieldText: String? = null,
        val prep: String?,
        val cook: String?,
        val sourceType: SourceType,
        val sourceUrl: String? = null,
        val author: String?,
        val notes: String? = null,
        val nutritionBasis: String? = null,
        val allergens: String? = null,
    )

    private fun build(
        owner: String,
        time: Long,
        t: RecipeTemplate,
        ings: List<Ing>,
        steps: List<String>,
        nutrition: List<Pair<String, String>> = emptyList(),
        categories: List<String>,
        tags: List<String>,
    ): DemoRecipe {
        val id = Ids.new()
        val recipe = RecipeEntity(
            id = id, ownerId = owner, status = RecipeStatus.APPROVED,
            name = t.name, servings = t.servings, yieldText = t.yieldText, prepTime = t.prep, cookTime = t.cook,
            notes = t.notes, sourceType = t.sourceType, sourceUrl = t.sourceUrl, sourceAuthor = t.author,
            materialKind = if (t.sourceUrl != null) MaterialKind.LINK else MaterialKind.NONE,
            materialText = t.sourceUrl,
            nutritionOrigin = if (nutrition.isEmpty()) InfoOrigin.NONE else InfoOrigin.SOURCE,
            nutritionBasis = t.nutritionBasis,
            allergensOrigin = if (t.allergens == null) InfoOrigin.NONE else InfoOrigin.SOURCE,
            allergensText = t.allergens,
            createdAt = time, updatedAt = time,
        )
        return DemoRecipe(
            recipe = recipe,
            ingredients = ings.mapIndexed { i, x ->
                IngredientEntity(Ids.new(), id, i, x.group, x.raw, x.name, x.qty, x.unit, x.noQty)
            },
            steps = steps.mapIndexed { i, s -> StepEntity(Ids.new(), id, i, s) },
            nutrition = nutrition.mapIndexed { i, (k, v) -> NutritionValueEntity(Ids.new(), id, i, k, v) },
            categoryNames = categories,
            tagNames = tags,
        )
    }
}
