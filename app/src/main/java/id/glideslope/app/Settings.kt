// Owner settings. The webhook secret is encrypted with a Keystore AES-GCM key (Crypto).
package id.glideslope.app

import android.content.Context
import android.util.Base64
import id.glideslope.app.data.Crypto

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

    private fun encrypt(plain: String) = Base64.encodeToString(Crypto.encrypt(ALIAS, plain.toByteArray()), Base64.NO_WRAP)
    private fun decrypt(stored: String) = String(Crypto.decrypt(ALIAS, Base64.decode(stored, Base64.NO_WRAP)))

    private companion object {
        const val ALIAS = "glideslope-webhook"
    }
}
