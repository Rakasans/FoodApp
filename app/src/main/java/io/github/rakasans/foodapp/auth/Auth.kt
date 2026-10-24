package io.github.rakasans.foodapp.auth

import android.content.Context
import io.github.rakasans.foodapp.core.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserAccount(
    val id: String,
    val displayName: String,
    val isDummy: Boolean,
)

/**
 * Warstwa logowania. W v0.1 jedyną implementacją jest [DummyAuthProvider].
 * Docelowo: Supabase Auth (Google, Facebook, e-mail) — decyzje 2 i 7.
 */
interface AuthProvider {
    val currentUser: StateFlow<UserAccount>
}

/**
 * Konto-atrapa: właścicielem danych jest stały identyfikator instalacji.
 * Przy pierwszym prawdziwym logowaniu rekordy z tym ownerId zostaną przypisane do konta.
 */
class DummyAuthProvider(context: Context) : AuthProvider {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private val installationId: String =
        prefs.getString(KEY_INSTALLATION_ID, null) ?: Ids.new().also {
            prefs.edit().putString(KEY_INSTALLATION_ID, it).apply()
        }

    private val user = MutableStateFlow(
        UserAccount(id = installationId, displayName = "Właściciel projektu", isDummy = true)
    )

    override val currentUser: StateFlow<UserAccount> = user.asStateFlow()

    private companion object {
        const val KEY_INSTALLATION_ID = "installation_id"
    }
}
