package io.github.akhilesh2491.scry.perf

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Everything measured during one launch of the app.
 *
 * A session is the unit of comparison. "Is this slower than yesterday?" is only
 * answerable if yesterday's launch was kept as a whole — a startup number without
 * the device, app version and frame context beside it invites the wrong
 * conclusion.
 */
@Serializable
public data class PerfSession(
    public val id: String,
    public val startedAtMillis: Long,
    public val platform: String,
    public val deviceModel: String? = null,
    public val appVersion: String? = null,
    public val startup: StartupMetric? = null,
    /**
     * Returns to the foreground after this launch.
     *
     * Separate from [startup] because a hot start is not the app starting — the
     * process and the activity both survived. Averaging the two together is how
     * a startup number ends up flattering and useless.
     */
    public val hotStarts: List<StartupMetric> = emptyList(),
    public val screens: List<ScreenLoadMetric> = emptyList(),
    public val frames: List<FrameStats> = emptyList(),
    public val spans: List<SpanMetric> = emptyList(),
    public val violations: List<PerfViolation> = emptyList(),
) {

    /** Slow frames over all screens, as a share of every frame drawn. */
    public val overallJankPercent: Double
        get() {
            val total = frames.sumOf { it.frameCount }
            if (total == 0) return 0.0
            return frames.sumOf { it.slowFrames } * 100.0 / total
        }

    /** How this session compares against an earlier one. */
    public fun diff(previous: PerfSession): PerfComparison {
        val previousScreens = previous.screens.associate { it.screen to it.ttidMillis }
        return PerfComparison(
            sessionId = id,
            previousSessionId = previous.id,
            startupDeltaMillis = startup?.totalMillis
                ?.let { current -> previous.startup?.totalMillis?.let { current - it } },
            jankDeltaPercent = overallJankPercent - previous.overallJankPercent,
            screenDeltaMillis = screens.mapNotNull { screen ->
                previousScreens[screen.screen]?.let { screen.screen to (screen.ttidMillis - it) }
            }.toMap(),
        )
    }

    public companion object {
        /**
         * A new session id.
         *
         * Same shape as `scry-network`'s transaction ids — timestamp plus random
         * suffix — for the same reason: there is no multiplatform UUID worth the
         * dependency, and sorting by id happens to sort by time.
         */
        public fun newId(): String = "${nowMillis()}-${Random.nextInt(0, 1_000_000)}"
    }
}

/**
 * Deltas between two sessions. Negative means faster.
 *
 * Null fields mean one of the two sessions never recorded that metric — an app
 * killed before its first frame has no startup to compare.
 */
@Serializable
public data class PerfComparison(
    public val sessionId: String,
    public val previousSessionId: String,
    public val startupDeltaMillis: Long? = null,
    public val jankDeltaPercent: Double? = null,
    public val screenDeltaMillis: Map<String, Long> = emptyMap(),
)
