package io.wiggle.domain

import io.wiggle.data.db.WaterEntryEntity
import io.wiggle.data.db.WeightEntryEntity
import org.junit.Assert.assertEquals

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CsvTest {

    private val zone = ZoneId.of("UTC")

    private fun millis(date: String, hour: Int = 9) =
        LocalDate.parse(date).atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli()

    @Test
    fun `weights export writes a header and one row per entry, oldest first`() {
        val csv = Csv.weights(
            listOf(
                WeightEntryEntity(id = 2, profileId = 1, measuredAt = millis("2026-03-02"), weightKg = 81.0),
                WeightEntryEntity(id = 1, profileId = 1, measuredAt = millis("2026-03-01"), weightKg = 80.25),
            ),
            zone,
        )

        assertEquals(
            listOf("Date,Weight (kg),Note", "2026-03-01,80.25,", "2026-03-02,81.00,"),
            csv.trim().lines(),
        )
    }

    @Test
    fun `a note containing a comma survives the round trip`() {
        val csv = Csv.weights(
            listOf(
                WeightEntryEntity(
                    id = 1,
                    profileId = 1,
                    measuredAt = millis("2026-03-01"),
                    weightKg = 80.0,
                    note = "after a run, before breakfast",
                ),
            ),
            zone,
        )

        // Quoted, so the comma inside the note does not split the row into four fields.
        assertEquals("2026-03-01,80.00,\"after a run, before breakfast\"", csv.trim().lines()[1])
    }

    /** The export is only finished if the app can read it back in. */
    @Test
    fun `an exported weight file imports cleanly`() {
        val entries = listOf(
            WeightEntryEntity(id = 1, profileId = 1, measuredAt = millis("2026-03-01"), weightKg = 80.4),
            WeightEntryEntity(id = 2, profileId = 1, measuredAt = millis("2026-03-05"), weightKg = 79.8),
        )

        val result = BulkParse.parse(
            text = Csv.weights(entries, zone),
            unit = WeightUnit.Kg,
            today = LocalDate.parse("2026-04-01"),
        )

        assertEquals(emptyList<BulkParseError>(), result.errors)
        assertEquals(listOf(80.4, 79.8), result.rows.map { it.weight })
        assertEquals(
            listOf(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-05")),
            result.rows.map { it.date },
        )
    }

    @Test
    fun `water export records the time of day, not just the date`() {
        val csv = Csv.water(
            listOf(WaterEntryEntity(id = 1, profileId = 1, loggedAt = millis("2026-03-01", hour = 14), amountMl = 250)),
            zone,
        )

        assertEquals(listOf("Date,Time,Amount (ml)", "2026-03-01,14:00,250"), csv.trim().lines())
    }
}
