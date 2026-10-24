package io.github.rakasans.foodapp.data.repository

import androidx.room.withTransaction
import io.github.rakasans.foodapp.auth.AuthProvider
import io.github.rakasans.foodapp.core.Clock
import io.github.rakasans.foodapp.core.Ids
import io.github.rakasans.foodapp.data.local.CategoryEntity
import io.github.rakasans.foodapp.data.local.FoodDatabase
import io.github.rakasans.foodapp.data.local.RecipeDetails
import io.github.rakasans.foodapp.data.local.RecipeSummary
import io.github.rakasans.foodapp.data.local.TagEntity
import io.github.rakasans.foodapp.data.local.IngredientEntity
import io.github.rakasans.foodapp.data.local.RecipeCategoryCrossRef
import io.github.rakasans.foodapp.data.local.RecipeEntity
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.local.RecipeTagCrossRef
import io.github.rakasans.foodapp.data.local.StepEntity
import io.github.rakasans.foodapp.data.local.MaterialKind
import io.github.rakasans.foodapp.data.local.ProcessingState
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.domain.IngredientParser
import io.github.rakasans.foodapp.domain.ParsedRecipeText
import io.github.rakasans.foodapp.domain.InstagramPost
import io.github.rakasans.foodapp.domain.WebRecipe
import io.github.rakasans.foodapp.data.local.InfoOrigin
import io.github.rakasans.foodapp.data.local.NutritionValueEntity
import io.github.rakasans.foodapp.domain.RecipeTextParser
import io.github.rakasans.foodapp.domain.DuplicateRules
import io.github.rakasans.foodapp.domain.ExistingRecipe
import io.github.rakasans.foodapp.domain.normalizeName
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val db: FoodDatabase,
    private val auth: AuthProvider,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()

    /** Domyślne kategorie przy pierwszym uruchomieniu (użytkownik może dodawać własne — decyzja 10). */
    suspend fun ensureDefaults() {
        val dao = db.categoryDao()
        if (dao.count() > 0) return
        val now = clock.now()
        val owner = auth.currentUser.value.id
        dao.upsert(DEFAULT_CATEGORIES.mapIndexed { index, name ->
            CategoryEntity(Ids.new(), owner, name, isDefault = true, position = index, createdAt = now, updatedAt = now)
        })
    }

    /** Zwraca istniejącą kategorię o tej nazwie albo tworzy nową. */
    suspend fun getOrCreate(name: String): CategoryEntity {
        val dao = db.categoryDao()
        dao.findByName(name.trim())?.let { return it }
        val now = clock.now()
        val created = CategoryEntity(
            Ids.new(), auth.currentUser.value.id, name.trim(),
            isDefault = false, position = 100, createdAt = now, updatedAt = now,
        )
        dao.upsert(listOf(created))
        return created
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf("Śniadanie", "Obiad", "Kolacja", "Deser", "Przekąska")
    }
}

class TagRepository(
    private val db: FoodDatabase,
    private val auth: AuthProvider,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<TagEntity>> = db.tagDao().observeAll()

    suspend fun getOrCreate(name: String): TagEntity {
        val dao = db.tagDao()
        val clean = name.trim().removePrefix("#")
        dao.findByName(clean)?.let { return it }
        val now = clock.now()
        val created = TagEntity(Ids.new(), auth.currentUser.value.id, clean, createdAt = now, updatedAt = now)
        dao.upsert(listOf(created))
        return created
    }
}

