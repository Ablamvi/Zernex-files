package com.zernex.files

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor

/** Palette principale : surfaces bleu nuit, accents chauds et couleurs de fichiers. */
internal object ZernexPalette {
    val background = Color(0xFF101522)
    val surface = Color(0xFF192235)
    val surfaceVariant = Color(0xFF242F45)
    val primary = Color(0xFFFFB45C)
    val secondary = Color(0xFF45D1C1)
    val tertiary = Color(0xFF9A8CFF)
    val blue = Color(0xFF68B9FF)
    val green = Color(0xFF62D49A)
    val red = Color(0xFFFF7185)
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFFD0DAEA)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ZernexPalette.primary,
                    onPrimary = Color(0xFF2B1A08),
                    primaryContainer = Color(0xFF4C3219),
                    onPrimaryContainer = Color(0xFFFFDDB8),
                    secondary = ZernexPalette.secondary,
                    onSecondary = Color(0xFF062B27),
                    secondaryContainer = Color(0xFF123F3A),
                    onSecondaryContainer = Color(0xFFB8FFF4),
                    tertiary = ZernexPalette.tertiary,
                    onTertiary = Color(0xFF211A48),
                    background = ZernexPalette.background,
                    surface = ZernexPalette.surface,
                    surfaceVariant = ZernexPalette.surfaceVariant,
                    onBackground = ZernexPalette.text,
                    onSurface = ZernexPalette.text,
                    onSurfaceVariant = ZernexPalette.muted,
                    inverseSurface = Color(0xFFE7ECF5),
                    inverseOnSurface = Color(0xFF182033),
                    surfaceTint = ZernexPalette.primary,
                    outline = Color(0xFF53627B),
                    outlineVariant = Color(0xFF344158),
                    error = ZernexPalette.red,
                    onError = Color(0xFF3A0710)
                )
            ) {
                // Texte par défaut clair : évite les libellés noirs hérités dans les composants personnalisés.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    AdvancedFileManagerScreen()
                }
            }
        }
    }
}
