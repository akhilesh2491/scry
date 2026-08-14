package io.github.akhilesh2491.scry.perf

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.min

/**
 * Frame timing for one screen, already aggregated.
 *
 * Individual frame durations are never persisted or exported — at 120 Hz a minute
 * of scrolling is 7 200 samples, and none of them are interesting on their own.
 * What survives is the shape: the percentiles and the two counts that matter.
 *
 * [budgetMillis] comes from the display's actual refresh rate rather than a
 * hardcoded 16.67 ms. On a 120 Hz phone a 16 ms frame *is* a dropped frame, and a
 * tool that says otherwise is lying about the device in the user's hand.
 */
@Serializable
public data class FrameStats(
    public val screen: String,
    public val frameCount: Int,
    public val budgetMillis: Double,
    /** Frames that missed the budget. */
    public val slowFrames: Int,
    /** Frames over [FROZEN_FRAME_MILLIS] — visible as a stall, not a stutter. */
    public val frozenFrames: Int,
    public val p50Millis: Double,
    public val p90Millis: Double,
    public val p95Millis: Double,
    public val p99Millis: Double,
    public val worstMillis: Double,
) {

    /** Share of frames that missed the budget, 0–100. */
    public val jankPercent: Double
        get() = if (frameCount == 0) 0.0 else slowFrames * 100.0 / frameCount

    public companion object {

        /**
         * Above this, a frame reads as the app having hung rather than stuttered.
         *
         * 700 ms is Play Console's definition, kept identical so a number here
         * means the same thing as a number there.
         */
        public const val FROZEN_FRAME_MILLIS: Double = 700.0

        /** An empty result, so a screen with no frames yet still renders. */
        public fun empty(screen: String, budgetMillis: Double): FrameStats = FrameStats(
            screen = screen,
            frameCount = 0,
            budgetMillis = budgetMillis,
            slowFrames = 0,
            frozenFrames = 0,
            p50Millis = 0.0,
            p90Millis = 0.0,
            p95Millis = 0.0,
            p99Millis = 0.0,
            worstMillis = 0.0,
        )

        /** Aggregates raw frame durations. [durationsMillis] need not be sorted. */
        public fun of(
            screen: String,
            durationsMillis: List<Double>,
            budgetMillis: Double,
        ): FrameStats {
            if (durationsMillis.isEmpty()) return empty(screen, budgetMillis)

            val sorted = durationsMillis.sorted()
            return FrameStats(
                screen = screen,
                frameCount = sorted.size,
                budgetMillis = budgetMillis,
                slowFrames = sorted.count { it > budgetMillis },
                frozenFrames = sorted.count { it > FROZEN_FRAME_MILLIS },
                p50Millis = percentileOfSorted(sorted, 50.0),
                p90Millis = percentileOfSorted(sorted, 90.0),
                p95Millis = percentileOfSorted(sorted, 95.0),
                p99Millis = percentileOfSorted(sorted, 99.0),
                worstMillis = sorted.last(),
            )
        }

        /**
         * Nearest-rank percentile over an already-sorted list.
         *
         * Nearest-rank rather than interpolating: every value it returns is a
         * frame that actually happened, which is what you want when the next step
         * is "go find that frame".
         */
        internal fun percentileOfSorted(sorted: List<Double>, percentile: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val rank = ceil(percentile / 100.0 * sorted.size).toInt()
            return sorted[min(rank, sorted.size).coerceAtLeast(1) - 1]
        }
    }
}

/**
 * Fixed-capacity buffer of the most recent frame durations for one screen.
 *
 * Capacity is the whole point: frame callbacks arrive up to 120 times a second
 * for as long as the app is open, and an unbounded list would make a debug build
 * run out of memory in an afternoon of scrolling. Oldest samples are dropped.
 *
 * Not thread-safe. Frame callbacks arrive on the main thread on every platform
 * Scry supports, and adding a lock to the frame path would make the tool part of
 * the problem it is measuring.
 */
internal class FrameRing(private val capacity: Int) {

    private val values = ArrayDeque<Double>()

    var budgetMillis: Double = DEFAULT_BUDGET_MILLIS
        private set

    /** Counts kept separately from [values] so eviction cannot erase history. */
    var totalFrames: Int = 0
        private set
    var totalSlowFrames: Int = 0
        private set
    var totalFrozenFrames: Int = 0
        private set
    var worstMillis: Double = 0.0
        private set

    fun add(durationMillis: Double, budgetMillis: Double) {
        this.budgetMillis = budgetMillis
        totalFrames++
        if (durationMillis > budgetMillis) totalSlowFrames++
        if (durationMillis > FrameStats.FROZEN_FRAME_MILLIS) totalFrozenFrames++
        if (durationMillis > worstMillis) worstMillis = durationMillis

        if (values.size == capacity) values.removeFirst()
        values.addLast(durationMillis)
    }

    /**
     * Percentiles over the retained window, counts over the whole session.
     *
     * Mixed on purpose: percentiles describe "how it feels right now", while a
     * slow-frame count that reset every 5 000 frames would never trip a budget.
     */
    fun snapshot(screen: String): FrameStats {
        val windowed = FrameStats.of(screen, values.toList(), budgetMillis)
        return windowed.copy(
            frameCount = totalFrames,
            slowFrames = totalSlowFrames,
            frozenFrames = totalFrozenFrames,
            worstMillis = worstMillis,
        )
    }

    /** The most recent [count] durations, oldest first. For the sparkline. */
    fun recent(count: Int): List<Double> {
        if (values.size <= count) return values.toList()
        return values.toList().subList(values.size - count, values.size)
    }

    fun clear() {
        values.clear()
        totalFrames = 0
        totalSlowFrames = 0
        totalFrozenFrames = 0
        worstMillis = 0.0
    }

    companion object {
        /** 60 Hz, used until a real refresh rate is known. */
        const val DEFAULT_BUDGET_MILLIS: Double = 1000.0 / 60.0
    }
}
