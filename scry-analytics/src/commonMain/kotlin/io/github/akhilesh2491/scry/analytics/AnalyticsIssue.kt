package io.github.akhilesh2491.scry.analytics

import kotlinx.serialization.Serializable

/** What an [AnalyticsIssue] is complaining about. */
public enum class AnalyticsIssueKind {
    /** A required event never fired while the screen was open. */
    MISSING_EVENT,

    /** The event fired, but a required parameter was absent. */
    MISSING_PARAM,

    /** The parameter was there with the wrong type. */
    WRONG_TYPE,

    /** The value was outside the declared `oneOf` set. */
    DISALLOWED_VALUE,

    /** An `atMostOnce` event fired more than once on one visit. */
    DUPLICATE_EVENT,

    /** An event declared `forbid` fired anyway. */
    FORBIDDEN_EVENT,

    /** An event with no expectation fired on a screen that has a spec. */
    UNEXPECTED_EVENT,
}

/**
 * One way the events on a screen did not match what was declared.
 *
 * The analytics counterpart of `PerfViolation`: a measurement the spec turned
 * into a failure, carrying enough context to fix it without re-running the app.
 */
@Serializable
public data class AnalyticsIssue(
    public val kind: AnalyticsIssueKind,
    public val screen: String,
    public val eventName: String,
    /** The parameter involved, when the issue is about one. */
    public val paramName: String? = null,
    public val expected: String? = null,
    public val actual: String? = null,
    public val timestampMillis: Long,
) {
    /** One-line rendering for the UI, exports and crash reports. */
    public fun format(): String {
        val subject = "$screen / $eventName"
        return when (kind) {
            AnalyticsIssueKind.MISSING_EVENT -> "$subject: never fired"
            AnalyticsIssueKind.MISSING_PARAM -> "$subject: missing required param '$paramName'"
            AnalyticsIssueKind.WRONG_TYPE ->
                "$subject: param '$paramName' expected $expected, got $actual"
            AnalyticsIssueKind.DISALLOWED_VALUE ->
                "$subject: param '$paramName' was '$actual', expected one of $expected"
            AnalyticsIssueKind.DUPLICATE_EVENT -> "$subject: fired $actual times, expected at most once"
            AnalyticsIssueKind.FORBIDDEN_EVENT -> "$subject: fired on a screen that forbids it"
            AnalyticsIssueKind.UNEXPECTED_EVENT -> "$subject: fired but is not in this screen's spec"
        }
    }
}
