package io.github.akhilesh2491.scry.perf

import kotlinx.serialization.Serializable

/**
 * How the app arrived at its first frame.
 *
 * The three names are the platform's, not Scry's, so that numbers here can be
 * compared against Play Vitals and `am start -W` without a translation step.
 */
public enum class StartupKind {
    /** A fresh process: nothing of the app was alive beforehand. */
    COLD,

    /** The process survived, but the entry activity had to be created again. */
    WARM,

    /** Everything survived; the app only came back to the foreground. */
    HOT,
}

/** What kind of thing a [ScreenLoadMetric] measured. */
public enum class ScreenKind { ACTIVITY, FRAGMENT, COMPOSABLE }

/**
 * One segment of a startup breakdown.
 *
 * Offsets are relative to the startup's own t0, so a list of phases renders as a
 * waterfall without the reader needing an absolute clock.
 */
@Serializable
public data class PerfPhase(
    public val name: String,
    public val startOffsetMillis: Long,
    public val durationMillis: Long,
)

/**
 * How long the app took to show its first frame.
 *
 * [truncated] is the honest flag: on API 23 and on iOS, Scry cannot see true
 * process start, so `totalMillis` is measured from the earliest point it *can*
 * see. A number that silently under-reports is worse than no number, so the UI
 * and the export both carry this through.
 */
@Serializable
public data class StartupMetric(
    public val kind: StartupKind,
    public val totalMillis: Long,
    public val timestampMillis: Long,
    public val phases: List<PerfPhase> = emptyList(),
    public val truncated: Boolean = false,
)

/**
 * How long a screen took to become visible.
 *
 * [ttidMillis] — time to initial display — is measured automatically. [ttfdMillis]
 * — time to *full* display, with real data rather than placeholders — has no
 * cross-platform automatic equivalent, so it is only present when the app called
 * [ScryTrace.reportFullyDrawn].
 */
@Serializable
public data class ScreenLoadMetric(
    public val id: String,
    public val screen: String,
    public val kind: ScreenKind,
    public val ttidMillis: Long,
    public val timestampMillis: Long,
    public val ttfdMillis: Long? = null,
)

/** A manually timed block. See [ScryTrace]. */
@Serializable
public data class SpanMetric(
    public val id: String,
    public val name: String,
    public val durationMillis: Long,
    public val startedAtMillis: Long,
    /** How many spans were already open when this one began. Display only. */
    public val depth: Int = 0,
)
