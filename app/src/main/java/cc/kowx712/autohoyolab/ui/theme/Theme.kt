package cc.kowx712.autohoyolab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

private val Purple = Color(0xFF6750A4)

@Composable
fun HoyolabAutomationTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    DynamicMaterialExpressiveTheme(
        seedColor = Purple,
        style = PaletteStyle.TonalSpot,
        motionScheme = MotionScheme.expressive(),
        isDark = isDark,
        animate = true,
        content = content,
    )
}
