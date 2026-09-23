package io.wiggle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BulkParseTest {

    private val today = LocalDate.of(2026, 9, 23)

    @Test
    fun `reads the app's own csv, header and all`() {
        val result = BulkParse.parse(
            """
            date,weight_kg,note
            2026-09-20,72.40,After workout
            2026-09-21,72.10,
            """.trimIndent(),
            today = today,
        )
        assertEquals(emptyList<BulkParseError>(), result.errors)
        assertEquals(2, result.rows.size)
        assertEquals(LocalDate.of(2026, 9, 20), result.rows[0].date)
        assertEquals(72.4, result.rows[0].weight, 1e-9)
        assertEquals("After workout", result.rows[0].note)
        assertNull(result.rows[1].note)
    }

    @Test
    fun `accepts tabs, semicolons and units written into the value`() {
        val result = BulkParse.parse(
            "2026-09-20\t72.4 kg\n2026-09-21;71.9kg",
            today = today,
        )
        assertEquals(emptyList<BulkParseError>(), result.errors)
        assertEquals(2, result.rows.size)
        assertEquals(71.9, result.rows[1].weight, 1e-9)
    }

    @Test
    fun `day-first and month-first read the same digits differently`() {
        val dayFirst = BulkParse.parseDate("03/04/2026", dayFirst = true)
        val monthFirst = BulkParse.parseDate("03/04/2026", dayFirst = false)
        assertEquals(LocalDate.of(2026, 4, 3), dayFirst)
        assertEquals(LocalDate.of(2026, 3, 4), monthFirst)
    }

    @Test
    fun `an iso date is read as iso whatever the convention says`() {
        assertEquals(LocalDate.of(2026, 4, 3), BulkParse.parseDate("2026-04-03", dayFirst = true))
        assertEquals(LocalDate.of(2026, 4, 3), BulkParse.parseDate("2026-04-03", dayFirst = false))
    }

    @Test
    fun `written-out months are read`() {
        assertEquals(LocalDate.of(2026, 9, 20), BulkParse.parseDate("20 Sep 2026", dayFirst = true))
        assertEquals(LocalDate.of(2026, 9, 20), BulkParse.parseDate("20 September 2026", dayFirst = true))
    }

    @Test
    fun `pounds convert on the way in`() {
        val result = BulkParse.parse("2026-09-20,160", unit = WeightUnit.Lb, today = today)
        assertEquals(72.57, result.rows[0].weight, 0.01)
    }

    @Test
    fun `a comma decimal separator is read as a decimal point`() {
        assertEquals(72.4, BulkParse.parseWeight("72,4")!!, 1e-9)
    }

    @Test
    fun `bad lines are reported rather than dropped silently`() {
        val result = BulkParse.parse(
            """
            2026-09-20,72.4
            not a date,72.4
            2026-09-21,heavy
            2026-09-22
            2027-01-01,72.0
            2026-09-19,4.0
            """.trimIndent(),
            today = today,
        )
        assertEquals(1, result.rows.size)
        assertEquals(5, result.errors.size)
        assertTrue(result.errors.any { "future" in it.reason })
        assertTrue(result.errors.any { "outside the range" in it.reason })
        // Line numbers point at the original text, so the user can find the offending row.
        assertEquals(2, result.errors.first().lineNumber)
    }

    @Test
    fun `a repeated date keeps the last reading`() {
        val result = BulkParse.parse("2026-09-20,72.4\n2026-09-20,71.0", today = today)
        assertEquals(1, result.rows.size)
        assertEquals(71.0, result.rows[0].weight, 1e-9)
    }

    @Test
    fun `an impossible calendar date is rejected, not rolled over`() {
        assertNull(BulkParse.parseDate("31/02/2026", dayFirst = true))
    }

    @Test
    fun `export and re-import round trip`() {
        val rows = listOf(
            ParsedWeightRow(LocalDate.of(2026, 9, 20), 72.4, "note"),
            ParsedWeightRow(LocalDate.of(2026, 9, 21), 71.95, null),
        )
        val result = BulkParse.parse(BulkParse.toCsv(rows), today = today)
        assertEquals(emptyList<BulkParseError>(), result.errors)
        assertEquals(2, result.rows.size)
        assertEquals(72.4, result.rows[0].weight, 1e-9)
        assertEquals(71.95, result.rows[1].weight, 1e-9)
    }
}
