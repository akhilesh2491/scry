package io.github.akhilesh2491.scry.analytics

import kotlinx.serialization.Serializable

/** The state of one [ScreenVisit] against its expectations. */
public enum class AnalyticsVerdict {
    /** Still on the screen, and nothing has gone wrong yet. */
    PENDING,

    /** Everything the spec asked for arrived, correctly. */
    PASS,

    /** At least one [AnalyticsIssue]. */
    FAIL,

    /**
     * No expectation was declared for this screen.
     *
     * Deliberately not [PASS]: a screen nobody has written a spec for is
     * untested, and colouring it green is how an untested screen stays untested.
     */
    NO_SPEC,
}

/**
 * One stay on one screen, and the events fired during it.
 *
 * The visit — not the session — is the unit a verdict attaches to. "Did checkout
 * fire `begin_checkout`?" is a question about a particular time you were on
 * checkout; answering it across a whole session hides the run where it did not.
 */
@Serializable
public data class ScreenVisit(
    public val id: String,
    public val screen: String,
    public val enteredAtMillis: Long,
    public val events: List<AnalyticsEvent> = emptyList(),
    public val issues: List<AnalyticsIssue> = emptyList(),
    public val verdict: AnalyticsVerdict = AnalyticsVerdict.PENDING,
) {
    /** True once the visit has been judged and its verdict is final. */
    public val isSettled: Boolean get() = verdict != AnalyticsVerdict.PENDING

    /**
     * Adds an event to the visit.
     *
     * An event that arrives after the visit settled still counts against it: a
     * verdict of [AnalyticsVerdict.PASS] flips to [AnalyticsVerdict.FAIL] if the
     * late event brings issues. It cannot flip the other way — an event that was
     * missing when the screen was judged *was* missing.
     */
    internal fun withEvent(event: AnalyticsEvent, newIssues: List<AnalyticsIssue>): ScreenVisit =
        copy(
            events = events + event,
            issues = issues + newIssues,
            verdict = if (newIssues.isNotEmpty() && verdict == AnalyticsVerdict.PASS) {
                AnalyticsVerdict.FAIL
            } else {
                verdict
            },
        )

    /**
     * Applies the visit-level checks and freezes the verdict.
     *
     * Idempotent: a visit that has already been judged is returned unchanged, so
     * the settle timer firing and then the user leaving the screen cannot produce
     * the same missing-event issue twice.
     */
    internal fun settle(
        visitIssues: List<AnalyticsIssue>,
        hasSpec: Boolean,
    ): ScreenVisit {
        if (isSettled) return this
        val all = issues + visitIssues
        return copy(
            issues = all,
            verdict = when {
                !hasSpec -> AnalyticsVerdict.NO_SPEC
                all.isEmpty() -> AnalyticsVerdict.PASS
                else -> AnalyticsVerdict.FAIL
            },
        )
    }
}
