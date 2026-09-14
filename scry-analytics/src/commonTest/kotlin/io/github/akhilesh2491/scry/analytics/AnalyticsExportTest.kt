package io.github.akhilesh2491.scry.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsExportTest {

    private val visit = ScreenVisit(
        id = "v1",
        screen = "Checkout",
        enteredAtMillis = 1_700_000_000_000,
        events = listOf(
            AnalyticsEvent(
                id = "e1",
                name = "begin_checkout",
                params = mapOf(
                    "cart_value" to AnalyticsValue.Number(49.9),
                    "currency" to AnalyticsValue.Text("USD"),
                ),
                screen = "Checkout",
                timestampMillis = 1_700_000_001_000,
            ),
        ),
        verdict = AnalyticsVerdict.PASS,
    )

    @Test
    fun `json keeps the visit whole`() {
        val json = listOf(visit).toJson()

        assertTrue("begin_checkout" in json)
        assertTrue("cart_value" in json)
        assertTrue("PASS" in json)
    }

    @Test
    fun `csv has one row per event`() {
        val rows = listOf(visit).toCsv().trim().lines()

        assertEquals(2, rows.size)
        assertTrue(rows[0].startsWith("time,screen,verdict,event"))
        assertTrue("begin_checkout" in rows[1])
        assertTrue("cart_value=49.9; currency=USD" in rows[1])
    }

    @Test
    fun `a comma inside a value does not shift the columns`() {
        val withComma = visit.copy(
            events = listOf(
                visit.events.single().copy(
                    params = mapOf("title" to AnalyticsValue.Text("Hat, red")),
                ),
            ),
        )

        val row = withComma.let { listOf(it) }.toCsv().trim().lines()[1]

        assertTrue("\"title=Hat, red\"" in row, row)
    }

    @Test
    fun `a visit that fired nothing still produces a row`() {
        val empty = visit.copy(
            id = "v2",
            events = emptyList(),
            verdict = AnalyticsVerdict.FAIL,
            issues = listOf(
                AnalyticsIssue(
                    kind = AnalyticsIssueKind.MISSING_EVENT,
                    screen = "Checkout",
                    eventName = "begin_checkout",
                    timestampMillis = 1_700_000_002_000,
                ),
            ),
        )

        val rows = listOf(empty).toCsv().trim().lines()

        assertEquals(2, rows.size)
        assertTrue("never fired" in rows[1])
    }

    @Test
    fun `timestamps export as ISO-8601 UTC`() {
        assertEquals("2023-11-14T22:13:20Z", epochMillisToIso8601(1_700_000_000_000))
        assertEquals("22:13:20", clockTime(1_700_000_000_000))
    }
}
