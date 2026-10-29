package io.github.rakasans.foodapp.data.backup

import androidx.room.withTransaction
import io.github.rakasans.foodapp.auth.AuthProvider
import io.github.rakasans.foodapp.core.Clock
import io.github.rakasans.foodapp.core.Ids
import io.github.rakasans.foodapp.data.local.FoodDatabase
import io.github.rakasans.foodapp.data.local.InfoOrigin
import io.github.rakasans.foodapp.data.local.IngredientEntity
import io.github.rakasans.foodapp.data.local.MaterialKind
import io.github.rakasans.foodapp.data.local.NutritionValueEntity
import io.github.rakasans.foodapp.data.local.ProcessingState
import io.github.rakasans.foodapp.data.local.RecipeCategoryCrossRef
import io.github.rakasans.foodapp.data.local.RecipeCollectionCrossRef
import io.github.rakasans.foodapp.data.local.RecipeDetails
import io.github.rakasans.foodapp.data.local.RecipeEntity
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.local.RecipeTagCrossRef
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.data.local.StepEntity
import io.github.rakasans.foodapp.data.local.SyncState
import io.github.rakasans.foodapp.data.repository.CategoryRepository
import io.github.rakasans.foodapp.data.repository.CollectionRepository
import io.github.rakasans.foodapp.data.repository.PhotoStore
import io.github.rakasans.foodapp.data.repository.TagRepository
import io.github.rakasans.foodapp.domain.Backup
import io.github.rakasans.foodapp.domain.BackupFile
import io.github.rakasans.foodapp.domain.BackupIngredient
import io.github.rakasans.foodapp.domain.BackupRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Eksport i przywracanie kopii (decyzja 9). Archiwum ZIP: `recipes.json` + `photos/<plik>`.
 * Przywracanie dokłada brakujące przepisy (po identyfikatorze) — niczego nie nadpisuje i nie usuwa.
 */
