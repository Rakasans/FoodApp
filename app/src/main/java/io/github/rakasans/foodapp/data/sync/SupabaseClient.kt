package io.github.rakasans.foodapp.data.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimalny klient Supabase (Auth, PostgREST, Storage) na HttpURLConnection — bez dodatkowych bibliotek.
 * Sesja (tokeny) w prywatnych ustawieniach aplikacji. Klucz to „publishable” — dane chronią reguły RLS w bazie.
 */
class SupabaseClient(context: Context, private val baseUrl: String, private val apiKey: String) {

    data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String, val anonymous: Boolean)

    class HttpError(val code: Int, body: String) : IOException("HTTP $code: ${body.take(300)}")

    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val authLock = Mutex()

    val userId: String? get() = prefs.getString("user_id", null)
    val isAnonymous: Boolean get() = prefs.getBoolean("anonymous", true)

    private fun stored(): Session? {
        val access = prefs.getString("access", null) ?: return null
        return Session(
            access, prefs.getString("refresh", "").orEmpty(), prefs.getLong("expires_at", 0),
            prefs.getString("user_id", "").orEmpty(), prefs.getBoolean("anonymous", true),
        )
    }

    private fun save(json: JSONObject): Session {
        val user = json.getJSONObject("user")
        val s = Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)),
            userId = user.getString("id"),
            anonymous = user.optBoolean("is_anonymous", false),
        )
        prefs.edit()
            .putString("access", s.accessToken).putString("refresh", s.refreshToken).putLong("expires_at", s.expiresAt)
            .putString("user_id", s.userId).putBoolean("anonymous", s.anonymous)
            .apply()
        return s
    }

    /** Ważna sesja: odświeżona w razie potrzeby; bez sesji — anonimowe konto (do podpięcia Google później). */
    suspend fun session(): Session = authLock.withLock {
        val s = stored()
        val now = System.currentTimeMillis() / 1000
        when {
            s != null && s.expiresAt - 60 > now -> s
            s != null && s.refreshToken.isNotEmpty() -> try {
                save(JSONObject(raw("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", s.refreshToken).toString(), auth = false)))
            } catch (e: HttpError) {
                // token odświeżania unieważniony (np. konto usunięte w panelu) — nie zakładamy po cichu nowego konta,
                // bo dane z chmury przestałyby być widoczne; użytkownik decyduje w „Koncie”
                if (e.code in 400..499) throw SessionLost() else throw e
            }
            else -> save(JSONObject(raw("POST", "/auth/v1/signup", JSONObject().put("data", JSONObject().put("app", "przepisarnia")).toString(), auth = false)))
        }
    }

    /** Sesja w chmurze utracona (np. konto usunięte) — wymaga decyzji użytkownika. */
    class SessionLost : IOException("Sesja w chmurze wygasła albo konto zostało usunięte.")

    /** Zapomina sesję (np. „Rozłącz z chmurą”) — przepisy na telefonie zostają. */
    fun forget() = prefs.edit().clear().apply()

    // ---------- PostgREST

    suspend fun select(table: String, query: String): JSONArray = JSONArray(raw("GET", "/rest/v1/$table?$query", null))

    /** Upsert wielu wierszy; zwraca zapisane wiersze (z server_updated_at). */
    suspend fun upsert(table: String, rows: JSONArray, onConflict: String? = null): JSONArray {
        if (rows.length() == 0) return JSONArray()
        val q = onConflict?.let { "?on_conflict=${enc(it)}" }.orEmpty()
        return JSONArray(raw("POST", "/rest/v1/$table$q", rows.toString(), prefer = "resolution=merge-duplicates,return=representation"))
    }

    suspend fun delete(table: String, query: String) {
        raw("DELETE", "/rest/v1/$table?$query", null, prefer = "return=minimal")
    }

    // ---------- Storage

    suspend fun upload(path: String, file: File, contentType: String = "image/jpeg") = withContext(Dispatchers.IO) {
        request("POST", "/storage/v1/object/photos/$path", contentType, file.readBytes(), extra = mapOf("x-upsert" to "true"))
        Unit
    }

    suspend fun deletePhoto(path: String) {
        request("DELETE", "/storage/v1/object/photos/$path", null, null)
    }

    suspend fun download(path: String, target: File) = withContext(Dispatchers.IO) {
        val bytes = request("GET", "/storage/v1/object/authenticated/photos/$path", null, null, binary = true)
        target.writeBytes(bytes)
    }

    // ---------- HTTP

    private suspend fun raw(method: String, path: String, body: String?, prefer: String? = null, auth: Boolean = true): String =
        String(request(method, path, if (body != null) "application/json" else null, body?.toByteArray(Charsets.UTF_8), prefer = prefer, auth = auth), Charsets.UTF_8)

    private suspend fun request(
        method: String, path: String, contentType: String?, body: ByteArray?,
        prefer: String? = null, auth: Boolean = true, extra: Map<String, String> = emptyMap(), binary: Boolean = false,
        retried: Boolean = false,
    ): ByteArray {
        val token = if (auth) session().accessToken else null
        val result = withContext(Dispatchers.IO) {
            val c = URL(baseUrl + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("apikey", apiKey)
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { c.setRequestProperty("Prefer", it) }
            extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (!binary) c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                c.outputStream.use { it.write(body) }
            }
            try {
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                code to bytes
            } finally {
                c.disconnect()
            }
        }
        val (code, bytes) = result
        if (code == 401 && auth && !retried) {
            // token wygasł między sprawdzeniem a zapytaniem — odśwież i spróbuj raz jeszcze
            prefs.edit().putLong("expires_at", 0).apply()
            return request(method, path, contentType, body, prefer, auth, extra, binary, retried = true)
        }
        if (code !in 200..299) throw HttpError(code, String(bytes, Charsets.UTF_8))
        return bytes
    }

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
