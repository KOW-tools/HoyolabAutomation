package cc.kowx712.autohoyolab.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.kowx712.autohoyolab.data.cookie.CookieStore
import cc.kowx712.autohoyolab.data.local.AppDatabase
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import cc.kowx712.autohoyolab.network.HoyolabApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    context: Context
) : ViewModel() {

    private val applicationContext = context.applicationContext
    private val cookieStore = CookieStore(applicationContext)
    private val database = AppDatabase.getDatabase(applicationContext)

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

    init {
        loadAccountInfo()
    }

    fun loadAccountInfo() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _accountInfo.value = AccountState.Loading

            val cookie = cookieStore.getCookie()
            if (cookie == null) {
                _accountInfo.value = AccountState.NoCookie
                _gameRoles.value = null
                _isRefreshing.value = false
                return@launch
            }

            // Check if cookie is marked as expired
            if (cookieStore.isExpired()) {
                _accountInfo.value = AccountState.Expired("Cookie has expired")
                _gameRoles.value = null
                _isRefreshing.value = false
                return@launch
            }

            // Try to load cached account info first for faster display
            val cachedAccountId = cookieStore.getAccountId()
            val cachedAccountName = cookieStore.getAccountName()
            val cachedEmail = cookieStore.getEmail()
            val lastValidated = cookieStore.getLastValidatedAt()
            val capturedAt = cookieStore.getCapturedAt()

            if (cachedAccountId != null && cachedAccountName != null) {
                // Show cached data immediately
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

            // Only fetch game roles if we don't have them cached
            if (_gameRoles.value == null) {
                _isLoadingGames.value = true
            }

            try {
                val apiClient = HoyolabApiClient(cookie)
                val account = apiClient.validateCookie()

                // Only fetch roles if not cached
                val roles = if (_gameRoles.value == null) {
                    apiClient.getUserGameRoles()
                } else {
                    // Use cached roles, but still update account info
                    emptyList()
                }

                cookieStore.saveAccountInfo(
                    accountId = account.accountId,
                    accountName = account.accountName,
                    email = account.email,
                    validatedAt = account.validatedAt
                )

                if (_gameRoles.value == null) {
                    val mappedGames = roles.mapNotNull { role ->
                        HoyoGame.fromGameBiz(role.gameBiz)?.let { game -> game to role }
                    }

                    _gameRoles.value = mappedGames

                    // Load last logs for each game
                    val logs = mutableMapOf<String, String>()
                    withContext(Dispatchers.IO) {
                        mappedGames.forEach { (game, _) ->
                            val log = database.checkInLogDao().getLastLogForGame(game.id)
                            if (log != null) {
                                logs[game.id] = if (log.status == "SUCCESS" || log.status == "ALREADY_SIGNED") {
                                    formatDate(log.timestamp)
                                } else {
                                    log.message ?: "Error"
                                }
                            }
                        }
                    }
                    _lastLogs.value = logs
                }

                _accountInfo.value = AccountState.Success(
                    accountId = account.accountId,
                    accountName = account.accountName ?: "Unknown",
                    email = account.email ?: "Unknown",
                    validatedAt = account.validatedAt,
                    capturedAt = cookieStore.getCapturedAt(),
                    expiresAt = cookieStore.getExpiresAt(),
                    gameCount = _gameRoles.value?.size ?: 0
                )
            } catch (e: HoyolabApiClient.CookieExpiredException) {
                cookieStore.markAsExpired()
                _accountInfo.value = AccountState.Expired(e.message ?: "Cookie expired")
                _gameRoles.value = null
            } catch (e: Exception) {
                _accountInfo.value = AccountState.Error(e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
                _isLoadingGames.value = false
            }
        }
    }

    fun logout() {
        pruneWebViewData()

        cookieStore.clear()
        _accountInfo.value = AccountState.NoCookie
        _gameRoles.value = null
    }

    fun relogin() {
        pruneWebViewData()

        // Start fresh WebViewActivity for re-login
        val intent = Intent(applicationContext, cc.kowx712.autohoyolab.ui.WebViewActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(intent)
    }

    private fun pruneWebViewData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
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
