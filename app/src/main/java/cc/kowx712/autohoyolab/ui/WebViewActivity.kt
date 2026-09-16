package cc.kowx712.autohoyolab.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.data.cookie.CookieStore
import cc.kowx712.autohoyolab.network.HoyolabApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var cookieStore: CookieStore
    private var isValidating = false
    private var cookieCheckJob: Job? = null
    private var ltokenExpiresAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cookieStore = CookieStore(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                    // Intercept the specific API call that returns Set-Cookie headers
                    if (url.contains("hoyolab.com/event/sol/info")) {
                        try {
                            val client = OkHttpClient()
                            val okHttpRequest = Request.Builder()
                                .url(url)
                                .apply {
                                    request.requestHeaders?.forEach { (key, value) ->
                                        addHeader(key, value)
                                    }
                                }
                                .build()

                            val response = client.newCall(okHttpRequest).execute()

                            // Parse Set-Cookie headers for ltoken_v2
                            response.headers("Set-Cookie").forEach { setCookie ->
                                if (setCookie.contains("ltoken_v2=")) {
                                    parseSetCookieMaxAge(setCookie)
                                }
                            }

                            // Return the response to WebView
                            val body = response.body.bytes()
                            val contentType = response.header("Content-Type") ?: "application/json"
                            val encoding = response.header("Content-Encoding") ?: "utf-8"

                            return WebResourceResponse(
                                contentType.split(";")[0],
                                encoding,
                                response.code,
                                response.message,
                                response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                                ByteArrayInputStream(body)
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to intercept request: ${e.message}")
                        }
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Flush cookies to make them available to CookieManager
                    CookieManager.getInstance().flush()
                    // Start auto-click loop in JavaScript
                    startAutoClickLoop()
                    // Start polling for cookies
                    startCookiePolling()
                }
            }

            webChromeClient = android.webkit.WebChromeClient()
        }

        // Enable cookie syncing
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        val webViewContainer = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(webViewContainer)

        ViewCompat.setOnApplyWindowInsetsListener(webViewContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(webViewContainer)

        // Load HoYoLab sign-in page
        webView.loadUrl("https://act.hoyolab.com/ys/event/signin-sea-v3/index.html?act_id=e202102251931481")
    }

    private fun captureAndValidateCookie() {
        // Skip if already validating
        if (isValidating) {
            return
        }

        // Force cookie sync from WebView to CookieManager
        CookieManager.getInstance().flush()

        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://act.hoyolab.com")

        if (!cookies.isNullOrEmpty()) {
            val cookieMap = parseCookies(cookies)
            val ltokenV2 = cookieMap["ltoken_v2"]
            val ltuidV2 = cookieMap["ltuid_v2"]

            if (!ltokenV2.isNullOrEmpty() && !ltuidV2.isNullOrEmpty()) {
                lifecycleScope.launch {
                    // Use stored expiration or default to 180 days if not captured
                    val expiresAt = if (ltokenExpiresAt > 0) ltokenExpiresAt else {
                        System.currentTimeMillis() + (180L * 24 * 60 * 60 * 1000) // 180 days default
                    }
                    validateCookie(cookies, expiresAt)
                }
            }
        }
    }

    private suspend fun validateCookie(fullCookie: String, expiresAt: Long) {
        // Only validate if not already validating
        if (isValidating) {
            return
        }

        isValidating = true
        Log.d(TAG, "Cookie captured successfully")
        Log.d(TAG, "All cookies: $fullCookie")
        Log.d(TAG, "Cookie expires at: $expiresAt")

        // Validate the cookie
        try {
            val apiClient = HoyolabApiClient(fullCookie)
            val account = apiClient.validateCookie()
            val gameRoles = apiClient.getUserGameRoles()

            // Save cookie and account info with expiration date
            cookieStore.saveCookie(fullCookie, expiresAt)
            cookieStore.saveAccountInfo(
                accountId = account.accountId,
                accountName = account.accountName,
                email = account.email,
                validatedAt = account.validatedAt
            )

            Log.d(TAG, "Cookie validated for account: ${account.accountId}")
            Log.d(TAG, "Found ${gameRoles.size} game roles")

            runOnUiThread {
                Toast.makeText(
                    this@WebViewActivity,
                    getString(R.string.toast_login_success, gameRoles.size),
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate cookie: ${e.message}")
            runOnUiThread {
                Toast.makeText(
                    this@WebViewActivity,
                    getString(R.string.toast_login_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
            isValidating = false
        }
    }

    private fun startCookiePolling() {
        // Cancel existing job if any
        cookieCheckJob?.cancel()

        cookieCheckJob = lifecycleScope.launch {
            while (true) {
                captureAndValidateCookie()
                delay(2.seconds)
            }
        }
    }

    private fun startAutoClickLoop() {
        webView.evaluateJavascript(
            """
            (function() {
                // Only start if not already running
                if (window.autoClickInterval) {
                    clearInterval(window.autoClickInterval);
                }
                
                window.autoClickInterval = setInterval(function() {
                    // Try both desktop and mobile selectors
                    var element = document.querySelector(".components-home-assets-__sign-content-test_---red-point---2jUBf9") ||
                                  document.querySelector(".components-m-assets-__index_---red-point---2Ug_WL");
                    if (element) {
                        element.click();
                        console.log("Auto-clicked sign-in button");
                        clearInterval(window.autoClickInterval);
                        window.autoClickInterval = null;
                    }
                }, 200);
            })();
            """.trimIndent(),
            null
        )
    }

    private fun parseCookies(cookieString: String): Map<String, String> {
        return cookieString.split(";")
            .mapNotNull { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun parseSetCookieMaxAge(setCookieHeader: String) {
        val maxAgeRegex = Regex("Max-Age=(\\d+)", RegexOption.IGNORE_CASE)
        val match = maxAgeRegex.find(setCookieHeader)

        if (match != null) {
            val maxAgeSeconds = match.groupValues[1].toLongOrNull()
            if (maxAgeSeconds != null && maxAgeSeconds > 0) {
                ltokenExpiresAt = System.currentTimeMillis() + (maxAgeSeconds * 1000)
                Log.d(TAG, "Captured ltoken_v2 expiration: Max-Age=$maxAgeSeconds seconds")
                Log.d(TAG, "Expires at: ${java.util.Date(ltokenExpiresAt)}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel cookie polling
        cookieCheckJob?.cancel()
        // Clear the auto-click interval
        webView.evaluateJavascript(
            """
            if (window.autoClickInterval) {
                clearInterval(window.autoClickInterval);
                window.autoClickInterval = null;
            }
            """.trimIndent(),
            null
        )
        webView.destroy()
    }

    companion object {
        private const val TAG = "WebViewActivity"
    }
}
