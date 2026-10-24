package io.github.rakasans.foodapp

import android.app.Application
import android.content.Context
import io.github.rakasans.foodapp.auth.AuthProvider
import io.github.rakasans.foodapp.auth.DummyAuthProvider
import io.github.rakasans.foodapp.core.Clock
import io.github.rakasans.foodapp.core.SystemClock
import io.github.rakasans.foodapp.data.local.FoodDatabase
import io.github.rakasans.foodapp.data.repository.CategoryRepository
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
}

class FoodApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.categories.ensureDefaults() }
    }
}
