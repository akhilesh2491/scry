package io.github.akhilesh2491.scry.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportTest {

    private fun transaction(
        method: String = "GET",
        url: String = "https://api.example.com/things?page=2&sort=asc",
        requestBody: String? = null,
        requestHeaders: List<HttpHeader> = listOf(HttpHeader("Accept", "application/json")),
    ) = NetworkTransaction(
        id = "1",
        startedAtMillis = 1_786_298_357_646,
        method = method,
        url = url,
        durationMillis = 123,
        protocol = "HTTP/1.1",
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        requestBodySize = requestBody?.length?.toLong(),
        responseCode = 200,
        responseMessage = "OK",
        responseHeaders = listOf(HttpHeader("Content-Type", "application/json")),
        responseBody = """{"ok":true}""",
        responseBodySize = 11,
    )

    // --- cURL ---------------------------------------------------------------

    @Test
    fun `GET omits the redundant method flag`() {
        val curl = transaction().toCurl()
        assertFalse(curl.contains("-X GET"))
        assertTrue(curl.startsWith("curl '"))
    }

    @Test
    fun `non-GET methods are explicit`() {
        assertTrue(transaction(method = "POST").toCurl().contains("-X POST"))
    }

    @Test
    fun `headers and body are included`() {
        val curl = transaction(method = "POST", requestBody = """{"a":1}""").toCurl()
        assertTrue(curl.contains("-H 'Accept: application/json'"))
        assertTrue(curl.contains("""--data-raw '{"a":1}'"""))
    }

    @Test
    fun `single quotes are escaped so the command stays runnable`() {
        // Inside single quotes a quote cannot be backslash-escaped; the quoting
        // has to be closed and reopened. Getting this wrong produces a command
        // that silently does the wrong thing when pasted.
        val curl = transaction(
            method = "POST",
            requestBody = """{"name":"O'Brien"}""",
        ).toCurl()

        assertTrue(curl.contains("""O'\''Brien"""), curl)
    }

    @Test
    fun `truncated bodies are flagged in the command`() {
        val truncated = transaction(method = "POST", requestBody = "abc")
            .copy(requestBodyTruncated = true)
        assertTrue(truncated.toCurl().contains("truncated"))
    }

    @Test
    fun `redacted values flow through to the export`() {
        // Redaction happens on capture, so an export cannot leak a secret that
        // was never stored. This pins that property.
        val redacted = transaction(
            requestHeaders = listOf(HttpHeader("Authorization", REDACTED_SAMPLE)),
        )
        val curl = redacted.toCurl()
        assertTrue(curl.contains(REDACTED_SAMPLE))
        assertFalse(curl.contains("Bearer"))
    }

    // --- HAR ----------------------------------------------------------------

    private fun harLog(vararg transactions: NetworkTransaction): JsonObject =
        Json.parseToJsonElement(transactions.toList().toHar()).jsonObject["log"]!!.jsonObject

    @Test
    fun `har declares version and creator`() {
        val log = harLog(transaction())
        assertEquals("1.2", log["version"]!!.jsonPrimitive.content)
        assertEquals("Scry", log["creator"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `har entry carries request and response detail`() {
        val entry = harLog(transaction()).let { it["entries"]!!.jsonArray.single().jsonObject }

        val request = entry["request"]!!.jsonObject
        assertEquals("GET", request["method"]!!.jsonPrimitive.content)
        assertEquals("HTTP/1.1", request["httpVersion"]!!.jsonPrimitive.content)

        val response = entry["response"]!!.jsonObject
        assertEquals(200, response["status"]!!.jsonPrimitive.content.toInt())
        assertEquals("OK", response["statusText"]!!.jsonPrimitive.content)
        assertEquals(
            """{"ok":true}""",
            response["content"]!!.jsonObject["text"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `har parses the query string into name value pairs`() {
        val entry = harLog(transaction()).let { it["entries"]!!.jsonArray.single().jsonObject }
        val query = entry["request"]!!.jsonObject["queryString"]!!.jsonArray

        assertEquals(2, query.size)
        assertEquals("page", query[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("2", query[0].jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("sort", query[1].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `har handles a url with no query string`() {
        val entry = harLog(transaction(url = "https://api.example.com/things"))
            .let { it["entries"]!!.jsonArray.single().jsonObject }
        assertEquals(0, entry["request"]!!.jsonObject["queryString"]!!.jsonArray.size)
    }

    @Test
    fun `har of a failed request still produces a valid entry`() {
        val failed = NetworkTransaction(
            id = "2",
            startedAtMillis = 1_786_298_357_646,
            method = "GET",
            url = "https://nope.invalid/x",
            error = "Unable to resolve host",
        )
        val entry = harLog(failed).let { it["entries"]!!.jsonArray.single().jsonObject }

        assertEquals(0, entry["response"]!!.jsonObject["status"]!!.jsonPrimitive.content.toInt())
        assertTrue(
            entry["response"]!!.jsonObject["statusText"]!!.jsonPrimitive.content
                .contains("Unable to resolve host"),
        )
    }

    @Test
    fun `empty session produces a valid empty har`() {
        assertEquals(0, harLog().let { it["entries"]!!.jsonArray.size })
    }

    // --- timestamps ---------------------------------------------------------

    @Test
    fun `formats epoch millis as iso 8601 utc`() {
        assertEquals("1970-01-01T00:00:00.000Z", epochMillisToIso8601(0))
        assertEquals("2026-08-10T22:39:17.646Z", epochMillisToIso8601(1_786_401_557_646))
    }

    @Test
    fun `handles leap days and year boundaries`() {
        // 2024-02-29T12:00:00Z — a leap day, the classic off-by-one in civil-date math.
        assertEquals("2024-02-29T12:00:00.000Z", epochMillisToIso8601(1_709_208_000_000))
        assertEquals("2023-12-31T23:59:59.999Z", epochMillisToIso8601(1_704_067_199_999))
    }

    private companion object {
        const val REDACTED_SAMPLE = "██ redacted by Scry ██"
    }
}
