package com.dosetap.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Calm Focus" palette — a bespoke, ADHD-tuned identity (not Material You).
 *
 * Principles:
 *  - Calm, warm-tinted base (never stark white / pure black) to lower visual arousal.
 *  - One confident indigo accent (#5B6CFF family) reserved for the primary action, so the eye
 *    always has a single obvious focal point.
 *  - Muted, unambiguous semantic colors (success / warning / danger) — informative, not alarming.
 *  - Foreground/background pairs meet WCAG-AA.
 *
 * Variable names mirror the Material slots so every existing `colorScheme.*` usage adopts this
 * palette automatically.
 */

// ===== Light =====
val md_theme_light_primary = Color(0xFF4E5BD6)          // indigo accent (AA white text)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFE1E0FF) // soft indigo tint
val md_theme_light_onPrimaryContainer = Color(0xFF0B0764)
val md_theme_light_secondary = Color(0xFF5B5D72)        // calm slate
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE1E0F9)
val md_theme_light_onSecondaryContainer = Color(0xFF181A2C)
val md_theme_light_tertiary = Color(0xFF785900)         // warm amber (celebration / anchor)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFDF9B)
val md_theme_light_onTertiaryContainer = Color(0xFF261900)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFBF8F4)        // warm off-white
val md_theme_light_onBackground = Color(0xFF1B1B21)
val md_theme_light_surface = Color(0xFFFBF8F4)
val md_theme_light_onSurface = Color(0xFF1B1B21)
val md_theme_light_surfaceVariant = Color(0xFFE5E1EC)    // card tint
val md_theme_light_onSurfaceVariant = Color(0xFF46464F)
val md_theme_light_outline = Color(0xFF777680)
val md_theme_light_inverseOnSurface = Color(0xFFF3EFF7)
val md_theme_light_inverseSurface = Color(0xFF303036)
val md_theme_light_inversePrimary = Color(0xFFBFC2FF)
val md_theme_light_surfaceTint = Color(0xFF4E5BD6)
val md_theme_light_outlineVariant = Color(0xFFC7C5D0)
val md_theme_light_scrim = Color(0xFF000000)

// ===== Dark =====
val md_theme_dark_primary = Color(0xFFBFC2FF)           // light indigo (AA on charcoal)
val md_theme_dark_onPrimary = Color(0xFF1B2379)
val md_theme_dark_primaryContainer = Color(0xFF363E9E)
val md_theme_dark_onPrimaryContainer = Color(0xFFE1E0FF)
val md_theme_dark_secondary = Color(0xFFC4C4DD)
val md_theme_dark_onSecondary = Color(0xFF2D3042)
val md_theme_dark_secondaryContainer = Color(0xFF434559)
val md_theme_dark_onSecondaryContainer = Color(0xFFE1E0F9)
val md_theme_dark_tertiary = Color(0xFFF4BE48)          // warm amber
val md_theme_dark_onTertiary = Color(0xFF3F2E00)
val md_theme_dark_tertiaryContainer = Color(0xFF5B4300)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFDF9B)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF131318)        // soft charcoal, not pure black
val md_theme_dark_onBackground = Color(0xFFE5E1E9)
val md_theme_dark_surface = Color(0xFF131318)
val md_theme_dark_onSurface = Color(0xFFE5E1E9)
val md_theme_dark_surfaceVariant = Color(0xFF46464F)
val md_theme_dark_onSurfaceVariant = Color(0xFFC8C5D0)
val md_theme_dark_outline = Color(0xFF918F9A)
val md_theme_dark_inverseOnSurface = Color(0xFF1B1B21)
val md_theme_dark_inverseSurface = Color(0xFFE5E1E9)
val md_theme_dark_inversePrimary = Color(0xFF4E5BD6)
val md_theme_dark_surfaceTint = Color(0xFFBFC2FF)
val md_theme_dark_outlineVariant = Color(0xFF46464F)
val md_theme_dark_scrim = Color(0xFF000000)

// ===== Shared brand accents (for bespoke, non-Material surfaces) =====
/** The vivid brand indigo — use for hero emphasis where a fixed accent is wanted. */
val BrandIndigo = Color(0xFF5B6CFF)
/** Muted success / danger for celebratory or cautionary moments (both themes). */
val CalmSuccess = Color(0xFF3E7D4F)
val CalmWarning = Color(0xFFB26A00)
