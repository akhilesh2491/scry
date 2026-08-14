package io.github.akhilesh2491.scry.network

import kotlinx.serialization.Serializable

/** A single HTTP header. */
@Serializable
public data class HttpHeader(
    public val name: String,
    public val value: String,
)

/**
 * One captured HTTP exchange.
 *
 * **Engine-agnostic on purpose.** This type must never reference Ktor or OkHttp
 * types. It is what lets one UI, one storage format, and one export path serve
 * every capture adapter — and what will let the iOS `NSURLProtocol` adapter drop
 * in during M2 without touching anything downstream.
 */
@Serializable
public data class NetworkTransaction(
    public val id: String,
    public val startedAtMillis: Long,
    public val method: String,
    public val url: String,
    public val durationMillis: Long? = null,
    public val protocol: String? = null,
    public val requestHeaders: List<HttpHeader> = emptyList(),
    public val requestBody: String? = null,
    public val requestBodyTruncated: Boolean = false,
    public val requestBodySize: Long? = null,
    public val responseCode: Int? = null,
    public val responseMessage: String? = null,
    public val responseHeaders: List<HttpHeader> = emptyList(),
    public val responseBody: String? = null,
    public val responseBodyTruncated: Boolean = false,
    public val responseBodySize: Long? = null,
    /** Message of the failure that ended this exchange, if it failed. */
    public val error: String? = null,
) {
    /** Host portion of [url], for the list view. */
    public val host: String
        get() = url.substringAfter("://", "").substringBefore('/').substringBefore('?')

    /** Path (and query) portion of [url], for the list view. */
    public val path: String
        get() = url.substringAfter("://", url).substringAfter('/', "").let { "/$it" }

    /** True once a response or an error has arrived. */
    public val isComplete: Boolean get() = responseCode != null || error != null

    /** `https`, checked on the URL scheme. */
    public val isSecure: Boolean get() = url.startsWith("https://", ignoreCase = true)

    /** Short status text for the list row. */
    public val statusLabel: String
        get() = when {
            error != null -> "error"
            responseCode != null -> responseCode.toString()
            else -> "…"
        }
}
