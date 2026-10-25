package io.github.rakasans.foodapp.data.web

import android.util.Log
import io.github.rakasans.foodapp.core.Connectivity
import io.github.rakasans.foodapp.data.local.ProcessingState
import io.github.rakasans.foodapp.data.repository.PhotoStore
import io.github.rakasans.foodapp.data.repository.RecipeRepository
import io.github.rakasans.foodapp.domain.WebRecipe
import io.github.rakasans.foodapp.domain.WebRecipePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Import przepisu ze strony (v0.3, decyzja 3): telefon pobiera stronę i czyta dane strukturalne
 * schema.org/Recipe. Bez internetu link czeka i pobiera się po połączeniu. Strona bez danych
 * strukturalnych → link zostaje zapisany, użytkownik może wpisać przepis ręcznie (AI — później).
 */
class WebImporter(
    private val recipes: RecipeRepository,
    private val photos: PhotoStore,
    private val connectivity: Connectivity,
    private val debugDir: java.io.File? = null,
) {
    sealed interface Result {
        val id: String
        data class Draft(override val id: String) : Result
        data class Waiting(override val id: String) : Result
        data class Failed(override val id: String, val reason: String) : Result
    }

    private val mutex = Mutex()

    suspend fun import(url: String, note: String? = null): Result {
        val online = connectivity.isOnline()
        val id = recipes.saveLinkMaterial(
            url, note, instagram = false,
            processing = if (online) ProcessingState.RUNNING else ProcessingState.WAITING_NETWORK,
        )
        return if (online) process(id) else Result.Waiting(id)
    }

    suspend fun process(id: String): Result = mutex.withLock {
        val recipe = recipes.getRecipe(id) ?: return Result.Failed(id, "Nie znaleziono zapisanego linku.")
        val url = recipe.sourceUrl ?: return Result.Failed(id, "Brak adresu strony.")
        if (!connectivity.isOnline()) {
            recipes.setProcessing(id, ProcessingState.WAITING_NETWORK)
            return Result.Waiting(id)
        }
        recipes.setProcessing(id, ProcessingState.RUNNING)
        val html = get(url)
        if (html == null) {
            recipes.setProcessing(id, ProcessingState.ERROR)
            return Result.Failed(id, "Nie udało się pobrać strony.")
        }
        saveDebug("web_last.html", html)
        val parsed: WebRecipe? = runCatching { WebRecipePage.parse(html) }.getOrNull()
        Log.d(
            TAG,
            "url=$url length=${html.length} ldjson=${html.contains("application/ld+json")} recipe=${parsed != null} " +
                "fromText=${parsed?.pageText != null} ingredients=${parsed?.ingredients?.size} steps=${parsed?.steps?.size}",
        )
        if (parsed == null) {
            recipes.setProcessing(id, ProcessingState.ERROR)
            return Result.Failed(id, "Ta strona nie udostępnia przepisu w formie, którą umiemy odczytać.")
        }
        val photo = parsed.imageUrl?.let { download(it) }
        recipes.createDraftFromWeb(id, parsed, photo)
        Result.Draft(id)
    }

    suspend fun retryWaiting() {
        if (!connectivity.isOnline()) return
        recipes.waitingWebIds().forEach { process(it) }
    }

    /** Niektóre serwisy odrzucają przeglądarkę mobilną albo desktopową — próbujemy obu. */
    private suspend fun get(url: String): String? {
        for (agent in listOf(UA, UA_DESKTOP)) get(url, agent)?.let { return it }
        return null
    }

    private suspend fun get(url: String, agent: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", agent)
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            c.setRequestProperty("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
            try {
                if (c.responseCode != 200) {
                    Log.d(TAG, "HTTP ${c.responseCode} for $url (ua=${agent.take(30)})")
                    return@runCatching null
                }
                val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(c.contentType.orEmpty())
                    ?.groupValues?.get(1)?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
                c.inputStream.bufferedReader(charset).use { it.readText() }
            } finally {
                c.disconnect()
            }
        }.onFailure { Log.d(TAG, "fetch failed: $it") }.getOrNull()
    }

    private suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = photos.newPhotoFile()
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            try {
                if (c.responseCode != 200) return@runCatching null
                c.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            } finally {
                c.disconnect()
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun saveDebug(name: String, content: String) {
        val dir = debugDir ?: return
        runCatching { dir.mkdirs(); java.io.File(dir, name).writeText(content) }
    }

    private companion object {
        const val TAG = "WebImporter"
        const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
