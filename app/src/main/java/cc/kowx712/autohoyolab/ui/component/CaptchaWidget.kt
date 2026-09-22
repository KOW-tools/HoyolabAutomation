package cc.kowx712.autohoyolab.ui.component

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.kowx712.autohoyolab.auth.CaptchaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWidget(
    session: CaptchaSession,
    onDismiss: () -> Unit,
    onCaptchaSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val surfaceDim = MaterialTheme.colorScheme.surfaceDim
    val isV4 = session is CaptchaSession.V4
    val sessionId = when (session) {
        is CaptchaSession.V3 -> session.session.sessionId
        is CaptchaSession.V4 -> session.session.sessionId
    }
    val v4Session = (session as? CaptchaSession.V4)?.session
    val v3Session = (session as? CaptchaSession.V3)?.session
    val sdkUrl = if (isV4) {
        "https://static.geetest.com/v4/gt4.js"
    } else {
        "https://static.geetest.com/static/js/gt.0.5.0.js"
    }
    var sdkSource by remember(session) { mutableStateOf<String?>(null) }

    LaunchedEffect(session) {
        sdkSource = null
        try {
            val source = withContext(Dispatchers.IO) {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                    .newCall(Request.Builder().url(sdkUrl).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code}")
                        }
                        response.body.string().also {
                            require(it.isNotBlank()) { "empty response" }
                        }
                    }
            }
            sdkSource = source
        } catch (_: Exception) {
            // Silently fail - user can dismiss if it doesn't load
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(scrimAlpha = 0.6f)
    ) {
        if (sdkSource != null) {
            AndroidView(
                modifier = Modifier
                    .width(340.dp)
                    .height(386.25.dp),
                factory = {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun solved(result: String) {
                                val payload = try {
                                    JSONObject(result)
                                } catch (_: Exception) {
                                    return
                                }
                                val headerData = payload.toString().toByteArray()
                                val aigis = "$sessionId;${Base64.encodeToString(headerData, Base64.NO_WRAP)}"
                                post { onCaptchaSuccess(aigis) }
                            }
                        }, "AndroidCaptcha")

                        val initCall = if (isV4) {
                            "initGeetest4({captchaId:${JSONObject.quote(v4Session!!.captchaId)},riskType:${JSONObject.quote(v4Session.riskType)},userInfo:JSON.stringify({session_id:${
                                JSONObject.quote(
                                    v4Session.sessionId
                                )
                            }}),apiServers:['gcaptcha4.captchami.com'],product:'bind',language:'en'}, function(c){c.onReady(function(){c.showCaptcha();});c.onSuccess(function(){AndroidCaptcha.solved(JSON.stringify(c.getValidate()));});});"
                        } else {
                            "initGeetest({gt:${JSONObject.quote(v3Session!!.gt)},challenge:${JSONObject.quote(v3Session.challenge)},new_captcha:${v3Session.newCaptcha},api_server:'api-na.geetest.com',https:true,product:'bind',lang:'en'}, function(c){c.onReady(function(){c.verify();});c.onSuccess(function(){AndroidCaptcha.solved(JSON.stringify(c.getValidate()));});});"
                        }
                        val initFunction = if (isV4) "initGeetest4" else "initGeetest"
                        val initScript =
                            "(function(){var attempts=0;function start(){if(typeof $initFunction !== 'function'){if(++attempts<100){setTimeout(start,100);}else{document.body.innerText='Unable to load verification SDK.';}return;}try{$initCall}catch(e){document.body.innerText='Unable to start verification: '+e.message;}}start();})();"

                        webViewClient = object : WebViewClient() {}

                        // Convert surfaceDim color to hex string
                        val red = (surfaceDim.red * 255).toInt()
                        val green = (surfaceDim.green * 255).toInt()
                        val blue = (surfaceDim.blue * 255).toInt()
                        val bgColor = String.format("#%02X%02X%02X", red, green, blue)

                        val html = """
                            <!doctype html><html><head><meta name="referrer" content="no-referrer"><meta name="viewport" content="width=device-width,initial-scale=1">
                            <script>${sdkSource!!}</script>
                            <style>body { background-color: $bgColor; margin: 0; padding: 0; }</style>
                            </head>
                            <body><script>$initScript</script></body></html>
                        """.trimIndent()

                        loadDataWithBaseURL("https://captcha.local/", html, "text/html", "UTF-8", null)
                    }
                }
            )
        }
    }
}
