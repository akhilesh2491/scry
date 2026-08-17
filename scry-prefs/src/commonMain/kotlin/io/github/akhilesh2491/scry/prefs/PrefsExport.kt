package io.github.akhilesh2491.scry.prefs

/**
 * Store export.
 *
 * JSON rather than the flat text the screen shows, because the thing people do
 * with an exported store is diff it against another device's — and a diff is
 * only useful if the type is in the file. `"enabled": "true"` and
 * `"enabled": true` are different bugs.
 *
 * Hand-rolled rather than via kotlinx.serialization: this module has no
 * serialization dependency, and adding one to a debug library so it can write
 * one object shape is a poor trade.
 */
internal fun KeyValueStore.jsonExport(keys: List<String> = keys()): String {
    val entries = keys.mapNotNull { key -> read(key)?.let { key to it } }

    return buildString {
        appendLine("{")
        appendLine("  \"store\": ${name.jsonQuoted()},")
        appendLine("  \"readOnly\": ${!isMutable},")
        appendLine("  \"entries\": [")
        entries.forEachIndexed { index, (key, value) ->
            append("    { \"key\": ${key.jsonQuoted()}")
            append(", \"type\": ${value.typeLabel.jsonQuoted()}")
            append(", \"value\": ${value.toJsonLiteral()} }")
            appendLine(if (index == entries.lastIndex) "" else ",")
        }
        appendLine("  ]")
        append("}")
    }
}

/**
 * The value as a JSON literal of the matching kind.
 *
 * [PreferenceValue.Unsupported] is emitted as its rendered string: it is a value
 * Scry could not decode, and inventing a type for it in the export would be a
 * lie the reader has no way to detect.
 */
private fun PreferenceValue.toJsonLiteral(): String = when (this) {
    is PreferenceValue.StringValue -> value.jsonQuoted()
    is PreferenceValue.IntValue -> value.toString()
    is PreferenceValue.LongValue -> value.toString()
    is PreferenceValue.FloatValue -> value.toString()
    is PreferenceValue.DoubleValue -> value.toString()
    is PreferenceValue.BooleanValue -> value.toString()
    is PreferenceValue.StringSetValue ->
        value.joinToString(prefix = "[", postfix = "]") { it.jsonQuoted() }
    is PreferenceValue.Unsupported -> rendered.jsonQuoted()
}

/**
 * Quotes and escapes a JSON string.
 *
 * Preference values are whatever the app put there — a stored JSON blob, a
 * newline, a stray backslash from a Windows path. Escaping the control range as
 * `\uXXXX` keeps any of them from producing a file no parser will read.
 */
private fun String.jsonQuoted(): String = buildString(length + 2) {
    append('"')
    this@jsonQuoted.forEach { char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' -> append("\\u" + char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}
