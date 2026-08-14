package io.github.akhilesh2491.scry.network.okhttp

import io.github.akhilesh2491.scry.core.REDACTED
import io.github.akhilesh2491.scry.core.ScryTesting
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.network.NetworkPluginBuilder
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runtime cover for the OkHttp adapter.
 *
 * The Ktor path had eight tests and this one had none, which meant "both
 * adapters produce the same model" was an architectural claim with no
 * regression net under half of it.
 */
class ScryInterceptorTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun plugin(
        configure: NetworkPluginBuilder.() -> Unit = {},
    ): NetworkPlugin = NetworkPlugin(configure).also { it.onInstall(ScryTesting.scope()) }

    private fun client(plugin: NetworkPlugin): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(ScryInterceptor(plugin)).build()

    private fun response(code: Int = 200, body: String = "{}") =
        MockResponse.Builder().code(code).body(body).build()

    @Test
    fun `captures a successful request and response`() {
        val plugin = plugin()
        server.enqueue(response(body = """{"ok":true}"""))

        val body = client(plugin)
            .newCall(Request.Builder().url(server.url("/things")).build())
            .execute()
            .use { it.body?.string() }

        assertEquals("""{"ok":true}""", body, "the caller must still get its body")

        val captured = plugin.transactions.value.single()
        assertEquals("GET", captured.method)
        assertEquals(200, captured.responseCode)
        assertEquals("/things", captured.path)
        assertEquals("""{"ok":true}""", captured.responseBody)
        assertNotNull(captured.durationMillis)
        assertTrue(captured.isComplete)
    }

    @Test
    fun `peekBody does not consume the caller's stream`() {
        // The whole reason peekBody exists. If Scry read the real body, the app
        // would receive an empty response — a debugger breaking the app it is
        // attached to is the worst failure mode this adapter has.
        val plugin = plugin()
        val payload = """{"items":[1,2,3]}"""
        server.enqueue(response(body = payload))

        val received = client(plugin)
            .newCall(Request.Builder().url(server.url("/x")).build())
            .execute()
            .use { it.body!!.string() }

        assertEquals(payload, received)
        assertEquals(payload, plugin.transactions.value.single().responseBody)
    }

    @Test
    fun `captures request bodies without emptying them`() {
        val plugin = plugin()
        server.enqueue(response(code = 201))
        val payload = """{"name":"widget"}"""

        client(plugin).newCall(
            Request.Builder()
                .url(server.url("/things"))
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build(),
        ).execute().close()

        assertEquals(payload, plugin.transactions.value.single().requestBody)
        // The server must have received it too — proving writeTo() to a Buffer
        // did not consume the one-shot body.
        assertEquals(payload, server.takeRequest().body?.utf8())
    }

    @Test
    fun `redacts sensitive headers before storing`() {
        val plugin = plugin()
        server.enqueue(response())

        client(plugin).newCall(
            Request.Builder()
                .url(server.url("/me"))
                .header("Authorization", "Bearer super-secret-token")
                .build(),
        ).execute().close()

        val captured = plugin.transactions.value.single()
        assertEquals(
            REDACTED,
            captured.requestHeaders.first { it.name.equals("Authorization", true) }.value,
        )
        assertFalse(captured.requestHeaders.any { it.value.contains("super-secret-token") })
    }

    @Test
    fun `redacts credential keys in request bodies`() {
        val plugin = plugin()
        server.enqueue(response())

        client(plugin).newCall(
            Request.Builder()
                .url(server.url("/login"))
                .post(
                    """{"user":"ada","password":"hunter2"}"""
                        .toRequestBody("application/json".toMediaType()),
                )
                .build(),
        ).execute().close()

        val captured = plugin.transactions.value.single()
        assertFalse(captured.requestBody!!.contains("hunter2"))
        assertTrue(captured.requestBody!!.contains("ada"))
    }

    @Test
    fun `truncates bodies over the cap`() {
        val plugin = plugin { maxBodyBytes = 64 }
        server.enqueue(response(body = "x".repeat(5_000)))

        client(plugin).newCall(Request.Builder().url(server.url("/big")).build())
            .execute().close()

        val captured = plugin.transactions.value.single()
        assertEquals(64, captured.responseBody!!.length)
        assertTrue(captured.responseBodyTruncated)
    }

    @Test
    fun `records error responses`() {
        val plugin = plugin()
        server.enqueue(response(code = 500, body = "boom"))

        client(plugin).newCall(Request.Builder().url(server.url("/boom")).build())
            .execute().close()

        assertEquals(500, plugin.transactions.value.single().responseCode)
    }

    @Test
    fun `records transport failures and rethrows`() {
        val plugin = plugin()
        val unreachable = server.url("/gone")
        server.close() // nothing is listening now

        assertFailsWith<Exception> {
            client(plugin).newCall(Request.Builder().url(unreachable).build()).execute()
        }

        val captured = plugin.transactions.value.single()
        assertNotNull(captured.error, "the failure must be recorded")
        assertTrue(captured.isComplete)

        server = MockWebServer().also { it.start() } // so tearDown has something to close
    }

    @Test
    fun `updates the in-flight record rather than duplicating it`() {
        val plugin = plugin()
        server.enqueue(response())

        client(plugin).newCall(Request.Builder().url(server.url("/x")).build())
            .execute().close()

        assertEquals(1, plugin.transactions.value.size)
    }

    @Test
    fun `is a pass-through when no plugin is resolvable`() {
        // Safe to leave the interceptor in place across build types.
        io.github.akhilesh2491.scry.core.Scry.uninstall()
        server.enqueue(response(body = "hello"))

        val body = OkHttpClient.Builder().addInterceptor(ScryInterceptor()).build()
            .newCall(Request.Builder().url(server.url("/x")).build())
            .execute()
            .use { it.body?.string() }

        assertEquals("hello", body)
    }

    @Test
    fun `produces the same model shape as the ktor adapter`() {
        // The engine-agnostic claim, pinned: every field the UI and exporters
        // rely on must be populated by this adapter too.
        val plugin = plugin()
        server.enqueue(response(body = """{"ok":true}"""))

        client(plugin).newCall(Request.Builder().url(server.url("/things?page=2")).build())
            .execute().close()

        val captured = plugin.transactions.value.single()
        assertNotNull(captured.protocol)
        assertNotNull(captured.responseMessage)
        assertNotNull(captured.responseBodySize)
        assertTrue(captured.responseHeaders.isNotEmpty())
        assertTrue(captured.url.contains("page=2"))
    }
}
