package io.wiggle.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class WaterCoachTest {

    private val morning = LocalTime.of(9, 0)
    private val lateAfternoon = LocalTime.of(16, 0)

    @Test
    fun `nothing logged asks for a first glass`() {
        assertEquals(WaterTone.Empty, WaterCoach.message(0, 2500, morning).tone)
    }

    @Test
    fun `a slow morning is not scolded, a slow afternoon is`() {
        assertEquals(WaterTone.OnTrack, WaterCoach.message(400, 2500, morning).tone)
        assertEquals(WaterTone.Behind, WaterCoach.message(400, 2500, lateAfternoon).tone)
    }

    @Test
    fun `hitting the goal is a cheer, not a warning`() {
        assertEquals(WaterTone.Met, WaterCoach.message(2500, 2500, morning).tone)
        assertEquals(WaterTone.Met, WaterCoach.message(3000, 2500, morning).tone)
    }

    @Test
    fun `well past the goal gets a gentle word`() {
        assertEquals(WaterTone.Plenty, WaterCoach.message(3600, 2500, morning).tone)
    }

    @Test
    fun `a risky total warns whatever the goal is set to`() {
        assertEquals(WaterTone.TooMuch, WaterCoach.message(5200, 2500, morning).tone)
        // A goal set absurdly high must not hide the total.
        assertEquals(WaterTone.TooMuch, WaterCoach.message(6000, 9000, morning).tone)
    }

    @Test
    fun `a small goal reached twice over warns once the total is genuinely large`() {
        // 2 litres against a 1 litre goal is double, but not a risky amount of water.
        assertEquals(WaterTone.Plenty, WaterCoach.message(2000, 1000, morning).tone)
        assertEquals(WaterTone.TooMuch, WaterCoach.message(4200, 1000, morning).tone)
    }

    @Test
    fun `the warning says what to do, not just that something is wrong`() {
        val text = WaterCoach.message(5500, 2500, morning).text
        assertEquals(true, text.contains("Ease off"))
    }
}
