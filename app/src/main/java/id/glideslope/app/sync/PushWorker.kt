// F-14.2: sends the outbox in order (≤ 50 per request) and settles each answer. Rejections and conflicts stay
// visible in "Perlu ditinjau"; a transient failure keeps the rest queued for the next try. Then pulls.
package id.glideslope.app.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.ApiResult
import id.glideslope.app.data.Db
import id.glideslope.app.data.SessionStore
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

/** Sends the outbox in order. true = nothing left to retry; false = offline or a transient failure, the rest stays queued. */
suspend fun pushOutbox(ctx: Context): Boolean {
    val db = Db.get(ctx)
    val dao = db.sync()
    val api = Api(Settings(ctx), SessionStore(ctx))
    while (true) {
        val batch = dao.outboxPending()
        if (batch.isEmpty()) return true
        val answer = when (val r = api.push(batch)) {
            is ApiResult.Ok -> r.value
            ApiResult.Offline -> return false
            is ApiResult.Fail -> return !(r.status >= 500 || r.status == 429)
        }
        var stop = false
        db.withTransaction {
            for (res in answer["results"]!!.jsonArray.map { it.jsonObject }) {
                val opId = res["op_id"]!!.jsonPrimitive.content
                val op = batch.first { it.opId == opId }
                when (res["status"]!!.jsonPrimitive.content) {
                    "applied", "duplicate" -> {
                        op.localIds.split(',').filter { it.isNotBlank() }.forEach { dao.deleteTx(it) }
                        dao.outboxDone(opId)
                    }
                    "rejected" -> dao.outboxMark(opId, "ditolak", res["error"]?.takeIf { it !is JsonNull }?.jsonObject?.get("message")?.jsonPrimitive?.content)
                    "conflict" -> dao.outboxMark(opId, "konflik", "Diubah juga di laptop. Pilih versi yang dipakai.")
                    else -> stop = true // retry: keep this and everything after it queued
                }
            }
        }
        if (stop) return false
    }
}

class PushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (SessionStore(ctx).get()?.aal != "aal2") return Result.success()
        if (!pushOutbox(ctx)) return Result.retry()
        PullWorker.now(ctx)
        return Result.success()
    }

    companion object {
        fun now(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniqueWork(
            "kirim-perubahan", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<PushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }
}
