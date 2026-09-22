package cc.kowx712.autohoyolab.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.kowx712.autohoyolab.data.local.AppDatabase
import cc.kowx712.autohoyolab.data.local.CheckInLog
import cc.kowx712.autohoyolab.data.preferences.LogsFilterPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel(
    context: Context
) : ViewModel() {

    private val applicationContext = context.applicationContext
    private val database = AppDatabase.getDatabase(applicationContext)
    private val _filterSuccess = MutableStateFlow(LogsFilterPrefs.isFilterSuccess(applicationContext))
    val filterSuccess: StateFlow<Boolean> = _filterSuccess.asStateFlow()

    private val _filterFailed = MutableStateFlow(LogsFilterPrefs.isFilterFailed(applicationContext))
    val filterFailed: StateFlow<Boolean> = _filterFailed.asStateFlow()

    private val _logs = MutableStateFlow<List<CheckInLog>>(emptyList())
    val logs: StateFlow<List<CheckInLog>> = _logs.asStateFlow()

    init {
        observeLogs()
    }

    private fun observeLogs() {
        viewModelScope.launch {
            database.checkInLogDao().getAllLogs().collect { allLogs ->
                val successEnabled = _filterSuccess.value
                val failedEnabled = _filterFailed.value

                _logs.value = allLogs.filter { log ->
                    // Filter out ALREADY_SIGNED logs
                    if (log.status == "ALREADY_SIGNED") return@filter false

                    // Apply success/failed filters
                    when (log.status) {
                        "SUCCESS", "RESIGN_SUCCESS" -> successEnabled
                        "FAILED", "COOKIE_EXPIRED", "NETWORK_ERROR",
                        "RESIGN_FAILED", "RESIGN_COOKIE_EXPIRED", "RESIGN_NETWORK_ERROR" -> failedEnabled

                        else -> false
                    }
                }
            }
        }
    }

    fun setFilterSuccess(enabled: Boolean) {
        _filterSuccess.value = enabled
        LogsFilterPrefs.setFilterSuccess(applicationContext, enabled)
        observeLogs()
    }

    fun setFilterFailed(enabled: Boolean) {
        _filterFailed.value = enabled
        LogsFilterPrefs.setFilterFailed(applicationContext, enabled)
        observeLogs()
    }
}
