package io.github.rakasans.foodapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        NutritionValueEntity::class,
        CategoryEntity::class,
        RecipeCategoryCrossRef::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
        CollectionEntity::class,
        RecipeCollectionCrossRef::class,
        ShoppingPartEntity::class,
        ShoppingItemStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao

    companion object {
        fun create(context: Context): FoodDatabase =
            Room.databaseBuilder(context, FoodDatabase::class.java, "foodapp.db").build()
    }
}
