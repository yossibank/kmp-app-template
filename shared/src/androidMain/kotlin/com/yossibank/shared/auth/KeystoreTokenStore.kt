package com.yossibank.shared.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class KeystoreTokenStore(
    context: Context,
) : TokenStore {
    private val preferences = context.getSharedPreferences("com.yossibank.shared.tokens", Context.MODE_PRIVATE)

    override fun load(): Tokens? {
        val stored = preferences.getString("tokens", null) ?: return null

        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            Json.decodeFromString<Tokens>(cipher.doFinal(bytes, 12, bytes.size - 12).decodeToString())
        }.getOrNull()
    }

    override fun save(tokens: Tokens) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(Json.encodeToString(tokens).encodeToByteArray())

        preferences.edit().putString("tokens", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override fun clear() {
        preferences.edit().remove("tokens").apply()
    }

    private fun key(): SecretKey {
        val alias = "com.yossibank.shared.tokens"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }
}
