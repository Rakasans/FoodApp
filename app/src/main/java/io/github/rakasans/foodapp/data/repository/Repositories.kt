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
