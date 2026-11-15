// F-01.2 on Android: unlock with a strong biometric, proven by a Keystore key that only works after the owner
// authenticated (CryptoObject), so a faked "success" callback unlocks nothing. The app's own PIN (checked by the
// server, see Lock.kt) is the way out when the fingerprint fails; the phone's screen lock only stands in when the PIN
// cannot be checked (offline), because a screen lock someone watched being typed must not open the finances (OWASP MASWE-0045).
package id.glideslope.app

import android.content.Context
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
    private const val HANDOFF_MS = 2 * 60 * 1000L // how long a picker or camera we opened may keep the app unlocked

    @Volatile var locked = true
        private set
    private var handoffUntil = 0L

    // device credential with a CryptoObject needs Android 11+; older phones have no screen-lock stand-in
    private val credential = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

    /** A fingerprint (or other strong biometric) is enrolled. */
    fun canBiometric(activity: FragmentActivity) =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** The phone's screen lock can open the app (Android 11+), the offline stand-in for the PIN. */
    fun canCredential(activity: FragmentActivity) =
        Build.VERSION.SDK_INT >= 30 && BiometricManager.from(activity).canAuthenticate(credential) == BiometricManager.BIOMETRIC_SUCCESS

    /** Whether the server has a PIN on (from /api/v1/me at every sync); kept here so the lock screen knows before anything loads. */
    fun pinEnabled(ctx: Context) = ctx.getSharedPreferences("kunci", Context.MODE_PRIVATE).getBoolean("pin", false)
    fun setPinEnabled(ctx: Context, on: Boolean) = ctx.getSharedPreferences("kunci", Context.MODE_PRIVATE).edit().putBoolean("pin", on).apply()

    /** False when the phone has no fingerprint, no PIN and no screen lock: the app then cannot lock itself, and says so. */
    fun available(activity: FragmentActivity) = canBiometric(activity) || pinEnabled(activity) || canCredential(activity)

    /** Call right before opening a picker, the camera or another app on the owner's behalf: coming back must not lock. */
    fun handOff() { handoffUntil = SystemClock.elapsedRealtime() + HANDOFF_MS }

    /** The app is left (screen off, another app, Home): locked at once, unless we handed off ourselves (F-01.2). */
    fun onLeave() {
        if (SystemClock.elapsedRealtime() > handoffUntil) locked = true
        handoffUntil = 0L
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

    private fun show(
        activity: FragmentActivity, authenticators: Int, subtitle: String, negative: String?,
        onUnlocked: () -> Unit, onError: (Int) -> Unit,
    ) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // only a key the owner just unlocked can encrypt; anything else throws and the app stays locked
                val ok = runCatching { result.cryptoObject?.cipher?.doFinal(byteArrayOf(1)) }.getOrNull() != null
                if (ok) { locked = false; onUnlocked() }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode)
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Glideslope")
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            // a negative button and the screen-lock stand-in exclude each other (BiometricPrompt), so only the biometric prompt has one
            .apply { if (negative != null) setNegativeButtonText(negative) }
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher()))
    }

    /**
     * The fingerprint prompt: biometrics only. With a PIN on it carries "Gunakan PIN"; [onUsePin] runs when that is tapped and
     * when the fingerprint is locked out after too many tries, so the owner is never stuck at a dialog.
     */
    fun promptBiometric(activity: FragmentActivity, pinOn: Boolean, onUnlocked: () -> Unit, onUsePin: () -> Unit) =
        show(activity, BIOMETRIC_STRONG, "Pakai sidik jari", if (pinOn) "Gunakan PIN" else "Batal", onUnlocked) { code ->
            if (pinOn && (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_LOCKOUT || code == BiometricPrompt.ERROR_LOCKOUT_PERMANENT)) onUsePin()
        }

    /** The phone's screen lock, offered only when the PIN cannot be checked (offline) or the owner has no fingerprint and no PIN. */
    fun promptCredential(activity: FragmentActivity, onUnlocked: () -> Unit) =
        show(activity, credential, "Pakai kunci layar HP", if (credential == BIOMETRIC_STRONG) "Batal" else null, onUnlocked) {}

    /** The server accepted the PIN. */
    fun unlockWithPin() { locked = false }

    fun unlockWithoutPrompt() { locked = false }
}

/** Opens another screen on the owner's behalf (system settings, WhatsApp, the browser) without locking on return. */
fun android.content.Context.startHandOff(intent: android.content.Intent) {
    AppLock.handOff()
    startActivity(intent)
}
