// F-14.9: "segarkan" — what pull-to-refresh and the Segarkan button run: send the outbox, then pull, and say how it went.
package id.glideslope.app.sync

import android.content.Context
import id.glideslope.app.data.SessionStore

/** Returns a short note when the screen should say something (offline, failed), or null after a quiet success. */
suspend fun refreshAll(ctx: Context): String? {
    if (SessionStore(ctx).get()?.aal != "aal2") return null
    val sent = pushOutbox(ctx)
    return when (val r = pullAndRecord(ctx)) {
        PullOutcome.Done -> if (sent) null else "Sebagian perubahan belum terkirim. Dicoba lagi nanti."
        PullOutcome.Offline -> "Tidak ada koneksi. Menampilkan data terakhir."
        is PullOutcome.Failed -> r.message
    }
}
