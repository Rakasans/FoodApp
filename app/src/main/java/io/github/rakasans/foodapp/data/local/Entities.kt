package io.github.rakasans.foodapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Wspólne zasady (gotowość do synchronizacji, decyzja 2):
 * - id: UUID z urządzenia, ownerId: identyfikator właściciela (do czasu kont — instalacja),
 * - createdAt / updatedAt (epoch ms), deletedAt != null = miękko usunięty,
 * - syncState: w v0.1 zawsze LOCAL_ONLY.
 */

@Entity(tableName = "recipe", indices = [Index("ownerId"), Index("status")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val status: RecipeStatus,
    val processing: ProcessingState = ProcessingState.NONE,
    val name: String,
    /** null = liczba porcji nieznana (nie zgadujemy). */
    val servings: Int? = null,
    /** Np. „ok. 1 blacha” — gdy źródło podaje wydajność zamiast porcji. */
    val yieldText: String? = null,
    val prepTime: String? = null,
    val cookTime: String? = null,
    val notes: String? = null,
    val photoUri: String? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val sourceUrl: String? = null,
    val sourceAuthor: String? = null,
    val materialKind: MaterialKind = MaterialKind.NONE,
    val materialText: String? = null,
    val materialUri: String? = null,
    /** Notatka do materiału, np. „od Ani — makaron z dynią” (rolki z Instagrama). */
    val materialNote: String? = null,
    val nutritionOrigin: InfoOrigin = InfoOrigin.NONE,
    /** Podstawa wartości odżywczych: „1 porcja”, „cały przepis”, „100 g”. */
    val nutritionBasis: String? = null,
    val allergensOrigin: InfoOrigin = InfoOrigin.NONE,
    val allergensText: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "ingredient",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val groupName: String? = null,
    /** Oryginalny zapis ze źródła — zawsze zachowany. */
    val rawText: String,
    val name: String,
    /** null + noQuantity=false oznacza brak danych w źródle. */
    val quantity: Double? = null,
    val unit: String? = null,
    /** „do smaku” / bez ilości — świadomie brak ilości, nie brak danych. */
    val noQuantity: Boolean = false,
    /** Jednostka przyjęta automatycznie przez parser (do sprawdzenia przez użytkownika). */
    val unitInferred: Boolean = false,
    /** Górna granica zakresu („300–350 g”); `quantity` jest wtedy dolną. Dodane w wersji bazy 2. */
    val quantityMax: Double? = null,
)

@Entity(
    tableName = "step",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class StepEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val text: String,
)

@Entity(
    tableName = "nutrition_value",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class NutritionValueEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val label: String,
    val valueText: String,
)

@Entity(tableName = "category", indices = [Index("ownerId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val isDefault: Boolean = false,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "recipe_category",
    primaryKeys = ["recipeId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class RecipeCategoryCrossRef(val recipeId: String, val categoryId: String)

@Entity(tableName = "tag", indices = [Index("ownerId")])
data class TagEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "recipe_tag",
    primaryKeys = ["recipeId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class RecipeTagCrossRef(val recipeId: String, val tagId: String)

@Entity(tableName = "collection", indices = [Index("ownerId")])
data class CollectionEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "recipe_collection",
    primaryKeys = ["recipeId", "collectionId"],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("collectionId")],
)
data class RecipeCollectionCrossRef(val recipeId: String, val collectionId: String)

/** Wkład jednego przepisu (lub ręcznego wpisu) w listę zakupów. Pozycje liczy domena. */
@Entity(tableName = "shopping_part", indices = [Index("ownerId")])
data class ShoppingPartEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val noQuantity: Boolean = false,
    val sourceRecipeId: String? = null,
    val sourceRecipeName: String? = null,
    val rawText: String? = null,
    /** Np. „4 porc. zamiast 2” albo „×2 przepisu”. */
    val factorLabel: String? = null,
    val manual: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    /** Górna granica zakresu (już przeliczona). Dodane w wersji bazy 3. */
    val quantityMax: Double? = null,
)

/** Stan pozycji listy (po znormalizowanej nazwie): odznaczenie i ręczna ilość. */
@Entity(tableName = "shopping_item_state", primaryKeys = ["ownerId", "normalizedName"])
data class ShoppingItemStateEntity(
    val ownerId: String,
    val normalizedName: String,
    val checked: Boolean = false,
    val quantityOverride: String? = null,
    val updatedAt: Long,
)
