package io.github.akhilesh2491.scry.perf

import kotlinx.serialization.json.Json

/**
 * Exports, in the two formats performance numbers actually get used in.
 *
 * JSON keeps the full session for a diff or a script; CSV is what gets pasted
 * into a spreadsheet next to last week's run. Neither includes raw frame
 * durations — see [FrameStats].
 */

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** The full session as JSON. */
public fun PerfSession.toJson(): String = exportJson.encodeToString(this)

/** Several sessions as a JSON array, newest first. */
public fun List<PerfSession>.toJson(): String = exportJson.encodeToString(this)

/**
 * One row per measurement, across every session given.
 *
 * A long format rather than a column per metric: sessions do not all contain the
 * same screens, and a wide table would be mostly empty cells that pivot badly.
 */
public fun List<PerfSession>.toCsv(): String = buildString {
    appendLine(
        listOf(
            "session", "started_at", "platform", "device", "app_version",
            "metric", "name", "value_ms", "detail",
        ).joinToString(","),
    )

    this@toCsv.forEach { session ->
        val prefix = listOf(
            session.id,
            epochMillisToIso8601(session.startedAtMillis),
            session.platform,
            session.deviceModel.orEmpty(),
            session.appVersion.orEmpty(),
        )

        fun row(metric: String, name: String, value: String, detail: String) {
            appendLine((prefix + listOf(metric, name, value, detail)).joinToString(",") { it.csvEscaped() })
        }

        session.startup?.let {
            row("startup", it.kind.name, it.totalMillis.toString(), if (it.truncated) "truncated" else "")
            it.phases.forEach { phase ->
                row("startup_phase", phase.name, phase.durationMillis.toString(), "")
            }
        }
        session.hotStarts.forEach {
            row("hot_start", it.kind.name, it.totalMillis.toString(), epochMillisToIso8601(it.timestampMillis))
        }
        session.screens.forEach {
            row("screen_ttid", it.screen, it.ttidMillis.toString(), it.kind.name)
            it.ttfdMillis?.let { ttfd -> row("screen_ttfd", it.screen, ttfd.toString(), it.kind.name) }
        }
        session.frames.forEach {
            row("frames_p50", it.screen, it.p50Millis.roundTo(2), "${it.frameCount} frames")
            row("frames_p99", it.screen, it.p99Millis.roundTo(2), "${it.frameCount} frames")
            row("frames_slow", it.screen, it.slowFrames.toString(), "${it.jankPercent.roundTo(1)}%")
            row("frames_frozen", it.screen, it.frozenFrames.toString(), "")
        }
        session.spans.forEach { row("span", it.name, it.durationMillis.toString(), "") }
        session.violations.forEach {
            row("violation", it.label, it.actual.roundTo(1), it.kind.name)
        }
    }
}

/**
 * Quotes a CSV field when it contains anything that would break the row.
 *
 * Screen names and span names are app-supplied strings — a comma in a screen
 * title silently shifting every column is exactly the kind of bug that makes
 * people stop trusting an export.
 */
private fun String.csvEscaped(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${replace("\"", "\"\"")}\""
    } else {
        this
    }

/**
 * Epoch millis as an ISO-8601 UTC timestamp.
 *
 * `scry-network` has the same function, but it is `internal` to that module and
 * `scry-perf` does not depend on it. Duplicating ~20 lines is cheaper than
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
