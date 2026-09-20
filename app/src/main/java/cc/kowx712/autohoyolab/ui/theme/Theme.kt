package cc.kowx712.autohoyolab.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

private val Purple = Color(0xFF6750A4)

@Composable
fun HoyolabAutomationTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(isDark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    DynamicMaterialExpressiveTheme(
        seedColor = Purple,
        style = PaletteStyle.TonalSpot,
        motionScheme = MotionScheme.expressive(),
        isDark = isDark,
        animate = true,
        content = content,
    )
}
