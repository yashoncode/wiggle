package io.wiggle.domain

import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.Sex
import java.time.LocalDate
import kotlin.math.roundToInt

/** Grams of protein, carbohydrate and fat. */
data class Macros(val proteinG: Int, val carbsG: Int, val fatG: Int)

/**
 * Calorie and walking arithmetic. Pure, so every rule here is tested without a device. All of it is
 * an estimate and the screens say so.
 */
object Nutrition {

    /** Age used when no birth year was given: the middle of the range the formulas were fitted on. */
    const val DEFAULT_AGE = 30

    fun age(birthYear: Int?, today: LocalDate = LocalDate.now()): Int =
        birthYear?.let { today.year - it }?.coerceIn(14, 100) ?: DEFAULT_AGE

    /** Mifflin–St Jeor resting energy in kcal a day. Unspecified sex takes the midpoint. */
    fun restingKcal(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (sex) {
            Sex.Male -> base + 5
            Sex.Female -> base - 161
            Sex.Unspecified -> base - 78
        }
    }

    /**
     * Sedentary maintenance, resting energy times 1.2. Walking is deliberately left out: it comes in
     * from the step count each day, so counting an "active" multiplier as well would count it twice.
     */
    fun maintenanceKcal(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double =
        restingKcal(weightKg, heightCm, age, sex) * 1.2

    /**
     * The daily goal when the person has not set one: maintenance, 500 under it to lose (about
     * 0.45 kg a week) or 250 over it to gain, rounded to 50 and never below a floor that is safe
     * without supervision.
     */
    fun calorieGoal(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        sex: Sex,
        goalWeightKg: Double?,
    ): Int {
        val maintenance = maintenanceKcal(weightKg, heightCm, age, sex)
        val adjust = when {
            goalWeightKg == null -> 0
            goalWeightKg < weightKg - 0.5 -> -500
            goalWeightKg > weightKg + 0.5 -> 250
            else -> 0
        }
        val floor = if (sex == Sex.Male) 1500 else 1200
        return (((maintenance + adjust) / 50).roundToInt() * 50).coerceAtLeast(floor)
    }

    /** A 27 / 43 / 30 split of the goal across protein, carbohydrate and fat. */
    fun macroGoals(kcal: Int): Macros = Macros(
        proteinG = (kcal * 0.27 / 4).roundToInt(),
        carbsG = (kcal * 0.43 / 4).roundToInt(),
        fatG = (kcal * 0.30 / 9).roundToInt(),
    )

    /** Walking stride from height, the usual 0.415 / 0.413 rule of thumb. */
    fun strideMetres(heightCm: Double, sex: Sex): Double = heightCm / 100 * when (sex) {
        Sex.Male -> 0.415
        Sex.Female -> 0.413
        Sex.Unspecified -> 0.414
    }

    fun distanceKm(steps: Long, heightCm: Double, sex: Sex): Double =
        steps * strideMetres(heightCm, sex) / 1000

    /**
     * Energy spent walking on top of resting: about 0.5 kcal per kg per km. The net figure, because
     * the goal already pays for the resting part.
     */
    fun stepKcal(steps: Long, heightCm: Double, sex: Sex, weightKg: Double?): Int {
        val weight = weightKg ?: return 0
        return (distanceKm(steps, heightCm, sex) * weight * 0.5).roundToInt()
    }

    /** Minutes on foot, at the hundred steps a minute of an ordinary walk. */
    fun activeMinutes(steps: Long): Int = (steps / 100).toInt()

    /**
     * Energy balance over the days that have food logged: eaten, less maintenance and walking.
     * Negative is a deficit. Days with nothing logged are left out rather than counted as fasting,
     * and with no food logged at all there is no balance to report.
     */
    fun balance(eatenByDay: Map<LocalDate, Int>, walkedByDay: Map<LocalDate, Int>, maintenance: Int): Int? {
        if (eatenByDay.isEmpty()) return null
        return eatenByDay.entries.sumOf { (date, eaten) -> eaten - maintenance - (walkedByDay[date] ?: 0) }
    }

    /** The person's own goal when they set one, otherwise the one worked out from their numbers. */
    fun goalFor(profile: ProfileEntity, currentKg: Double?, today: LocalDate = LocalDate.now()): Int =
        profile.dailyCalorieGoal ?: calorieGoal(
            weightKg = currentKg ?: profile.startWeightKg ?: profile.goalWeightKg ?: 70.0,
            heightCm = profile.heightCm,
            age = age(profile.birthYear, today),
            sex = profile.sex,
            goalWeightKg = profile.goalWeightKg,
        )
}

/** One day's calories: what the goal allows, what was eaten, and what walking added to it. */
data class CalorieBudget(val goal: Int, val eaten: Int, val walked: Int) {
    val left: Int get() = goal - eaten + walked

    /** How much of the day's allowance is eaten, 1 at the allowance and above it past. */
    val progress: Float get() = if (goal + walked <= 0) 0f else eaten.toFloat() / (goal + walked)
}
