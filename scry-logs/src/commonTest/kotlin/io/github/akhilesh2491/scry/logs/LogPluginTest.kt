package io.github.akhilesh2491.scry.logs

import io.github.akhilesh2491.scry.core.ScryTesting
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogPluginTest {

    private fun plugin(configure: LogPluginBuilder.() -> Unit = {}): LogPlugin =
        LogPlugin(configure).also { it.onInstall(ScryTesting.scope()) }

    @AfterTest
    fun tearDown() {
        ScryLog.detach()
    }

    @Test
    fun `records entries newest first`() {
        val plugin = plugin()
        plugin.record(LogLevel.INFO, "A", "first")
        plugin.record(LogLevel.INFO, "B", "second")

        assertEquals(listOf("second", "first"), plugin.entries.value.map { it.message })
    }

    @Test
    fun `drops entries below the minimum level at the source`() {
        val plugin = plugin { minimumLevel = LogLevel.WARN }
        plugin.record(LogLevel.DEBUG, "A", "noise")
        plugin.record(LogLevel.ERROR, "A", "real")

        assertEquals(listOf("real"), plugin.entries.value.map { it.message })
    }

    @Test
    fun `trims to maxEntries on write`() {
        // Trimming has to happen on write. Logs arrive far faster than anything
        // else Scry captures, so deferring it means unbounded growth between
        // UI visits.
        val plugin = plugin { maxEntries = 5 }
        repeat(20) { plugin.record(LogLevel.INFO, "T", "line $it") }

        assertEquals(5, plugin.entries.value.size)
        assertEquals("line 19", plugin.entries.value.first().message)
        assertEquals("line 15", plugin.entries.value.last().message)
    }

    @Test
    fun `captures a throwable's stack trace`() {
        val plugin = plugin()
        plugin.record(LogLevel.ERROR, "T", "failed", IllegalStateException("boom"))

        val entry = plugin.entries.value.single()
        assertTrue(entry.stackTrace!!.contains("IllegalStateException"))
        assertTrue(entry.stackTrace!!.contains("boom"))
    }

    @Test
    fun `badge counts errors when present and entries otherwise`() {
        val plugin = plugin()
        plugin.record(LogLevel.INFO, "T", "fine")
        assertEquals("1", plugin.badge.value)

        plugin.record(LogLevel.ERROR, "T", "bad")
        assertEquals("1", plugin.badge.value, "badge should switch to the error count")

        plugin.record(LogLevel.ERROR, "T", "worse")
        assertEquals("2", plugin.badge.value)
    }

    @Test
    fun `clear empties the buffer and the badge`() {
        val plugin = plugin()
        plugin.record(LogLevel.INFO, "T", "x")
        plugin.onClear()

        assertTrue(plugin.entries.value.isEmpty())
        assertEquals(null, plugin.badge.value)
    }

    @Test
    fun `the facade routes through the installed plugin`() {
        val plugin = plugin()
        ScryLog.i("Checkout", "placing order")

        assertEquals("placing order", plugin.entries.value.single().message)
    }

    @Test
    fun `the facade is inert with no plugin installed`() {
        // Call sites left in shared code must be safe in a release build.
        ScryLog.detach()
        ScryLog.e("Anything", "should not throw")
    }

    @Test
    fun `filtering matches tag and message case-insensitively`() {
        val entry = LogEntry(
            id = "1",
            timestampMillis = 0,
            level = LogLevel.INFO,
            tag = "Checkout",
            message = "Placing order 42",
        )

        assertTrue(entry.matches("checkout"))
        assertTrue(entry.matches("ORDER"))
        assertTrue(entry.matches(""), "a blank query matches everything")
        assertFalse(entry.matches("payment"))
    }

    @Test
    fun `levels order so filtering by minimum is a comparison`() {
        assertTrue(LogLevel.ERROR > LogLevel.WARN)
        assertTrue(LogLevel.WARN > LogLevel.INFO)
        assertTrue(LogLevel.VERBOSE < LogLevel.DEBUG)
    }

    @Test
    fun `format renders level tag and message`() {
        val entry = LogEntry(
            id = "1",
            timestampMillis = 0,
            level = LogLevel.WARN,
            tag = "Net",
            message = "slow response",
        )
        assertEquals("W/Net: slow response", entry.format())
    }

    @Test
    fun `logs are contributed to crash context oldest first`() {
        // A crash report reads top-to-bottom as a narrative, unlike the UI where
        // newest-first is right.
        val scope = ScryTesting.scope()
        val plugin = LogPlugin().also { it.onInstall(scope) }
        plugin.record(LogLevel.INFO, "T", "first")
        plugin.record(LogLevel.INFO, "T", "second")

        val body = scope.contextRegistry.collect().single().body
        assertTrue(
            body.indexOf("first") < body.indexOf("second"),
            "crash context should read forwards:\n$body",
        )
    }
}
