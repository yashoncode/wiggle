package io.wiggle.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** One row understood from pasted or imported text. */
data class ParsedWeightRow(val date: LocalDate, val weight: Double, val note: String? = null)

/** A line that could not be read, kept so the user is told rather than silently losing data. */
data class BulkParseError(val lineNumber: Int, val line: String, val reason: String)

data class BulkParseResult(
    val rows: List<ParsedWeightRow>,
    val errors: List<BulkParseError>,
)

/**
 * Reads pasted text or a CSV export into dated weights.
 *
 * Deliberately forgiving about separators and date formats, because the text comes from another
 * app's export, a spreadsheet or a notes file, and rejecting the whole paste over one odd line
 * is how people lose a year of history.
 */
object BulkParse {

    // uuuu rather than yyyy, and STRICT rather than the default, so 31 February is rejected
    // instead of being quietly rolled back to the 28th. Locale is pinned because these come from
    // machine exports, which write English month names whatever the phone is set to.
    private val DateFormats = listOf(
        "uuuu-MM-dd",
        "uuuu/MM/dd",
        "dd-MM-uuuu",
        "dd/MM/uuuu",
        "d MMM uuuu",
        "d MMMM uuuu",
        "MMM d uuuu",
        "dd.MM.uuuu",
    ).map {
        DateTimeFormatter.ofPattern(it, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }

    /**
     * [dayFirst] resolves the genuinely ambiguous case: 03/04/2026 is 3 April in most of the
     * world and 4 March in the United States. There is no way to tell from the text, so the
     * caller states which convention the source used.
     */
    fun parse(
        text: String,
        unit: WeightUnit = WeightUnit.Kg,
        dayFirst: Boolean = true,
        today: LocalDate = LocalDate.now(),
    ): BulkParseResult {
        val rows = mutableListOf<ParsedWeightRow>()
        val errors = mutableListOf<BulkParseError>()

        text.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            // Skip a header row, and anything obviously a comment.
            if (line.startsWith("#")) return@forEachIndexed
            if (index == 0 && line.lowercase().let { "date" in it && ("weight" in it || "kg" in it || "lb" in it) }) {
                return@forEachIndexed
            }

            val parts = line.split(',', ';', '\t').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) {
                errors += BulkParseError(index + 1, line, "Needs a date and a weight")
                return@forEachIndexed
            }

            val date = parseDate(parts[0], dayFirst)
            if (date == null) {
                errors += BulkParseError(index + 1, line, "Could not read the date \"${parts[0]}\"")
                return@forEachIndexed
            }
            if (date.isAfter(today)) {
                errors += BulkParseError(index + 1, line, "Dated in the future")
                return@forEachIndexed
            }

            val value = parseWeight(parts[1])
            if (value == null) {
                errors += BulkParseError(index + 1, line, "Could not read the weight \"${parts[1]}\"")
                return@forEachIndexed
            }

            val kg = unit.toKg(value)
            if (kg < 20 || kg > 400) {
                errors += BulkParseError(index + 1, line, "$value ${unit.label} is outside the range the app stores")
                return@forEachIndexed
            }

            rows += ParsedWeightRow(
                date = date,
                weight = kg,
                note = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
            )
        }

        // Later lines win for a repeated date, matching how a spreadsheet would read.
        val deduplicated = rows.associateBy { it.date }.values.sortedBy { it.date }
        return BulkParseResult(deduplicated, errors)
    }

    fun parseDate(raw: String, dayFirst: Boolean): LocalDate? {
        val text = raw.trim().removeSurrounding("\"")
        if (text.isEmpty()) return null

        // A plain numeric d/m/y or m/d/y, where only the caller knows which.
        val numeric = Regex("""^(\d{1,4})[/\-.](\d{1,2})[/\-.](\d{1,4})$""").find(text)
        if (numeric != null) {
            val (a, b, c) = numeric.destructured.toList().map { it.toInt() }
            val candidate = when {
                // A four-digit first field can only be the year.
                a > 31 -> runCatching { LocalDate.of(a, b, c) }
                dayFirst -> runCatching { LocalDate.of(fullYear(c), b, a) }
                else -> runCatching { LocalDate.of(fullYear(c), a, b) }
            }
            candidate.getOrNull()?.let { return it }
        }

        DateFormats.forEach { formatter ->
            try {
                return LocalDate.parse(text, formatter)
            } catch (_: DateTimeParseException) {
                // Try the next format.
            } catch (_: DateTimeException) {
                // A real date that does not exist, such as 31 February.
            }
        }
        return null
    }

    /** Two-digit years are read as this century, which is the only sane reading for a weight log. */
    private fun fullYear(year: Int): Int = if (year < 100) 2000 + year else year

    fun parseWeight(raw: String): Double? {
        val cleaned = raw.trim()
            .removeSurrounding("\"")
            .replace(Regex("""(?i)\s*(kg|kgs|lb|lbs|pounds?|kilograms?)\s*$"""), "")
            .replace(',', '.')
            .trim()
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    }

    /** The CSV the app exports, which is also what it can read back. */
    fun toCsv(rows: List<ParsedWeightRow>): String = buildString {
        appendLine("date,weight_kg,note")
        rows.sortedBy { it.date }.forEach { row ->
            append(row.date)
            append(',')
            append(row.weight.format(2))
            append(',')
            appendLine(row.note?.replace(",", " ").orEmpty())
        }
    }
}
