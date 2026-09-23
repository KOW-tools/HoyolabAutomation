package cc.kowx712.autohoyolab.ui.viewmodel

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.kowx712.autohoyolab.auth.ActionTicket
import cc.kowx712.autohoyolab.auth.CaptchaSession
import cc.kowx712.autohoyolab.auth.HoyoLabAuthClient
import cc.kowx712.autohoyolab.data.cookie.CookieStore
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

class SetupViewModel(context: Context) : ViewModel() {

    private val applicationContext = context.applicationContext
    private val cookieStore = CookieStore(applicationContext)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _captchaSession = MutableStateFlow<CaptchaSession?>(null)
    val captchaSession: StateFlow<CaptchaSession?> = _captchaSession.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _verificationCode = MutableStateFlow("")
    val verificationCode: StateFlow<String> = _verificationCode.asStateFlow()

    private val _otpCountdown = MutableStateFlow(0)
    val otpCountdown: StateFlow<Int> = _otpCountdown.asStateFlow()

    private val _otpSending = MutableStateFlow(false)
    val otpSending: StateFlow<Boolean> = _otpSending.asStateFlow()

    private val _otpError = MutableStateFlow<String?>(null)
    val otpError: StateFlow<String?> = _otpError.asStateFlow()

    private val _notificationGranted = MutableStateFlow(false)
    val notificationGranted: StateFlow<Boolean> = _notificationGranted.asStateFlow()

    private val authClient = HoyoLabAuthClient()
    private var pendingActionTicket: ActionTicket? = null
    private var verificationContinuation: CancellableContinuation<String>? = null
    private var captchaContinuation: CancellableContinuation<String>? = null
    private var loginJob: Job? = null
    private var otpSendJob: Job? = null
    private var otpCountdownJob: Job? = null

    fun updateEmail(value: String) {
        _email.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun updateVerificationCode(value: String) {
        _verificationCode.value = value
    }

    fun checkNotificationPermission() {
        _notificationGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun updateNotificationPermission(granted: Boolean) {
        _notificationGranted.value = granted
    }

    fun requestAutostartPermission() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates: List<Intent> = when {
            manufacturer.contains("xiaomi") -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            )

            manufacturer.contains("oppo") -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            )

            manufacturer.contains("vivo") -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            )

