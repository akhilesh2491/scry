package io.github.akhilesh2491.scry.perf

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScryTraceTest {

    @AfterTest
    fun tearDown() {
        ScryTrace.detach()
    }

    @Test
    fun `spans are dropped when no plugin is installed`() {
        ScryTrace.detach()

        // The contract that lets ScryTrace calls stay in shared code compiled
        // against scry-no-op: calling it uninstalled must be inert, not fatal.
        ScryTrace.measure("checkout") { }
        ScryTrace.screenEntered("Home")
        ScryTrace.reportFullyDrawn("Home", 100)
    }

    @Test
    fun `measure records the span and returns the block's value`() {
        val plugin = attachedPlugin()

        val result = ScryTrace.measure("checkout") { 42 }

        assertEquals(42, result)
        assertEquals(listOf("checkout"), plugin.session.value.spans.map { it.name })
    }

    @Test
    fun `a span is recorded even when the block throws`() {
        val plugin = attachedPlugin()

        assertFailsWith<IllegalStateException> {
            ScryTrace.measure("checkout") { error("boom") }
        }

        assertEquals(listOf("checkout"), plugin.session.value.spans.map { it.name })
    }

    @Test
    fun `nested spans record their depth`() {
        val plugin = attachedPlugin()

        ScryTrace.measure("outer") {
            ScryTrace.measure("inner") { }
        }

        val byName = plugin.session.value.spans.associate { it.name to it.depth }
        assertEquals(0, byName["outer"])
        assertEquals(1, byName["inner"])
    }

    @Test
    fun `depth returns to zero after spans close`() {
        val plugin = attachedPlugin()

        ScryTrace.measure("first") { }
        ScryTrace.measure("second") { }

        assertTrue(plugin.session.value.spans.all { it.depth == 0 })
    }

    @Test
    fun `closing a span twice records it once`() {
        val plugin = attachedPlugin()

        val span = ScryTrace.begin("work")
        span.close()
        span.close()

        assertEquals(1, plugin.session.value.spans.size)
    }

    @Test
    fun `end is equivalent to close`() {
        val plugin = attachedPlugin()

        ScryTrace.end(ScryTrace.begin("work"))

        assertEquals(listOf("work"), plugin.session.value.spans.map { it.name })
    }

    @Test
    fun `screenDisplayed records a screen load directly`() {
        val plugin = attachedPlugin()

        ScryTrace.screenDisplayed("Checkout", 250)

        val screen = plugin.session.value.screens.single()
        assertEquals("Checkout", screen.screen)
        assertEquals(250, screen.ttidMillis)
    }

    @Test
    fun `reportFullyDrawn attaches TTFD to the matching screen load`() {
        val plugin = attachedPlugin()
        ScryTrace.screenDisplayed("Checkout", 250)

        ScryTrace.reportFullyDrawn("Checkout", 900)

        assertEquals(900, plugin.session.value.screens.single().ttfdMillis)
    }

    @Test
    fun `reportFullyDrawn for an unknown screen is ignored`() {
        val plugin = attachedPlugin()

        ScryTrace.reportFullyDrawn("NeverShown", 900)

        assertTrue(plugin.session.value.screens.isEmpty())
    }

    @Test
    fun `detach stops recording`() {
        val plugin = attachedPlugin()
        ScryTrace.detach()

        ScryTrace.measure("after detach") { }

        assertTrue(plugin.session.value.spans.isEmpty())
    }

    private fun attachedPlugin(configure: PerfPluginBuilder.() -> Unit = {}): PerfPlugin =
        PerfPlugin(configure).also { ScryTrace.attach(it) }
}
