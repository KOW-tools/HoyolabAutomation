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
import cc.kowx712.autohoyolab.ui.navigation.AppNavGraph
import cc.kowx712.autohoyolab.ui.navigation.Home
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

                val startDestination = if (setupComplete) Home else Setup
                val navigator = rememberNavigator(startDestination)

                Scaffold { AppNavGraph(navigator = navigator) }
            }
        }
    }
}
