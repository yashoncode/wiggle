package io.wiggle.domain

import io.wiggle.data.db.BodyMeasurementEntity

/**
 * The tape measurements the app tracks, in the order the step-by-step editor walks through them:
 * top of the body downwards, so the tape does not jump around.
 */
enum class BodyPart(
    val label: String,
    val hint: String,
    /** Used by the US Navy body-fat estimate. */
    val requiredForBodyFat: Boolean,
    val range: ClosedFloatingPointRange<Double>,
) {
    Neck(
        "Neck",
        "Just below the larynx, tape sloping slightly down at the front.",
        requiredForBodyFat = true,
        range = 20.0..70.0,
    ),
    Chest(
        "Chest",
        "Across the fullest part, arms relaxed at your sides.",
        requiredForBodyFat = false,
        range = 60.0..180.0,
    ),
    Waist(
        "Waist",
        "At the navel, relaxed — do not pull the tape tight or hold your breath.",
        requiredForBodyFat = true,
        range = 40.0..200.0,
    ),
    Hips(
        "Hips",
        "Around the widest part of the buttocks, feet together.",
        requiredForBodyFat = true,
        range = 50.0..200.0,
    ),
    Arm(
        "Arm",
        "Mid-bicep, arm relaxed and hanging down.",
        requiredForBodyFat = false,
        range = 15.0..80.0,
    ),
    Forearm(
        "Forearm",
        "The widest part, just below the elbow.",
        requiredForBodyFat = false,
        range = 15.0..60.0,
    ),
    Thigh(
        "Thigh",
        "Halfway between hip and knee, weight on both feet.",
        requiredForBodyFat = false,
        range = 25.0..110.0,
    ),
    Calf(
        "Calf",
        "The widest part, standing with weight evenly spread.",
        requiredForBodyFat = false,
        range = 20.0..80.0,
    ),
    ;

    fun valueOf(entry: BodyMeasurementEntity): Double? = when (this) {
        Neck -> entry.neckCm
        Chest -> entry.chestCm
        Waist -> entry.waistCm
        Hips -> entry.hipsCm
        Arm -> entry.armCm
        Forearm -> entry.forearmCm
        Thigh -> entry.thighCm
        Calf -> entry.calfCm
    }

    fun with(entry: BodyMeasurementEntity, value: Double?): BodyMeasurementEntity = when (this) {
        Neck -> entry.copy(neckCm = value)
        Chest -> entry.copy(chestCm = value)
        Waist -> entry.copy(waistCm = value)
        Hips -> entry.copy(hipsCm = value)
        Arm -> entry.copy(armCm = value)
        Forearm -> entry.copy(forearmCm = value)
        Thigh -> entry.copy(thighCm = value)
        Calf -> entry.copy(calfCm = value)
    }

    /** A sensible starting value when there is nothing to copy from a previous session. */
    val defaultCm: Double
        get() = when (this) {
            Neck -> 37.0
            Chest -> 95.0
            Waist -> 82.0
            Hips -> 96.0
            Arm -> 32.0
            Forearm -> 27.0
            Thigh -> 55.0
            Calf -> 38.0
        }
}
