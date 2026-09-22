package cc.kowx712.autohoyolab.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object LogsFilterPrefs {
    /** SharedPreferences file name. */
    const val NAME = "logs_filter"

    /** Managed pref */
    const val FILTER_SUCCESS = "filter_success"
    const val FILTER_FAILED = "filter_failed"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isFilterSuccess(context: Context): Boolean =
        get(context).getBoolean(FILTER_SUCCESS, true)

    fun setFilterSuccess(context: Context, enabled: Boolean) {
        get(context).edit { putBoolean(FILTER_SUCCESS, enabled) }
    }

    fun isFilterFailed(context: Context): Boolean =
        get(context).getBoolean(FILTER_FAILED, true)

    fun setFilterFailed(context: Context, enabled: Boolean) {
        get(context).edit { putBoolean(FILTER_FAILED, enabled) }
    }
}
