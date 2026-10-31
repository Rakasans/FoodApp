package io.github.rakasans.foodapp.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import io.github.rakasans.foodapp.auth.AuthProvider
import io.github.rakasans.foodapp.core.Connectivity
import io.github.rakasans.foodapp.core.Ids
import io.github.rakasans.foodapp.data.local.CategoryEntity
import io.github.rakasans.foodapp.data.local.CollectionEntity
import io.github.rakasans.foodapp.data.local.FoodDatabase
import io.github.rakasans.foodapp.data.local.InfoOrigin
import io.github.rakasans.foodapp.data.local.IngredientEntity
import io.github.rakasans.foodapp.data.local.MaterialKind
import io.github.rakasans.foodapp.data.local.NutritionValueEntity
import io.github.rakasans.foodapp.data.local.ProcessingState
import io.github.rakasans.foodapp.data.local.RecipeCategoryCrossRef
import io.github.rakasans.foodapp.data.local.RecipeCollectionCrossRef
import io.github.rakasans.foodapp.data.local.RecipeEntity
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.local.RecipeTagCrossRef
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.data.local.StepEntity
import io.github.rakasans.foodapp.data.local.SyncMetaEntity
import io.github.rakasans.foodapp.data.local.SyncState
import io.github.rakasans.foodapp.data.local.TagEntity
import io.github.rakasans.foodapp.data.repository.PhotoStore
import io.github.rakasans.foodapp.domain.SyncAction
import io.github.rakasans.foodapp.domain.SyncRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stan synchronizacji widoczny w „Koncie”. */
sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data object Never : SyncStatus
    data object Running : SyncStatus
    data object WaitingForNetwork : SyncStatus
    data class Done(val at: Long, val pushed: Int, val pulled: Int) : SyncStatus
    data class Failed(val at: Long, val message: String) : SyncStatus
}

/**
 * Synchronizacja telefon ↔ Supabase: przepisy (ze składnikami, krokami, wartościami odżywczymi, powiązaniami),
 * kategorie, etykiety, kolekcje i zdjęcia przepisów. Room zostaje źródłem prawdy na telefonie.
 *
 * 1. Pobieranie: rekordy zmienione w chmurze od ostatniego razu (server_updated_at > znacznik).
 * 2. Wysyłanie: rekordy, których skrót treści różni się od zapamiętanej bazy.
 * Konflikty rozstrzyga domain/SyncRules (nowsza zmiana wygrywa). Lista zakupów — w kolejnym kroku.
 */
