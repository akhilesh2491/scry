package io.github.akhilesh2491.scry.network.ktor

import io.github.akhilesh2491.scry.network.CapturedBody
import io.github.akhilesh2491.scry.network.HttpHeader
import io.github.akhilesh2491.scry.network.MockAction
import io.github.akhilesh2491.scry.network.MockOutcome
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.network.NetworkTransaction
import io.github.akhilesh2491.scry.network.newTransactionId
import io.github.akhilesh2491.scry.network.nowMillis
import io.github.akhilesh2491.scry.network.truncateForCapture
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.util.date.GMTDate
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.delay
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent

/** Configuration for the [ScryKtor] client plugin. */
public class ScryKtorConfig {
    /**
     * Where captured traffic is sent.
     *
     * Left `null`, the plugin resolves [NetworkPlugin.installed] at call time —
     * so `install(ScryKtor)` works with no wiring, and a client built before
     * `Scry.install()` still captures once Scry comes up. Set it explicitly in
     * tests, or when running more than one Scry instance.
     */
    public var networkPlugin: NetworkPlugin? = null
}

/**
 * Ktor client plugin that reports traffic to Scry.
 *
 * ```kotlin
 * val client = HttpClient { install(ScryKtor) }
 * ```
 *
 * This is the multiplatform capture path: one implementation covering every
 * target Ktor runs on. `scry-network-okhttp` exists alongside it for Android and
 * JVM codebases on OkHttp/Retrofit.
 */
public val ScryKtor: ClientPlugin<ScryKtorConfig> =
    createClientPlugin("ScryKtor", ::ScryKtorConfig) {
        val configuredPlugin = pluginConfig.networkPlugin

        on(Send) { request ->
            val plugin = configuredPlugin ?: NetworkPlugin.installed()
            if (plugin == null) return@on proceed(request)

            val id = newTransactionId()
            val startedAt = nowMillis()

            val requestBody = if (plugin.config.captureRequestBodies) {
                (request.body as? OutgoingContent).readTextOrNull(plugin.config.maxBodyBytes)
            } else {
                null
            }

            // Record the in-flight request immediately, so a request that never
            // comes back is still visible. A hung call showing nothing at all is
            // the worst possible behaviour for a network inspector.
            plugin.record(
                NetworkTransaction(
                    id = id,
                    startedAtMillis = startedAt,
                    method = request.method.value,
                    url = request.url.buildString(),
                    requestHeaders = request.headers.entries()
                        .flatMap { entry -> entry.value.map { HttpHeader(entry.key, it) } },
                    requestBody = requestBody?.text,
                    requestBodyTruncated = requestBody?.truncated ?: false,
                    requestBodySize = requestBody?.originalSize,
                ),
            )

            // Mocking is consulted before the request leaves. resolve() returns
            // null when nothing is configured, so the ordinary path is unchanged.
            val call = try {
                when (val outcome = plugin.mocks.resolve(request.method.value, request.url.buildString())) {
                    null -> proceed(request)
                    is MockOutcome.Proceed -> {
                        delay(outcome.delayMillis)
                        proceed(request)
                    }
                    is MockOutcome.Fail -> {
                        delay(outcome.delayMillis)
                        throw ScryMockedFailure(outcome.message)
                    }
                    is MockOutcome.Respond -> {
                        delay(outcome.action.delayMillis)
                        mockedCall(client, request, outcome.action)
                    }
                }
            } catch (cause: Throwable) {
                plugin.record(
                    NetworkTransaction(
                        id = id,
                        startedAtMillis = startedAt,
                        method = request.method.value,
                        url = request.url.buildString(),
                        durationMillis = nowMillis() - startedAt,
                        error = cause.message ?: cause::class.simpleName ?: "unknown error",
                    ),
                )
                throw cause
            }

            // save() buffers the response so Scry can read the body without
            // consuming the stream the app is about to read.
            val saved = if (plugin.config.captureResponseBodies) call.save() else call
            val responseBody = if (plugin.config.captureResponseBodies) {
                runCatching { saved.response.bodyAsText() }.getOrNull()
                    ?.truncateForCapture(plugin.config.maxBodyBytes)
            } else {
                null
            }

            plugin.record(
                NetworkTransaction(
                    id = id,
                    startedAtMillis = startedAt,
                    method = saved.request.method.value,
                    url = saved.request.url.toString(),
                    durationMillis = nowMillis() - startedAt,
                    protocol = saved.response.version.toString(),
                    requestHeaders = request.headers.entries()
                        .flatMap { entry -> entry.value.map { HttpHeader(entry.key, it) } },
                    requestBody = requestBody?.text,
                    requestBodyTruncated = requestBody?.truncated ?: false,
                    requestBodySize = requestBody?.originalSize,
                    responseCode = saved.response.status.value,
                    responseMessage = saved.response.status.description,
                    responseHeaders = saved.response.headers.entries()
                        .flatMap { entry -> entry.value.map { HttpHeader(entry.key, it) } },
                    responseBody = responseBody?.text,
                    responseBodyTruncated = responseBody?.truncated ?: false,
                    responseBodySize = responseBody?.originalSize,
                ),
            )

            saved
        }
    }

/**
 * Extracts a readable body from outgoing content.
 *
 * Only the in-memory content types are handled. Streaming and multipart bodies
 * are skipped deliberately — reading them here would consume the stream the
 * client is about to send, which would break the app to show a debug screen.
 */
internal fun OutgoingContent?.readTextOrNull(maxBytes: Int): CapturedBody? = when (this) {
    is TextContent -> text.truncateForCapture(maxBytes)
    is ByteArrayContent -> bytes().decodeToString().truncateForCapture(maxBytes)
    else -> null
}

/** Thrown when a Scry rule injects a transport failure. */
public class ScryMockedFailure(message: String) : RuntimeException(message)

/**
 * Builds a Ktor call that never touched the network.
 *
 * Ktor has no "return this response" hook, so the call is assembled by hand from
 * [HttpResponseData]. That constructor is `@InternalAPI`, which is the one place
 * Scry depends on Ktor internals — if a Ktor upgrade breaks mocking, this is the
 * line to look at. Capture itself uses only public API and is unaffected.
 *
 * Tagged with `Scry-Mocked` for the same reason as the OkHttp path: a faked
 * response must be identifiable as one.
 */
@OptIn(io.ktor.utils.io.InternalAPI::class)
private fun mockedCall(
    client: HttpClient,
    request: HttpRequestBuilder,
    action: MockAction.Respond,
): HttpClientCall {
    val bodyBytes = action.body.encodeToByteArray()

    val headers = buildList {
        add("Content-Type" to action.contentType)
        add("Content-Length" to bodyBytes.size.toString())
        add("Scry-Mocked" to "true")
        action.headers.forEach { add(it.name to it.value) }
    }

    val responseData = HttpResponseData(
        statusCode = HttpStatusCode.fromValue(action.statusCode),
        requestTime = GMTDate(),
        headers = io.ktor.http.headers {
            headers.forEach { (name, value) -> append(name, value) }
        },
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(bodyBytes),
        callContext = request.executionContext,
    )
    return HttpClientCall(client, request.build(), responseData)
}
