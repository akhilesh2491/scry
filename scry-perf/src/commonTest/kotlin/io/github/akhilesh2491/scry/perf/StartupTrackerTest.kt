package io.github.akhilesh2491.scry.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartupTrackerTest {

    @Test
    fun `a fresh process with no saved state is a cold start`() {
        val tracker = tracker(processStart = 0, install = 300)

        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)
        tracker.onActivityStarted(410)
        val metric = tracker.onFirstFrame(500)

        assertNotNull(metric)
        assertEquals(StartupKind.COLD, metric.kind)
        // Measured from process start, not from when Scry happened to install.
        assertEquals(500, metric.totalMillis)
    }

    @Test
    fun `saved instance state makes it a warm start measured from activity create`() {
        val tracker = tracker(processStart = 0, install = 300)

        tracker.onActivityCreated(hasSavedState = true, nowMillis = 400)
        val metric = tracker.onFirstFrame(500)

        assertNotNull(metric)
        assertEquals(StartupKind.WARM, metric.kind)
        assertEquals(100, metric.totalMillis)
    }

    @Test
    fun `a cold start without a known process start is marked truncated`() {
        val tracker = tracker(processStart = null, install = 300)

        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)
        val metric = tracker.onFirstFrame(500)

        assertNotNull(metric)
        assertTrue(metric.truncated)
        // Falls back to install time, so the number is smaller and says so.
        assertEquals(200, metric.totalMillis)
    }

    @Test
    fun `a cold start with a known process start is not truncated`() {
        val tracker = tracker(processStart = 0, install = 300)

        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)

        assertFalse(tracker.onFirstFrame(500)!!.truncated)
    }

    @Test
    fun `only a hot start reports that it began one`() {
        val tracker = tracker(processStart = 0, install = 300)

        // A cold start creates an activity, so the caller already has a view to
        // watch and must not be told to register a second listener.
        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)
        assertFalse(tracker.onActivityStarted(410))
        tracker.onFirstFrame(500)

        tracker.onActivityStopped()
        assertTrue(tracker.onActivityStarted(2_000))
    }

    @Test
    fun `returning to the foreground is a hot start`() {
        val tracker = coldStarted()

        tracker.onActivityStopped()
        tracker.onActivityStarted(2_000)
        val metric = tracker.onFirstFrame(2_050)

        assertNotNull(metric)
        assertEquals(StartupKind.HOT, metric.kind)
        assertEquals(50, metric.totalMillis)
    }

    @Test
    fun `a hot start does not overwrite the launch measurement`() {
        val tracker = coldStarted()

        tracker.onActivityStopped()
        tracker.onActivityStarted(2_000)
        tracker.onFirstFrame(2_050)

        // A second background/foreground cycle still reports hot, so the launch
        // classification is not consumed by the first one.
        tracker.onActivityStopped()
        tracker.onActivityStarted(3_000)
        assertEquals(StartupKind.HOT, tracker.onFirstFrame(3_010)?.kind)
    }

    @Test
    fun `recreating the activity after backgrounding is not a hot start`() {
        val tracker = coldStarted()
        tracker.onActivityStopped()

        // The activity was destroyed and rebuilt: there is real work here that a
        // hot start does not have.
        tracker.onActivityCreated(hasSavedState = true, nowMillis = 2_000)
        tracker.onActivityStarted(2_010)

        assertEquals(StartupKind.WARM, tracker.onFirstFrame(2_100)?.kind)
    }

    @Test
    fun `rotating an activity does not report a second launch`() {
        val tracker = coldStarted()

        // Rotation: create happens while the old activity is still started, so
        // the app never actually went to the background.
        tracker.onActivityCreated(hasSavedState = true, nowMillis = 2_000)
        tracker.onActivityStarted(2_010)
        tracker.onActivityStopped()

        assertNull(tracker.onFirstFrame(2_100))
    }

    @Test
    fun `frames after a start is reported produce nothing`() {
        val tracker = coldStarted()

        assertNull(tracker.onFirstFrame(2_000))
        assertNull(tracker.onFirstFrame(3_000))
    }

    @Test
    fun `a frame before any activity is created produces nothing`() {
        assertNull(tracker(processStart = 0, install = 100).onFirstFrame(200))
    }

    @Test
    fun `several activities during one launch report a single start`() {
        val tracker = tracker(processStart = 0, install = 100)

        tracker.onActivityCreated(hasSavedState = false, nowMillis = 200)
        tracker.onActivityCreated(hasSavedState = false, nowMillis = 250)
        tracker.onActivityStarted(260)

        assertNotNull(tracker.onFirstFrame(300))
        assertNull(tracker.onFirstFrame(400))
    }

    @Test
    fun `a cold start is broken into three phases`() {
        val tracker = tracker(processStart = 0, install = 300)
        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)

        val phases = tracker.onFirstFrame(500)!!.phases

        assertEquals(
            listOf(
                "Process start → Scry install",
                "Scry install → first Activity.onCreate",
                "Activity.onCreate → first frame",
            ),
            phases.map { it.name },
        )
        assertEquals(listOf(300L, 100L, 100L), phases.map { it.durationMillis })
        assertEquals(listOf(0L, 300L, 400L), phases.map { it.startOffsetMillis })
    }

    @Test
    fun `phase durations sum to the total`() {
        val tracker = tracker(processStart = 0, install = 300)
        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)

        val metric = tracker.onFirstFrame(500)!!

        assertEquals(metric.totalMillis, metric.phases.sumOf { it.durationMillis })
    }

    @Test
    fun `the process-start phase is omitted when process start is unknown`() {
        val tracker = tracker(processStart = null, install = 300)
        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)

        assertEquals(2, tracker.onFirstFrame(500)!!.phases.size)
    }

    @Test
    fun `warm and hot starts carry no phase breakdown`() {
        val warm = tracker(processStart = 0, install = 100)
        warm.onActivityCreated(hasSavedState = true, nowMillis = 200)

        assertTrue(warm.onFirstFrame(300)!!.phases.isEmpty())
    }

    @Test
    fun `a clock that goes backwards cannot produce a negative duration`() {
        val tracker = tracker(processStart = 1_000, install = 1_000)
        tracker.onActivityCreated(hasSavedState = false, nowMillis = 1_100)

        // Not expected, but uptime readings across threads have surprised people
        // before, and a negative startup time in the UI destroys trust in the
        // whole screen.
        assertEquals(0, tracker.onFirstFrame(900)!!.totalMillis)
    }

    private fun tracker(processStart: Long?, install: Long) =
        StartupTracker(processStartMillis = processStart, installMillis = install)

    /** A tracker that has already reported a cold start and is in the foreground. */
    private fun coldStarted(): StartupTracker {
        val tracker = tracker(processStart = 0, install = 300)
        tracker.onActivityCreated(hasSavedState = false, nowMillis = 400)
        tracker.onActivityStarted(410)
        tracker.onFirstFrame(500)
        return tracker
    }
}
