package io.wiggle.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Raw palette. Semantic tokens live in [WiggleColors]; screens should use those. */
object Palette {
    val Navy = Color(0xFF0B1020)
    val Ink = Color(0xFFF4F6FB)
    val InkDark = Color(0xFF0B1020)

    // Background glows
    val GlowTeal = Color(0xFF1FB89A)
    val GlowBlue = Color(0xFF3B6CFF)
    val GlowViolet = Color(0xFFB04DFF)
    val GlowCoral = Color(0xFFFF7A6B)

    // Accents
    val Weight = Color(0xFF5EE6C8)
    val WeightSoft = Color(0xFF7FE3D0)
    val Water = Color(0xFF6FB7FF)
    val WaterSoft = Color(0xFF9CCBFF)
    val Body = Color(0xFFFF8A7A)
    val BodySoft = Color(0xFFFFA99D)
    val Goal = Color(0xFFFFB36B)
    val GoalSoft = Color(0xFFFFC48E)
    val Toggle = Color(0xFF34C77B)

    val LightCanvas = Color(0xFFF2F5FC)
}

/**
 * Semantic design tokens. Every glass surface and accent in the app reads from here so the
 * light theme is a single swap rather than a per-screen branch.
 */
@Immutable
data class WiggleColors(
    val background: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    /** Fill of a glass card, painted over the blurred backdrop. */
    val glassFill: Color,
    /** Fill used when real backdrop blur is unavailable (below API 31). */
    val glassFillOpaque: Color,
    val glassBorder: Color,
    /** 1dp highlight along the top edge of a glass card. */
    val glassHighlight: Color,
    val glassPressedFill: Color,
    val scrim: Color,
    val divider: Color,
    val track: Color,
    val weight: Color,
    val weightSoft: Color,
    val water: Color,
    val waterSoft: Color,
    val body: Color,
    val bodySoft: Color,
    val goal: Color,
    val goalSoft: Color,
    val toggleOn: Color,
    val glowTeal: Color,
    val glowBlue: Color,
    val glowViolet: Color,
    val glowAlpha: Float,
    val onAccent: Color,
    val isDark: Boolean,
)

val DarkWiggleColors = WiggleColors(
    background = Palette.Navy,
    ink = Palette.Ink,
    inkMuted = Palette.Ink.copy(alpha = 0.72f),
    inkFaint = Palette.Ink.copy(alpha = 0.50f),
    glassFill = Color.White.copy(alpha = 0.08f),
    glassFillOpaque = Color(0xFF1A2140).copy(alpha = 0.85f),
    glassBorder = Color.White.copy(alpha = 0.16f),
    glassHighlight = Color.White.copy(alpha = 0.22f),
    glassPressedFill = Color.White.copy(alpha = 0.14f),
    scrim = Color(0xFF050812).copy(alpha = 0.45f),
    divider = Color.White.copy(alpha = 0.10f),
    track = Color.White.copy(alpha = 0.12f),
    weight = Palette.Weight,
    weightSoft = Palette.WeightSoft,
    water = Palette.Water,
    waterSoft = Palette.WaterSoft,
    body = Palette.Body,
    bodySoft = Palette.BodySoft,
    goal = Palette.Goal,
    goalSoft = Palette.GoalSoft,
    toggleOn = Palette.Toggle,
    glowTeal = Palette.GlowTeal,
    glowBlue = Palette.GlowBlue,
    glowViolet = Palette.GlowViolet,
    glowAlpha = 0.50f,
    onAccent = Color(0xFF06231D),
    isDark = true,
)

/**
 * Light theme. The glass recipe inverts: a white scrim over the glows instead of a white tint
 * over navy, and the accents darken so text on them still clears 4.5:1.
 */
val LightWiggleColors = WiggleColors(
    background = Palette.LightCanvas,
    ink = Color(0xFF111726),
    inkMuted = Color(0xFF111726).copy(alpha = 0.66f),
    // 0.60, not 0.45: against the light canvas, 0.45 measures 2.9:1 and fails WCAG AA for the
    // small text it is used on (footers, axis labels). 0.60 measures 4.5:1.
    inkFaint = Color(0xFF111726).copy(alpha = 0.60f),
    glassFill = Color.White.copy(alpha = 0.62f),
    glassFillOpaque = Color(0xFFFFFFFF).copy(alpha = 0.90f),
    glassBorder = Color.White.copy(alpha = 0.85f),
    glassHighlight = Color.White.copy(alpha = 0.95f),
    glassPressedFill = Color.White.copy(alpha = 0.78f),
    scrim = Color(0xFF0B1020).copy(alpha = 0.28f),
    divider = Color(0xFF111726).copy(alpha = 0.10f),
    track = Color(0xFF111726).copy(alpha = 0.10f),
    weight = Color(0xFF0E9C7E),
    weightSoft = Color(0xFF0B7C64),
    water = Color(0xFF2F76D8),
    waterSoft = Color(0xFF1D5CB4),
    body = Color(0xFFD9503C),
    bodySoft = Color(0xFFB03C2C),
    goal = Color(0xFFC07A22),
    goalSoft = Color(0xFF9A5F17),
    toggleOn = Color(0xFF1FA463),
    glowTeal = Palette.GlowTeal,
    glowBlue = Palette.GlowBlue,
    glowViolet = Palette.GlowViolet,
    glowAlpha = 0.30f,
    onAccent = Color.White,
    isDark = false,
)
