package io.github.rakasans.foodapp.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Treść przekazana systemowym „Udostępnij” (tekst/link albo obraz). */
data class SharedContent(
    val text: String? = null,
    val imageUri: Uri? = null,
    /** Unikalny znacznik udostępnienia — dwa udostępnienia tego samego linku to dwa osobne zdarzenia. */
    val token: String = System.nanoTime().toString(),
)

/** Skrzynka na treść z „Udostępnij”: aktywność ją wypełnia, nawigacja odbiera i kieruje do importu. */
class ShareInbox {
    private val _pending = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()

    fun receive(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        _pending.value = when {
            type.startsWith("image/") -> SharedContent(imageUri = intent.streamUri())
            else -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { SharedContent(text = it) }
        }
    }

    /** Odbiera treść jednorazowo. */
    fun take(): SharedContent? = _pending.value.also { _pending.value = null }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)
}
