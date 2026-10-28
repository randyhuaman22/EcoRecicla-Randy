package pe.upeu.ecorecicla.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EcoColors = lightColorScheme(
    primary = Color(0xFF176B52),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF557568),
    background = Color(0xFFF6F8F5),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
)

@Composable
fun EcoRutasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EcoColors, content = content)
}
