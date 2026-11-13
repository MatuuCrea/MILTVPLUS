package com.matuucrea.miltvplus.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class ParentalAccessStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("miltvplus_parental", Context.MODE_PRIVATE)
    val hasPin: Boolean get() = prefs.contains("pin_hash") && prefs.contains("pin_salt")
    val ageConfirmed: Boolean get() = prefs.getBoolean("age_confirmed", false)

    fun setPin(pin: String) {
        require(pin.matches(Regex("^[0-9]{6}$"))) { "El PIN debe tener exactamente 6 números." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .remove("failed_attempts").remove("locked_until").apply()
    }

    fun verifyPin(pin: String): Boolean {
        if (!hasPin || !pin.matches(Regex("^[0-9]{6}$"))) return false
        val now = System.currentTimeMillis()
        if (prefs.getLong("locked_until", 0L) > now) return false
        val salt = Base64.decode(prefs.getString("pin_salt", ""), Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("pin_hash", ""), Base64.NO_WRAP)
        val valid = MessageDigest.isEqual(expected, derive(pin, salt))
        if (valid) {
            prefs.edit().remove("failed_attempts").remove("locked_until").apply()
        } else {
            val attempts = prefs.getInt("failed_attempts", 0) + 1
            prefs.edit().putInt("failed_attempts", attempts)
                .putLong("locked_until", if (attempts >= 5) now + 5 * 60 * 1000L else 0L).apply()
        }
        return valid
    }

    fun confirmAge() { prefs.edit().putBoolean("age_confirmed", true).apply() }
    fun resetAgeConfirmation() { prefs.edit().putBoolean("age_confirmed", false).apply() }
    fun clearPin() { prefs.edit().remove("pin_hash").remove("pin_salt").apply() }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 160_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
