// Sends waiting items to the webhook (F-12.1). WorkManager runs it only with a network and retries with backoff;
// Android 15 blocks network calls started outside a valid lifecycle, so the listener never sends directly.
package id.glideslope.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.glideslope.app.core.Outcome
import id.glideslope.app.core.outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val s = Settings(applicationContext)
        val q = Queue.get(applicationContext)
        val secret = s.secret
        if (!s.ready || secret == null) return@withContext Result.success() // waits for the owner; saving settings enqueues again
        for (item in q.pending()) {
            val code = post(s.webhookUrl, secret, item.payload().toJson())
            when (outcome(code)) {
                Outcome.SENT -> { q.mark(item.id, TERKIRIM, "HTTP $code"); s.lastSentAt = System.currentTimeMillis() }
                Outcome.DROP -> q.mark(item.id, DITOLAK, "HTTP $code")
                Outcome.BAD_SECRET -> { s.secretRejected = true; return@withContext Result.success() }
                Outcome.RETRY -> return@withContext Result.retry()
            }
        }
        q.purge()
        if (q.count(MENUNGGU) > 0) enqueue(applicationContext) // more than one batch was waiting
        Result.success()
    }

    private fun post(url: String, secret: String, json: String): Int? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000; readTimeout = 15_000; requestMethod = "POST"; doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-webhook-secret", secret)
            outputStream.use { it.write(json.toByteArray()) }
            responseCode.also { disconnect() }
        }
    } catch (_: IOException) {
        null
    }

    companion object {
        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SendWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("kirim", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