            manufacturer.contains("huawei") -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            )

            manufacturer.contains("honor") -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            )

            manufacturer.contains("samsung") -> listOf(
                Intent().apply {
                    action = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"
                    setPackage("com.samsung.android.lool")
                    putExtra("activity_type", 2)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                },
            )

            else -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${applicationContext.packageName}".toUri()
        }

        for (intent in candidates + fallback) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun login() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _loginState.value = LoginState.Error("Email and password cannot be empty")
            return
        }

        cancelPendingLogin()
        resetOtpState()
        loginJob = viewModelScope.launch {
            _loginState.value = LoginState.Loading

            try {
                val result = withContext(Dispatchers.IO) {
                    authClient.authenticate(
                        account = _email.value.trim(),
                        password = _password.value,
                        onCaptchaVerificationNeeded = { session ->
                            val description = when (session) {
                                is CaptchaSession.V3 -> "Geetest verification required (v3)."
                                is CaptchaSession.V4 -> "Geetest verification required (v4)."
                            }
                            withContext(Dispatchers.Main) {
                                _captchaSession.value = session
                                _loginState.value = LoginState.CaptchaRequired(description)
                            }

                            suspendCancellableCoroutine { continuation ->
                                captchaContinuation = continuation
                                continuation.invokeOnCancellation {
                                    if (captchaContinuation === continuation) {
                                        captchaContinuation = null
                                    }
                                }
                            }
                        },
                        onEmailVerificationNeeded = { ticket ->
                            // Show the verification dialog and wait for the user.
                            // No OTP is sent until sendOtp() is triggered by a tap.
                            withContext(Dispatchers.Main) {
                                pendingActionTicket = ticket
                                _loginState.value = LoginState.EmailVerificationRequired("Email verification required")
                            }

                            // Suspend until code is provided
                            suspendCancellableCoroutine { continuation ->
                                verificationContinuation = continuation
                                continuation.invokeOnCancellation {
                                    if (verificationContinuation === continuation) {
                                        verificationContinuation = null
                                    }
                                }
                            }
                        }
                    )
                }

                // Save complete cookie
                cookieStore.saveCookie(result.toCookieString(), result.expiresAt)

                // Save stoken for future refresh
                cookieStore.saveStoken(
                    stoken = result.stoken,
                    ltuidV2 = result.ltuidV2,
                    ltmidV2 = result.ltmidV2,
                    accountIdV2 = result.accountIdV2,
                    accountMidV2 = result.accountMidV2
                )

                _loginState.value = LoginState.Success
            } catch (e: HoyoLabAuthClient.AuthException) {
                _captchaSession.value = null
                _loginState.value = LoginState.Error(e.message ?: "Authentication failed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _captchaSession.value = null
                _loginState.value = LoginState.Error("Unexpected error: ${e.message}")
            }
        }
    }

    fun verifyEmail() {
        val code = _verificationCode.value
        if (code.isBlank()) {
            _loginState.value = LoginState.Error("Verification code cannot be empty")
            return
        }

        // Resume the suspended authenticate coroutine with the code
        val continuation = verificationContinuation
        verificationContinuation = null
        if (continuation == null || !continuation.isActive) {
            _loginState.value = LoginState.Error("Email verification expired. Please try login again.")
            return
        }
        continuation.resume(code)

        _loginState.value = LoginState.Loading
    }

    /**
     * Request the OTP email for the pending action ticket. Triggered only by
     * the "Send OTP" button; the button is then disabled while a 60s
     * cooldown counts down.
     */
    fun sendOtp() {
        val ticket = pendingActionTicket ?: return
        if (_otpSending.value || _otpCountdown.value > 0) return
        _otpError.value = null

        otpSendJob = viewModelScope.launch {
            _otpSending.value = true
            try {
                withContext(Dispatchers.IO) {
                    authClient.sendVerificationEmail(ticket) { session ->
                        // Keep loginState as EmailVerificationRequired so the
                        // verification dialog stays visible under the captcha.
                        withContext(Dispatchers.Main) {
                            _captchaSession.value = session
                        }

                        suspendCancellableCoroutine { continuation ->
                            captchaContinuation = continuation
                            continuation.invokeOnCancellation {
                                if (captchaContinuation === continuation) {
                                    captchaContinuation = null
                                }
                            }
                        }
                    }
                }
                startOtpCountdown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _otpError.value = e.message ?: "Failed to send verification email"
            } finally {
                _otpSending.value = false
            }
        }
    }

    private fun startOtpCountdown() {
        otpCountdownJob?.cancel()
        otpCountdownJob = viewModelScope.launch {
            _otpCountdown.value = OTP_COOLDOWN_SECONDS
            while (_otpCountdown.value > 0) {
                delay(1000.milliseconds)
                _otpCountdown.value -= 1
            }
        }
    }

    private fun resetOtpState() {
        otpCountdownJob?.cancel()
        otpCountdownJob = null
        pendingActionTicket = null
        _otpCountdown.value = 0
        _otpSending.value = false
        _otpError.value = null
    }

    /**
     * Complete the currently displayed Geetest challenge. The value is the
     * complete x-rpc-aigis header (session id and base64-encoded validation).
     */
    fun submitCaptchaResult(aigisHeader: String) {
        // During an OTP send the verification dialog must stay open, so report
        // problems through otpError instead of flipping loginState.
        val duringOtpSend = _otpSending.value

        if (aigisHeader.isBlank()) {
            if (duringOtpSend) {
                _otpError.value = "Captcha verification returned an empty result"
            } else {
                _loginState.value = LoginState.Error("Captcha verification returned an empty result")
            }
            return
        }

        val continuation = captchaContinuation
        captchaContinuation = null
        _captchaSession.value = null
        if (continuation == null || !continuation.isActive) {
            if (duringOtpSend) {
                _otpError.value = "Captcha verification expired"
            } else {
                _loginState.value = LoginState.Error("Captcha verification expired. Please try login again.")
            }
            return
        }

        continuation.resume(aigisHeader)
        if (!duringOtpSend) {
            _loginState.value = LoginState.Loading
        }
    }

    /** Cancel the challenge and leave the login attempt in a recoverable state. */
    fun cancelCaptchaVerification() {
        val continuation = captchaContinuation
        captchaContinuation = null
        _captchaSession.value = null
        // During an OTP send the sendOtp coroutine turns the resumed exception
        // into otpError; keep loginState untouched so the dialog stays open.
        val duringOtpSend = _otpSending.value
        if (continuation != null && continuation.isActive) {
            continuation.resumeWithException(
                HoyoLabAuthClient.AuthException("Captcha verification was cancelled")
            )
        }
        if (!duringOtpSend) {
            _loginState.value = LoginState.Error("Captcha verification was cancelled")
        }
    }

    /**
     * Reset login state to Idle
     * Called when leaving login page or when user wants to retry
     */
    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    /** Cancel any suspended or in-flight authentication attempt. */
    fun cancelLogin() {
        cancelPendingLogin()
        resetOtpState()
        _captchaSession.value = null
        _loginState.value = LoginState.Idle
    }

    private fun cancelPendingLogin() {
        loginJob?.cancel()
        loginJob = null
        otpSendJob?.cancel()
        otpSendJob = null
        captchaContinuation?.cancel()
        verificationContinuation?.cancel()
        captchaContinuation = null
        verificationContinuation = null
    }

    override fun onCleared() {
        cancelPendingLogin()
        otpCountdownJob?.cancel()
    }

    sealed class LoginState {
        data object Idle : LoginState()
        data object Loading : LoginState()
        data object Success : LoginState()
        data class Error(val message: String) : LoginState()
        data class CaptchaRequired(val message: String) : LoginState()
        data class EmailVerificationRequired(val message: String) : LoginState()
    }

    companion object {
        private const val OTP_COOLDOWN_SECONDS = 60
    }
}
