@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.perf

/** Inert mirrors of `scry-perf`'s model. File name matches for the JVM facade. */

public enum class StartupKind { COLD, WARM, HOT }

public enum class ScreenKind { ACTIVITY, FRAGMENT, COMPOSABLE }

public enum class PerfBudgetKind { STARTUP, SCREEN_LOAD, JANK, FROZEN_FRAMES }

public data class PerfPhase(
    public val name: String,
    public val startOffsetMillis: Long,
    public val durationMillis: Long,
)

public data class StartupMetric(
    public val kind: StartupKind,
    public val totalMillis: Long,
    public val timestampMillis: Long,
    public val phases: List<PerfPhase> = emptyList(),
    public val truncated: Boolean = false,
)

public data class ScreenLoadMetric(
    public val id: String,
    public val screen: String,
    public val kind: ScreenKind,
    public val ttidMillis: Long,
    public val timestampMillis: Long,
    public val ttfdMillis: Long? = null,
)

public data class SpanMetric(
    public val id: String,
    public val name: String,
    public val durationMillis: Long,
    public val startedAtMillis: Long,
    public val depth: Int = 0,
)

public data class FrameStats(
    public val screen: String,
    public val frameCount: Int,
    public val budgetMillis: Double,
    public val slowFrames: Int,
    public val frozenFrames: Int,
    public val p50Millis: Double,
    public val p90Millis: Double,
    public val p95Millis: Double,
    public val p99Millis: Double,
    public val worstMillis: Double,
) {
    public val jankPercent: Double get() = 0.0

    public companion object {
        public const val FROZEN_FRAME_MILLIS: Double = 700.0

        public fun empty(screen: String, budgetMillis: Double): FrameStats =
            FrameStats(screen, 0, budgetMillis, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0)

        public fun of(
            screen: String,
            durationsMillis: List<Double>,
            budgetMillis: Double,
        ): FrameStats = empty(screen, budgetMillis)
    }
}

public data class PerfViolation(
    public val kind: PerfBudgetKind,
    public val label: String,
    public val actual: Double,
    public val limit: Double,
    public val timestampMillis: Long,
) {
    public fun format(): String = ""
}

public class PerfBudget internal constructor(
    public val coldStartupMillis: Long?,
    public val screenLoadMillis: Long?,
    public val slowFramePercent: Double?,
    public val frozenFrames: Int?,
) {
    public fun check(metric: StartupMetric): PerfViolation? = null
    public fun check(metric: ScreenLoadMetric): PerfViolation? = null
    public fun check(stats: FrameStats, timestampMillis: Long): List<PerfViolation> = emptyList()

    public companion object {
        public const val MINIMUM_FRAMES_FOR_JANK: Int = 30
        public val DEFAULT: PerfBudget = PerfBudget(null, null, null, null)
        public val NONE: PerfBudget = PerfBudget(null, null, null, null)
    }
}

public class PerfBudgetBuilder internal constructor(base: PerfBudget) {
    public var coldStartupMillis: Long? = null
    public var screenLoadMillis: Long? = null
    public var slowFramePercent: Double? = null
    public var frozenFrames: Int? = null
}

public data class PerfPlatform(
    public val name: String,
    public val deviceModel: String? = null,
    public val appVersion: String? = null,
)

public data class PerfSession(
    public val id: String,
    public val startedAtMillis: Long,
    public val platform: String,
    public val deviceModel: String? = null,
    public val appVersion: String? = null,
    public val startup: StartupMetric? = null,
    public val hotStarts: List<StartupMetric> = emptyList(),
    public val screens: List<ScreenLoadMetric> = emptyList(),
    public val frames: List<FrameStats> = emptyList(),
    public val spans: List<SpanMetric> = emptyList(),
    public val violations: List<PerfViolation> = emptyList(),
) {
    public val overallJankPercent: Double get() = 0.0

    public fun diff(previous: PerfSession): PerfComparison = PerfComparison(id, previous.id)

    public companion object {
        public fun newId(): String = ""
    }
}

public data class PerfComparison(
    public val sessionId: String,
    public val previousSessionId: String,
    public val startupDeltaMillis: Long? = null,
    public val jankDeltaPercent: Double? = null,
    public val screenDeltaMillis: Map<String, Long> = emptyMap(),
)
