package io.github.rakasans.foodapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipe WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeSummaries(): Flow<List<RecipeSummary>>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    fun observeDetails(id: String): Flow<RecipeDetails?>

    @Query("SELECT COUNT(*) FROM recipe WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getRecipe(id: String): RecipeEntity?

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getDetails(id: String): RecipeDetails?

    @Transaction
    @Query("SELECT * FROM recipe WHERE deletedAt IS NULL")
    suspend fun allDetails(): List<RecipeDetails>

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertIngredients(items: List<IngredientEntity>)

    @Upsert
    suspend fun upsertSteps(items: List<StepEntity>)

    @Upsert
    suspend fun upsertNutrition(items: List<NutritionValueEntity>)

    @Query("DELETE FROM ingredient WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: String)

    @Query("DELETE FROM step WHERE recipeId = :recipeId")
    suspend fun deleteSteps(recipeId: String)

    @Query("DELETE FROM nutrition_value WHERE recipeId = :recipeId")
    suspend fun deleteNutrition(recipeId: String)

    @Query("DELETE FROM recipe_category WHERE recipeId = :recipeId")
    suspend fun clearCategories(recipeId: String)

    @Query("DELETE FROM recipe_tag WHERE recipeId = :recipeId")
    suspend fun clearTags(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCategories(refs: List<RecipeCategoryCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTags(refs: List<RecipeTagCrossRef>)

    @Query("UPDATE recipe SET processing = :state, updatedAt = :now WHERE id = :id")
    suspend fun setProcessing(id: String, state: ProcessingState, now: Long)

    @Query("UPDATE recipe SET materialNote = :note, name = :name, updatedAt = :now WHERE id = :id")
    suspend fun setMaterialNote(id: String, note: String?, name: String, now: Long)

    @Query("SELECT id FROM recipe WHERE deletedAt IS NULL AND status = 'MATERIAL' AND processing = 'WAITING_NETWORK' AND materialKind = 'INSTAGRAM_LINK'")
    suspend fun waitingReelIds(): List<String>

    @Query("SELECT id FROM recipe WHERE deletedAt IS NULL AND status = 'MATERIAL' AND processing = 'WAITING_NETWORK' AND materialKind = 'LINK'")
    suspend fun waitingWebIds(): List<String>

    @Query("SELECT id, name FROM recipe WHERE deletedAt IS NULL")
    suspend fun activeNames(): List<RecipeNameRow>

    @Query("UPDATE recipe SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, now: Long)

    @Query("UPDATE recipe SET deletedAt = NULL, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>, now: Long)
}

data class RecipeNameRow(val id: String, val name: String)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE deletedAt IS NULL ORDER BY position, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT * FROM category WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Upsert
    suspend fun upsert(items: List<CategoryEntity>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Upsert
    suspend fun upsert(items: List<TagEntity>)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_part WHERE deletedAt IS NULL AND ownerId = :ownerId ORDER BY createdAt")
    fun observeParts(ownerId: String): Flow<List<ShoppingPartEntity>>

    @Query("SELECT * FROM shopping_part WHERE deletedAt IS NULL AND ownerId = :ownerId")
    suspend fun activeParts(ownerId: String): List<ShoppingPartEntity>

    @Upsert
    suspend fun upsertParts(items: List<ShoppingPartEntity>)

    @Query("UPDATE shopping_part SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteParts(ids: List<String>, now: Long)

    @Query("SELECT * FROM shopping_item_state WHERE ownerId = :ownerId")
    fun observeStates(ownerId: String): Flow<List<ShoppingItemStateEntity>>

    @Query("SELECT * FROM shopping_item_state WHERE ownerId = :ownerId AND normalizedName = :key")
    suspend fun getState(ownerId: String, key: String): ShoppingItemStateEntity?

    @Upsert
    suspend fun upsertState(state: ShoppingItemStateEntity)

    @Query("DELETE FROM shopping_item_state WHERE ownerId = :ownerId AND normalizedName IN (:keys)")
    suspend fun deleteStates(ownerId: String, keys: List<String>)
}

@Dao
interface CollectionDao {
    @Transaction
    @Query("SELECT * FROM collection WHERE deletedAt IS NULL AND ownerId = :ownerId ORDER BY name COLLATE NOCASE")
    fun observeAll(ownerId: String): Flow<List<CollectionWithRecipes>>

    @Transaction
    @Query("SELECT * FROM collection WHERE id = :id")
    fun observeOne(id: String): Flow<CollectionWithRecipes?>

    @Query("SELECT * FROM collection WHERE deletedAt IS NULL AND ownerId = :ownerId")
    suspend fun active(ownerId: String): List<CollectionEntity>

    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Query("SELECT * FROM collection WHERE id = :id")
    suspend fun get(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRecipe(ref: RecipeCollectionCrossRef)

    @Query("DELETE FROM recipe_collection WHERE recipeId = :recipeId AND collectionId = :collectionId")
    suspend fun removeRecipe(recipeId: String, collectionId: String)

    @Query("SELECT collectionId FROM recipe_collection WHERE recipeId = :recipeId")
    suspend fun collectionIdsFor(recipeId: String): List<String>
}
