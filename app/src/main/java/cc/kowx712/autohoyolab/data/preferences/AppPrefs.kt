package cc.kowx712.autohoyolab.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppPrefs {
    /** SharedPreferences file name. */
    const val NAME = "app_prefs"

    /** Managed pref */
    // "setup_complete" - breaking change: b25d2d8 feat: native login method
    const val SETUP_COMPLETE = "setup_complete_v2"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isSetupComplete(context: Context): Boolean {
        migrate(context)
        return get(context).getBoolean(SETUP_COMPLETE, false)
    }

    fun setSetupComplete(context: Context, complete: Boolean) {
        migrate(context)
        get(context).edit { putBoolean(SETUP_COMPLETE, complete) }
    }

    /** Drop keys removed by breaking changes. */
    private fun migrate(context: Context) {
        val prefs = get(context)
        if (prefs.contains("setup_complete")) {
            prefs.edit { remove("setup_complete") }
        }
    }
}
