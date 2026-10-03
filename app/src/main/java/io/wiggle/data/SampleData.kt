package io.wiggle.data

import io.wiggle.data.db.BodyMeasurementEntity
import io.wiggle.data.db.Meal
import io.wiggle.data.db.MedicationEntity
import io.wiggle.domain.Food
import io.wiggle.data.db.ReminderKind
import io.wiggle.data.db.Sex
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fills a fresh debug install so every screen has something real to draw. Deliberately not a
 * clean curve: gaps, a plateau and day-to-day noise are what make the moving average and the
 * projection worth looking at.
 */
object SampleData {

    suspend fun seed(repo: WiggleRepository, zone: ZoneId = ZoneId.systemDefault()) {
        val today = LocalDate.now(zone)
        val random = Random(20260922)

        val yashId = repo.createProfile(
            name = "Yash",
            heightCm = 177.0,
            sex = Sex.Male,
            goalWeightKg = 68.0,
            birthYear = 1996,
            dailyWaterGoalMl = 2500,
        )
        seedWeights(repo, yashId, today, zone, random, startKg = 80.0, endKg = 72.4, days = 120)
        seedBody(repo, yashId, today, zone)
        seedWater(repo, yashId, today, zone, random, goalMl = 2500)
        seedFood(repo, yashId, today, zone)
        seedTablets(repo, yashId)

        // A second person, so the profile switcher has something to switch to.
        val partnerId = repo.createProfile(
            name = "Meera",
            heightCm = 163.0,
            sex = Sex.Female,
            goalWeightKg = 58.0,
            birthYear = 1997,
            dailyWaterGoalMl = 2200,
        )
        seedWeights(repo, partnerId, today, zone, Random(7), startKg = 62.0, endKg = 60.2, days = 60)
        seedWater(repo, partnerId, today, zone, Random(7), goalMl = 2200)

        repo.selectProfile(yashId)

        // Turn the weigh-in reminder on so the "Up next" list is not empty on first run.
        repo.reminder(yashId, ReminderKind.WeighIn)?.let {
            repo.saveReminder(it.copy(enabled = true))
        }
        repo.reminder(yashId, ReminderKind.Water)?.let {
            repo.saveReminder(it.copy(enabled = true))
        }

        // Sample data stands in for setup, so the debug build must not ask for it again.
        repo.setOnboardingComplete(true)
    }

    private suspend fun seedWeights(
        repo: WiggleRepository,
        profileId: Long,
        today: LocalDate,
        zone: ZoneId,
        random: Random,
        startKg: Double,
        endKg: Double,
        days: Int,
    ) {
        for (offset in days downTo 0) {
            // Skip about one day in five, the way a real log has gaps.
            if (random.nextInt(5) == 0 && offset != 0) continue
            val progress = 1.0 - offset.toDouble() / days
            // A plateau in the middle third, then the loss resumes.
            val eased = when {
                progress < 0.33 -> progress * 0.9
                progress < 0.6 -> 0.30 + (progress - 0.33) * 0.25
                else -> 0.37 + (progress - 0.6) * 1.575
            }.coerceIn(0.0, 1.0)
            val base = startKg + (endKg - startKg) * eased
            val noise = sin(offset * 1.7) * 0.22 + (random.nextDouble() - 0.5) * 0.35
            val date = today.minusDays(offset.toLong())
            val at = date.atTime(LocalTime.of(7, random.nextInt(0, 45)))
                .atZone(zone).toInstant().toEpochMilli()
            repo.addWeight(
                profileId = profileId,
                weightKg = Math.round((base + noise) * 10) / 10.0,
                measuredAt = at,
                note = if (offset == 0) "Before breakfast" else null,
            )
        }
    }

