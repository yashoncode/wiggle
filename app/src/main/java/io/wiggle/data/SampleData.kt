package io.wiggle.data

import io.wiggle.data.db.BodyMeasurementEntity
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
}