class SyncEngine(
    context: Context,
    private val db: FoodDatabase,
    private val client: SupabaseClient,
    private val photos: PhotoStore,
    private val auth: AuthProvider,
    private val connectivity: Connectivity,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(initialStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
            _status.value = if (value) initialStatus() else SyncStatus.Disabled
        }

    private fun initialStatus(): SyncStatus = when {
        !prefs.getBoolean("enabled", true) -> SyncStatus.Disabled
        prefs.getLong("last_ok", 0) > 0 -> SyncStatus.Done(prefs.getLong("last_ok", 0), 0, 0)
        else -> SyncStatus.Never
    }

    /** Jedna pełna synchronizacja. Bezpieczna do wywołania wiele razy (kolejne czekają na poprzednią). */
    suspend fun sync() {
        if (!enabled) return
        if (!connectivity.isOnline()) { _status.value = SyncStatus.WaitingForNetwork; return }
        mutex.withLock {
            _status.value = SyncStatus.Running
            try {
                val uid = client.session().userId
                var pulled = 0
                pulled += pullSimple("category", ::categoryHash, { db.categoryDao().get(it) }) { j -> db.categoryDao().upsert(listOf(categoryFrom(j))) }
                pulled += pullSimple("tag", ::tagHash, { db.tagDao().get(it) }) { j -> db.tagDao().upsert(listOf(tagFrom(j))) }
                pulled += pullSimple("collection", ::collectionHash, { db.collectionDao().get(it) }) { j -> db.collectionDao().upsert(collectionFrom(j)) }
                pulled += pullRecipes()

                var pushed = 0
                pushed += pushSimple("category", db.categoryDao().all(), { it.id }, ::categoryHash, ::categoryJson)
                pushed += pushSimple("tag", db.tagDao().all(), { it.id }, ::tagHash, ::tagJson)
                pushed += pushSimple("collection", db.collectionDao().all(), { it.id }, ::collectionHash, ::collectionJson)
                pushed += pushRecipes(uid)

                purgeDeleted()
                val now = System.currentTimeMillis()
                prefs.edit().putLong("last_ok", now).apply()
                _status.value = SyncStatus.Done(now, pushed, pulled)
            } catch (e: SupabaseClient.SessionLost) {
                _status.value = SyncStatus.Failed(System.currentTimeMillis(), e.message ?: "Sesja wygasła.")
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                _status.value = if (!connectivity.isOnline()) SyncStatus.WaitingForNetwork
                else SyncStatus.Failed(System.currentTimeMillis(), e.message ?: e.toString())
            }
        }
    }

    // ================================================================ pobieranie

    /** Zmienione w chmurze wiersze tabeli (stronicowane po server_updated_at). */
    private suspend fun changedRows(table: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var mark = prefs.getString("mark_$table", null)
        while (true) {
            val filter = mark?.let { "server_updated_at=gt.${SupabaseClient.enc(it)}&" }.orEmpty()
            val page = client.select(table, "${filter}select=*&order=server_updated_at.asc&limit=500")
            for (i in 0 until page.length()) out += page.getJSONObject(i)
            if (page.length() < 500) break
            mark = page.getJSONObject(page.length() - 1).getString("server_updated_at")
        }
        return out
    }

    private fun saveMark(table: String, rows: List<JSONObject>) {
        rows.lastOrNull()?.let { prefs.edit().putString("mark_$table", it.getString("server_updated_at")).apply() }
    }

    private suspend fun <T> pullSimple(
        kind: String, hash: (T) -> String, local: suspend (String) -> T?, apply: suspend (JSONObject) -> Unit,
    ): Int where T : Any {
        val rows = changedRows(kind)
        var n = 0
        for (j in rows) {
            val id = j.getString("id")
            val l = local(id)
            val meta = db.syncDao().get(kind, id)
            val action = SyncRules.decide(
                l?.let(hash), meta?.baseHash, j.getString("server_updated_at"), meta?.baseServerAt,
                l?.let(::updatedAtOf), j.optLong("updated_at"),
            )
            when (action) {
                SyncAction.PULL -> {
                    apply(j)
                    val now = local(id)!!
                    db.syncDao().upsert(SyncMetaEntity(kind, id, hash(now), j.getString("server_updated_at")))
                    n++
                }
                // ta sama treść, ale nowszy znacznik w chmurze (np. nasz własny zapis) — tylko przesuwamy bazę
                SyncAction.NONE -> if (l != null) db.syncDao().upsert(SyncMetaEntity(kind, id, hash(l), j.getString("server_updated_at")))
                SyncAction.PUSH -> Unit // zmiana na telefonie jest nowsza — wyślemy ją w drugim etapie
            }
        }
        saveMark(kind, rows)
        return n
    }

    private fun updatedAtOf(x: Any): Long = when (x) {
        is CategoryEntity -> x.updatedAt
        is TagEntity -> x.updatedAt
        is CollectionEntity -> x.updatedAt
        is RecipeEntity -> x.updatedAt
        else -> 0
    }

    private suspend fun pullRecipes(): Int {
        val rows = changedRows("recipe")
        val toApply = mutableListOf<JSONObject>()
        for (j in rows) {
            val id = j.getString("id")
            val local = db.recipeDao().getRecipe(id)
            val meta = db.syncDao().get("recipe", id)
            // znacznik usunięcia przepisu, którego nie mamy — nic do zrobienia
            if (local == null && !j.isNull("deleted_at")) continue
            val localHash = local?.let { recipeHash(it) }
            val action = SyncRules.decide(localHash, meta?.baseHash, j.getString("server_updated_at"), meta?.baseServerAt, local?.updatedAt, j.optLong("updated_at"))
            when (action) {
                SyncAction.PULL -> toApply += j
                SyncAction.NONE -> if (localHash != null) db.syncDao().upsert(SyncMetaEntity("recipe", id, localHash, j.getString("server_updated_at")))
                SyncAction.PUSH -> Unit
            }
        }
        // dzieci pobieramy paczkami dla przepisów, które naprawdę zmieniamy
        for (chunk in toApply.chunked(40)) {
            val ids = chunk.joinToString(",") { it.getString("id") }
            val inIds = "recipe_id=in.(${SupabaseClient.enc(ids)})"
            val ingredients = client.select("ingredient", "$inIds&select=*").objects().groupBy { it.getString("recipe_id") }
            val steps = client.select("step", "$inIds&select=*").objects().groupBy { it.getString("recipe_id") }
            val nutrition = client.select("nutrition_value", "$inIds&select=*").objects().groupBy { it.getString("recipe_id") }
            val cats = client.select("recipe_category", "$inIds&select=recipe_id,category_id").objects().groupBy { it.getString("recipe_id") }
            val tags = client.select("recipe_tag", "$inIds&select=recipe_id,tag_id").objects().groupBy { it.getString("recipe_id") }
            val cols = client.select("recipe_collection", "$inIds&select=recipe_id,collection_id").objects().groupBy { it.getString("recipe_id") }
            for (j in chunk) {
                val id = j.getString("id")
                val local = db.recipeDao().getRecipe(id)
                val photoPath = j.optStringOrNull("photo_path")
                val photoMeta = db.syncDao().get("photo", id)
                // zdjęcie: pobieramy, gdy w chmurze jest inne niż to, które już mamy
                var localPhoto = local?.photoUri
                if (photoPath == null) localPhoto = null
                else if (photoMeta?.baseServerAt != photoPath || localPhoto == null || !File(localPhoto).isFile) {
                    val file = photos.newPhotoFile()
                    runCatching { client.download(photoPath, file) }
                        .onSuccess { localPhoto = file.absolutePath }
                        .onFailure { Log.w(TAG, "photo download failed $photoPath", it) }
                }
                if (!j.isNull("deleted_at") && local != null) {
                    // usunięty na innym urządzeniu: oznaczamy jako usunięty, treść zostaje do skasowania po dobie
                    db.recipeDao().upsertRecipe(local.copy(deletedAt = j.optLong("deleted_at"), updatedAt = j.optLong("updated_at")))
                    val applied = db.recipeDao().getRecipe(id)!!
                    db.syncDao().upsert(SyncMetaEntity("recipe", id, recipeHash(applied), j.getString("server_updated_at")))
                    continue
                }
                val recipe = recipeFrom(j, local, localPhoto)
                val existingCats = db.categoryDao().all().map { it.id }.toSet()
                val existingTags = db.tagDao().all().map { it.id }.toSet()
                val existingCols = db.collectionDao().all().map { it.id }.toSet()
                db.withTransaction {
                    val dao = db.recipeDao()
                    dao.upsertRecipe(recipe)
                    dao.deleteIngredients(id); dao.upsertIngredients(ingredients[id].orEmpty().map(::ingredientFrom))
                    dao.deleteSteps(id); dao.upsertSteps(steps[id].orEmpty().map(::stepFrom))
                    dao.deleteNutrition(id); dao.upsertNutrition(nutrition[id].orEmpty().map(::nutritionFrom))
                    dao.clearCategories(id)
                    dao.addCategories(cats[id].orEmpty().map { it.getString("category_id") }.filter { it in existingCats }.map { RecipeCategoryCrossRef(id, it) })
                    dao.clearTags(id)
                    dao.addTags(tags[id].orEmpty().map { it.getString("tag_id") }.filter { it in existingTags }.map { RecipeTagCrossRef(id, it) })
                    dao.clearCollections(id)
                    dao.addCollections(cols[id].orEmpty().map { it.getString("collection_id") }.filter { it in existingCols }.map { RecipeCollectionCrossRef(id, it) })
                }
                val applied = db.recipeDao().getRecipe(id)!!
                db.syncDao().upsert(SyncMetaEntity("recipe", id, recipeHash(applied), j.getString("server_updated_at")))
                localPhoto?.let { db.syncDao().upsert(SyncMetaEntity("photo", id, it, photoPath)) }
            }
        }
        saveMark("recipe", rows)
        return toApply.size
    }

    // ================================================================ wysyłanie

    private suspend fun <T> pushSimple(
        kind: String, items: List<T>, idOf: (T) -> String, hash: (T) -> String, json: (T) -> JSONObject,
    ): Int {
        val changed = items.filter { db.syncDao().get(kind, idOf(it))?.baseHash != hash(it) }
        if (changed.isEmpty()) return 0
        for (chunk in changed.chunked(200)) {
            val saved = client.upsert(kind, JSONArray(chunk.map(json))).objects().associateBy { it.getString("id") }
            for (item in chunk) {
                val id = idOf(item)
                db.syncDao().upsert(SyncMetaEntity(kind, id, hash(item), saved[id]?.optStringOrNull("server_updated_at")))
            }
        }
        return changed.size
    }

    private suspend fun pushRecipes(uid: String): Int {
        var n = 0
        for (r in db.recipeDao().allRecipes()) {
            val h = recipeHash(r)
            if (db.syncDao().get("recipe", r.id)?.baseHash == h) continue

            // Usunięty przepis: w chmurze zostaje tylko pusty znacznik usunięcia (bez treści i zdjęcia),
            // żeby inne urządzenia też go usunęły. Treść zostaje na telefonie przez dobę (na wypadek „Cofnij”).
            if (r.deletedAt != null) {
                val rid = SupabaseClient.enc(r.id)
                for (t in listOf("ingredient", "step", "nutrition_value", "recipe_category", "recipe_tag", "recipe_collection")) {
                    client.delete(t, "recipe_id=eq.$rid")
                }
                db.syncDao().get("photo", r.id)?.baseServerAt?.let { old -> runCatching { client.deletePhoto(old) } }
                val saved = client.upsert("recipe", JSONArray().put(tombstoneJson(r))).objects().firstOrNull()
                db.syncDao().upsert(SyncMetaEntity("recipe", r.id, h, saved?.optStringOrNull("server_updated_at")))
                db.syncDao().upsert(SyncMetaEntity("photo", r.id, "", null))
                n++
                continue
            }

            // zdjęcie: wysyłamy, gdy na telefonie jest inne niż ostatnio wysłane
            val photoMeta = db.syncDao().get("photo", r.id)
            var photoPath: String? = null
            val photoFile = r.photoUri?.let(::File)?.takeIf { it.isFile }
            if (photoFile != null) {
                if (photoMeta != null && photoMeta.baseHash == r.photoUri && photoMeta.baseServerAt != null) photoPath = photoMeta.baseServerAt
                else {
                    val path = "$uid/${r.id}-${Ids.new().take(8)}.jpg"
                    client.upload(path, photoFile)
                    photoPath = path
                    // poprzednia wersja zdjęcia nie jest już potrzebna
                    photoMeta?.baseServerAt?.takeIf { it != path }?.let { old -> runCatching { client.deletePhoto(old) } }
                    db.syncDao().upsert(SyncMetaEntity("photo", r.id, r.photoUri!!, path))
                }
            }

            val saved = client.upsert("recipe", JSONArray().put(recipeJson(r, photoPath))).objects().firstOrNull()
            val rid = SupabaseClient.enc(r.id)
            // dzieci: zastępujemy cały zestaw (prosto i zawsze zgodnie z telefonem)
            client.delete("ingredient", "recipe_id=eq.$rid")
            client.upsert("ingredient", JSONArray(db.recipeDao().ingredientsFor(r.id).map(::ingredientJson)))
            client.delete("step", "recipe_id=eq.$rid")
            client.upsert("step", JSONArray(db.recipeDao().stepsFor(r.id).map(::stepJson)))
            client.delete("nutrition_value", "recipe_id=eq.$rid")
            client.upsert("nutrition_value", JSONArray(db.recipeDao().nutritionFor(r.id).map(::nutritionJson)))
            client.delete("recipe_category", "recipe_id=eq.$rid")
            client.upsert("recipe_category", JSONArray(db.recipeDao().categoryIdsFor(r.id).map { JSONObject().put("recipe_id", r.id).put("category_id", it) }), "recipe_id,category_id")
            client.delete("recipe_tag", "recipe_id=eq.$rid")
            client.upsert("recipe_tag", JSONArray(db.recipeDao().tagIdsFor(r.id).map { JSONObject().put("recipe_id", r.id).put("tag_id", it) }), "recipe_id,tag_id")
            client.delete("recipe_collection", "recipe_id=eq.$rid")
            client.upsert("recipe_collection", JSONArray(db.recipeDao().collectionIdsFor(r.id).map { JSONObject().put("recipe_id", r.id).put("collection_id", it) }), "recipe_id,collection_id")

            db.syncDao().upsert(SyncMetaEntity("recipe", r.id, h, saved?.optStringOrNull("server_updated_at")))
            n++
        }
        return n
    }

    /**
     * Ostateczne skasowanie na telefonie przepisów usuniętych ponad dobę temu, których usunięcie już jest w chmurze
     * (albo synchronizacja jest wyłączona). Kasujemy też pliki zdjęć.
     */
    suspend fun purgeDeleted(olderThanMs: Long = 24 * 60 * 60 * 1000L) {
        val old = db.recipeDao().deletedBefore(System.currentTimeMillis() - olderThanMs)
        val ready = old.filter { r -> !enabled || db.syncDao().get("recipe", r.id)?.baseHash == recipeHash(r) }
        if (ready.isEmpty()) return
        ready.forEach { r -> photos.delete(r.photoUri); photos.delete(r.materialUri) }
        db.recipeDao().hardDelete(ready.map { it.id })
    }

    // ================================================================ skróty treści

    private suspend fun recipeHash(r: RecipeEntity): String {
        val dao = db.recipeDao()
        if (r.deletedAt != null) return SyncRules.hash(listOf("deleted-v2", r.deletedAt, r.updatedAt))
        return SyncRules.hash(
            listOf(
                r.status, r.processing, r.name, r.servings, r.yieldText, r.prepTime, r.cookTime, r.notes, r.photoUri,
                r.sourceType, r.sourceUrl, r.sourceAuthor, r.materialKind, r.materialText, r.materialNote,
                r.nutritionOrigin, r.nutritionBasis, r.allergensOrigin, r.allergensText, r.favorite,
                r.createdAt, r.updatedAt, r.deletedAt,
            ) +
                dao.ingredientsFor(r.id).flatMap { listOf(it.id, it.position, it.groupName, it.rawText, it.name, it.quantity, it.quantityMax, it.unit, it.noQuantity, it.unitInferred) } +
                listOf("|steps") + dao.stepsFor(r.id).flatMap { listOf(it.id, it.position, it.text) } +
                listOf("|nutrition") + dao.nutritionFor(r.id).flatMap { listOf(it.id, it.position, it.label, it.valueText) } +
                listOf("|cats") + dao.categoryIdsFor(r.id) + listOf("|tags") + dao.tagIdsFor(r.id) +
                listOf("|cols") + dao.collectionIdsFor(r.id),
        )
    }

    private fun categoryHash(c: CategoryEntity) = SyncRules.hash(listOf(c.name, c.isDefault, c.position, c.createdAt, c.updatedAt, c.deletedAt))
    private fun tagHash(t: TagEntity) = SyncRules.hash(listOf(t.name, t.createdAt, t.updatedAt, t.deletedAt))
    private fun collectionHash(c: CollectionEntity) = SyncRules.hash(listOf(c.name, c.createdAt, c.updatedAt, c.deletedAt))

    // ================================================================ mapowanie Room ↔ JSON (snake_case w bazie)

    private val owner get() = auth.currentUser.value.id

    private fun recipeJson(r: RecipeEntity, photoPath: String?) = JSONObject()
        .put("id", r.id).put("status", r.status.name).put("processing", r.processing.name).put("name", r.name)
        .putOpt("servings", r.servings).putOpt("yield_text", r.yieldText).putOpt("prep_time", r.prepTime).putOpt("cook_time", r.cookTime)
        .putOpt("notes", r.notes).put("photo_path", photoPath ?: JSONObject.NULL)
        .put("source_type", r.sourceType.name).putOpt("source_url", r.sourceUrl).putOpt("source_author", r.sourceAuthor)
        .put("material_kind", r.materialKind.name).putOpt("material_text", r.materialText).putOpt("material_note", r.materialNote)
        .put("nutrition_origin", r.nutritionOrigin.name).putOpt("nutrition_basis", r.nutritionBasis)
        .put("allergens_origin", r.allergensOrigin.name).putOpt("allergens_text", r.allergensText)
        .put("favorite", r.favorite).put("created_at", r.createdAt).put("updated_at", r.updatedAt)
        .put("deleted_at", r.deletedAt ?: JSONObject.NULL)
        .nullsForMissing("servings", "yield_text", "prep_time", "cook_time", "notes", "source_url", "source_author", "material_text", "material_note", "nutrition_basis", "allergens_text")

    private fun recipeFrom(j: JSONObject, local: RecipeEntity?, photoUri: String?) = RecipeEntity(
        id = j.getString("id"),
        ownerId = local?.ownerId ?: owner,
        status = enumOr(j.optString("status"), RecipeStatus.DRAFT),
        processing = enumOr(j.optString("processing"), ProcessingState.NONE),
        name = j.optString("name"),
        servings = if (j.isNull("servings")) null else j.optInt("servings"),
        yieldText = j.optStringOrNull("yield_text"),
        prepTime = j.optStringOrNull("prep_time"),
        cookTime = j.optStringOrNull("cook_time"),
        notes = j.optStringOrNull("notes"),
        photoUri = photoUri,
        sourceType = enumOr(j.optString("source_type"), SourceType.MANUAL),
        sourceUrl = j.optStringOrNull("source_url"),
        sourceAuthor = j.optStringOrNull("source_author"),
        materialKind = enumOr(j.optString("material_kind"), MaterialKind.NONE),
        materialText = j.optStringOrNull("material_text"),
        materialUri = local?.materialUri,
        materialNote = j.optStringOrNull("material_note"),
        nutritionOrigin = enumOr(j.optString("nutrition_origin"), InfoOrigin.NONE),
        nutritionBasis = j.optStringOrNull("nutrition_basis"),
        allergensOrigin = enumOr(j.optString("allergens_origin"), InfoOrigin.NONE),
        allergensText = j.optStringOrNull("allergens_text"),
        createdAt = j.optLong("created_at"),
        updatedAt = j.optLong("updated_at"),
        deletedAt = if (j.isNull("deleted_at")) null else j.optLong("deleted_at"),
        syncState = SyncState.SYNCED,
        favorite = j.optBoolean("favorite", false),
    )

    /** Znacznik usunięcia: tylko to, co potrzebne do usunięcia przepisu na innych urządzeniach. */
    private fun tombstoneJson(r: RecipeEntity) = recipeJson(
        r.copy(
            name = "", servings = null, yieldText = null, prepTime = null, cookTime = null, notes = null,
            sourceUrl = null, sourceAuthor = null, materialText = null, materialNote = null,
            nutritionBasis = null, allergensText = null, favorite = false,
        ),
        photoPath = null,
    )

    private fun ingredientJson(i: IngredientEntity) = JSONObject()
        .put("id", i.id).put("recipe_id", i.recipeId).put("position", i.position).put("group_name", i.groupName ?: JSONObject.NULL)
        .put("raw_text", i.rawText).put("name", i.name)
        .put("quantity", i.quantity ?: JSONObject.NULL).put("quantity_max", i.quantityMax ?: JSONObject.NULL)
        .put("unit", i.unit ?: JSONObject.NULL).put("no_quantity", i.noQuantity).put("unit_inferred", i.unitInferred)

    private fun ingredientFrom(j: JSONObject) = IngredientEntity(
        id = j.getString("id"), recipeId = j.getString("recipe_id"), position = j.optInt("position"),
        groupName = j.optStringOrNull("group_name"), rawText = j.optString("raw_text"), name = j.optString("name"),
        quantity = if (j.isNull("quantity")) null else j.optDouble("quantity"),
        unit = j.optStringOrNull("unit"), noQuantity = j.optBoolean("no_quantity"), unitInferred = j.optBoolean("unit_inferred"),
        quantityMax = if (j.isNull("quantity_max")) null else j.optDouble("quantity_max"),
    )

    private fun stepJson(s: StepEntity) = JSONObject().put("id", s.id).put("recipe_id", s.recipeId).put("position", s.position).put("text", s.text)
    private fun stepFrom(j: JSONObject) = StepEntity(j.getString("id"), j.getString("recipe_id"), j.optInt("position"), j.optString("text"))

    private fun nutritionJson(n: NutritionValueEntity) = JSONObject()
        .put("id", n.id).put("recipe_id", n.recipeId).put("position", n.position).put("label", n.label).put("value_text", n.valueText)
    private fun nutritionFrom(j: JSONObject) = NutritionValueEntity(j.getString("id"), j.getString("recipe_id"), j.optInt("position"), j.optString("label"), j.optString("value_text"))

    private fun categoryJson(c: CategoryEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("is_default", c.isDefault).put("position", c.position)
        .put("created_at", c.createdAt).put("updated_at", c.updatedAt).put("deleted_at", c.deletedAt ?: JSONObject.NULL)
    private fun categoryFrom(j: JSONObject) = CategoryEntity(
        id = j.getString("id"), ownerId = owner, name = j.optString("name"), isDefault = j.optBoolean("is_default"),
        position = j.optInt("position"), createdAt = j.optLong("created_at"), updatedAt = j.optLong("updated_at"),
        deletedAt = if (j.isNull("deleted_at")) null else j.optLong("deleted_at"), syncState = SyncState.SYNCED,
    )

    private fun tagJson(t: TagEntity) = JSONObject()
        .put("id", t.id).put("name", t.name).put("created_at", t.createdAt).put("updated_at", t.updatedAt).put("deleted_at", t.deletedAt ?: JSONObject.NULL)
    private fun tagFrom(j: JSONObject) = TagEntity(
        id = j.getString("id"), ownerId = owner, name = j.optString("name"), createdAt = j.optLong("created_at"),
        updatedAt = j.optLong("updated_at"), deletedAt = if (j.isNull("deleted_at")) null else j.optLong("deleted_at"), syncState = SyncState.SYNCED,
    )

    private fun collectionJson(c: CollectionEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("created_at", c.createdAt).put("updated_at", c.updatedAt).put("deleted_at", c.deletedAt ?: JSONObject.NULL)
    private fun collectionFrom(j: JSONObject) = CollectionEntity(
        id = j.getString("id"), ownerId = owner, name = j.optString("name"), createdAt = j.optLong("created_at"),
        updatedAt = j.optLong("updated_at"), deletedAt = if (j.isNull("deleted_at")) null else j.optLong("deleted_at"), syncState = SyncState.SYNCED,
    )

    private companion object {
        const val TAG = "SyncEngine"
    }
}

// ---------- drobne pomocniki JSON

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

/** Pola z wartością null wysyłamy jawnie (inaczej upsert zostawiłby starą wartość w chmurze). */
private fun JSONObject.nullsForMissing(vararg keys: String): JSONObject {
    keys.forEach { if (!has(it)) put(it, JSONObject.NULL) }
    return this
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
