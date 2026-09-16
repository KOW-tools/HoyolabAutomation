package cc.kowx712.autohoyolab.data.cookie

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure cookie storage using AndroidKeyStore directly without deprecated EncryptedSharedPreferences.
 * This implementation uses AES-256-GCM encryption with keys stored in the hardware-backed keystore.
 */
class CookieStore(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "hoyolab_secure_prefs",
        Context.MODE_PRIVATE
    )

    private val keyAlias = "hoyolab_master_key_v2"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine IV and encrypted data
        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encrypted: String): String? {
        return try {
            val combined = Base64.decode(encrypted, Base64.NO_WRAP)

            // Extract IV (first 12 bytes for GCM) and encrypted data
            val ivSize = 12
            val iv = combined.sliceArray(0 until ivSize)
            val encryptedBytes = combined.sliceArray(ivSize until combined.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveCookie(cookie: String, expiresAt: Long = 0) {
        val encrypted = encrypt(cookie)
        sharedPreferences.edit {
            putString(KEY_COOKIE, encrypted)
                .putLong(KEY_CAPTURED_AT, System.currentTimeMillis())
                .putLong(KEY_EXPIRES_AT, expiresAt)
        }
    }

    fun getCookie(): String? {
        val encrypted = sharedPreferences.getString(KEY_COOKIE, null) ?: return null
        return decrypt(encrypted)
    }

    fun saveAccountInfo(
        accountId: String,
        accountName: String?,
        email: String?,
        validatedAt: Long
    ) {
        sharedPreferences.edit {
            putString(KEY_ACCOUNT_ID, encrypt(accountId))
            accountName?.let { putString(KEY_ACCOUNT_NAME, it) }
            email?.let { putString(KEY_EMAIL, it) }
            putLong(KEY_LAST_VALIDATED, validatedAt)
            putBoolean(KEY_EXPIRED, false)
        }
    }

    fun getAccountId(): String? {
        val encrypted = sharedPreferences.getString(KEY_ACCOUNT_ID, null) ?: return null
        return decrypt(encrypted)
    }

    fun getAccountName(): String? {
        return sharedPreferences.getString(KEY_ACCOUNT_NAME, null)
    }

    fun getEmail(): String? {
        return sharedPreferences.getString(KEY_EMAIL, null)
    }

    fun getLastValidatedAt(): Long = sharedPreferences.getLong(KEY_LAST_VALIDATED, 0)
    fun getCapturedAt(): Long = sharedPreferences.getLong(KEY_CAPTURED_AT, 0)
    fun getExpiresAt(): Long = sharedPreferences.getLong(KEY_EXPIRES_AT, 0)
    fun isExpired(): Boolean = sharedPreferences.getBoolean(KEY_EXPIRED, false)

    fun markAsExpired() {
        sharedPreferences.edit {
            putBoolean(KEY_EXPIRED, true)
        }
    }

    fun clear() {
        sharedPreferences.edit { clear() }
    }

    fun hasCookie(): Boolean = getCookie() != null

    companion object {
        private const val KEY_COOKIE = "cookie"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_LAST_VALIDATED = "last_validated"
        private const val KEY_CAPTURED_AT = "captured_at"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_EXPIRED = "expired"
    }
}
