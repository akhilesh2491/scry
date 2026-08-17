package io.github.akhilesh2491.scry.database

/**
 * Query results as CSV.
 *
 * CSV and not JSON because the destination is a spreadsheet or a `diff` against
 * a fixture — the two things anyone actually does with a table pulled off a
 * device. A NULL is written as an empty field, which is what every SQLite tool
 * does, and the header row carries the column names so the distinction between
 * NULL and the literal string "NULL" survives.
 */
internal fun QueryResult.csvExport(): String = buildString {
    appendLine(columns.joinToString(",") { it.csvEscaped() })
    rows.forEach { row ->
        appendLine(row.values.joinToString(",") { it.orEmpty().csvEscaped() })
    }
}

/**
 * Quotes a field when it contains anything that would break the row.
 *
 * Cell values are whatever the app stored — a comma in an address silently
 * shifting every column is exactly the kind of bug that makes people stop
 * trusting an export.
 */
private fun String.csvEscaped(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${replace("\"", "\"\"")}\""
    } else {
        this
    }
