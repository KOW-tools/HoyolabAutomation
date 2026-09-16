package cc.kowx712.autohoyolab.ui.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.kowx712.autohoyolab.data.local.AppDatabase
import cc.kowx712.autohoyolab.data.local.CheckInLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel(
    context: Context
) : ViewModel() {

    private val applicationContext = context.applicationContext
    private val database = AppDatabase.getDatabase(applicationContext)
    private val sharedPreferences = applicationContext.getSharedPreferences("logs_filter", Context.MODE_PRIVATE)

    private val _filterSuccess = MutableStateFlow(sharedPreferences.getBoolean("filter_success", true))
    val filterSuccess: StateFlow<Boolean> = _filterSuccess.asStateFlow()

    private val _filterFailed = MutableStateFlow(sharedPreferences.getBoolean("filter_failed", true))
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
                        "SUCCESS" -> successEnabled
                        "FAILED", "COOKIE_EXPIRED", "NETWORK_ERROR" -> failedEnabled
                        else -> false
                    }
                }
            }
        }
    }

    fun setFilterSuccess(enabled: Boolean) {
        _filterSuccess.value = enabled
        sharedPreferences.edit { putBoolean("filter_success", enabled) }
        observeLogs()
    }

    fun setFilterFailed(enabled: Boolean) {
        _filterFailed.value = enabled
        sharedPreferences.edit { putBoolean("filter_failed", enabled) }
        observeLogs()
    }
}
