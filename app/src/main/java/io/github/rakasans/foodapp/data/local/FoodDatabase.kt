package io.github.rakasans.foodapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
    exportSchema = true,
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        /** v2: zakresy ilości w składnikach. Migracja zachowuje istniejące przepisy. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredient ADD COLUMN quantityMax REAL")
            }
        }

        /** v3: zakresy na liście zakupów. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_part ADD COLUMN quantityMax REAL")
            }
        }

        fun create(context: Context): FoodDatabase =
            Room.databaseBuilder(context, FoodDatabase::class.java, "foodapp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
