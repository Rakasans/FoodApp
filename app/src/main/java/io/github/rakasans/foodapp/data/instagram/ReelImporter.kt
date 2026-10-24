package io.github.rakasans.foodapp.data.instagram

import android.util.Log
import io.github.rakasans.foodapp.core.Connectivity
import io.github.rakasans.foodapp.data.local.ProcessingState
import io.github.rakasans.foodapp.data.repository.PhotoStore
import io.github.rakasans.foodapp.data.repository.RecipeRepository
import io.github.rakasans.foodapp.domain.InstagramPage
import io.github.rakasans.foodapp.domain.InstagramPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Import rolki z samego linku (decyzja z 24.10): telefon pobiera publiczną stronę rolki, odczytuje opis,
 * miniaturę i autora, a z opisu powstaje szkic. Bez internetu rolka czeka i pobiera się po połączeniu.
 * Uwaga: to nieoficjalny odczyt strony — Instagram może go zablokować; wtedy zostaje zrzut ekranu.
 */
class ReelImporter(
    private val recipes: RecipeRepository,
    private val photos: PhotoStore,
    private val connectivity: Connectivity,
) {
    sealed interface Result {
        val id: String
        data class Draft(override val id: String) : Result
        data class Waiting(override val id: String) : Result
        data class Failed(override val id: String, val reason: String) : Result
    }

    private val mutex = Mutex()

    /** Zapisuje rolkę i od razu próbuje pobrać opis (gdy jest internet). */
    suspend fun import(url: String, note: String? = null): Result {
        val online = connectivity.isOnline()
        val id = recipes.saveLinkMaterial(
            url, note, instagram = true,
            processing = if (online) ProcessingState.RUNNING else ProcessingState.WAITING_NETWORK,
        )
        return if (online) process(id) else Result.Waiting(id)
    }

    /** Pobiera opis dla zapisanej rolki (ponowienie, po odzyskaniu sieci albo z ekranu materiału). */
    suspend fun process(id: String): Result = mutex.withLock {
        val recipe = recipes.getRecipe(id) ?: return Result.Failed(id, "Nie znaleziono rolki.")
        val url = recipe.sourceUrl ?: return Result.Failed(id, "Brak adresu rolki.")
        if (!connectivity.isOnline()) {
            recipes.setProcessing(id, ProcessingState.WAITING_NETWORK)
            return Result.Waiting(id)
        }
        recipes.setProcessing(id, ProcessingState.RUNNING)
        val post = fetch(url)
        if (post?.caption == null) {
            recipes.setProcessing(id, ProcessingState.NEEDS_SCREENSHOT)
            return Result.Failed(
                id,
                if (post == null) "Nie udało się pobrać strony rolki." else "Instagram nie udostępnił opisu tej rolki.",
            )
        }
        val photo = post.imageUrl?.let { download(it) }
        recipes.createDraftFromReel(id, post, photo)
        Result.Draft(id)
    }

    /** Rolki zapisane bez internetu — wywoływane po pojawieniu się sieci. */
    suspend fun retryWaiting() {
        if (!connectivity.isOnline()) return
        recipes.waitingReelIds().forEach { process(it) }
    }

    private suspend fun fetch(url: String): InstagramPost? = withContext(Dispatchers.IO) {
        val code = InstagramPage.shortcode(url) ?: return@withContext null
        var lastParsed: InstagramPost? = null
        for (page in listOf(InstagramPage.embedUrl(code), InstagramPage.pageUrl(code))) {
            val html = get(page) ?: continue
            val post = InstagramPage.parse(html)
            Log.d(TAG, "page=$page length=${html.length} caption=${post.caption != null} image=${post.imageUrl != null}")
            if (post.caption != null) return@withContext post
            lastParsed = post
        }
        lastParsed
    }

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
        try {
            if (c.responseCode != 200) {
                Log.d(TAG, "HTTP ${c.responseCode} for $url")
                null
            } else c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.onFailure { Log.d(TAG, "fetch failed: $it") }.getOrNull()

    private suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = photos.newPhotoFile()
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (c.responseCode != 200) return@runCatching null
                c.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            } finally {
                c.disconnect()
            }
            file.absolutePath
        }.getOrNull()
    }

    private companion object {
        const val TAG = "ReelImporter"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
