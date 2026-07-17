package com.stadiatv.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

@Singleton
class KeystoreCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("encrypted_provider_credentials", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    override suspend fun putSecret(sourceId: String, name: String, secret: String) {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(key(sourceId, name), "${VERSION}:${b64(iv)}:${b64(ciphertext)}")
            .apply()
    }

    override suspend fun getSecret(sourceId: String, name: String): String? {
        val envelope = prefs.getString(key(sourceId, name), null) ?: return null
        val parts = envelope.split(":")
        if (parts.size != 3 || parts[0] != VERSION) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    override suspend fun deleteSecret(sourceId: String, name: String) {
        prefs.edit().remove(key(sourceId, name)).apply()
    }

    override suspend fun deleteAll(sourceId: String) {
        val prefix = "$sourceId:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun key(sourceId: String, name: String): String = "$sourceId:$name"
    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "stadiatv.provider.credentials.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION = "v1"
    }
}
