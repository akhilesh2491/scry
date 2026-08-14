package io.github.akhilesh2491.scry.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameStatsTest {

    @Test
    fun `empty input produces an empty result rather than failing`() {
        val stats = FrameStats.of("Home", emptyList(), BUDGET_60HZ)

        assertEquals(0, stats.frameCount)
        assertEquals(0.0, stats.p99Millis)
        assertEquals(0.0, stats.jankPercent)
    }

    @Test
    fun `a single frame is its own every percentile`() {
        val stats = FrameStats.of("Home", listOf(12.0), BUDGET_60HZ)

        assertEquals(12.0, stats.p50Millis)
        assertEquals(12.0, stats.p99Millis)
        assertEquals(12.0, stats.worstMillis)
    }

    @Test
    fun `percentiles use nearest rank so every value is a real frame`() {
        val durations = (1..100).map { it.toDouble() }
        val stats = FrameStats.of("Home", durations, BUDGET_60HZ)

        assertEquals(50.0, stats.p50Millis)
        assertEquals(90.0, stats.p90Millis)
        assertEquals(99.0, stats.p99Millis)
        assertEquals(100.0, stats.worstMillis)
    }

    @Test
    fun `unsorted input is aggregated correctly`() {
        val stats = FrameStats.of("Home", listOf(40.0, 8.0, 22.0, 5.0), BUDGET_60HZ)

        assertEquals(4, stats.frameCount)
        assertEquals(40.0, stats.worstMillis)
        assertEquals(2, stats.slowFrames)
    }

    @Test
    fun `the same frames are slow at 60Hz and not at all at 30Hz`() {
        val durations = listOf(10.0, 20.0, 25.0, 12.0)

        assertEquals(2, FrameStats.of("Home", durations, BUDGET_60HZ).slowFrames)
        assertEquals(0, FrameStats.of("Home", durations, BUDGET_30HZ).slowFrames)
    }

    @Test
    fun `a 16ms frame is slow on a 120Hz display`() {
        val stats = FrameStats.of("Home", listOf(16.0), BUDGET_120HZ)

        assertEquals(1, stats.slowFrames)
        assertEquals(100.0, stats.jankPercent)
    }

    @Test
    fun `frames over 700ms count as frozen as well as slow`() {
        val stats = FrameStats.of("Home", listOf(8.0, 701.0), BUDGET_60HZ)

        assertEquals(1, stats.frozenFrames)
        assertEquals(1, stats.slowFrames)
    }

    @Test
    fun `700ms exactly is not frozen`() {
        assertEquals(0, FrameStats.of("Home", listOf(700.0), BUDGET_60HZ).frozenFrames)
    }

    @Test
    fun `jank percent is zero when no frames were drawn`() {
        assertEquals(0.0, FrameStats.empty("Home", BUDGET_60HZ).jankPercent)
    }

    @Test
    fun `the ring keeps counts for the whole session after evicting samples`() {
        val ring = FrameRing(capacity = 10)
        repeat(50) { ring.add(100.0, BUDGET_60HZ) }

        val stats = ring.snapshot("Home")
        assertEquals(50, stats.frameCount)
        assertEquals(50, stats.slowFrames)
    }

    @Test
    fun `the ring retains only the most recent samples for percentiles`() {
        val ring = FrameRing(capacity = 3)
        listOf(100.0, 200.0, 1.0, 2.0, 3.0).forEach { ring.add(it, BUDGET_60HZ) }

        assertEquals(listOf(1.0, 2.0, 3.0), ring.recent(10))
        // p99 comes from the retained window, so the evicted 200ms frame is gone…
        assertEquals(3.0, ring.snapshot("Home").p99Millis)
        // …but the worst frame of the session is remembered regardless.
        assertEquals(200.0, ring.snapshot("Home").worstMillis)
    }

    @Test
    fun `recent returns everything when fewer samples than asked for exist`() {
        val ring = FrameRing(capacity = 100)
        ring.add(5.0, BUDGET_60HZ)

        assertEquals(listOf(5.0), ring.recent(50))
    }

    @Test
    fun `clearing the ring resets session counts`() {
        val ring = FrameRing(capacity = 10)
        repeat(5) { ring.add(900.0, BUDGET_60HZ) }
        ring.clear()

        val stats = ring.snapshot("Home")
        assertEquals(0, stats.frameCount)
        assertEquals(0, stats.frozenFrames)
        assertEquals(0.0, stats.worstMillis)
    }

    @Test
    fun `the budget follows the most recent frame's display`() {
        val ring = FrameRing(capacity = 10)
        ring.add(10.0, BUDGET_60HZ)
        ring.add(10.0, BUDGET_120HZ)

        assertTrue(ring.snapshot("Home").budgetMillis < BUDGET_60HZ)
    }

    private companion object {
        const val BUDGET_30HZ = 1000.0 / 30.0
        const val BUDGET_60HZ = 1000.0 / 60.0
        const val BUDGET_120HZ = 1000.0 / 120.0
    }
}
