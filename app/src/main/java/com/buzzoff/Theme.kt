package com.buzzoff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BrandPink = Color(0xFF7C3085)
val BrandViolet = Color(0xFF611F69)
val BrandIndigo = Color(0xFF3F0E40)

/** Slack-style aubergine gradient used for headers, buttons and the ringing screen. */
val BrandGradient = Brush.linearGradient(listOf(BrandViolet, BrandIndigo))
val BrandGradientTall = Brush.verticalGradient(listOf(BrandPink, BrandViolet, BrandIndigo))

private val LightColors = lightColorScheme(
    primary = Color(0xFF611F69),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E1F5),
    onPrimaryContainer = Color(0xFF3A0A3D),
    secondary = Color(0xFF7C3085),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E6F7),
    onSecondaryContainer = Color(0xFF3A0A3D),
    background = Color(0xFFFBF6FB),
    onBackground = Color(0xFF1F1320),
    surface = Color(0xFFFBF6FB),
    onSurface = Color(0xFF1F1320),
    surfaceVariant = Color(0xFFF0E4F0),
    onSurfaceVariant = Color(0xFF5E4C60),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFAFD),
    surfaceContainer = Color(0xFFF6ECF6),
    surfaceContainerHigh = Color(0xFFFCF8FC),
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF7F6C82),
    outlineVariant = Color(0xFFDCC8DD),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFE4E8),
    onErrorContainer = Color(0xFF5C0011),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8B4EE),
    onPrimary = Color(0xFF4A154B),
    primaryContainer = Color(0xFF611F69),
    onPrimaryContainer = Color(0xFFF9DDFB),
    secondary = Color(0xFFD9A6DF),
    onSecondary = Color(0xFF3F0E40),
    secondaryContainer = Color(0xFF4E2450),
    onSecondaryContainer = Color(0xFFF9DDFB),
    background = Color(0xFF1A0D1B),
    onBackground = Color(0xFFF0E1F0),
    surface = Color(0xFF1A0D1B),
    onSurface = Color(0xFFF0E1F0),
    surfaceVariant = Color(0xFF33223A),
    onSurfaceVariant = Color(0xFFD3C0D5),
    surfaceContainerLowest = Color(0xFF140914),
    surfaceContainerLow = Color(0xFF211222),
    surfaceContainer = Color(0xFF261627),
    surfaceContainerHigh = Color(0xFF2E1C2F),
    surfaceContainerHighest = Color(0xFF2A192B),
    outline = Color(0xFF9C889E),
    outlineVariant = Color(0xFF4A3A4C),
    errorContainer = Color(0xFF5C1024),
    onErrorContainer = Color(0xFFFFD9DF),
)

@Composable
fun BuzzOffTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** Pill-shaped button filled with the brand gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .height(height)
            .shadow(if (enabled) 6.dp else 0.dp, RoundedCornerShape(50), ambientColor = BrandViolet, spotColor = BrandViolet)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) BrandGradient else SolidColor(Color.Gray.copy(alpha = 0.35f)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) { leading?.invoke() }
            Text(
                text,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Top bar drawn on the brand gradient, extending under the status bar. */
@Composable
fun GradientTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(BrandGradient)
            .statusBarsPadding()
            .height(64.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
        }
    }
}
