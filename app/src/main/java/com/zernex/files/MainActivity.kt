package com.zernex.files

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor

/**
 * ZERNEX Files 2.0 visual contract:
 * near-black / graphite surfaces, white text, and exactly three semantic accents.
 */
internal object ZernexPalette {
    val background = Color(0xFF0B0B0C)
    val surface = Color(0xFF151516)
    val surfaceVariant = Color(0xFF222224)
    val primary = Color(0xFFF4C542)   // yellow: primary action / focus
    val secondary = Color(0xFF38C978) // green: documents / success
    val tertiary = Color(0xFFE34D4D)  // red: media / destructive / warning
    val yellow = Color(0xFFF4C542)
    val green = Color(0xFF38C978)
    val red = Color(0xFFE34D4D)
    val text = Color(0xFFF5F5F5)
    val muted = Color(0xFFB8B8BA)
    val divider = Color(0xFF343437)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ZernexPalette.primary,
                    onPrimary = Color(0xFF171307),
                    primaryContainer = Color(0xFF3D310A),
                    onPrimaryContainer = Color(0xFFFFE9A3),
                    secondary = ZernexPalette.secondary,
                    onSecondary = Color(0xFF06180D),
                    secondaryContainer = Color(0xFF10331F),
                    onSecondaryContainer = Color(0xFFB9F5D0),
                    tertiary = ZernexPalette.tertiary,
                    onTertiary = Color(0xFF210707),
                    tertiaryContainer = Color(0xFF451414),
                    onTertiaryContainer = Color(0xFFFFDAD7),
                    background = ZernexPalette.background,
                    surface = ZernexPalette.surface,
                    surfaceVariant = ZernexPalette.surfaceVariant,
                    onBackground = ZernexPalette.text,
                    onSurface = ZernexPalette.text,
                    onSurfaceVariant = ZernexPalette.muted,
                    inverseSurface = Color(0xFFE7E7E8),
                    inverseOnSurface = Color(0xFF171718),
                    surfaceTint = ZernexPalette.primary,
                    outline = Color(0xFF66666A),
                    outlineVariant = ZernexPalette.divider,
                    error = ZernexPalette.red,
                    onError = Color(0xFF210707)
                )
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    AdvancedFileManagerScreen()
                }
            }
        }
    }
}
