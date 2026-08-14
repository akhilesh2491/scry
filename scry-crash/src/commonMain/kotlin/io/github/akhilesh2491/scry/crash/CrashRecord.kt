package io.github.akhilesh2491.scry.crash

import kotlinx.serialization.Serializable

/** What kind of failure was captured. */
@Serializable
public enum class CrashKind {
    /** An uncaught exception that terminated a thread. */
    CRASH,

    /** An unhandled exception inside a coroutine. */
    COROUTINE,

    /** The main thread stopped responding for longer than the configured threshold. */
    ANR,

    /** Reported by the app through [CrashPlugin.recordHandled]. */
    HANDLED,
}

/** A titled block of context captured alongside a failure. */
@Serializable
public class CrashSection(
    public val title: String,
    public val body: String,
)

/**
 * One captured failure, with everything needed to act on it.
 *
 * The stack trace is the least interesting part. What makes a crash report
 * actionable is what the app was *doing* — which network calls had just run,
 * what the device state was — and that is why [sections] is captured at failure
 * time rather than reconstructed later, when it is gone.
 */
@Serializable
public class CrashRecord(
    public val id: String,
    public val timestampMillis: Long,
    public val kind: CrashKind,
    public val threadName: String,
    public val exceptionClass: String,
    public val message: String?,
    public val stackTrace: String,
    public val sections: List<CrashSection> = emptyList(),
) {
    /** One-line summary for the list view. */
    public val headline: String
        get() = "$exceptionClass${message?.let { ": $it" }.orEmpty()}"

    /** The full report as plain text — what gets shared. */
    public fun toReportText(): String = buildString {
        appendLine("=== Scry ${kind.name} report ===")
        appendLine("when:      $timestampMillis")
        appendLine("thread:    $threadName")
        appendLine("exception: $exceptionClass")
        message?.let { appendLine("message:   $it") }
        appendLine()
        appendLine(stackTrace)
        sections.forEach { section ->
            appendLine()
            appendLine("--- ${section.title} ---")
            appendLine(section.body)
        }
    }
}
