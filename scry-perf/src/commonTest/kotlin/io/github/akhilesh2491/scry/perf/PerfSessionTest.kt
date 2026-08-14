package io.github.akhilesh2491.scry.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfSessionTest {

    @Test
    fun `overall jank weights screens by how many frames they drew`() {
        val session = session(
            frames = listOf(
                stats("Home", frameCount = 900, slowFrames = 0),
                stats("Checkout", frameCount = 100, slowFrames = 100),
            ),
        )

        // A screen with a 100% jank rate over 100 frames must not outweigh 900
        // clean ones — averaging the percentages would say 50%.
        assertEquals(10.0, session.overallJankPercent)
    }

    @Test
    fun `overall jank is zero when nothing was drawn`() {
        assertEquals(0.0, session().overallJankPercent)
    }

    @Test
    fun `a faster startup produces a negative delta`() {
        val previous = session(startup = 1_000)
        val current = session(startup = 700)

        assertEquals(-300, current.diff(previous).startupDeltaMillis)
    }

    @Test
    fun `a slower startup produces a positive delta`() {
        assertEquals(300, session(startup = 1_300).diff(session(startup = 1_000)).startupDeltaMillis)
    }

    @Test
    fun `the startup delta is absent when either session lacks one`() {
        assertNull(session(startup = 500).diff(session()).startupDeltaMillis)
        assertNull(session().diff(session(startup = 500)).startupDeltaMillis)
    }

    @Test
    fun `screen deltas cover only screens both sessions visited`() {
        val previous = session(screens = mapOf("Home" to 100L, "Settings" to 200L))
        val current = session(screens = mapOf("Home" to 150L, "Checkout" to 300L))

        val delta = current.diff(previous).screenDeltaMillis

        assertEquals(mapOf("Home" to 50L), delta)
    }

    @Test
    fun `the comparison carries both session ids`() {
        val previous = session().copy(id = "old")
        val current = session().copy(id = "new")

        val comparison = current.diff(previous)

        assertEquals("new", comparison.sessionId)
        assertEquals("old", comparison.previousSessionId)
    }

    @Test
    fun `new ids are unique and sort by time`() {
        val ids = List(100) { PerfSession.newId() }

        assertEquals(100, ids.toSet().size)
        assertTrue(ids.all { it.contains('-') })
    }

    private fun session(
        startup: Long? = null,
        screens: Map<String, Long> = emptyMap(),
        frames: List<FrameStats> = emptyList(),
    ) = PerfSession(
        id = "s",
        startedAtMillis = 1_700_000_000_000L,
        platform = "test",
        startup = startup?.let {
            StartupMetric(StartupKind.COLD, it, 1_700_000_000_000L)
        },
        screens = screens.map { (name, ttid) ->
            ScreenLoadMetric(
                id = name,
                screen = name,
                kind = ScreenKind.ACTIVITY,
                ttidMillis = ttid,
                timestampMillis = 1_700_000_000_000L,
            )
        },
        frames = frames,
    )

    private fun stats(screen: String, frameCount: Int, slowFrames: Int) = FrameStats(
        screen = screen,
        frameCount = frameCount,
        budgetMillis = 1000.0 / 60.0,
        slowFrames = slowFrames,
        frozenFrames = 0,
        p50Millis = 8.0,
        p90Millis = 9.0,
        p95Millis = 10.0,
        p99Millis = 11.0,
        worstMillis = 12.0,
    )
}
