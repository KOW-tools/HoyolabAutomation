package cc.kowx712.autohoyolab.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.edit
import cc.kowx712.autohoyolab.ui.navigation.AppNavGraph
import cc.kowx712.autohoyolab.ui.navigation.Home
import cc.kowx712.autohoyolab.ui.navigation.IntentDispatcher
import cc.kowx712.autohoyolab.ui.navigation.Logs
import cc.kowx712.autohoyolab.ui.navigation.Setup
import cc.kowx712.autohoyolab.ui.navigation.rememberNavigator
import cc.kowx712.autohoyolab.ui.theme.HoyolabAutomationTheme
import cc.kowx712.autohoyolab.worker.AlarmScheduler

class MainActivity : ComponentActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule the daily alarm on app start
        AlarmScheduler.scheduleNextMidnight(this)

        // Handle cookie expired action
        val action = intent.getStringExtra("action")
        if (action == "cookie_expired") {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit { putBoolean("setup_complete", false) }
        }

        setContent {
            val darkMode = isSystemInDarkTheme()

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            HoyolabAutomationTheme {
                // Check if setup is complete
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val setupComplete = prefs.getBoolean("setup_complete", false)

                val intentDestination = IntentDispatcher.getDestinationFromIntent(intent)
                
                val startDestination = when {
                    setupComplete -> Home
                    else -> Setup
                }
                
                val navigator = rememberNavigator(startDestination)
                
                if (intentDestination != null && intentDestination != startDestination) {
                    LaunchedEffect(Unit) {
                        navigator.push(intentDestination)
                    }
                }

                Scaffold { AppNavGraph(navigator = navigator) }
            }
        }
    }
}
