package io.wiggle.domain

import kotlin.math.roundToInt

/**
 * The database stores kg, cm and ml. These are the only conversions in the app, and they belong
 * at the edge of the UI: nothing else should ever hold a pound or an inch.
 */

const val KG_PER_LB = 0.45359237
const val CM_PER_INCH = 2.54
const val ML_PER_FL_OZ = 29.5735295625

enum class WeightUnit(val label: String) {
    Kg("kg"),
    Lb("lb"),
    ;

    fun fromKg(kg: Double): Double = if (this == Kg) kg else kg / KG_PER_LB
    fun toKg(value: Double): Double = if (this == Kg) value else value * KG_PER_LB

    /** One detent of the ruler and one tap of −/+. */
    val step: Double get() = 0.1
}

enum class LengthUnit(val label: String) {
    Cm("cm"),
    In("in"),
    ;

    fun fromCm(cm: Double): Double = if (this == Cm) cm else cm / CM_PER_INCH
    fun toCm(value: Double): Double = if (this == Cm) value else value * CM_PER_INCH
}

enum class VolumeUnit(val label: String) {
    Ml("ml"),
    FlOz("fl oz"),
    ;

    fun fromMl(ml: Int): Double = if (this == Ml) ml.toDouble() else ml / ML_PER_FL_OZ
    fun toMl(value: Double): Int =
        if (this == Ml) value.roundToInt() else (value * ML_PER_FL_OZ).roundToInt()
}

/** Formats a double to [decimals] places without locale surprises in the number itself. */
fun Double.format(decimals: Int = 1): String {
    if (isNaN() || isInfinite()) return "—"
    val factor = generateSequence(1.0) { it * 10 }.elementAt(decimals)
    val rounded = Math.round(this * factor) / factor
    return if (decimals == 0) rounded.toLong().toString() else String.format("%.${decimals}f", rounded)
}

/** A signed change, e.g. "−0.6" or "+1.2". Uses a real minus sign, not a hyphen. */
fun Double.formatSigned(decimals: Int = 1): String = when {
    this > 0 -> "+" + format(decimals)
    this < 0 -> "−" + (-this).format(decimals)
    else -> format(decimals)
}

fun formatVolume(ml: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.Ml -> if (ml >= 1000) "${(ml / 100) / 10.0}" else ml.toString()
    VolumeUnit.FlOz -> unit.fromMl(ml).format(0)
}

fun volumeUnitLabel(ml: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.Ml -> if (ml >= 1000) "L" else "ml"
    VolumeUnit.FlOz -> "fl oz"
}
