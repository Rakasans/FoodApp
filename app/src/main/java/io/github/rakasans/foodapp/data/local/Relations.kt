package io.github.rakasans.foodapp.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** Przepis z danymi potrzebnymi na liście biblioteki (wyszukiwanie po składnikach, znaczniki). */
data class RecipeSummary(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(RecipeCategoryCrossRef::class, parentColumn = "recipeId", entityColumn = "categoryId"),
    )
    val categories: List<CategoryEntity>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(RecipeTagCrossRef::class, parentColumn = "recipeId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)

/** Pełny przepis do ekranu szczegółów i edytora. */
data class RecipeDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val nutrition: List<NutritionValueEntity>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(RecipeCategoryCrossRef::class, parentColumn = "recipeId", entityColumn = "categoryId"),
    )
    val categories: List<CategoryEntity>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(RecipeTagCrossRef::class, parentColumn = "recipeId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(RecipeCollectionCrossRef::class, parentColumn = "recipeId", entityColumn = "collectionId"),
    )
    val collections: List<CollectionEntity>,
)
