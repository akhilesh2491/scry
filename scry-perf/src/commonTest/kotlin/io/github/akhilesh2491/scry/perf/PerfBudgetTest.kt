package io.github.akhilesh2491.scry.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfBudgetTest {

    @Test
    fun `a cold start over budget is a violation`() {
        val violation = budget().check(startup(StartupKind.COLD, 1_200))

        assertNotNull(violation)
        assertEquals(PerfBudgetKind.STARTUP, violation.kind)
        assertEquals(1_200.0, violation.actual)
        assertEquals(1_000.0, violation.limit)
    }

    @Test
    fun `a cold start exactly on budget passes`() {
        assertNull(budget().check(startup(StartupKind.COLD, 1_000)))
    }

    @Test
    fun `warm and hot starts are never budgeted`() {
        // A warm start does less work by definition; holding it to the cold
        // budget would make the number meaningless.
        assertNull(budget().check(startup(StartupKind.WARM, 9_999)))
        assertNull(budget().check(startup(StartupKind.HOT, 9_999)))
    }

    @Test
    fun `a null startup budget disables the check`() {
        val budget = PerfBudgetBuilder(PerfBudget.DEFAULT).apply { coldStartupMillis = null }.build()

        assertNull(budget.check(startup(StartupKind.COLD, 60_000)))
    }

    @Test
    fun `screen loads are judged on TTFD when one was reported`() {
        val metric = screen(ttid = 100, ttfd = 900)
        val violation = budget().check(metric)

        assertNotNull(violation)
        assertEquals(900.0, violation.actual)
    }

    @Test
    fun `screen loads fall back to TTID when no TTFD was reported`() {
        assertNull(budget().check(screen(ttid = 100, ttfd = null)))
        assertNotNull(budget().check(screen(ttid = 900, ttfd = null)))
    }

    @Test
    fun `jank is not judged before enough frames have been drawn`() {
        // Three slow frames right after a screen appears is normal, not a bug.
        val stats = FrameStats.of("Home", List(3) { 40.0 }, BUDGET_60HZ)

        assertTrue(budget().check(stats, NOW).none { it.kind == PerfBudgetKind.JANK })
    }

    @Test
    fun `jank over budget is a violation once enough frames exist`() {
        val durations = List(PerfBudget.MINIMUM_FRAMES_FOR_JANK) { 40.0 }
        val violations = budget().check(FrameStats.of("Home", durations, BUDGET_60HZ), NOW)

        val jank = violations.single { it.kind == PerfBudgetKind.JANK }
        assertEquals(100.0, jank.actual)
        assertEquals("Home", jank.label)
    }

    @Test
    fun `frozen frames are judged immediately and independently of frame count`() {
        val violations = budget().check(FrameStats.of("Home", listOf(1_000.0), BUDGET_60HZ), NOW)

        assertEquals(listOf(PerfBudgetKind.FROZEN_FRAMES), violations.map { it.kind })
    }

    @Test
    fun `NONE measures everything and judges nothing`() {
        assertNull(PerfBudget.NONE.check(startup(StartupKind.COLD, 60_000)))
        assertNull(PerfBudget.NONE.check(screen(ttid = 60_000, ttfd = null)))
        assertTrue(PerfBudget.NONE.check(FrameStats.of("Home", listOf(5_000.0), BUDGET_60HZ), NOW).isEmpty())
    }

    @Test
    fun `the builder starts from the budget it is given`() {
        val budget = PerfBudgetBuilder(PerfBudget.DEFAULT).apply { coldStartupMillis = 500 }.build()

        assertEquals(500, budget.coldStartupMillis)
        assertEquals(PerfBudget.DEFAULT.screenLoadMillis, budget.screenLoadMillis)
    }

    @Test
    fun `a violation formats with its unit`() {
        val violation = PerfViolation(PerfBudgetKind.STARTUP, "Cold start", 1_200.0, 1_000.0, NOW)

        assertTrue(violation.format().contains("1200.0ms"), violation.format())
        assertTrue(violation.format().contains("Cold start"))
    }

    private fun budget() = PerfBudget(
        coldStartupMillis = 1_000,
        screenLoadMillis = 500,
        slowFramePercent = 10.0,
        frozenFrames = 0,
    )

    private fun startup(kind: StartupKind, millis: Long) =
        StartupMetric(kind = kind, totalMillis = millis, timestampMillis = NOW)

    private fun screen(ttid: Long, ttfd: Long?) = ScreenLoadMetric(
        id = "1",
        screen = "Home",
        kind = ScreenKind.ACTIVITY,
        ttidMillis = ttid,
        timestampMillis = NOW,
        ttfdMillis = ttfd,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val BUDGET_60HZ = 1000.0 / 60.0
    }
}
