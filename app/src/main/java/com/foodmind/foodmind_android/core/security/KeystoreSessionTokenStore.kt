package com.foodmind.foodmind_android.core.security

import android.content.Context
import android.util.Base64
import com.foodmind.foodmind_android.core.network.SessionTokenStore
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the short-lived access token in memory and encrypts only the refresh token.
 * The preference file contains ciphertext and IV, never a bearer credential.
 */
class KeystoreSessionTokenStore(context: Context) : SessionTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    @Volatile private var accessTokenValue: String? = null

    override fun accessToken(): String? = accessTokenValue

    override fun saveAccessToken(token: String) {
        accessTokenValue = token
    }

    override fun refreshToken(): String? {
        val cipherText = preferences.getString(REFRESH_CIPHERTEXT, null) ?: return null
        val ivText = preferences.getString(REFRESH_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(ivText)))
            String(cipher.doFinal(decode(cipherText)), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    override fun saveRefreshToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        preferences.edit()
            .putString(REFRESH_CIPHERTEXT, encode(cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))))
            .putString(REFRESH_IV, encode(cipher.iv))
            .apply()
    }

    override fun userId(): String? = preferences.getString(USER_ID, null)

    override fun saveUserId(userId: String) {
        preferences.edit().putString(USER_ID, userId).apply()
    }

    override fun clear() {
        accessTokenValue = null
        preferences.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE).apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_ALIAS = "foodmind.refresh-token"
        const val PREFERENCES = "foodmind.session"
        const val REFRESH_CIPHERTEXT = "refresh_ciphertext"
        const val REFRESH_IV = "refresh_iv"
        const val USER_ID = "user_id"
    }
}
