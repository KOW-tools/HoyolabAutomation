package cc.kowx712.autohoyolab.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cc.kowx712.autohoyolab.worker.AlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, rescheduling alarm")
            AlarmScheduler.scheduleNextMidnight(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
