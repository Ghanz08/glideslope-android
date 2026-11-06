// Runs the pull with a network, every 15 minutes and whenever the app asks (opening, pull-to-refresh).
package id.glideslope.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.glideslope.app.Settings
import id.glideslope.app.data.Api
import id.glideslope.app.data.Db
import id.glideslope.app.data.MetaEntity
import id.glideslope.app.data.SessionStore
import java.util.concurrent.TimeUnit

class PullWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (SessionStore(ctx).get()?.aal != "aal2") return Result.success() // signed out or waiting for the code
        val db = Db.get(ctx)
        return when (val r = Syncer(db, Api(Settings(ctx), SessionStore(ctx))).pull()) {
            PullOutcome.Done -> { db.sync().meta(MetaEntity("sync_error", "")); Result.success() }
            PullOutcome.Offline -> Result.retry()
            is PullOutcome.Failed -> { db.sync().meta(MetaEntity("sync_error", "${r.code}|${r.message}")); Result.success() }
        }
    }

    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun now(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniqueWork(
            "tarik", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<PullWorker>().setConstraints(network).build(),
        )

        fun schedule(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "tarik-berkala", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PullWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
        )
    }
}
