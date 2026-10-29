package pe.upeu.ecorecicla.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EcoColors = lightColorScheme(
    primary = Color(0xFF126B54),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F3E5),
    onPrimaryContainer = Color(0xFF08382A),
    secondary = Color(0xFF4C6E64),
    secondaryContainer = Color(0xFFE0EDE6),
    tertiary = Color(0xFF996400),
    background = Color(0xFFF4F7F3),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF0EC),
    outlineVariant = Color(0xFFD4E0D8),
    error = Color(0xFFB3261E),
)

@Composable
fun EcoRutasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EcoColors, content = content)
}
