package io.github.akhilesh2491.scry.crash

import io.github.akhilesh2491.scry.core.ScryContextRegistry
import io.github.akhilesh2491.scry.core.ScryContextSection
import io.github.akhilesh2491.scry.core.ScryTesting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportTest {

    private fun record(
        kind: CrashKind = CrashKind.CRASH,
        sections: List<CrashSection> = emptyList(),
        message: String? = "boom",
    ) = CrashRecord(
        id = "1",
        timestampMillis = 1_786_298_357_646,
        kind = kind,
        threadName = "main",
        exceptionClass = "IllegalStateException",
        message = message,
        stackTrace = "java.lang.IllegalStateException: boom\n\tat Foo.bar(Foo.kt:1)",
        sections = sections,
    )

    @Test
    fun `headline combines exception and message`() {
        assertEquals("IllegalStateException: boom", record().headline)
    }

    @Test
    fun `headline omits the separator when there is no message`() {
        assertEquals("IllegalStateException", record(message = null).headline)
    }

    @Test
    fun `report contains the identifying facts and the stack`() {
        val text = record().toReportText()

        assertTrue(text.contains("CRASH report"))
        assertTrue(text.contains("main"))
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("at Foo.bar(Foo.kt:1)"))
    }

    @Test
    fun `report renders every context section under its title`() {
        val text = record(
            sections = listOf(
                CrashSection("Last 2 network calls", "200 GET /a\n500 GET /b"),
                CrashSection("Feature flags", "new_checkout=false"),
            ),
        ).toReportText()

        assertTrue(text.contains("--- Last 2 network calls ---"))
        assertTrue(text.contains("500 GET /b"))
        assertTrue(text.contains("--- Feature flags ---"))
        assertTrue(text.contains("new_checkout=false"))
    }

    @Test
    fun `report omits the message line when absent`() {
        assertFalse(record(message = null).toReportText().contains("message:"))
    }

    @Test
    fun `anr reports are labelled as such`() {
        assertTrue(record(kind = CrashKind.ANR).toReportText().contains("ANR report"))
    }

    // --- context collection -------------------------------------------------

    @Test
    fun `registry collects from every contributor`() {
        val registry = ScryTesting.scope().contextRegistry
        registry.register("a") { listOf(ScryContextSection("A", "a-body")) }
        registry.register("b") { listOf(ScryContextSection("B", "b-body")) }

        val titles = registry.collect().map { it.title }
        assertEquals(setOf("A", "B"), titles.toSet())
    }

    @Test
    fun `a throwing contributor does not lose the rest of the report`() {
        // This runs while the process is already crashing. Losing the whole
        // report because one contributor failed would defeat the purpose, so the
        // failure is reported as a section instead of propagating.
        val registry = ScryTesting.scope().contextRegistry
        registry.register("healthy") { listOf(ScryContextSection("Healthy", "fine")) }
        registry.register("broken") { error("contributor exploded") }

        val sections = registry.collect()

        assertTrue(sections.any { it.title == "Healthy" }, "healthy section must survive")
        val failure = sections.single { it.title.contains("broken") }
        assertTrue(failure.title.contains("failed"))
        assertTrue(failure.body.contains("contributor exploded"))
    }

    @Test
    fun `re-registering the same source replaces rather than duplicates`() {
        val registry = ScryTesting.scope().contextRegistry
        registry.register("plugin") { listOf(ScryContextSection("v1", "old")) }
        registry.register("plugin") { listOf(ScryContextSection("v2", "new")) }

        val sections = registry.collect()
        assertEquals(1, sections.size)
        assertEquals("v2", sections.single().title)
    }

    @Test
    fun `unregister removes a contributor`() {
        val registry = ScryTesting.scope().contextRegistry
        registry.register("plugin") { listOf(ScryContextSection("X", "x")) }
        registry.unregister("plugin")

        assertTrue(registry.collect().isEmpty())
    }

    @Test
    fun `an empty registry collects nothing rather than failing`() {
        assertTrue(ScryContextRegistryProbe.empty().collect().isEmpty())
    }
}

/** Reaches a fresh [ScryContextRegistry] without standing up a whole scope. */
private object ScryContextRegistryProbe {
    fun empty(): ScryContextRegistry = ScryTesting.scope().contextRegistry
}
