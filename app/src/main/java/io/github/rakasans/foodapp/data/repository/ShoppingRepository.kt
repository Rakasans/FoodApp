package io.github.rakasans.foodapp.data.repository

import androidx.room.withTransaction
import io.github.rakasans.foodapp.auth.AuthProvider
import io.github.rakasans.foodapp.core.Clock
import io.github.rakasans.foodapp.core.Ids
import io.github.rakasans.foodapp.data.local.CollectionEntity
import io.github.rakasans.foodapp.data.local.CollectionWithRecipes
import io.github.rakasans.foodapp.data.local.FoodDatabase
import io.github.rakasans.foodapp.data.local.RecipeCollectionCrossRef
import io.github.rakasans.foodapp.data.local.RecipeDetails
import io.github.rakasans.foodapp.data.local.ShoppingItemStateEntity
import io.github.rakasans.foodapp.data.local.ShoppingPartEntity
import io.github.rakasans.foodapp.domain.IngredientForShopping
import io.github.rakasans.foodapp.domain.IngredientParser
import io.github.rakasans.foodapp.domain.Scaling
import io.github.rakasans.foodapp.domain.ShoppingBuilder
import io.github.rakasans.foodapp.domain.ShoppingPart
import io.github.rakasans.foodapp.domain.normalizeName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Lista zakupów: wkłady przepisów i ręczne pozycje; pozycje (łączenie) liczy domena. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingRepository(
    private val db: FoodDatabase,
    private val auth: AuthProvider,
    private val clock: Clock,
) {
    private val owner get() = auth.currentUser.value.id

    fun observeParts(): Flow<List<ShoppingPartEntity>> =
        auth.currentUser.flatMapLatest { db.shoppingDao().observeParts(it.id) }

    fun observeStates(): Flow<Map<String, ShoppingItemStateEntity>> =
        auth.currentUser.flatMapLatest { u -> db.shoppingDao().observeStates(u.id).map { l -> l.associateBy { it.normalizedName } } }

    /** Dodaje składniki przepisu po przeliczeniu. Przepis się nie zmienia. */
    suspend fun addRecipe(details: RecipeDetails, scaling: Scaling): Int {
        val items = details.ingredients.sortedBy { it.position }.map {
            IngredientForShopping(it.name, it.rawText, it.quantity, it.quantityMax, it.unit, it.noQuantity)
        }
        val parts = ShoppingBuilder.parts(details.recipe.name, items, scaling)
        val now = clock.now()
        db.shoppingDao().upsertParts(parts.map { it.toEntity(details.recipe.id, now) })
        // pozycja dodana ponownie nie powinna być od razu odhaczona
        db.shoppingDao().deleteStates(owner, parts.map { normalizeName(it.name) }.distinct())
        return parts.size
    }

    /** Ręczna pozycja: „mleko”, „2 l mleka” — ilość zostaje w zapisie, nie sumujemy jej. */
    suspend fun addManual(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val p = IngredientParser.parse(clean)
        val now = clock.now()
        db.shoppingDao().upsertParts(
            listOf(
                ShoppingPartEntity(
                    id = Ids.new(), ownerId = owner, name = p.name.ifBlank { clean },
                    rawText = clean, manual = true, createdAt = now, updatedAt = now,
                )
            )
        )
    }

    suspend fun setChecked(key: String, checked: Boolean) {
        val current = db.shoppingDao().getState(owner, key)
        db.shoppingDao().upsertState(
            (current ?: ShoppingItemStateEntity(owner, key, updatedAt = clock.now())).copy(checked = checked, updatedAt = clock.now())
        )
    }

    /** Ilość wpisana ręcznie na liście (np. gdy przepis jej nie podał). null = usuń. */
    suspend fun setOverride(key: String, text: String?) {
        val current = db.shoppingDao().getState(owner, key)
        db.shoppingDao().upsertState(
            (current ?: ShoppingItemStateEntity(owner, key, updatedAt = clock.now()))
                .copy(quantityOverride = text?.trim()?.ifBlank { null }, updatedAt = clock.now())
        )
    }

    suspend fun removeLines(keys: Collection<String>) {
        if (keys.isEmpty()) return
        db.withTransaction {
            val ids = db.shoppingDao().activeParts(owner).filter { normalizeName(it.name) in keys }.map { it.id }
            if (ids.isNotEmpty()) db.shoppingDao().softDeleteParts(ids, clock.now())
            db.shoppingDao().deleteStates(owner, keys.toList())
        }
    }

    suspend fun clearAll() {
        db.withTransaction {
            val parts = db.shoppingDao().activeParts(owner)
            if (parts.isNotEmpty()) db.shoppingDao().softDeleteParts(parts.map { it.id }, clock.now())
            db.shoppingDao().deleteStates(owner, parts.map { normalizeName(it.name) }.distinct())
        }
    }

    private fun ShoppingPart.toEntity(recipeId: String, now: Long) = ShoppingPartEntity(
        id = Ids.new(), ownerId = owner, name = name, quantity = quantity, unit = unit, quantityMax = quantityMax,
        noQuantity = noQuantity, sourceRecipeId = recipeId, sourceRecipeName = recipeName, rawText = rawText,
        factorLabel = factorLabel, createdAt = now, updatedAt = now,
    )
}

