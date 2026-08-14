package io.github.akhilesh2491.scry.network.ktor

import io.github.akhilesh2491.scry.core.REDACTED
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.ScryTesting
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.network.NetworkPluginBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScryKtorTest {

    /** A plugin wired to an in-memory store, without installing Scry. */
    private fun networkPlugin(
        configure: NetworkPluginBuilder.() -> Unit = {},
    ): NetworkPlugin = NetworkPlugin(configure).also { it.onInstall(ScryTesting.scope()) }

    private fun client(plugin: NetworkPlugin, engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ScryKtor) { networkPlugin = plugin }
        }

    @Test
    fun `captures a successful request and response`() = runTest {
        val plugin = networkPlugin()
        val engine = MockEngine {
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val body = client(plugin, engine).get("https://api.example.com/things?page=2").bodyAsText()
        assertEquals("""{"ok":true}""", body, "the app must still receive its body")

        val captured = plugin.transactions.value.single()
        assertEquals("GET", captured.method)
        assertEquals(200, captured.responseCode)
        assertEquals("api.example.com", captured.host)
        assertEquals("""{"ok":true}""", captured.responseBody)
        assertNotNull(captured.durationMillis)
        assertTrue(captured.isComplete)
        assertTrue(captured.isSecure)
    }

    @Test
    fun `captures request bodies`() = runTest {
        val plugin = networkPlugin()
        val engine = MockEngine { respond("{}", HttpStatusCode.Created) }

        client(plugin, engine).post("https://api.example.com/things") {
            setBody("""{"name":"widget"}""")
        }

        val captured = plugin.transactions.value.single()
        assertEquals("""{"name":"widget"}""", captured.requestBody)
        assertEquals(201, captured.responseCode)
    }

    @Test
    fun `redacts sensitive headers before storing`() = runTest {
        val plugin = networkPlugin()
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }

        client(plugin, engine).get("https://api.example.com/me") {
            header("Authorization", "Bearer super-secret-token")
        }

        val captured = plugin.transactions.value.single()
        val auth = captured.requestHeaders.first { it.name.equals("Authorization", true) }
        assertEquals(REDACTED, auth.value)
        assertFalse(
            captured.requestHeaders.any { it.value.contains("super-secret-token") },
            "the raw token must never reach storage",
        )
    }

    @Test
    fun `redacts credential keys in request bodies`() = runTest {
        val plugin = networkPlugin()
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }

        client(plugin, engine).post("https://api.example.com/login") {
            setBody("""{"user":"ada","password":"hunter2"}""")
        }

        val captured = plugin.transactions.value.single()
        assertFalse(captured.requestBody!!.contains("hunter2"))
        assertTrue(captured.requestBody!!.contains("ada"))
    }

    @Test
    fun `truncates bodies over the cap instead of storing them whole`() = runTest {
        val plugin = networkPlugin { maxBodyBytes = 64 }
        val huge = "x".repeat(5_000)
        val engine = MockEngine { respond(huge, HttpStatusCode.OK) }

        client(plugin, engine).get("https://api.example.com/big")

        val captured = plugin.transactions.value.single()
        assertTrue(captured.responseBodyTruncated)
        assertEquals(64, captured.responseBody!!.length)
        assertEquals(5_000L, captured.responseBodySize, "original size is still reported")
    }

    @Test
    fun `records failures with the error message`() = runTest {
        val plugin = networkPlugin()
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }

        client(plugin, engine).get("https://api.example.com/boom")

        val captured = plugin.transactions.value.single()
        assertEquals(500, captured.responseCode)
        assertEquals("500", captured.statusLabel)
    }

    @Test
    fun `an in-flight request is visible before its response arrives`() = runTest {
        // Two records with the same id: the in-flight one, then the completed
        // one replacing it. A hung request must not be invisible.
        val plugin = networkPlugin()
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }

        client(plugin, engine).get("https://api.example.com/thing")

        assertEquals(1, plugin.transactions.value.size, "the update must replace, not duplicate")
        assertTrue(plugin.transactions.value.single().isComplete)
    }

    @Test
    fun `capture is a no-op when no plugin is resolvable`() = runTest {
        // A client built with ScryKtor but no Scry installed must behave exactly
        // like a client without it — this is what makes the plugin safe to leave
        // in place across build types.
        Scry.uninstall()
        val engine = MockEngine { respond("""{"ok":true}""", HttpStatusCode.OK) }
        val bare = HttpClient(engine) { install(ScryKtor) }

        assertEquals("""{"ok":true}""", bare.get("https://api.example.com/x").bodyAsText())
    }
}
