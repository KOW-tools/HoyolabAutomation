package cc.kowx712.autohoyolab.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cc.kowx712.autohoyolab.auth.HoyoLabApiClient
import cc.kowx712.autohoyolab.data.cookie.CookieStore
import cc.kowx712.autohoyolab.data.local.AppDatabase
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import cc.kowx712.autohoyolab.worker.CheckInWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    context: Context
) : ViewModel() {

    private val applicationContext = context.applicationContext
    private val cookieStore = CookieStore(applicationContext)
    private val database = AppDatabase.getDatabase(applicationContext)

    /** Cookie string that [loadAccountInfo] last validated successfully. */
    private var lastValidatedCookie: String? = null

    private val _accountInfo = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountInfo: StateFlow<AccountState> = _accountInfo.asStateFlow()

    private val _gameRoles = MutableStateFlow<List<Pair<HoyoGame, HoyoGameRole>>?>(null)
    val gameRoles: StateFlow<List<Pair<HoyoGame, HoyoGameRole>>?> = _gameRoles.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingGames = MutableStateFlow(false)
    val isLoadingGames: StateFlow<Boolean> = _isLoadingGames.asStateFlow()

    private val _lastLogs = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastLogs: StateFlow<Map<String, String>> = _lastLogs.asStateFlow()

    private val _selectedProfiles = MutableStateFlow<Map<String, String>>(emptyMap()) // gameId -> gameUid
    val selectedProfiles: StateFlow<Map<String, String>> = _selectedProfiles.asStateFlow()

    val isCheckInRunning: StateFlow<Boolean> = WorkManager.getInstance(applicationContext)
        .getWorkInfosForUniqueWorkFlow(CheckInWork.UNIQUE_WORK_NAME)
        .map { infos ->
            infos.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        loadAccountInfo()
    }

    fun loadAccountInfo(force: Boolean = false) {
        viewModelScope.launch {
            val cookie = cookieStore.getCookie()

            if (!force && cookie != null && cookie == lastValidatedCookie && !cookieStore.isExpired()) {
                return@launch
            }

            if (_isRefreshing.value) {
                return@launch
            }

            _isRefreshing.value = true
            if (_accountInfo.value !is AccountState.Success) {
                _accountInfo.value = AccountState.Loading
            }

            try {
                if (cookie == null) {
                    lastValidatedCookie = null
                    _accountInfo.value = AccountState.NoCookie
                    _gameRoles.value = null
                    return@launch
                }

                // Check if cookie is marked as expired
                if (cookieStore.isExpired()) {
                    lastValidatedCookie = null
                    _accountInfo.value = AccountState.Expired("Cookie has expired")
                    _gameRoles.value = null
                    return@launch
                }

                // Try to load cached account info first for faster display
                val cachedAccountId = cookieStore.getAccountId()
                val cachedAccountName = cookieStore.getAccountName()
                val cachedEmail = cookieStore.getEmail()
                val lastValidated = cookieStore.getLastValidatedAt()
                val capturedAt = cookieStore.getCapturedAt()

                if (cachedAccountId != null && cachedAccountName != null) {
                    _accountInfo.value = AccountState.Success(
                        accountId = cachedAccountId,
                        accountName = cachedAccountName,
                        email = cachedEmail ?: "Unknown",
                        validatedAt = lastValidated,
                        capturedAt = capturedAt,
                        expiresAt = cookieStore.getExpiresAt(),
                        gameCount = _gameRoles.value?.size ?: 0
                    )
                }

                if (_gameRoles.value == null) {
                    _isLoadingGames.value = true
                }

                val apiClient = HoyoLabApiClient(cookie)
                val account = apiClient.validateCookie()

                // Only fetch roles if not cached
                val roles = if (_gameRoles.value == null) {
                    apiClient.getUserGameRoles()
                } else {
                    emptyList()
                }

                cookieStore.saveAccountInfo(
                    accountId = account.accountId,
                    accountName = account.accountName,
                    email = account.email,
                    validatedAt = account.validatedAt
                )
                cookieStore.updateExpiresAt(account.expiresAt)

                if (_gameRoles.value == null) {
                    val mappedGames = roles.mapNotNull { role ->
                        HoyoGame.fromGameBiz(role.gameBiz)?.let { game -> game to role }
                    }

                    _gameRoles.value = mappedGames

                    // Load preferences from database
                    val preferences = withContext(Dispatchers.IO) {
                        database.gameProfilePreferenceDao().getAllPreferences()
                    }
                    val preferenceMap = preferences.associate { it.gameId to it.selectedGameUid }

                    // If no preferences set, auto-select highest level for games with multiple servers
                    val gamesById = mappedGames.groupBy { it.first.id }
                    val autoSelectedMap = mutableMapOf<String, String>()

                    gamesById.forEach { (gameId, profiles) ->
                        if (profiles.size > 1) {
                            // Check if user has preference
                            if (!preferenceMap.containsKey(gameId)) {
                                // Auto-select highest level, or first if tie
                                val selected = profiles.maxByOrNull { it.second.level } ?: profiles.first()
                                autoSelectedMap[gameId] = selected.second.gameUid
                            }
                        }
                    }

                    _selectedProfiles.value = preferenceMap + autoSelectedMap

                    // Load last logs for each game
                    val logs = mutableMapOf<String, String>()
                    withContext(Dispatchers.IO) {
                        mappedGames.forEach { (game, _) ->
                            val log = database.checkInLogDao().getLastCheckInLogForGame(game.id)
                            if (log != null) {
                                logs[game.id] = when (log.status) {
                                    "SUCCESS", "ALREADY_SIGNED" -> formatDate(log.timestamp)
                                    else -> log.message ?: "Error"
                                }
                            }
                        }
                    }
                    _lastLogs.value = logs
                }

                _accountInfo.value = AccountState.Success(
                    accountId = account.accountId,
                    accountName = account.accountName ?: "Traveler",
                    email = account.email ?: "Unknown",
                    validatedAt = account.validatedAt,
                    capturedAt = cookieStore.getCapturedAt(),
                    expiresAt = cookieStore.getExpiresAt(),
                    gameCount = _gameRoles.value?.size ?: 0
                )
                lastValidatedCookie = cookie
            } catch (e: HoyoLabApiClient.CookieExpiredException) {
                lastValidatedCookie = null
                cookieStore.markAsExpired()
                _accountInfo.value = AccountState.Expired(e.message ?: "Cookie expired")
                _gameRoles.value = null
            } catch (e: Exception) {
                lastValidatedCookie = null
                _accountInfo.value = AccountState.Error(e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
                _isLoadingGames.value = false
            }
        }
    }

    fun logout() {
        cookieStore.clear()
        lastValidatedCookie = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.gameProfilePreferenceDao().clearAll()
            }
        }
        _accountInfo.value = AccountState.NoCookie
        _gameRoles.value = null
        _selectedProfiles.value = emptyMap()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun selectProfile(gameId: String, gameUid: String, region: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.gameProfilePreferenceDao().savePreference(
                    cc.kowx712.autohoyolab.data.local.GameProfilePreference(
                        gameId = gameId,
                        selectedRegion = region,
                        selectedGameUid = gameUid
                    )
                )
            }
            _selectedProfiles.value += (gameId to gameUid)
        }
    }
}

sealed class AccountState {
    data object Loading : AccountState()
    data object NoCookie : AccountState()
    data class Success(
        val accountId: String,
        val accountName: String,
        val email: String,
        val validatedAt: Long,
        val capturedAt: Long,
        val expiresAt: Long,
        val gameCount: Int
    ) : AccountState()

    data class Expired(val message: String) : AccountState()
    data class Error(val message: String) : AccountState()
}