    private suspend fun seedBody(
        repo: WiggleRepository,
        profileId: Long,
        today: LocalDate,
        zone: ZoneId,
    ) {
        // One tape session a fortnight for three months. A tape reading is never repeatable to
        // the millimetre, so each one carries a little noise; without it the sparklines are
        // perfectly straight and nothing on the screen looks like real data.
        val random = Random(4242)
        val sessions = 7
        fun reading(from: Double, to: Double, t: Double, jitter: Double) =
            Math.round((from + (to - from) * t + (random.nextDouble() - 0.5) * jitter) * 10) / 10.0

        for (index in sessions - 1 downTo 0) {
            val t = (sessions - 1 - index).toDouble() / (sessions - 1)
            val at = today.minusDays(index * 14L).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
            repo.addBodyMeasurement(
                BodyMeasurementEntity(
                    profileId = profileId,
                    measuredAt = at,
                    neckCm = reading(38.0, 37.5, t, 0.4),
                    chestCm = reading(99.5, 98.0, t, 0.8),
                    waistCm = reading(88.0, 82.5, t, 1.0),
                    hipsCm = reading(99.0, 96.0, t, 0.8),
                    armCm = reading(31.4, 32.0, t, 0.5),
                    thighCm = reading(57.0, 55.5, t, 0.8),
                    calfCm = reading(38.5, 38.1, t, 0.5),
                    forearmCm = reading(27.5, 27.7, t, 0.4),
                )
            )
        }
    }

    private suspend fun seedWater(
        repo: WiggleRepository,
        profileId: Long,
        today: LocalDate,
        zone: ZoneId,
        random: Random,
        goalMl: Int,
    ) {
        for (offset in 13 downTo 0) {
            val date = today.minusDays(offset.toLong())
            // Today is deliberately part-way through, so the bottle is not already full.
            val target = if (offset == 0) (goalMl * 0.56).toInt() else (goalMl * (0.6 + random.nextDouble() * 0.6)).toInt()
            var poured = 0
            var hour = 8
            while (poured < target && hour < 22) {
                val amount = listOf(150, 250, 250, 500).random(random)
                repo.addWater(
                    profileId = profileId,
                    amountMl = amount,
                    at = date.atTime(hour, random.nextInt(0, 59)).atZone(zone).toInstant().toEpochMilli(),
                )
                poured += amount
                hour += 1 + random.nextInt(2)
            }
        }
    }

    /** A week of meals, with today stopped before dinner so the Calories screen has a gap to show. */
    private suspend fun seedFood(repo: WiggleRepository, profileId: Long, today: LocalDate, zone: ZoneId) {
        fun food(name: String, kcal: Double, p: Double, c: Double, f: Double, label: String, grams: Double) =
            Food(name, kcal, p, c, f, label, grams, source = "IN")
        val oats = food("Oats porridge", 110.0, 4.0, 18.0, 2.5, "1 bowl", 250.0)
        val banana = food("Banana", 89.0, 1.1, 22.8, 0.3, "1 medium", 118.0)
        val chapati = food("Chapati", 297.0, 9.0, 51.0, 7.0, "1 chapati", 40.0)
        val dal = food("Toor dal", 120.0, 7.0, 16.0, 3.0, "1 katori", 150.0)
        val rice = food("Rice, cooked", 130.0, 2.7, 28.0, 0.3, "1 cup", 158.0)
        val curd = food("Curd", 60.0, 3.1, 4.7, 3.3, "1 katori", 100.0)
        val paneer = food("Paneer tikka", 265.0, 18.0, 5.0, 19.0, "1 plate", 100.0)
        for (offset in 7 downTo 0) {
            val date = today.minusDays(offset.toLong())
            fun at(hour: Int) = date.atTime(hour, 15).atZone(zone).toInstant().toEpochMilli()
            repo.addFoods(profileId, Meal.Breakfast, listOf(oats to 1.0, banana to 1.0), at(8))
            repo.addFoods(profileId, Meal.Lunch, listOf(chapati to 2.0, dal to 1.0, curd to 1.0), at(13))
            repo.addFoods(profileId, Meal.Snacks, listOf(banana to 1.0), at(17))
            if (offset > 0) repo.addFoods(profileId, Meal.Dinner, listOf(rice to 1.0, paneer to 1.0), at(20))
        }
    }

    private suspend fun seedTablets(repo: WiggleRepository, profileId: Long) {
        listOf(
            MedicationEntity(profileId = profileId, name = "Multivitamin", note = "after breakfast", times = "510", colorIndex = 0),
            MedicationEntity(profileId = profileId, name = "Omega-3", note = "with food", times = "510", stock = 6, colorIndex = 1),
            MedicationEntity(profileId = profileId, name = "Iron", note = "after lunch", times = "840", colorIndex = 2),
            MedicationEntity(profileId = profileId, name = "Vitamin D3", note = "after dinner", times = "1230", colorIndex = 3),
        ).forEach { repo.saveMedication(it) }
    }
}
