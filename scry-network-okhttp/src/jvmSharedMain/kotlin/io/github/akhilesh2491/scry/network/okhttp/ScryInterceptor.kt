package io.github.akhilesh2491.scry.network.okhttp

import io.github.akhilesh2491.scry.network.HttpHeader
import io.github.akhilesh2491.scry.network.MockOutcome
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.network.NetworkTransaction
import io.github.akhilesh2491.scry.network.newTransactionId
import io.github.akhilesh2491.scry.network.nowMillis
import io.github.akhilesh2491.scry.network.truncateForCapture
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/**
 * OkHttp interceptor that reports traffic to Scry.
 *
 * ```kotlin
 * OkHttpClient.Builder().addInterceptor(ScryInterceptor()).build()
 * ```
 *
 * Exists alongside the Ktor plugin because the Android/Retrofit population is
 * enormous and will not migrate clients to try a debugging tool. Both adapters
 * produce the same [NetworkTransaction], so they share the UI, storage and
 * exports — which is the whole reason that type refuses to reference either
 * engine.
 */
public class ScryInterceptor @JvmOverloads constructor(
    /**
     * Where captured traffic goes. Resolved from the installed Scry instance at
     * call time when left null, so `addInterceptor(ScryInterceptor())` needs no
     * further wiring.
     */
    private val networkPlugin: NetworkPlugin? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val plugin = networkPlugin ?: NetworkPlugin.installed()
            ?: return chain.proceed(chain.request())

        val request = chain.request()
        val id = newTransactionId()
        val startedAt = nowMillis()
        val maxBytes = plugin.config.maxBodyBytes

        val requestBody = if (plugin.config.captureRequestBodies) {
            request.readBodyOrNull(maxBytes)
        } else {
            null
        }

        val base = NetworkTransaction(
            id = id,
            startedAtMillis = startedAt,
            method = request.method,
            url = request.url.toString(),
            requestHeaders = request.headers.toScryHeaders(),
            requestBody = requestBody?.text,
            requestBodyTruncated = requestBody?.truncated ?: false,
            requestBodySize = requestBody?.originalSize,
        )
        // Publish the in-flight call before proceeding, so a request that hangs
        // is still visible in the UI rather than absent until it returns.
        plugin.record(base)

        // Mocking is consulted before the network is touched. resolve() returns
        // null when nothing is configured, so the ordinary path costs one check.
        val response = try {
            when (val outcome = plugin.mocks.resolve(request.method, request.url.toString())) {
                null -> chain.proceed(request)
                is MockOutcome.Proceed -> {
                    sleepQuietly(outcome.delayMillis)
                    chain.proceed(request)
                }
                is MockOutcome.Fail -> {
                    sleepQuietly(outcome.delayMillis)
                    throw IOException(outcome.message)
                }
                is MockOutcome.Respond -> {
                    sleepQuietly(outcome.action.delayMillis)
                    outcome.action.toOkHttpResponse(request)
                }
            }
        } catch (cause: Exception) {
            plugin.record(
                base.copy(
                    durationMillis = nowMillis() - startedAt,
                    error = cause.message ?: cause::class.simpleName ?: "unknown error",
                ),
            )
            throw cause
        }

        // peekBody copies up to the cap without consuming the stream the caller
        // is about to read — the one safe way to see a response body here.
        val responseBody = if (plugin.config.captureResponseBodies) {
            runCatching { response.peekBody(maxBytes.toLong()).string() }.getOrNull()
        } else {
            null
        }
        val declaredLength = response.body?.contentLength()?.takeIf { it >= 0 }

        plugin.record(
            base.copy(
                durationMillis = nowMillis() - startedAt,
                protocol = response.protocol.toString(),
                responseCode = response.code,
                responseMessage = response.message,
                responseHeaders = response.headers.toScryHeaders(),
                responseBody = responseBody,
                // peekBody stops at the cap, so a body that filled it exactly is
                // reported as truncated — over-reporting is the safe direction.
                responseBodyTruncated = responseBody != null && responseBody.length >= maxBytes,
                responseBodySize = declaredLength ?: responseBody?.length?.toLong(),
            ),
        )
        return response
    }
}

private fun okhttp3.Headers.toScryHeaders(): List<HttpHeader> =
    (0 until size).map { HttpHeader(name(it), value(it)) }

/**
 * Reads a request body without consuming it.
 *
 * One-shot and duplex bodies are skipped: reading them here would consume the
 * only copy, so the request would be sent empty. Breaking the app to populate a
 * debug screen is never an acceptable trade.
 */
private fun Request.readBodyOrNull(maxBytes: Int) = body?.let { body ->
    if (body.isOneShot() || body.isDuplex()) return@let null
    runCatching {
        Buffer().also { body.writeTo(it) }.readUtf8().truncateForCapture(maxBytes)
    }.getOrNull()
}

/**
 * Builds a canned OkHttp response.
 *
 * Marked with a `Scry-Mocked` header so a mocked response is distinguishable
 * from a real one both in the app and in Scry's own capture — silently faking
 * traffic with no trace is how people lose an afternoon.
 */
private fun io.github.akhilesh2491.scry.network.MockAction.Respond.toOkHttpResponse(
    request: Request,
): Response {
    val builder = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message(if (statusCode in 200..299) "OK" else "Mocked")
        .body(body.toResponseBody(contentType.toMediaTypeOrNull()))
        .header("Scry-Mocked", "true")
    headers.forEach { builder.header(it.name, it.value) }
    return builder.build()
}

/** Sleeps without letting an interrupt escape as an unexpected failure. */
private fun sleepQuietly(millis: Long) {
    if (millis <= 0) return
    runCatching { Thread.sleep(millis) }
}
