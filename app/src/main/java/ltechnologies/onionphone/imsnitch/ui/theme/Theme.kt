package ltechnologies.onionphone.imsnitch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenSafe = Color(0xFF1B5E20)
private val AmberWarn = Color(0xFFF9A825)
private val RedAlert = Color(0xFFB71C1C)
private val Slate = Color(0xFF263238)
private val Mist = Color(0xFFECEFF1)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003730),
    secondary = AmberWarn,
    background = Color(0xFF102027),
    surface = Slate,
    onBackground = Mist,
    onSurface = Mist,
    error = RedAlert,
)

private val LightColors = lightColorScheme(
    primary = GreenSafe,
    onPrimary = Color.White,
    secondary = AmberWarn,
    background = Mist,
    surface = Color.White,
    onBackground = Slate,
    onSurface = Slate,
    error = RedAlert,
)

@Composable
fun IMSnitchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
