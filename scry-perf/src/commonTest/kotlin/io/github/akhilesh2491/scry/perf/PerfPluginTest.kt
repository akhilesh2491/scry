package io.github.akhilesh2491.scry.perf

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfPluginTest {

    @AfterTest
    fun tearDown() {
        ScryTrace.detach()
    }

    @Test
    fun `only the first startup of a launch is kept`() {
        val plugin = PerfPlugin()

        plugin.recordStartup(startup(500))
        plugin.recordStartup(startup(9_000))

        assertEquals(500, plugin.session.value.startup?.totalMillis)
    }

    @Test
    fun `hot starts accumulate instead of replacing the launch`() {
        val plugin = PerfPlugin()
        plugin.recordStartup(startup(500))

        plugin.recordStartup(hotStart(40))
        plugin.recordStartup(hotStart(60))

        assertEquals(500, plugin.session.value.startup?.totalMillis)
        assertEquals(listOf(40L, 60L), plugin.session.value.hotStarts.map { it.totalMillis })
    }

    @Test
    fun `hot starts are not judged against the cold budget`() {
        val plugin = PerfPlugin { budget { coldStartupMillis = 10 } }

        plugin.recordStartup(hotStart(5_000))

        assertTrue(plugin.session.value.violations.isEmpty())
    }

    @Test
    fun `startup is not recorded when tracking is off`() {
        val plugin = PerfPlugin { trackStartup = false }

        plugin.recordStartup(startup(500))

        assertNull(plugin.session.value.startup)
    }

    @Test
    fun `screen loads are not recorded when tracking is off`() {
        val plugin = PerfPlugin { trackScreens = false }

        plugin.recordScreenLoad("Home", ScreenKind.ACTIVITY, 100)

        assertTrue(plugin.session.value.screens.isEmpty())
    }

    @Test
    fun `the first frame after a screen is entered completes its load`() {
        val plugin = PerfPlugin()

        plugin.screenEntered("Checkout", ScreenKind.ACTIVITY)
        plugin.recordFrame("Checkout", 8.0, BUDGET_60HZ)

        val screen = plugin.session.value.screens.single()
        assertEquals("Checkout", screen.screen)
        assertEquals(ScreenKind.ACTIVITY, screen.kind)
    }

    @Test
    fun `later frames do not record further screen loads`() {
        val plugin = PerfPlugin()

        plugin.screenEntered("Checkout", ScreenKind.ACTIVITY)
        repeat(10) { plugin.recordFrame("Checkout", 8.0, BUDGET_60HZ) }

        assertEquals(1, plugin.session.value.screens.size)
    }

    @Test
    fun `frame stats appear once enough frames have been drawn to aggregate`() {
        val plugin = PerfPlugin()

        repeat(20) { plugin.recordFrame("Home", 8.0, BUDGET_60HZ) }

        val stats = plugin.session.value.frames.single()
        assertEquals("Home", stats.screen)
        assertEquals(20, stats.frameCount)
    }

    @Test
    fun `a frozen frame is published without waiting for the aggregation interval`() {
        val plugin = PerfPlugin()

        // One frame, well short of the interval — a stall must not sit unreported
        // until nineteen more frames happen to arrive.
        plugin.recordFrame("Home", 1_500.0, BUDGET_60HZ)

        assertEquals(1, plugin.session.value.frames.single().frozenFrames)
    }

    @Test
    fun `frames are not recorded when tracking is off`() {
        val plugin = PerfPlugin { trackFrames = false }

        repeat(50) { plugin.recordFrame("Home", 900.0, BUDGET_60HZ) }

        assertTrue(plugin.session.value.frames.isEmpty())
    }

    @Test
    fun `frame stats are kept per screen`() {
        val plugin = PerfPlugin()

        repeat(20) { plugin.recordFrame("Home", 8.0, BUDGET_60HZ) }
        repeat(20) { plugin.recordFrame("Checkout", 8.0, BUDGET_60HZ) }

        assertEquals(setOf("Home", "Checkout"), plugin.session.value.frames.map { it.screen }.toSet())
    }

    @Test
    fun `a violation is raised once per kind and label`() {
        val plugin = PerfPlugin { budget { slowFramePercent = 1.0 } }

        repeat(200) { plugin.recordFrame("Home", 900.0, BUDGET_60HZ) }

        // Every aggregation tick sees the same broken budget; a badge counting
        // into the dozens would say less than one that says "two things".
        val jank = plugin.session.value.violations.filter { it.kind == PerfBudgetKind.JANK }
        assertEquals(1, jank.size)
    }

    @Test
    fun `the violation listener is called`() {
        val seen = mutableListOf<PerfViolation>()
        val plugin = PerfPlugin {
            budget { coldStartupMillis = 100 }
            onViolation { seen += it }
        }

        plugin.recordStartup(startup(500))

        assertEquals(1, seen.size)
        assertEquals(PerfBudgetKind.STARTUP, seen.single().kind)
    }

    @Test
    fun `the badge counts violations when there are any`() {
        val plugin = PerfPlugin { budget { coldStartupMillis = 100 } }

        plugin.recordStartup(startup(500))

        assertEquals("1", plugin.badge.value)
    }

    @Test
    fun `the badge counts measurements when nothing is over budget`() {
        val plugin = PerfPlugin { budget { coldStartupMillis = null } }

        plugin.recordStartup(startup(500))
        plugin.recordScreenLoad("Home", ScreenKind.ACTIVITY, 10)

        assertEquals("2", plugin.badge.value)
    }

    @Test
    fun `the badge is absent before anything is measured`() {
        assertNull(PerfPlugin().badge.value)
    }

    @Test
    fun `TTFD attaches to the most recent unfinished load of that screen`() {
        val plugin = PerfPlugin()
        plugin.recordScreenLoad("Home", ScreenKind.ACTIVITY, 10)
        plugin.recordScreenLoad("Home", ScreenKind.ACTIVITY, 20)

        plugin.recordFullyDrawn("Home", 300)

        val screens = plugin.session.value.screens
        assertEquals(300, screens[1].ttfdMillis)
        assertNull(screens[0].ttfdMillis)
    }

    @Test
    fun `clearing resets the session and its measurements`() {
        val plugin = PerfPlugin()
        plugin.recordStartup(startup(500))
        repeat(20) { plugin.recordFrame("Home", 8.0, BUDGET_60HZ) }

        plugin.onClear()

        assertNull(plugin.session.value.startup)
        assertTrue(plugin.session.value.frames.isEmpty())
        assertNull(plugin.frameStats("Home"))
        assertNull(plugin.badge.value)
    }

    @Test
    fun `installed reports the attached plugin`() {
        val plugin = PerfPlugin()
        ScryTrace.attach(plugin)

        assertEquals(plugin, PerfPlugin.installed())

        ScryTrace.detach()
        assertNull(PerfPlugin.installed())
    }

    @Test
    fun `recentFrames returns nothing for an unknown screen`() {
        assertTrue(PerfPlugin().recentFrames("Nowhere", 10).isEmpty())
    }

    @Test
    fun `frameStats aggregates up to the current instant`() {
        val plugin = PerfPlugin()

        repeat(5) { plugin.recordFrame("Home", 8.0, BUDGET_60HZ) }

        assertNotNull(plugin.frameStats("Home"))
        assertEquals(5, plugin.frameStats("Home")?.frameCount)
    }

    private fun startup(millis: Long) = StartupMetric(
        kind = StartupKind.COLD,
        totalMillis = millis,
        timestampMillis = 1_700_000_000_000L,
    )

    private fun hotStart(millis: Long) = StartupMetric(
        kind = StartupKind.HOT,
        totalMillis = millis,
        timestampMillis = 1_700_000_000_000L,
    )

    private companion object {
        const val BUDGET_60HZ = 1000.0 / 60.0
    }
}
