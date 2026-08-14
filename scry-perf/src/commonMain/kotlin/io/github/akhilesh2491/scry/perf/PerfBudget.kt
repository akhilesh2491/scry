package io.github.akhilesh2491.scry.perf

import kotlinx.serialization.Serializable

/** Which budget a [PerfViolation] broke. */
public enum class PerfBudgetKind { STARTUP, SCREEN_LOAD, JANK, FROZEN_FRAMES }

/** A measurement that exceeded its budget. */
@Serializable
public data class PerfViolation(
    public val kind: PerfBudgetKind,
    /** What was measured: a screen name, or `"Cold start"`. */
    public val label: String,
    public val actual: Double,
    public val limit: Double,
    public val timestampMillis: Long,
) {
    /** One-line rendering for the UI, exports and crash reports. */
    public fun format(): String {
        val unit = if (kind == PerfBudgetKind.JANK) "%" else if (kind == PerfBudgetKind.FROZEN_FRAMES) "" else "ms"
        return "$label: ${actual.roundTo(1)}$unit over budget of ${limit.roundTo(1)}$unit"
    }
}

/**
 * Thresholds that turn a measurement into a pass or a fail.
 *
 * Every field is nullable and a null means "do not check this" — a budget you
 * have not thought about should not produce a red badge you learn to ignore.
 *
 * The [DEFAULT] values are deliberately loose. Scry runs in a debug build, with
 * a debuggable JIT, no R8, and Scry itself installed; holding that to release
 * thresholds would flag every launch and the badge would stop meaning anything.
 * Treat these numbers as "something is badly wrong", and tighten them per project.
 */
public class PerfBudget internal constructor(
    public val coldStartupMillis: Long?,
    public val screenLoadMillis: Long?,
    public val slowFramePercent: Double?,
    public val frozenFrames: Int?,
) {

    /** Checks a startup measurement. Warm and hot starts are not budgeted. */
    public fun check(metric: StartupMetric): PerfViolation? {
        val limit = coldStartupMillis ?: return null
        if (metric.kind != StartupKind.COLD || metric.totalMillis <= limit) return null
        return PerfViolation(
            kind = PerfBudgetKind.STARTUP,
            label = "Cold start",
            actual = metric.totalMillis.toDouble(),
            limit = limit.toDouble(),
            timestampMillis = metric.timestampMillis,
        )
    }

    /** Checks a screen load. Uses TTFD when the app reported one, else TTID. */
    public fun check(metric: ScreenLoadMetric): PerfViolation? {
        val limit = screenLoadMillis ?: return null
        val actual = metric.ttfdMillis ?: metric.ttidMillis
        if (actual <= limit) return null
        return PerfViolation(
            kind = PerfBudgetKind.SCREEN_LOAD,
            label = metric.screen,
            actual = actual.toDouble(),
            limit = limit.toDouble(),
            timestampMillis = metric.timestampMillis,
        )
    }

    /**
     * Checks frame timing.
     *
     * [MINIMUM_FRAMES_FOR_JANK] guards the jank check: the first handful of
     * frames after a screen appears are almost always slow, and flagging a 100 %
     * jank rate off three frames would fire on every screen.
     */
    public fun check(stats: FrameStats, timestampMillis: Long): List<PerfViolation> {
        val violations = mutableListOf<PerfViolation>()

        val jankLimit = slowFramePercent
        if (jankLimit != null &&
            stats.frameCount >= MINIMUM_FRAMES_FOR_JANK &&
            stats.jankPercent > jankLimit
        ) {
            violations += PerfViolation(
                kind = PerfBudgetKind.JANK,
                label = stats.screen,
                actual = stats.jankPercent,
                limit = jankLimit,
                timestampMillis = timestampMillis,
            )
        }

        val frozenLimit = frozenFrames
        if (frozenLimit != null && stats.frozenFrames > frozenLimit) {
            violations += PerfViolation(
                kind = PerfBudgetKind.FROZEN_FRAMES,
                label = stats.screen,
                actual = stats.frozenFrames.toDouble(),
                limit = frozenLimit.toDouble(),
                timestampMillis = timestampMillis,
            )
        }

        return violations
    }

    public companion object {
        /** Frames a screen must have produced before its jank rate is judged. */
        public const val MINIMUM_FRAMES_FOR_JANK: Int = 30

        /** Loose thresholds suitable for a debug build. See the class docs. */
        public val DEFAULT: PerfBudget = PerfBudget(
            coldStartupMillis = 2_000,
            screenLoadMillis = 1_000,
            slowFramePercent = 10.0,
            frozenFrames = 2,
        )

        /** Measures everything, judges nothing. */
        public val NONE: PerfBudget = PerfBudget(null, null, null, null)
    }
}

/** Builder for [PerfBudget]. Set a field to null to stop checking it. */
public class PerfBudgetBuilder internal constructor(base: PerfBudget) {
    public var coldStartupMillis: Long? = base.coldStartupMillis
    public var screenLoadMillis: Long? = base.screenLoadMillis
    public var slowFramePercent: Double? = base.slowFramePercent
    public var frozenFrames: Int? = base.frozenFrames

    internal fun build(): PerfBudget =
        PerfBudget(coldStartupMillis, screenLoadMillis, slowFramePercent, frozenFrames)
}

/**
 * Rounds for display.
 *
 * Frame durations are sub-millisecond-precise and rendering all of it turns a
 * table into noise; one decimal is the most anyone acts on.
 */
internal fun Double.roundTo(decimals: Int): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val rounded = kotlin.math.round(this * factor) / factor
    val whole = rounded.toLong()
    if (decimals == 0) return whole.toString()
    val fraction = kotlin.math.round(kotlin.math.abs(rounded - whole) * factor).toLong()
    return "$whole.${fraction.toString().padStart(decimals, '0')}"
}
