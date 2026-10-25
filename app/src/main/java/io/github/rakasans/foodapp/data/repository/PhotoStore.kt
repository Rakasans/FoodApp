package io.github.rakasans.foodapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import io.github.rakasans.foodapp.core.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Zdjęcia przepisów w prywatnej pamięci aplikacji (działa offline, bez uprawnień do galerii —
 * wybór przez systemowy Photo Picker). Docelowo synchronizowane do Supabase Storage.
 */
class PhotoStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** Kopiuje wybrane zdjęcie do pamięci aplikacji; zwraca ścieżkę albo null przy błędzie. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "${Ids.new()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            target.absolutePath
        }.getOrNull()
    }

    /** Nowy plik na zdjęcie z aparatu (udostępniany aparatowi przez FileProvider). */
    fun newPhotoFile(): File = File(dir, "${Ids.new()}.jpg")

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path != null && path.startsWith(dir.absolutePath)) File(path).delete()
    }

    /**
     * Usuwa pliki zdjęć, do których nie odwołuje się żaden przepis (także usunięty — można go jeszcze przywrócić).
     * Pomija pliki młodsze niż [minAgeMs] — mogą należeć do importu, który właśnie trwa.
     * Zwraca liczbę usuniętych plików.
     */
    suspend fun cleanup(referenced: Set<String>, minAgeMs: Long = 24 * 60 * 60 * 1000L): Int = withContext(Dispatchers.IO) {
        val keep = referenced.map { it.removePrefix("file://") }.toSet()
        val now = System.currentTimeMillis()
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.absolutePath !in keep && now - it.lastModified() > minAgeMs }
            .count { it.delete() }
    }

    /** Wczytuje pomniejszony podgląd z uwzględnieniem obrotu z EXIF. */
    suspend fun loadPreview(path: String, maxSizePx: Int = 1280): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSizePx || bounds.outHeight / (sample * 2) >= maxSizePx) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) bitmap
            else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        }.getOrNull()
    }
}
