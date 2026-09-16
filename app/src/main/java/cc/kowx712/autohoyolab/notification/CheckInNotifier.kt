package cc.kowx712.autohoyolab.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.data.model.CheckInResult
import cc.kowx712.autohoyolab.worker.CheckInWorker

class CheckInNotifier(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showSummaryNotification(results: List<CheckInResult>) {
        val successCount = results.count { it is CheckInResult.Success }
        val alreadySignedCount = results.count { it is CheckInResult.AlreadySigned }
        val failedCount = results.count { it is CheckInResult.Failed }
        val expiredCount = results.count { it is CheckInResult.CookieExpired }
        val networkErrorCount = results.count { it is CheckInResult.NetworkError }

        val title = when {
            expiredCount > 0 -> context.getString(R.string.notification_title_cookie_expired)
            networkErrorCount > 0 -> context.getString(R.string.notification_title_network_error)
            failedCount > 0 -> context.getString(R.string.notification_title_checkin_issues)
            successCount > 0 -> context.getString(R.string.notification_title_checkin_complete)
            alreadySignedCount > 0 -> context.getString(R.string.notification_title_already_signed)
            else -> context.getString(R.string.notification_title_checkin_complete)
        }

        val message = buildString {
            if (successCount > 0) append(context.getString(R.string.notification_summary_checked_in, successCount))
            if (alreadySignedCount > 0) {
                if (isNotEmpty()) append(", ")
                append(context.getString(R.string.notification_summary_already_signed, alreadySignedCount))
            }
            if (failedCount > 0) {
                if (isNotEmpty()) append(", ")
                append(context.getString(R.string.notification_summary_failed, failedCount))
            }
            if (networkErrorCount > 0) {
                if (isNotEmpty()) append(", ")
                append(context.getString(R.string.notification_summary_network_error, networkErrorCount))
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SUMMARY, notification)
    }

    fun showCookieExpiredNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title_cookie_expired))
            .setContentText(context.getString(R.string.notification_message_cookie_expired))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COOKIE_EXPIRED, notification)
    }

    fun showNetworkErrorNotification() {
        val retryIntent = Intent(context, CheckInWorker::class.java)
        val retryPendingIntent = PendingIntent.getService(
            context,
            0,
            retryIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title_network_error))
            .setContentText(context.getString(R.string.notification_message_network_error))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_retry),
                retryPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_NETWORK_ERROR, notification)
    }

    fun showNoGamesNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title_no_games))
            .setContentText(context.getString(R.string.notification_message_no_games))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_NO_GAMES, notification)
    }

    fun showManualCheckInStarted() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title_checkin_started))
            .setContentText(context.getString(R.string.notification_message_checkin_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    fun dismissProgressNotification() {
        notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
    }

    companion object {
        private const val CHANNEL_ID = "checkin_channel"
        private const val NOTIFICATION_ID_SUMMARY = 1
        private const val NOTIFICATION_ID_COOKIE_EXPIRED = 2
        private const val NOTIFICATION_ID_NETWORK_ERROR = 3
        private const val NOTIFICATION_ID_NO_GAMES = 4
        private const val NOTIFICATION_ID_PROGRESS = 5
    }
}
