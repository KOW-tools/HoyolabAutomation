package cc.kowx712.autohoyolab.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CheckInWork {
    /** Unique name so a midnight alarm can never stack with a pending retry chain. */
    const val UNIQUE_WORK_NAME = "daily_check_in"

    /**
     * Requires connectivity so the worker never runs while the device is offline
     * (e.g. internet turned off overnight): the work stays ENQUEUED until the
     * network returns, then executes. Retries use exponential backoff, capped by
     * WorkManager at 5 hours, and continue indefinitely until the run completes.
     */
    fun buildRequest() = OneTimeWorkRequestBuilder<CheckInWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    /**
     * Enqueues a fresh check-in run, replacing any pending or running chain.
     * A manual "Run now" tap while a retry chain is pending therefore starts
     * immediately instead of waiting for the next backoff window.
     */
    fun enqueue(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, buildRequest())
    }
}
