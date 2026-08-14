package io.github.akhilesh2491.scry.perf

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerfExportTest {

    @Test
    fun `a session survives a JSON round trip`() {
        val session = fullSession()

        val decoded = lenientJson.decodeFromString<PerfSession>(session.toJson())

        assertEquals(session, decoded)
    }

    @Test
    fun `the truncated flag survives export`() {
        val session = fullSession()

        assertTrue(session.toJson().contains("\"truncated\": true"), session.toJson())
    }

    @Test
    fun `several sessions export as a JSON array`() {
        val json = listOf(fullSession(), fullSession()).toJson()

        assertEquals(2, Json.decodeFromString<List<PerfSession>>(json).size)
    }

    @Test
    fun `the CSV header comes first and names every column`() {
        val header = listOf(fullSession()).toCsv().lineSequence().first()

        assertEquals(
            "session,started_at,platform,device,app_version,metric,name,value_ms,detail",
            header,
        )
    }

    @Test
    fun `every metric family gets a row`() {
        val metrics = listOf(fullSession()).toCsv()
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",")[5] }
            .toSet()

        assertTrue("startup" in metrics)
        assertTrue("startup_phase" in metrics)
        assertTrue("screen_ttid" in metrics)
        assertTrue("screen_ttfd" in metrics)
        assertTrue("frames_p99" in metrics)
        assertTrue("span" in metrics)
        assertTrue("violation" in metrics)
    }

    @Test
    fun `a comma in a screen name is quoted rather than shifting the columns`() {
        val session = fullSession().copy(
            screens = listOf(
                ScreenLoadMetric("1", "Checkout, step 2", ScreenKind.ACTIVITY, 100, NOW),
            ),
        )

        val row = listOf(session).toCsv().lineSequence().first { it.contains("Checkout") }

        assertTrue(row.contains("\"Checkout, step 2\""), row)
    }

    @Test
    fun `a quote in a name is doubled`() {
        val session = fullSession().copy(
            spans = listOf(SpanMetric("1", "say \"hi\"", 5, NOW)),
        )

        val row = listOf(session).toCsv().lineSequence().first { it.contains("say") }

        assertTrue(row.contains("\"say \"\"hi\"\"\""), row)
    }

    @Test
    fun `a session with nothing in it exports a header and no rows`() {
        val csv = listOf(
            PerfSession(id = "s", startedAtMillis = NOW, platform = "test"),
        ).toCsv()

        assertEquals(1, csv.lineSequence().filter { it.isNotBlank() }.count())
    }

    @Test
    fun `timestamps are formatted as ISO-8601 UTC`() {
        // 2023-11-14T22:13:20Z
        assertEquals("2023-11-14T22:13:20Z", epochMillisToIso8601(1_700_000_000_000L))
    }

    @Test
    fun `the epoch itself formats correctly`() {
        assertEquals("1970-01-01T00:00:00Z", epochMillisToIso8601(0))
    }

    @Test
    fun `a leap day formats correctly`() {
        // 2024-02-29T12:00:00Z
        assertEquals("2024-02-29T12:00:00Z", epochMillisToIso8601(1_709_208_000_000L))
    }

    @Test
    fun `the day after a leap day formats correctly`() {
        assertEquals("2024-03-01T00:00:00Z", epochMillisToIso8601(1_709_251_200_000L))
    }

    private fun fullSession() = PerfSession(
        id = "session-1",
        startedAtMillis = NOW,
        platform = "Android 14 (API 34)",
        deviceModel = "Pixel 8",
        appVersion = "1.2.3",
        startup = StartupMetric(
            kind = StartupKind.COLD,
            totalMillis = 812,
            timestampMillis = NOW,
            phases = listOf(PerfPhase("Application.onCreate", 0, 120)),
            truncated = true,
        ),
        screens = listOf(
            ScreenLoadMetric("1", "Home", ScreenKind.ACTIVITY, 140, NOW, ttfdMillis = 420),
        ),
        frames = listOf(
            FrameStats("Home", 600, 8.33, 12, 1, 6.0, 9.0, 11.0, 40.0, 900.0),
        ),
        spans = listOf(SpanMetric("1", "checkout", 33, NOW)),
        violations = listOf(
            PerfViolation(PerfBudgetKind.FROZEN_FRAMES, "Home", 1.0, 0.0, NOW),
        ),
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
        val lenientJson = Json { ignoreUnknownKeys = true }
    }
}
