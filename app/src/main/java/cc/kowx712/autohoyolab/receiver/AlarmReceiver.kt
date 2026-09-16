package cc.kowx712.autohoyolab.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cc.kowx712.autohoyolab.worker.CheckInWorker

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received, starting CheckInWorker")

        // Enqueue the check-in worker
        val workRequest = OneTimeWorkRequestBuilder<CheckInWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
