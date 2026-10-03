package io.wiggle.domain

import io.wiggle.data.db.DoseLogEntity
import io.wiggle.data.db.Meal
import io.wiggle.data.db.MedicationEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.data.db.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NutritionTest {

    @Test fun `goal is maintenance less 500 when losing, rounded to 50, with a floor`() {
        // 10*72.4 + 6.25*177 - 5*30 + 5 = 1685.25; * 1.2 = 2022.3; - 500 = 1522 -> 1500
        assertEquals(1500, Nutrition.calorieGoal(72.4, 177.0, 30, Sex.Male, 68.0))
        assertEquals(2000, Nutrition.calorieGoal(72.4, 177.0, 30, Sex.Male, null))
        assertEquals(2250, Nutrition.calorieGoal(72.4, 177.0, 30, Sex.Male, 80.0))
        assertEquals(1200, Nutrition.calorieGoal(45.0, 150.0, 60, Sex.Female, 40.0))
    }

    @Test fun `macros split the goal 27 43 30`() {
        assertEquals(Macros(proteinG = 142, carbsG = 226, fatG = 70), Nutrition.macroGoals(2100))
    }

    @Test fun `walking adds half a kcal per kg per km`() {
        // 10,000 steps * 0.73455 m = 7.35 km; * 70 kg * 0.5 = 257
        assertEquals(257, Nutrition.stepKcal(10_000, 177.0, Sex.Male, 70.0))
        assertEquals(0, Nutrition.stepKcal(10_000, 177.0, Sex.Male, null))
    }

    @Test fun `age falls back to 30 and is clamped`() {
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(30, Nutrition.age(null, today))
        assertEquals(30, Nutrition.age(1996, today))
        assertEquals(14, Nutrition.age(2024, today))
    }

    @Test fun `balance skips days with nothing eaten`() {
        val d1 = LocalDate.of(2026, 10, 1)
        val d2 = LocalDate.of(2026, 10, 2)
        assertNull(Nutrition.balance(emptyMap(), emptyMap(), 2000))
        assertEquals(-700, Nutrition.balance(mapOf(d1 to 1800, d2 to 1700), mapOf(d2 to 200), 2000))
        assertEquals(1240, CalorieBudget(goal = 2100, eaten = 1160, walked = 300).left)
    }
}

class FoodSearchTest {
    private val foods = FoodSearch(
        FoodSearch.parse(
            sequenceOf(
                "name\taka\tkcal\tprotein\tcarbs\tfat\tfiber\tserving\tserving_g\tsource",
                "Toor dal\tarhar tuvar pigeon pea\t120\t7\t16\t3\t\t1 katori\t150\tIN",
                "Rice, white, cooked\tchawal\t130\t2.7\t28.2\t0.3\t0.4\t1 cup\t158\tUS",
                "Rice flour\t\t366\t6\t80\t1.4\t\t\t100\tUS",
                "Masala dosa\tdosai\t165\t3.3\t19.6\t7.8\t2.5\t1 dosa\t209.7\tIN",
                "broken row without kcal\t\tx",
            )
        )
    )

    @Test fun `parses rows and skips broken ones`() = assertEquals(4, foods.size)

    @Test fun `matches word prefixes and other names`() {
        assertEquals("Toor dal", foods.search("dal").first().name)
        assertEquals("Toor dal", foods.search("arhar").first().name)
        assertEquals("Masala dosa", foods.search("dos").first().name)
        assertEquals("Rice, white, cooked", foods.search("rice").first().name)
        assertTrue(foods.search("pizza").isEmpty())
    }

    @Test fun `portions scale from per 100 g`() {
        val dosa = foods.search("masala dosa").first()
        assertEquals(346, dosa.kcal(1.0))
        assertEquals("2 × 1 dosa", portionText(2.0, dosa.servingLabel, dosa.servingG))
        assertEquals("½ × 1 dosa", portionText(0.5, dosa.servingLabel, dosa.servingG))
        assertEquals("150 g", portionText(1.5, "", 100.0))
    }

