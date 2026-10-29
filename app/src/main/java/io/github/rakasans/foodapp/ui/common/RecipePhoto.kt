package io.github.rakasans.foodapp.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.data.repository.PhotoStore

/**
 * Zdjęcie przepisu z pamięci aplikacji (podgląd ładowany w tle).
 * Wyświetlane w swoich proporcjach na pełną szerokość — bez kadrowania. Tylko skrajnie wąskie lub
 * szerokie zdjęcia są lekko przycięte (proporcje ograniczone do 2:3 … 2:1), żeby nie zajęły całego ekranu.
 */
@Composable
fun RecipePhoto(path: String, photos: PhotoStore, contentDescription: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = photos.loadPreview(path)?.asImageBitmap()
    }
    val shape = RoundedCornerShape(16.dp)
    val current = bitmap
    if (current != null) {
        val ratio = (current.width.toFloat() / current.height.coerceAtLeast(1)).coerceIn(2f / 3f, 2f)
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(ratio).clip(shape),
        )
    } else {
        Box(modifier.aspectRatio(4f / 3f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("Wczytywanie zdjęcia…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Kwadratowa miniatura na liście przepisów: zdjęcie przycięte do kwadratu (mały podgląd, szybkie ładowanie).
 * Bez zdjęcia — spokojne pole z pierwszą literą nazwy.
 */
@Composable
fun RecipeThumbnail(path: String?, photos: PhotoStore, name: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let { photos.loadPreview(it, maxSizePx = 256)?.asImageBitmap() }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
