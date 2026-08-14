package io.github.akhilesh2491.scry.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Exporting captured traffic.
 *
 * Both formats read from [NetworkTransaction], which is already redacted — the
 * masking happens on capture, before anything is stored. Exports are therefore
 * safe by construction rather than by remembering to sanitise them here.
 */

private val exportJson = Json { prettyPrint = true }

/**
 * Renders a transaction as a runnable `curl` command.
 *
 * The single most requested feature of every network inspector, because it turns
 * "it failed on my phone" into a command a backend engineer can paste into a
 * terminal.
 */
public fun NetworkTransaction.toCurl(): String = buildString {
    append("curl")
    if (!method.equals("GET", ignoreCase = true)) {
        append(" -X ").append(method)
    }
    append(" '").append(shellEscape(url)).append("'")
    requestHeaders.forEach { header ->
        append(" \\\n  -H '").append(shellEscape("${header.name}: ${header.value}")).append("'")
    }
    requestBody?.takeIf { it.isNotEmpty() }?.let { body ->
        append(" \\\n  --data-raw '").append(shellEscape(body)).append("'")
        if (requestBodyTruncated) {
            append(" \\\n  # NOTE: body truncated by Scry; this is not the full payload")
        }
    }
}

/**
 * Escapes a value for a single-quoted shell string.
 *
 * `'` cannot be escaped inside single quotes, so the quoting is closed, an
 * escaped quote is emitted, and quoting reopens. Without this, any header or
 * body containing an apostrophe produces a command that silently does the wrong
 * thing when pasted.
 */
internal fun shellEscape(value: String): String = value.replace("'", "'\\''")

/**
 * Renders transactions as an HTTP Archive (HAR 1.2) document.
 *
 * HAR is the reason this matters: Charles, Proxyman, Insomnia and Chrome DevTools
 * all import it, so an on-device capture becomes something a developer can
 * replay and diff at their desk.
 */
public fun List<NetworkTransaction>.toHar(creatorVersion: String = "0.1.0"): String {
    val log = buildJsonObject {
        putJsonObject("log") {
            put("version", "1.2")
            putJsonObject("creator") {
                put("name", "Scry")
                put("version", creatorVersion)
            }
            put("entries", JsonArray(this@toHar.map { it.toHarEntry() }))
        }
    }
    return exportJson.encodeToString(JsonObject.serializer(), log)
}

private fun NetworkTransaction.toHarEntry(): JsonObject = buildJsonObject {
    put("startedDateTime", epochMillisToIso8601(startedAtMillis))
    put("time", durationMillis ?: 0L)

    putJsonObject("request") {
        put("method", method)
        put("url", url)
        put("httpVersion", protocol ?: "HTTP/1.1")
        put("headers", requestHeaders.toHarHeaders())
        put("queryString", queryStringToHar(url))
        put("cookies", JsonArray(emptyList()))
        put("headersSize", -1)
        put("bodySize", requestBodySize ?: -1L)
        requestBody?.let { body ->
            putJsonObject("postData") {
                put("mimeType", requestHeaders.contentType() ?: "text/plain")
                put("text", body)
            }
        }
    }

    putJsonObject("response") {
        put("status", responseCode ?: 0)
        put("statusText", responseMessage ?: error?.let { "(failed) $it" } ?: "")
        put("httpVersion", protocol ?: "HTTP/1.1")
        put("headers", responseHeaders.toHarHeaders())
        put("cookies", JsonArray(emptyList()))
        putJsonObject("content") {
            put("size", responseBodySize ?: 0L)
            put("mimeType", responseHeaders.contentType() ?: "text/plain")
            responseBody?.let { put("text", it) }
        }
        put("redirectURL", "")
        put("headersSize", -1)
        put("bodySize", responseBodySize ?: -1L)
    }

    putJsonObject("cache") {}
    putJsonObject("timings") {
        // Scry captures total duration, not a per-phase breakdown. HAR requires
        // send/wait/receive, so the whole duration is reported as wait and the
        // others as -1 (the spec's "not applicable") rather than fabricated.
        put("send", -1)
        put("wait", durationMillis ?: 0L)
        put("receive", -1)
    }
}

private fun List<HttpHeader>.toHarHeaders(): JsonArray = buildJsonArray {
    this@toHarHeaders.forEach { header ->
        add(
            buildJsonObject {
                put("name", header.name)
                put("value", header.value)
            },
        )
    }
}

private fun List<HttpHeader>.contentType(): String? =
    firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }?.value

private fun queryStringToHar(url: String): JsonArray = buildJsonArray {
    val query = url.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return@buildJsonArray
    query.split('&').forEach { pair ->
        if (pair.isEmpty()) return@forEach
        add(
            buildJsonObject {
                put("name", pair.substringBefore('='))
                put("value", pair.substringAfter('=', ""))
            },
        )
    }
}

/**
 * Formats epoch milliseconds as an ISO-8601 UTC timestamp.
 *
 * Hand-rolled civil-date arithmetic rather than `kotlinx-datetime` or a
 * per-platform `java.time` actual: it is about twenty lines, it keeps the module
 * dependency-free, and it works unchanged on every target including the iOS one
 * still to come.
 */
internal fun epochMillisToIso8601(millis: Long): String {
    val totalSeconds = millis.floorDiv(1000)
    val millisPart = millis.mod(1000L)
    var days = totalSeconds.floorDiv(86_400)
    val secondOfDay = totalSeconds.mod(86_400L)

    val hour = secondOfDay / 3600
    val minute = (secondOfDay % 3600) / 60
    val second = secondOfDay % 60

    // Civil-from-days (Howard Hinnant's algorithm), shifting the epoch to
    // 0000-03-01 so leap days land at the end of the cycle.
    days += 719_468
    val era = (if (days >= 0) days else days - 146_096).floorDiv(146_097)
    val dayOfEra = days - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
    val calendarYear = if (month <= 2) year + 1 else year

    fun pad(value: Long, width: Int): String = value.toString().padStart(width, '0')

    return "${pad(calendarYear, 4)}-${pad(month, 2)}-${pad(day, 2)}" +
        "T${pad(hour, 2)}:${pad(minute, 2)}:${pad(second, 2)}" +
        ".${pad(millisPart, 3)}Z"
}
