package io.wiggle.domain

import java.time.LocalTime

/**
 * What the water card says about where you are.
 *
 * [Tone] is what the UI colours from: encouragement in the water blue, the goal in the same
 * blue but celebratory, and the two over-drinking tiers in amber and coral.
 */
enum class WaterTone { Empty, Behind, OnTrack, Nearly, Met, Plenty, TooMuch }

data class WaterCoachMessage(val text: String, val tone: WaterTone)

/**
 * The line under the bottle.
 *
 * The upper tiers matter more than the lower ones: drinking far past what the body can clear
 * dilutes blood sodium (hyponatremia), and it is a real risk at several litres in a short
 * stretch. The kidneys clear roughly 0.8–1.0 L an hour, so the warnings are keyed to a daily
 * total well inside that, and they say "spread it out" rather than "stop drinking".
 */
object WaterCoach {

    /** Past this, the day's total is worth a gentle word however high the goal is set. */
    const val PlentyMl = 3_500

    /** Past this, say plainly that more is not better. */
    const val TooMuchMl = 5_000

    fun message(
        totalMl: Int,
        goalMl: Int,
        now: LocalTime = LocalTime.now(),
    ): WaterCoachMessage {
        val goal = goalMl.coerceAtLeast(1)
        val ratio = totalMl.toDouble() / goal

        // Over-drinking is checked first: a high goal must not hide a risky total.
        if (totalMl >= TooMuchMl || (ratio >= 2.0 && totalMl >= 4_000)) {
            return WaterCoachMessage(
                "That is a lot of water for one day. More is not better past this point — " +
                    "too much too fast thins the salt in your blood. Ease off and eat something salty.",
                WaterTone.TooMuch,
            )
        }
        if (totalMl >= PlentyMl || ratio >= 1.5) {
            return WaterCoachMessage(
                "Well past your goal. Sip rather than gulp from here, and let thirst lead.",
                WaterTone.Plenty,
            )
        }
        if (totalMl >= goal) {
            return WaterCoachMessage(goalMetLine(ratio), WaterTone.Met)
        }

        if (totalMl <= 0) {
            return WaterCoachMessage(
                if (now.hour >= 14) "Nothing logged yet today. A glass now still counts."
                else "Nothing yet. Start with a glass.",
                WaterTone.Empty,
            )
        }

        return when {
            ratio >= 0.8 -> WaterCoachMessage("Almost there. One more glass does it.", WaterTone.Nearly)
            ratio >= 0.5 -> WaterCoachMessage("Halfway. Good pace.", WaterTone.OnTrack)
            // Behind for the hour: by mid-afternoon, under 40% of the goal is behind.
            now.hour >= 15 -> WaterCoachMessage(
                "Behind for this time of day. Two glasses would catch you up.",
                WaterTone.Behind,
            )

            else -> WaterCoachMessage("Off to a start. Keep it going.", WaterTone.OnTrack)
        }
    }

    private fun goalMetLine(ratio: Double): String = when {
        ratio >= 1.2 -> "Goal met, and then some. Nicely done."
        else -> "Goal met. That is the day done."
    }
}