    @Test fun `serving label drops the grams`() {
        assertEquals("1 packet", servingLabelFrom("1 packet (70 g)"))
        assertEquals("", servingLabelFrom("30 g"))
        assertEquals("2 biscuits", servingLabelFrom("2 biscuits (18.5g)"))
    }
}

class StepStatsTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test fun `streak counts back from yesterday while today is short`() {
        val daily = mapOf(
            today to 3_000L,
            today.minusDays(1) to 12_000L,
            today.minusDays(2) to 10_000L,
            today.minusDays(3) to 9_999L,
        )
        assertEquals(2, StepStats.streak(daily, 10_000, today))
        assertEquals(3, StepStats.streak(daily + (today to 10_500L), 10_000, today))
        assertEquals(0, StepStats.streak(emptyMap(), 10_000, today))
    }

    @Test fun `busiest hour and its label`() {
        assertEquals(14, StepStats.busiestHour(List(24) { if (it == 14) 1320L else 100L }))
        assertNull(StepStats.busiestHour(List(24) { 0L }))
        assertEquals("2–3 PM", StepStats.hourRange(14))
        assertEquals("11 AM–12 PM", StepStats.hourRange(11))
        assertEquals("11 PM–12 AM", StepStats.hourRange(23))
    }
}

class DosesTest {
    private val med = MedicationEntity(id = 1, profileId = 1, name = "Iron", times = "840,510", stock = 9)

    @Test fun `times parse sorted and bounded`() {
        assertEquals(listOf(510, 840), Doses.parseTimes(med.times))
        assertEquals(listOf(60), Doses.parseTimes("60, 9999, x, 60"))
    }

    @Test fun `today marks taken, overdue and due`() {
        val taken = listOf(DoseLogEntity(medicationId = 1, day = 0, slotMinutes = 510, takenAt = 0))
        assertEquals(listOf(DoseStatus.Taken, DoseStatus.Overdue), Doses.today(listOf(med), taken, 900).map { it.status })
        assertEquals(DoseStatus.Due, Doses.today(listOf(med), emptyList(), 600)[1].status)
    }

    @Test fun `stock lasts whole days`() {
        assertEquals(4, Doses.daysLeft(med))
        assertNull(Doses.daysLeft(med.copy(stock = null)))
    }

    @Test fun `nags until taken, then tomorrow`() {
        val now = LocalDateTime.of(2026, 10, 3, 14, 30)
        assertEquals(LocalDateTime.of(2026, 10, 3, 14, 45), Doses.nextAlarm(840, now, takenToday = false, repeatMinutes = 15))
        assertEquals(LocalDateTime.of(2026, 10, 4, 14, 0), Doses.nextAlarm(840, now, takenToday = true, repeatMinutes = 15))
        assertEquals(LocalDateTime.of(2026, 10, 4, 14, 0), Doses.nextAlarm(840, now.plusHours(2), takenToday = false, repeatMinutes = 15))
        assertEquals(LocalDateTime.of(2026, 10, 3, 20, 30), Doses.nextAlarm(1230, now, takenToday = false, repeatMinutes = 15))
    }
}

class MealReminderTest {
    private val meals = ReminderEntity(profileId = 1, kind = ReminderKind.Meals, enabled = true, timeMinutes = 510, times = "510,810,1230")

    @Test fun `next meal slot and which meal it is`() {
        val at = LocalDateTime.of(2026, 10, 3, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 3, 13, 30), ReminderSchedule.nextOccurrence(meals, at))
        assertEquals(Meal.Lunch, ReminderSchedule.mealAt(meals, 812))
        assertEquals(Meal.Dinner, ReminderSchedule.mealAt(meals, 1230))
        assertNull(ReminderSchedule.nextOccurrence(meals.copy(kind = ReminderKind.Tablets), at))
    }
}
