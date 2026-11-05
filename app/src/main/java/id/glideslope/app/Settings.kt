// Owner settings. The webhook secret is encrypted with an AES-GCM key that never leaves the Android Keystore
// (security-crypto's EncryptedSharedPreferences is deprecated).
package id.glideslope.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Settings(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("setelan", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(v) = prefs.edit().putString("server_url", v).apply()

    var allowed: Set<String>
        get() = prefs.getStringSet("allowed", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("allowed", v.toSet()).apply()

    var secret: String?
        get() = prefs.getString("secret", null)?.let { runCatching { decrypt(it) }.getOrNull() }
        set(v) = prefs.edit().putString("secret", v?.let(::encrypt)).putBoolean("secret_rejected", false).apply()

    /** Set when the server answered 401; the queue waits until a new secret is saved. */
    var secretRejected: Boolean
        get() = prefs.getBoolean("secret_rejected", false)
        set(v) = prefs.edit().putBoolean("secret_rejected", v).apply()

    var lastSentAt: Long
        get() = prefs.getLong("last_sent_at", 0)
        set(v) = prefs.edit().putLong("last_sent_at", v).apply()

    val webhookUrl get() = serverUrl.trimEnd('/') + "/api/ingest/webhook"
    val ready get() = serverUrl.startsWith("https://") && !secret.isNullOrEmpty() && !secretRejected

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val b = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance(AES_GCM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12)) }
        return String(c.doFinal(b, 12, b.size - 12))
    }

    private companion object {
        const val ALIAS = "glideslope-webhook"
        const val AES_GCM = "AES/GCM/NoPadding"
    }
}
