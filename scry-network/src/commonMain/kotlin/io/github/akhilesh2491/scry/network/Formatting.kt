package io.github.akhilesh2491.scry.network

/** Human-readable byte count for the list and overview. */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} kB"
    else -> "${bytes / (1024 * 1024)} MB"
}

/**
 * Indents a JSON document for display.
 *
 * A hand-rolled formatter rather than `Json.encodeToString` of a parsed tree,
 * for the same reason the redactor is hand-rolled: captured bodies are routinely
 * truncated or not JSON at all, and the viewer must show *something* rather than
 * throw. Anything that does not look like JSON is returned untouched.
 */
internal fun prettyPrintJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    if (trimmed[0] != '{' && trimmed[0] != '[') return raw

    val out = StringBuilder(trimmed.length + trimmed.length / 4)
    var indent = 0
    var inString = false
    var escaped = false

    fun newline() {
        out.append('\n')
        repeat(indent) { out.append("  ") }
    }

    for (char in trimmed) {
        if (inString) {
            out.append(char)
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> {
                inString = true
                out.append(char)
            }
            '{', '[' -> {
                out.append(char)
                indent++
                newline()
            }
            '}', ']' -> {
                indent = (indent - 1).coerceAtLeast(0)
                newline()
                out.append(char)
            }
            ',' -> {
                out.append(char)
                newline()
            }
            ':' -> out.append(": ")
            ' ', '\n', '\t', '\r' -> Unit // collapse existing whitespace
            else -> out.append(char)
        }
    }
    return out.toString()
}