fun ShoppingPartEntity.toDomain() = ShoppingPart(
    name = name, quantity = quantity, unit = unit, quantityMax = quantityMax, noQuantity = noQuantity,
    manual = manual, recipeName = sourceRecipeName, rawText = rawText, factorLabel = factorLabel,
)

/** Kolekcje: zestawy przepisów, niezależne od kategorii i etykiet. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionRepository(
    private val db: FoodDatabase,
    private val auth: AuthProvider,
    private val clock: Clock,
) {
    private val owner get() = auth.currentUser.value.id

    fun observeAll(): Flow<List<CollectionWithRecipes>> =
        auth.currentUser.flatMapLatest { db.collectionDao().observeAll(it.id) }.map { list -> list.map { it.withoutDeleted() } }

    fun observeOne(id: String): Flow<CollectionWithRecipes?> = db.collectionDao().observeOne(id).map { it?.withoutDeleted() }

    /** Tworzy kolekcję albo zwraca istniejącą o tej samej nazwie (bez względu na wielkość liter). */
    suspend fun getOrCreate(name: String): CollectionEntity {
        val clean = name.trim()
        db.collectionDao().active(owner).firstOrNull { normalizeName(it.name) == normalizeName(clean) }?.let { return it }
        val now = clock.now()
        val c = CollectionEntity(Ids.new(), owner, clean, createdAt = now, updatedAt = now)
        db.collectionDao().upsert(c)
        return c
    }

    suspend fun rename(id: String, name: String) {
        val c = db.collectionDao().get(id) ?: return
        db.collectionDao().upsert(c.copy(name = name.trim(), updatedAt = clock.now()))
    }

    /** Usuwa kolekcję (miękko). Przepisy zostają — kolekcja to tylko zestaw. */
    suspend fun delete(id: String) {
        val c = db.collectionDao().get(id) ?: return
        db.collectionDao().upsert(c.copy(deletedAt = clock.now(), updatedAt = clock.now()))
    }

    suspend fun collectionIdsFor(recipeId: String): Set<String> = db.collectionDao().collectionIdsFor(recipeId).toSet()

    suspend fun setMembership(recipeId: String, collectionId: String, member: Boolean) {
        if (member) db.collectionDao().addRecipe(RecipeCollectionCrossRef(recipeId, collectionId))
        else db.collectionDao().removeRecipe(recipeId, collectionId)
        db.collectionDao().get(collectionId)?.let { db.collectionDao().upsert(it.copy(updatedAt = clock.now())) }
    }

    private fun CollectionWithRecipes.withoutDeleted() = copy(recipes = recipes.filter { it.deletedAt == null })
}
