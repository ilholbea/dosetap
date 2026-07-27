package com.dosetap.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colorful, ADHD-friendly accent set that harmonizes with the "Calm Focus" indigo.
 *
 * Rationale: a wall of same-colored grey icons is hard to scan and easy to tune out. Giving each
 * function its own stable hue turns the UI into a color-coded map — faster to recognize, easier to
 * remember, and less monotonous. Colors are saturated enough to differentiate but not neon-harsh,
 * and each has a lighter variant so it stays legible on the dark charcoal ground.
 */
data class Accents(
    val indigo: Color,
    val teal: Color,
    val green: Color,
    val amber: Color,
    val coral: Color,
    val violet: Color,
    val blue: Color,
    val pink: Color,
)

private val AccentsLight = Accents(
    indigo = Color(0xFF5B6CFF),
    teal = Color(0xFF0E9C9C),
    green = Color(0xFF2E9E5B),
    amber = Color(0xFFCF8A17),
    coral = Color(0xFFE05C54),
    violet = Color(0xFF8B5CF6),
    blue = Color(0xFF2C7BE5),
    pink = Color(0xFFD8558F),
)

private val AccentsDark = Accents(
    indigo = Color(0xFF9AA4FF),
    teal = Color(0xFF4DD6D6),
    green = Color(0xFF63C98A),
    amber = Color(0xFFF2B84B),
    coral = Color(0xFFFF8A82),
    violet = Color(0xFFBFA3FF),
    blue = Color(0xFF6FA9F5),
    pink = Color(0xFFF287B7),
)

/** The theme-appropriate accent palette (auto light/dark). */
@Composable
fun accents(): Accents = if (isSystemInDarkTheme()) AccentsDark else AccentsLight

// Semantic status hues — one consistent meaning across History, Stats, Home, notifications.
@Composable fun statusTaken(): Color = accents().green
@Composable fun statusDue(): Color = accents().amber
@Composable fun statusMissed(): Color = accents().coral
@Composable fun statusSnoozed(): Color = accents().blue

/**
 * A leading icon drawn in [accent], sitting on a soft tint of the same hue. The standard building
 * block for colorful list rows (Settings, Paywall, Onboarding). Container tint is a touch stronger
 * in dark mode so it reads on charcoal.
 */
@Composable
fun IconChip(
    icon: ImageVector,
    accent: Color,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val containerAlpha = if (isSystemInDarkTheme()) 0.22f else 0.14f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(accent.copy(alpha = containerAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}
