package io.github.akhilesh2491.scry.logs

import kotlinx.serialization.Serializable

/** Severity, ordered so filtering by minimum level is a comparison. */
@Serializable
public enum class LogLevel(public val label: String) {
    VERBOSE("V"),
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E"),
    ASSERT("A"),
}

/** Where an entry came from. */
@Serializable
public enum class LogSource {
    /** Logged through [ScryLog]. */
    SCRY,

    /** Read from the platform log (Android logcat). */
    PLATFORM,
}

/** One log line. */
@Serializable
public data class LogEntry(
    public val id: String,
    public val timestampMillis: Long,
    public val level: LogLevel,
    public val tag: String,
    public val message: String,
    public val stackTrace: String? = null,
    public val source: LogSource = LogSource.SCRY,
) {
    /** Single-line rendering used for search, export and crash context. */
    public fun format(): String = buildString {
        append(level.label).append('/').append(tag).append(": ").append(message)
        stackTrace?.let { append('\n').append(it) }
    }

    /** True if this entry matches a free-text query across tag and message. */
    public fun matches(query: String): Boolean =
        query.isBlank() ||
            tag.contains(query, ignoreCase = true) ||
            message.contains(query, ignoreCase = true)
}