class BackupManager(
    private val db: FoodDatabase,
    private val auth: AuthProvider,
    private val clock: Clock,
    private val photos: PhotoStore,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val collections: CollectionRepository,
) {
    data class RestoreResult(val added: Int, val skipped: Int)

    suspend fun export(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val all = db.recipeDao().allDetails()
        val recipes = all.map { it.toBackup() }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("recipes.json"))
            zip.write(Backup.toJson(BackupFile(Backup.VERSION, clock.now(), recipes)).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            all.zip(recipes).forEach { (d, b) ->
                val name = b.photoFile ?: return@forEach
                val f = d.recipe.photoUri?.let(::File)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry("photos/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        recipes.size
    }

    /** Rzuca IllegalArgumentException z komunikatem dla użytkownika, gdy plik nie jest kopią FoodApp. */
    suspend fun restore(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        var json: String? = null
        val photoFiles = mutableMapOf<String, String>() // nazwa w archiwum → ścieżka na urządzeniu
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == "recipes.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                    e.name.startsWith("photos/") && !e.isDirectory -> {
                        val name = e.name.removePrefix("photos/")
                        // ochrona przed ścieżkami typu „../” w archiwum
                        if (name.isNotBlank() && !name.contains('/') && !name.contains("..")) {
                            val f = photos.newPhotoFile()
                            f.outputStream().use { zip.copyTo(it) }
                            photoFiles[name] = f.absolutePath
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val file = Backup.fromJson(json ?: throw IllegalArgumentException("W pliku nie ma kopii przepisów Przepisarni."))
        var added = 0
        var skipped = 0
        val usedPhotos = mutableSetOf<String>()
        file.recipes.forEach { b ->
            if (db.recipeDao().getRecipe(b.id) != null) { skipped++; return@forEach }
            val photo = b.photoFile?.let { photoFiles[it] }?.also { usedPhotos += it }
            insert(b, photo)
            added++
        }
        // zdjęcia przepisów, które już były na telefonie, nie są potrzebne
        photoFiles.values.filterNot { it in usedPhotos }.forEach { File(it).delete() }
        RestoreResult(added, skipped)
    }

    private suspend fun insert(b: BackupRecipe, photoPath: String?) {
        val f = b.fields
        val now = clock.now()
        val categoryIds = b.categories.map { categories.getOrCreate(it).id }
        val tagIds = b.tags.map { tags.getOrCreate(it).id }
        val collectionIds = b.collections.map { collections.getOrCreate(it).id }
        db.withTransaction {
            val dao = db.recipeDao()
            dao.upsertRecipe(
                RecipeEntity(
                    id = b.id,
                    ownerId = auth.currentUser.value.id,
                    status = enumOr(f["status"], RecipeStatus.DRAFT),
                    // przetwarzanie z innego telefonu nie jest wznawiane — materiał można pobrać ponownie
                    processing = ProcessingState.NONE,
                    name = f.str("name").orEmpty(),
                    servings = (f["servings"] as? Double)?.toInt(),
                    yieldText = f.str("yieldText"),
                    prepTime = f.str("prepTime"),
                    cookTime = f.str("cookTime"),
                    notes = f.str("notes"),
                    photoUri = photoPath,
                    sourceType = enumOr(f["sourceType"], SourceType.MANUAL),
                    sourceUrl = f.str("sourceUrl"),
                    sourceAuthor = f.str("sourceAuthor"),
                    materialKind = enumOr(f["materialKind"], MaterialKind.NONE),
                    materialText = f.str("materialText"),
                    materialNote = f.str("materialNote"),
                    nutritionOrigin = enumOr(f["nutritionOrigin"], InfoOrigin.NONE),
                    nutritionBasis = f.str("nutritionBasis"),
                    allergensOrigin = enumOr(f["allergensOrigin"], InfoOrigin.NONE),
                    allergensText = f.str("allergensText"),
                    createdAt = (f["createdAt"] as? Double)?.toLong() ?: now,
                    updatedAt = (f["updatedAt"] as? Double)?.toLong() ?: now,
                    syncState = SyncState.LOCAL_ONLY,
                    favorite = f["favorite"] == true,
                )
            )
            dao.upsertIngredients(b.ingredients.mapIndexed { i, x ->
                IngredientEntity(
                    id = Ids.new(), recipeId = b.id, position = i, groupName = x.groupName, rawText = x.rawText,
                    name = x.name, quantity = x.quantity, unit = x.unit, noQuantity = x.noQuantity,
                    unitInferred = x.unitInferred, quantityMax = x.quantityMax,
                )
            })
            dao.upsertSteps(b.steps.mapIndexed { i, s -> StepEntity(Ids.new(), b.id, i, s) })
            dao.upsertNutrition(b.nutrition.mapIndexed { i, (l, v) -> NutritionValueEntity(Ids.new(), b.id, i, l, v) })
            dao.addCategories(categoryIds.map { RecipeCategoryCrossRef(b.id, it) })
            dao.addTags(tagIds.map { RecipeTagCrossRef(b.id, it) })
            collectionIds.forEach { db.collectionDao().addRecipe(RecipeCollectionCrossRef(b.id, it)) }
        }
    }

    private fun RecipeDetails.toBackup(): BackupRecipe {
        val r = recipe
        val photo = r.photoUri?.let(::File)?.takeIf { it.isFile }?.let { "${r.id}.${it.extension.ifBlank { "jpg" }}" }
        return BackupRecipe(
            id = r.id,
            fields = linkedMapOf(
                "status" to r.status.name, "name" to r.name, "servings" to r.servings?.toDouble(),
                "yieldText" to r.yieldText, "prepTime" to r.prepTime, "cookTime" to r.cookTime, "notes" to r.notes,
                "sourceType" to r.sourceType.name, "sourceUrl" to r.sourceUrl, "sourceAuthor" to r.sourceAuthor,
                "materialKind" to r.materialKind.name, "materialText" to r.materialText, "materialNote" to r.materialNote,
                "nutritionOrigin" to r.nutritionOrigin.name, "nutritionBasis" to r.nutritionBasis,
                "allergensOrigin" to r.allergensOrigin.name, "allergensText" to r.allergensText,
                "createdAt" to r.createdAt.toDouble(), "updatedAt" to r.updatedAt.toDouble(),
                "favorite" to r.favorite,
            ),
            ingredients = ingredients.sortedBy { it.position }.map {
                BackupIngredient(it.rawText, it.name, it.quantity, it.quantityMax, it.unit, it.noQuantity, it.unitInferred, it.groupName)
            },
            steps = steps.sortedBy { it.position }.map { it.text },
            nutrition = nutrition.sortedBy { it.position }.map { it.label to it.valueText },
            categories = this.categories.filter { it.deletedAt == null }.map { it.name },
            tags = this.tags.filter { it.deletedAt == null }.map { it.name },
            collections = this.collections.filter { it.deletedAt == null }.map { it.name },
            photoFile = photo,
        )
    }

    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String

    private inline fun <reified E : Enum<E>> enumOr(v: Any?, default: E): E =
        (v as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
