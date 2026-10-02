package cc.kowx712.autohoyolab.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cc.kowx712.autohoyolab.worker.CheckInWork

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received, starting CheckInWorker")

        // Enqueue the check-in worker (waits for connectivity, unique work)
        CheckInWork.enqueue(context)
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
