package io.wiggle.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.wiggle.R

private fun sora(weight: Int) = Font(
    R.font.sora,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Sora: titles and every number. */
val Sora = FontFamily(sora(400), sora(500), sora(600), sora(700))

/** Manrope: body copy, labels, buttons. */
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700))

/** Monospaced digits, so rolling counters do not jitter their layout. */
const val TabularFigures = "tnum"

val WiggleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 72.sp,
        lineHeight = 76.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TabularFigures,
    ),
    displayMedium = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.sp,
        lineHeight = 54.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TabularFigures,
    ),
    displaySmall = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.sp,
        lineHeight = 40.sp, letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp,
        lineHeight = 34.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TabularFigures,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp,
        lineHeight = 28.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TabularFigures,
    ),
    titleLarge = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp,
        lineHeight = 26.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TabularFigures,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp,
    ),
)

/** The uppercase date line that sits above every screen title. */
val EyebrowStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.06.em,
)

/** Any style used for a number gets tabular figures. */
fun TextStyle.tabular() = copy(fontFeatureSettings = TabularFigures)

fun TextStyle.centered() = copy(textAlign = TextAlign.Center)

