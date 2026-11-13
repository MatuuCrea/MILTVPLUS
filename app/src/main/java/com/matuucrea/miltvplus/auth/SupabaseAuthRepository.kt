package com.matuucrea.miltvplus.auth

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.matuucrea.miltvplus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val expiresAtEpochSeconds: Long
)

class SecureSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("miltvplus_secure_session", Context.MODE_PRIVATE)
    private val keyAlias = "miltvplus_session_aes_v1"

    fun save(session: AuthSession) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("user_id", session.userId)
            .put("email", session.email ?: JSONObject.NULL)
            .put("expires_at", session.expiresAtEpochSeconds)
            .toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString("session", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun read(): AuthSession? = runCatching {
        val encoded = prefs.getString("session", null) ?: throw IllegalStateException("No hay sesión guardada.")
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12)
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            userId = json.getString("user_id"),
            email = json.optString("email").takeIf { it.isNotBlank() && it != "null" },
            expiresAtEpochSeconds = json.getLong("expires_at")
        )
    }.getOrNull()

    fun clear() { prefs.edit().remove("session").apply() }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

class SupabaseAuthRepository(context: Context) {
    private val store = SecureSessionStore(context)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val refreshMutex = Mutex()

    suspend fun signIn(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        require(email.contains("@") && email.length <= 254) { "Ingresá un correo válido." }
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }
        val json = request("POST", "/auth/v1/token?grant_type=password", body = JSONObject().put("email", email.trim()).put("password", password))
        val session = parseSession(json)
        store.save(session)
        session
    }

    suspend fun signUp(email: String, password: String): AuthSession? = withContext(Dispatchers.IO) {
        require(email.contains("@") && email.length <= 254) { "Ingresá un correo válido." }
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }
        val json = request("POST", "/auth/v1/signup?redirect_to=miltvplus%3A%2F%2Fauth%2Fcallback", body = JSONObject().put("email", email.trim()).put("password", password))
        if (json.has("access_token") && json.has("refresh_token")) {
            parseSession(json).also(store::save)
        } else null
    }

    suspend fun sendPasswordRecovery(email: String) = withContext(Dispatchers.IO) {
        require(email.contains("@")) { "Ingresá un correo válido." }
        request("POST", "/auth/v1/recover?redirect_to=miltvplus%3A%2F%2Fauth%2Fcallback", body = JSONObject().put("email", email.trim()))
        Unit
    }

    suspend fun restoreSession(): AuthSession? {
        refreshMutex.lock()
        try {
            return withContext(Dispatchers.IO) {
                val current = store.read() ?: return@withContext null
                if (current.expiresAtEpochSeconds > (System.currentTimeMillis() / 1000L) + 90) return@withContext current
                runCatching {
                    val json = request(
                        "POST",
                        "/auth/v1/token?grant_type=refresh_token",
                        body = JSONObject().put("refresh_token", current.refreshToken)
                    )
                    parseSession(json).also(store::save)
                }.getOrElse {
                    store.clear()
                    null
                }
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    suspend fun acceptCallback(uri: Uri): AuthSession? = withContext(Dispatchers.IO) {
        if (uri.scheme != "miltvplus" || uri.host != "auth") return@withContext null
        val fragment = uri.fragment ?: return@withContext null
        val params = Uri.parse("https://callback.invalid/?$fragment")
        val access = params.getQueryParameter("access_token") ?: return@withContext null
        val refresh = params.getQueryParameter("refresh_token") ?: return@withContext null
        val expiresIn = params.getQueryParameter("expires_in")?.toLongOrNull() ?: 3600L
        val user = request("GET", "/auth/v1/user", accessToken = access)
        val session = AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = user.getString("id"),
            email = user.optString("email").takeIf { it.isNotBlank() && it != "null" },
            expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + expiresIn
        )
        store.save(session)
        session
    }

    suspend fun updatePassword(newPassword: String) = withContext(Dispatchers.IO) {
        require(newPassword.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }
        val session = restoreSession() ?: throw IllegalStateException("La sesión de recuperación no está disponible. Volvé a solicitar el enlace.")
        request("PUT", "/auth/v1/user", accessToken = session.accessToken, body = JSONObject().put("password", newPassword))
        Unit
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val current = store.read()
        try {
            if (current != null) request("POST", "/auth/v1/logout", accessToken = current.accessToken)
        } finally {
            store.clear()
        }
    }

    fun currentSession(): AuthSession? = store.read()

    private fun parseSession(json: JSONObject): AuthSession {
        val user = json.optJSONObject("user") ?: throw IllegalStateException("Supabase no devolvió el usuario. Si acabás de registrarte, verificá tu correo.")
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        require(access.isNotBlank() && refresh.isNotBlank()) {
            "La cuenta fue creada. Revisá tu correo para confirmar la dirección y después iniciá sesión."
        }
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = user.getString("id"),
            email = user.optString("email").takeIf { it.isNotBlank() && it != "null" },
            expiresAtEpochSeconds = json.optLong("expires_at").takeIf { it > 0 }
                ?: ((System.currentTimeMillis() / 1000L) + json.optLong("expires_in", 3600))
        )
    }

    private fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val uri = URI(baseUrl + path)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "La conexión de autenticación debe utilizar HTTPS."
        }
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) doOutput = true
        }
        try {
            body?.let { connection.outputStream.use { output -> output.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(status in 200..299) {
                val message = runCatching { JSONObject(response).optString("msg").ifBlank { JSONObject(response).optString("message") } }.getOrNull()
                message?.takeIf(String::isNotBlank) ?: runCatching { JSONObject(response).optString("error_description").ifBlank { JSONObject(response).optString("error") } }.getOrNull()?.takeIf(String::isNotBlank) ?: "Supabase Auth respondió HTTP $status."
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
