// F-01.2 on Android: unlock with a strong biometric (or the device screen lock) proven by a Keystore key that only
// works after the owner authenticated (CryptoObject), so a faked "success" callback unlocks nothing.
package id.glideslope.app

import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object AppLock {
    private const val ALIAS = "glideslope-unlock"
    private const val IDLE_MS = 5 * 60 * 1000L // F-01.2 default idle limit

    @Volatile var locked = true
        private set
    private var backgroundAt = 0L

    // device credential with a CryptoObject needs Android 11+; older phones use the fingerprint alone
    private val authenticators = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

    /** False when the phone has no screen lock or fingerprint: the app then cannot lock itself, and says so. */
    fun available(activity: FragmentActivity) =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun onBackground() { backgroundAt = SystemClock.elapsedRealtime() }

    fun onForeground() {
        if (backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > IDLE_MS) locked = true
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec.build()) }.generateKey()
    }

    private fun cipher(): Cipher = try {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
    } catch (_: KeyPermanentlyInvalidatedException) {
        // a fingerprint was added or removed: make a new key, the owner simply authenticates again
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
    }

    fun prompt(activity: FragmentActivity, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // only a key the owner just unlocked can encrypt; anything else throws and the app stays locked
                val ok = runCatching { result.cryptoObject?.cipher?.doFinal(byteArrayOf(1)) }.getOrNull() != null
                if (ok) { locked = false; onUnlocked() }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Glideslope")
            .setSubtitle("Pakai sidik jari atau kunci layar")
            .setAllowedAuthenticators(authenticators)
            .apply { if (authenticators == BIOMETRIC_STRONG) setNegativeButtonText("Batal") }
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher()))
    }

    fun unlockWithoutPrompt() { locked = false }
}
