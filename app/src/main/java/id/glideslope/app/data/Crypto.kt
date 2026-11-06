// AES-GCM with keys that never leave the Android Keystore. Used for the webhook secret, the session tokens and the
// database passphrase (F-14, ADR 0005/0007). security-crypto's EncryptedSharedPreferences is deprecated.
package id.glideslope.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Crypto {
    private const val AES_GCM = "AES/GCM/NoPadding"

    private fun key(alias: String): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    /** iv (12 bytes) followed by the ciphertext. */
    fun encrypt(alias: String, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key(alias)) }
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(alias: String, data: ByteArray): ByteArray {
        val c = Cipher.getInstance(AES_GCM).apply { init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(128, data, 0, 12)) }
        return c.doFinal(data, 12, data.size - 12)
    }

    fun deleteKey(alias: String) = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
}
