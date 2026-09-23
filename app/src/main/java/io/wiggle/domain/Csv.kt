package io.wiggle.domain

import io.wiggle.data.db.BodyMeasurementEntity
import io.wiggle.data.db.CustomMeasureTypeEntity
import io.wiggle.data.db.CustomMeasurementValueEntity
import io.wiggle.data.db.WaterEntryEntity
import io.wiggle.data.db.WeightEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns stored rows into CSV text.
 *
 * Pure so it can be tested without a device, and deliberately written in the shape
 * [BulkParse] already reads: an exported weight file can be imported straight back, which is the
 * only way to know the export is actually complete.
 *
 * Everything is written in base units — kilograms, centimetres, millilitres — because that is
 * what is stored, and a file that says which unit it is in cannot be misread later.
 */
object Csv {

    fun weights(
        entries: List<WeightEntryEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildCsv(listOf("Date", "Weight (kg)", "Note")) {
        entries.sortedBy { it.measuredAt }.forEach { entry ->
            row(dateOf(entry.measuredAt, zone).toString(), entry.weightKg.format(2), entry.note.orEmpty())
        }
    }

    fun water(
        entries: List<WaterEntryEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildCsv(listOf("Date", "Time", "Amount (ml)")) {
        entries.sortedBy { it.loggedAt }.forEach { entry ->
            row(
                dateOf(entry.loggedAt, zone).toString(),
                timeOf(entry.loggedAt, zone).toString(),
                entry.amountMl.toString(),
            )
        }
    }

    /**
     * One row per tape session: the eight built-in columns, then a column for every custom type
     * this person has ever defined. Blank means "not measured that day", not zero.
     */
    fun body(
        sessions: List<BodyMeasurementEntity>,
        customTypes: List<CustomMeasureTypeEntity> = emptyList(),
        customValues: List<CustomMeasurementValueEntity> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val header = listOf(
            "Date", "Neck (cm)", "Chest (cm)", "Waist (cm)", "Hips (cm)",
            "Arm (cm)", "Thigh (cm)", "Calf (cm)", "Forearm (cm)",
        ) + customTypes.map { "${it.name} (cm)" }
        val bySession = customValues.groupBy { it.sessionId }

        return buildCsv(header) {
            sessions.sortedBy { it.measuredAt }.forEach { session ->
                val custom = bySession[session.id].orEmpty().associateBy { it.typeId }
                row(
                    listOf(
                        dateOf(session.measuredAt, zone).toString(),
                        session.neckCm.cm(), session.chestCm.cm(), session.waistCm.cm(),
                        session.hipsCm.cm(), session.armCm.cm(), session.thighCm.cm(),
                        session.calfCm.cm(), session.forearmCm.cm(),
                    ) + customTypes.map { custom[it.id]?.valueCm.cm() }
                )
            }
        }
    }

    private fun Double?.cm(): String = this?.format(1).orEmpty()

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun timeOf(millis: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().withSecond(0).withNano(0)

    private fun buildCsv(header: List<String>, body: Builder.() -> Unit): String {
        val builder = Builder(StringBuilder())
        builder.row(header)
        builder.body()
        return builder.text.toString()
    }

    class Builder(val text: StringBuilder) {
        fun row(vararg fields: String) = row(fields.toList())

        fun row(fields: List<String>) {
            text.append(fields.joinToString(",") { escape(it) }).append('\n')
        }
    }

    /** RFC 4180 quoting: notes are free text and regularly contain commas. */
    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
