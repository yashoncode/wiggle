package io.wiggle.domain

import io.wiggle.data.db.CustomMeasureTypeEntity

/**
 * One thing to measure, whether it is one of the eight built in or one the user added.
 *
 * The editor and the Body screen only ever see this, so a custom measurement walks through the
 * same steps, the same ruler and the same cards as a built-in one.
 */
data class MeasureSpec(
    /** Stable across sessions: the enum name for a built-in, "custom:<id>" for a custom type. */
    val key: String,
    val label: String,
    val hint: String,
    val range: ClosedFloatingPointRange<Double>,
    val defaultCm: Double,
    /** Where the tape band sits on the diagram. */
    val anchor: BodyPart,
    /** Set for the eight built-in measurements; null for a user-defined one. */
    val builtIn: BodyPart?,
    /** Set for a user-defined measurement; null for a built-in one. */
    val customTypeId: Long?,
    val requiredForBodyFat: Boolean,
) {
    val isCustom: Boolean get() = customTypeId != null

    companion object {
        fun of(part: BodyPart) = MeasureSpec(
            key = part.name,
            label = part.label,
            hint = part.hint,
            range = part.range,
            defaultCm = part.defaultCm,
            anchor = part,
            builtIn = part,
            customTypeId = null,
            requiredForBodyFat = part.requiredForBodyFat,
        )

        fun of(type: CustomMeasureTypeEntity) = MeasureSpec(
            key = "custom:${type.id}",
            label = type.name,
            hint = "Your own measurement. Take it the same way each time so the numbers compare.",
            range = type.minCm..type.maxCm,
            defaultCm = type.defaultCm,
            anchor = runCatching { BodyPart.valueOf(type.anchor) }.getOrDefault(BodyPart.Waist),
            builtIn = null,
            customTypeId = type.id,
            requiredForBodyFat = false,
        )

        /** The eight built-ins first, in tape order, then the user's own in their chosen order. */
        fun all(customTypes: List<CustomMeasureTypeEntity>): List<MeasureSpec> =
            BodyPart.entries.map(::of) + customTypes.map(::of)
    }
}
