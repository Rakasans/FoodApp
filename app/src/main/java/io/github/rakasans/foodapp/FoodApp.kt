package io.github.rakasans.foodapp

import android.app.Application
import android.content.Context
import io.github.rakasans.foodapp.auth.AuthProvider
import io.github.rakasans.foodapp.auth.DummyAuthProvider
import io.github.rakasans.foodapp.core.Clock
import io.github.rakasans.foodapp.core.SystemClock
import io.github.rakasans.foodapp.data.local.FoodDatabase
import io.github.rakasans.foodapp.data.repository.CategoryRepository
import io.github.rakasans.foodapp.data.repository.PhotoStore
import io.github.rakasans.foodapp.data.ocr.OcrEngine
import io.github.rakasans.foodapp.share.ShareInbox
import io.github.rakasans.foodapp.core.Connectivity
import io.github.rakasans.foodapp.data.instagram.ReelImporter
import io.github.rakasans.foodapp.data.web.WebImporter
import io.github.rakasans.foodapp.data.repository.ShoppingRepository
import io.github.rakasans.foodapp.data.repository.CollectionRepository
import io.github.rakasans.foodapp.data.backup.BackupManager
import io.github.rakasans.foodapp.data.repository.RecipeRepository
import io.github.rakasans.foodapp.data.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Ręczne DI — prostsze niż Hilt przy obecnej skali (patrz docs/ARCHITECTURE.md). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = SystemClock
    val auth: AuthProvider = DummyAuthProvider(context)
    val db: FoodDatabase = FoodDatabase.create(context)
    val categories = CategoryRepository(db, auth, clock)
    val tags = TagRepository(db, auth, clock)
    val recipes = RecipeRepository(db, auth, clock, categories, tags)
    val photos = PhotoStore(context)
    val shopping = ShoppingRepository(db, auth, clock)
    val collections = CollectionRepository(db, auth, clock)
    val backup = BackupManager(db, auth, clock, photos, categories, tags, collections)
    val ocr = OcrEngine(context)
    val shareInbox = ShareInbox()
    val connectivity = Connectivity(context)
    val reels = ReelImporter(
        recipes, photos, connectivity,
        debugDir = if (BuildConfig.DEBUG) context.getExternalFilesDir("debug") else null,
    )
    val web = WebImporter(
        recipes, photos, connectivity,
        debugDir = if (BuildConfig.DEBUG) context.getExternalFilesDir("debug") else null,
    )
}

class FoodApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.categories.ensureDefaults() }
        // zdjęcia po podmianie albo z porzuconych importów — sprzątamy pliki bez przepisu
        container.appScope.launch { container.photos.cleanup(container.recipes.referencedFiles()) }
        // rolki i linki do stron zapisane bez internetu pobierają się, gdy sieć wróci
        val retry = {
            container.appScope.launch { container.reels.retryWaiting() }
            container.appScope.launch { container.web.retryWaiting() }
        }
        retry()
        container.connectivity.onAvailable { retry() }
    }
}
