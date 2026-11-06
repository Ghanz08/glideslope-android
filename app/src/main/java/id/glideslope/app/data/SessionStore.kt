// The signed-in session (F-14): encrypted in noBackupFilesDir, so it is never in a cloud backup or device transfer.
package id.glideslope.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Session(val access_token: String, val refresh_token: String, val expires_at: Long? = null, val aal: String)

class SessionStore(ctx: Context) {
    private val file = File(ctx.applicationContext.noBackupFilesDir, "session.bin")

    @Volatile private var cached: Session? = null

    fun get(): Session? = cached ?: runCatching {
        if (!file.exists()) null else Json.decodeFromString<Session>(String(Crypto.decrypt(ALIAS, file.readBytes())))
    }.getOrNull().also { cached = it }

    fun set(s: Session?) {
        cached = s
        if (s == null) file.delete() else file.writeBytes(Crypto.encrypt(ALIAS, Json.encodeToString(s).toByteArray()))
    }

    private companion object { const val ALIAS = "glideslope-session" }
}
