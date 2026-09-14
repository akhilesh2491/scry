package io.github.akhilesh2491.scry.analytics

import kotlinx.serialization.json.Json

/**
 * Exports, in the two formats an analytics trail actually gets used in.
 *
 * JSON keeps the visits whole — screen, events, params, verdict — for a diff or
 * a script. CSV is one row per event, which is what gets pasted next to the
 * tracking plan in a spreadsheet and compared line by line.
 */

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Visits with their events and verdicts, newest first. */
public fun List<ScreenVisit>.toJson(): String = exportJson.encodeToString(this)

/** One row per event, across every visit given. */
public fun List<ScreenVisit>.toCsv(): String = buildString {
    appendLine(
        listOf("time", "screen", "verdict", "event", "destination", "params", "issues")
            .joinToString(","),
    )
    this@toCsv.forEach { visit ->
        visit.events.forEach { event ->
            val issues = visit.issues
                .filter { it.eventName == event.name }
                .joinToString("; ") { it.format() }
            appendLine(
                listOf(
                    epochMillisToIso8601(event.timestampMillis),
                    visit.screen,
                    visit.verdict.name,
                    event.name,
                    event.destination.orEmpty(),
                    event.params.entries.joinToString("; ") { "${it.key}=${it.value.display()}" },
                    issues,
                ).joinToString(",") { it.csvEscaped() },
            )
        }
        // A visit that fired nothing is the most interesting row on the sheet —
        // it is what a missing event looks like — so it is not dropped for
        // having no events to iterate.
        if (visit.events.isEmpty()) {
            appendLine(
                listOf(
                    epochMillisToIso8601(visit.enteredAtMillis),
                    visit.screen,
                    visit.verdict.name,
                    "",
                    "",
                    "",
                    visit.issues.joinToString("; ") { it.format() },
                ).joinToString(",") { it.csvEscaped() },
            )
        }
    }
}

/**
 * Quotes a CSV field when it contains anything that would break the row.
 *
 * Event and parameter names are app-supplied strings, and a comma in a value
 * silently shifting every column is exactly the kind of bug that makes people
 * stop trusting an export.
 */
private fun String.csvEscaped(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${replace("\"", "\"\"")}\""
    } else {
        this
    }

/** `HH:MM:SS` in UTC, for dense list rows where the date is noise. */
internal fun clockTime(millis: Long): String =
    epochMillisToIso8601(millis).substringAfter('T').removeSuffix("Z")

/**
 * Epoch millis as an ISO-8601 UTC timestamp.
 *
 * `scry-perf` and `scry-network` each have the same function, `internal` to
 * their module. Duplicating ~20 lines a third time is still cheaper than
 * widening core's API for a formatter.
 */
internal fun epochMillisToIso8601(millis: Long): String {
    val totalSeconds = millis / 1000
    val secondOfDay = (totalSeconds % 86_400 + 86_400) % 86_400
    var days = (totalSeconds - secondOfDay) / 86_400

    var year = 1970
    while (true) {
        val yearDays = if (isLeapYear(year)) 366 else 365
        if (days < yearDays) break
        days -= yearDays
        year++
    }

    val lengths = monthLengths(year)
    var month = 0
    while (days >= lengths[month]) {
        days -= lengths[month]
        month++
    }

    val day = days + 1
    val hour = secondOfDay / 3600
    val minute = (secondOfDay % 3600) / 60
    val second = secondOfDay % 60

    return "$year-${(month + 1).pad2()}-${day.pad2()}T${hour.pad2()}:${minute.pad2()}:${second.pad2()}Z"
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun monthLengths(year: Int): IntArray =
    intArrayOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private fun Long.pad2(): String = toString().padStart(2, '0')

private fun Int.pad2(): String = toString().padStart(2, '0')