class RecipeRepository(
    private val db: FoodDatabase,
    private val auth: AuthProvider,
    private val clock: Clock,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
) {
    fun observeSummaries(): Flow<List<RecipeSummary>> = db.recipeDao().observeSummaries()

    fun observeDetails(id: String): Flow<RecipeDetails?> = db.recipeDao().observeDetails(id)

    suspend fun loadDraft(id: String): RecipeDraft? = db.recipeDao().getDetails(id)?.let(RecipeDraft::from)

    suspend fun getRecipe(id: String): RecipeEntity? = db.recipeDao().getRecipe(id)

    // ---------- Materiały (v0.2) ----------

    /**
     * Zapisuje materiał bez przepisu: rolkę z Instagrama (czeka na zrzut opisu) albo link do strony
     * (import ze stron — v0.3). Materiał od razu jest w bibliotece i daje się wyszukać po notatce.
     */
    suspend fun saveLinkMaterial(
        url: String,
        note: String?,
        instagram: Boolean,
        processing: ProcessingState = if (instagram) ProcessingState.NEEDS_SCREENSHOT else ProcessingState.NONE,
    ): String {
        val now = clock.now()
        val id = Ids.new()
        val cleanNote = note?.trim()?.ifBlank { null }
        db.recipeDao().upsertRecipe(
            RecipeEntity(
                id = id, ownerId = auth.currentUser.value.id,
                status = RecipeStatus.MATERIAL,
                processing = processing,
                name = when {
                    cleanNote != null -> (if (instagram) "Rolka: " else "Link: ") + cleanNote
                    instagram -> "Rolka z Instagrama"
                    else -> "Link: " + url.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(40)
                },
                sourceType = if (instagram) SourceType.INSTAGRAM_REEL else SourceType.WEB,
                sourceUrl = url,
                materialKind = if (instagram) MaterialKind.INSTAGRAM_LINK else MaterialKind.LINK,
                materialText = url,
                materialNote = cleanNote,
                createdAt = now, updatedAt = now,
            )
        )
        return id
    }

    /**
     * Tworzy szkic z rozpoznanego tekstu (wklejony tekst, OCR, opis rolki).
     * @param existingId materiał, który zamieniamy w szkic (np. zapisana rolka) — zachowuje link i notatkę.
     * Oryginalny tekst zostaje w `materialText`; puste pola zostają puste.
     */
    suspend fun createDraftFromText(
        parsed: ParsedRecipeText,
        originalText: String,
        sourceType: SourceType,
        materialKind: MaterialKind,
        materialUri: String? = null,
        existingId: String? = null,
    ): String {
        val now = clock.now()
        val owner = auth.currentUser.value.id
        val id = existingId ?: Ids.new()
        db.withTransaction {
            val dao = db.recipeDao()
            val existing = existingId?.let { dao.getRecipe(it) }
            val isReel = existing?.materialKind == MaterialKind.INSTAGRAM_LINK
            val base = existing ?: RecipeEntity(id = id, ownerId = owner, status = RecipeStatus.DRAFT, name = "", createdAt = now, updatedAt = now)
            dao.upsertRecipe(
                base.copy(
                    status = RecipeStatus.DRAFT,
                    processing = ProcessingState.NONE,
                    name = parsed.name,
                    servings = parsed.servings,
                    sourceType = sourceType,
                    materialKind = materialKind,
                    materialText = if (isReel) existing?.materialText else originalText,
                    // tekst z OCR zachowujemy w notatce materiału, gdy materialText to już link rolki
                    materialNote = existing?.materialNote,
                    materialUri = materialUri ?: existing?.materialUri,
                    notes = if (isReel) "Tekst z opisu rolki:\n$originalText" else base.notes,
                    updatedAt = now,
                    deletedAt = null,
                )
            )
            dao.deleteIngredients(id)
            dao.upsertIngredients(parsed.ingredients.mapIndexed { index, line ->
                val p = IngredientParser.parse(line.raw)
                IngredientEntity(
                    id = Ids.new(), recipeId = id, position = index, groupName = line.group,
                    rawText = line.raw, name = p.name.ifBlank { line.raw }, quantity = p.quantity, quantityMax = p.quantityMax, unit = p.unit,
                    unitInferred = p.unitInferred,
                )
            })
            dao.deleteSteps(id)
            dao.upsertSteps(parsed.steps.mapIndexed { index, s -> StepEntity(Ids.new(), id, index, s) })
        }
        return id
    }

    suspend fun setProcessing(id: String, state: ProcessingState) = db.recipeDao().setProcessing(id, state, clock.now())

    suspend fun waitingReelIds(): List<String> = db.recipeDao().waitingReelIds()

    /** Notatka do zapisanej rolki („od Ani — makaron”) — widoczna w nazwie i w wyszukiwaniu. */
    suspend fun setMaterialNote(id: String, note: String) {
        val clean = note.trim().ifBlank { null }
        db.recipeDao().setMaterialNote(id, clean, if (clean != null) "Rolka: $clean" else "Rolka z Instagrama", clock.now())
    }

    /**
     * Szkic z opisu rolki pobranego z Instagrama. Oryginalny opis zostaje w `materialText`,
     * miniatura rolki staje się zdjęciem przepisu, autor — autorem źródła.
     */
    suspend fun createDraftFromReel(id: String, post: InstagramPost, photoPath: String?) {
        val caption = post.caption ?: return
        val parsed = RecipeTextParser.parse(caption)
        val now = clock.now()
        db.withTransaction {
            val dao = db.recipeDao()
            val r = dao.getRecipe(id) ?: return@withTransaction
            dao.upsertRecipe(
                r.copy(
                    status = RecipeStatus.DRAFT,
                    processing = ProcessingState.NONE,
                    name = parsed.name,
                    servings = parsed.servings,
                    photoUri = photoPath ?: r.photoUri,
                    sourceType = SourceType.INSTAGRAM_REEL,
                    sourceAuthor = post.author?.let { "@$it" } ?: r.sourceAuthor,
                    materialText = caption,
                    updatedAt = now,
                )
            )
            dao.deleteIngredients(id)
            dao.upsertIngredients(parsed.ingredients.mapIndexed { index, line ->
                val p = IngredientParser.parse(line.raw)
                IngredientEntity(
                    id = Ids.new(), recipeId = id, position = index, groupName = line.group,
                    rawText = line.raw, name = p.name.ifBlank { line.raw }, quantity = p.quantity, quantityMax = p.quantityMax, unit = p.unit,
                    unitInferred = p.unitInferred,
                )
            })
            dao.deleteSteps(id)
            dao.upsertSteps(parsed.steps.mapIndexed { index, s -> StepEntity(Ids.new(), id, index, s) })
        }
    }

    suspend fun waitingWebIds(): List<String> = db.recipeDao().waitingWebIds()

    /**
     * Szkic z danych strukturalnych strony (schema.org/Recipe). Bierzemy tylko to, co strona podała;
     * wartości odżywcze z podstawą „1 porcja” (tak definiuje je schema.org), oryginał w materialText.
     */
    suspend fun createDraftFromWeb(id: String, recipe: WebRecipe, photoPath: String?) {
        val now = clock.now()
        db.withTransaction {
            val dao = db.recipeDao()
            val r = dao.getRecipe(id) ?: return@withTransaction
            val url = r.sourceUrl.orEmpty()
            dao.upsertRecipe(
                r.copy(
                    status = RecipeStatus.DRAFT,
                    processing = ProcessingState.NONE,
                    name = recipe.name,
                    servings = recipe.servings,
                    yieldText = recipe.yieldText,
                    prepTime = recipe.prepTime,
                    cookTime = recipe.cookTime,
                    photoUri = photoPath ?: r.photoUri,
                    sourceType = SourceType.WEB,
                    sourceAuthor = recipe.author ?: r.sourceAuthor,
                    materialText = recipe.originalText(url),
                    nutritionOrigin = if (recipe.nutrition.isNotEmpty()) InfoOrigin.SOURCE else InfoOrigin.NONE,
                    nutritionBasis = recipe.nutritionBasis,
                    updatedAt = now,
                )
            )
            dao.deleteIngredients(id)
            dao.upsertIngredients(recipe.ingredients.mapIndexed { index, raw ->
                val p = IngredientParser.parse(raw)
                IngredientEntity(
                    id = Ids.new(), recipeId = id, position = index,
                    rawText = raw, name = p.name.ifBlank { raw }, quantity = p.quantity, quantityMax = p.quantityMax, unit = p.unit,
                    unitInferred = p.unitInferred,
                )
            })
            dao.deleteSteps(id)
            dao.upsertSteps(recipe.steps.mapIndexed { index, s -> StepEntity(Ids.new(), id, index, s) })
            dao.deleteNutrition(id)
            dao.upsertNutrition(recipe.nutrition.mapIndexed { index, (label, value) ->
                NutritionValueEntity(Ids.new(), id, index, label, value)
            })
        }
    }

    /** „Wprowadź ręcznie” z materiału: pusty szkic, który zachowuje źródło (link, notatkę, zdjęcie). */
    suspend fun materialToEmptyDraft(id: String) {
        val r = db.recipeDao().getRecipe(id) ?: return
        db.recipeDao().upsertRecipe(
            r.copy(
                status = RecipeStatus.DRAFT, processing = ProcessingState.NONE,
                name = "", updatedAt = clock.now(),
            )
        )
    }

    /** Nowy pusty szkic z podanym źródłem (np. link do strony wpisywany ręcznie). */
    suspend fun createEmptyDraft(sourceType: SourceType, sourceUrl: String?): String {
        val now = clock.now()
        val id = Ids.new()
        db.recipeDao().upsertRecipe(
            RecipeEntity(
                id = id, ownerId = auth.currentUser.value.id, status = RecipeStatus.DRAFT, name = "",
                sourceType = sourceType, sourceUrl = sourceUrl,
                materialKind = if (sourceUrl != null) MaterialKind.LINK else MaterialKind.NONE, materialText = sourceUrl,
                createdAt = now, updatedAt = now,
            )
        )
        return id
    }

    /**
     * Zapisuje przepis z edytora w jednej transakcji. Pola spoza edytora (materiał źródłowy,
     * wartości odżywcze, alergeny, data utworzenia) zostają bez zmian.
     */
    suspend fun saveDraft(d: RecipeDraft, status: RecipeStatus) {
        val now = clock.now()
        val owner = auth.currentUser.value.id
        db.withTransaction {
            val dao = db.recipeDao()
            val existing = dao.getRecipe(d.id)
            val base = existing ?: RecipeEntity(
                id = d.id, ownerId = owner, status = status, name = d.name,
                createdAt = now, updatedAt = now,
            )
            dao.upsertRecipe(
                base.copy(
                    status = status,
                    name = d.name.trim(),
                    servings = d.servings,
                    prepTime = d.prepTime.trim().ifBlank { null },
                    cookTime = d.cookTime.trim().ifBlank { null },
                    notes = d.notes.trim().ifBlank { null },
                    photoUri = d.photoPath,
                    sourceType = d.sourceType,
                    sourceUrl = d.sourceUrl,
                    sourceAuthor = d.sourceAuthor,
                    updatedAt = now,
                    deletedAt = null,
                )
            )
            dao.deleteIngredients(d.id)
            dao.upsertIngredients(
                d.ingredients.filter { it.rawText.isNotBlank() }.mapIndexed { index, i ->
                    IngredientEntity(
                        id = i.id, recipeId = d.id, position = index, groupName = i.groupName,
                        rawText = i.rawText.trim(), name = i.name.trim().ifBlank { i.rawText.trim() },
                        quantity = if (i.noQuantity) null else i.quantity,
                        quantityMax = if (i.noQuantity) null else i.quantityMax,
                        unit = i.unit?.trim()?.ifBlank { null },
                        noQuantity = i.noQuantity, unitInferred = i.unitInferred,
                    )
                }
            )
            dao.deleteSteps(d.id)
            dao.upsertSteps(
                d.steps.filter { it.text.isNotBlank() }.mapIndexed { index, s ->
                    StepEntity(id = s.id, recipeId = d.id, position = index, text = s.text.trim())
                }
            )
            dao.clearCategories(d.id)
            dao.addCategories(d.categoryIds.map { RecipeCategoryCrossRef(d.id, it) })
            dao.clearTags(d.id)
            dao.addTags(d.tagNames.distinctBy { it.lowercase() }.map { RecipeTagCrossRef(d.id, tags.getOrCreate(it).id) })
        }
    }

    /** Miękkie usuwanie — rekord zostaje z deletedAt, można go przywrócić („Cofnij”). */
    suspend fun softDelete(ids: List<String>) = db.recipeDao().softDelete(ids, clock.now())

    suspend fun restore(ids: List<String>) = db.recipeDao().restore(ids, clock.now())

    suspend fun existingRecipes(): List<ExistingRecipe> =
        db.recipeDao().activeNames().map { ExistingRecipe(it.id, it.name) }

    /** Nazwy przepisów demonstracyjnych, które już są w bibliotece. */
    suspend fun demoDuplicates(): List<String> =
        DuplicateRules.partition(DemoData.names(), existingRecipes()).first

    /**
     * Wczytuje przepisy demonstracyjne (tylko do testów; ekran Konto).
     * @param skipExisting true = pomija przepisy o nazwach, które już istnieją.
     * @return liczba dodanych przepisów.
     */
    suspend fun insertDemoData(skipExisting: Boolean): Int {
        val owner = auth.currentUser.value.id
        val existingKeys = if (skipExisting) existingRecipes().map { normalizeName(it.name) }.toSet() else emptySet()
        var added = 0
        db.withTransaction {
            DemoData.recipes(owner, clock.now())
                .filterNot { normalizeName(it.recipe.name) in existingKeys }
                .forEach { demo ->
                added++
                val dao = db.recipeDao()
                dao.upsertRecipe(demo.recipe)
                dao.upsertIngredients(demo.ingredients)
                dao.upsertSteps(demo.steps)
                dao.upsertNutrition(demo.nutrition)
                dao.addCategories(demo.categoryNames.map {
                    io.github.rakasans.foodapp.data.local.RecipeCategoryCrossRef(demo.recipe.id, categories.getOrCreate(it).id)
                })
                dao.addTags(demo.tagNames.map {
                    io.github.rakasans.foodapp.data.local.RecipeTagCrossRef(demo.recipe.id, tags.getOrCreate(it).id)
                })
            }
        }
        return added
    }
}
