package cc.kowx712.autohoyolab.ui.navigation

import android.content.Intent
import androidx.navigation3.runtime.NavKey

/**
 * Handles intent-based navigation using deeplinks
 */
object IntentDispatcher {

    private const val DEEPLINK_SCHEME = "app"

    /**
     * Parses an intent and returns the appropriate navigation destination
     */
    fun getDestinationFromIntent(intent: Intent?): NavKey? {
        if (intent == null) return null

        // Handle deeplink URIs (e.g., app://logs, app://setup)
        intent.data?.let { uri ->
            if (uri.scheme == DEEPLINK_SCHEME) {
                return when (uri.host) {
                    "setup" -> Setup
                    "home" -> Home
                    "logs" -> Logs
                    else -> null
                }
            }
        }

        // Handle legacy string extras for backward compatibility
        intent.getStringExtra("navigate_to")?.let { destination ->
            return when (destination) {
                "setup" -> Setup
                "home" -> Home
                "logs" -> Logs
                else -> null
            }
        }

        return null
    }

    /**
     * Creates a deeplink URI for a given destination
     */
    fun createDeeplinkUri(destination: NavKey): String {
        val path = when (destination) {
            is Setup -> "setup"
            is Home -> "home"
            is Logs -> "logs"
            else -> throw IllegalArgumentException("Unknown destination: $destination")
        }
        return "$DEEPLINK_SCHEME://$path"
    }
}
