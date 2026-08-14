package io.github.akhilesh2491.scry.network.ktor

import io.github.akhilesh2491.scry.core.ScryTesting
import io.github.akhilesh2491.scry.network.MockAction
import io.github.akhilesh2491.scry.network.MockRule
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine as KtorMockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Mocking through the Ktor adapter.
 *
 * Worth its own suite because this path synthesises an `HttpClientCall` by hand
 * — Ktor has no "return this response" hook — so nothing about it is shared with
 * the OkHttp implementation.
 */
class KtorMockingTest {

    private fun plugin(): NetworkPlugin =
        NetworkPlugin().also { it.onInstall(ScryTesting.scope()) }

    private fun client(plugin: NetworkPlugin, engineCalls: MutableList<String>): HttpClient =
        HttpClient(
            KtorMockEngine { request ->
                engineCalls += request.url.toString()
                respond("real", HttpStatusCode.OK)
            },
        ) { install(ScryKtor) { networkPlugin = plugin } }

    @Test
    fun `a matching rule short-circuits the engine`() = runTest {
        val plugin = plugin()
        val calls = mutableListOf<String>()
        plugin.mocks.addRule(
            MockRule(
                id = "1",
                urlPattern = "*/orders",
                action = MockAction.Respond(statusCode = 503, body = """{"down":true}"""),
            ),
        )

        val response = client(plugin, calls).get("https://api.test/orders")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("""{"down":true}""", response.bodyAsText())
        assertEquals("true", response.headers["Scry-Mocked"])
        assertTrue(calls.isEmpty(), "the engine must not have been reached")
    }

    @Test
    fun `a non-matching request reaches the engine`() = runTest {
        val plugin = plugin()
        val calls = mutableListOf<String>()
        plugin.mocks.addRule(
            MockRule(id = "1", urlPattern = "*/orders", action = MockAction.Respond(statusCode = 503)),
        )

        assertEquals("real", client(plugin, calls).get("https://api.test/users").bodyAsText())
        assertEquals(1, calls.size)
    }

    @Test
    fun `offline mode fails the request`() = runTest {
        val plugin = plugin()
        val calls = mutableListOf<String>()
        plugin.mocks.setOffline(true)

        assertFailsWith<ScryMockedFailure> { client(plugin, calls).get("https://api.test/x") }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `an injected failure is recorded and rethrown`() = runTest {
        val plugin = plugin()
        plugin.mocks.addRule(
            MockRule(id = "1", urlPattern = "*", action = MockAction.Fail("simulated outage")),
        )

        val failure = assertFailsWith<ScryMockedFailure> {
            client(plugin, mutableListOf()).get("https://api.test/x")
        }
        assertTrue(failure.message!!.contains("simulated outage"))
        assertTrue(plugin.transactions.value.single().error!!.contains("simulated outage"))
    }

    @Test
    fun `a mocked response is captured like a real one`() = runTest {
        val plugin = plugin()
        plugin.mocks.addRule(
            MockRule(id = "1", urlPattern = "*", action = MockAction.Respond(statusCode = 418, body = "tea")),
        )

        client(plugin, mutableListOf()).get("https://api.test/x").bodyAsText()

        val captured = plugin.transactions.value.single()
        assertEquals(418, captured.responseCode)
        assertEquals("tea", captured.responseBody)
    }

    @Test
    fun `custom headers on a mocked response are delivered`() = runTest {
        val plugin = plugin()
        plugin.mocks.addRule(
            MockRule(
                id = "1",
                urlPattern = "*",
                action = MockAction.Respond(
                    statusCode = 200,
                    headers = listOf(io.github.akhilesh2491.scry.network.HttpHeader("X-Trace", "abc")),
                ),
            ),
        )

        val response = client(plugin, mutableListOf()).get("https://api.test/x")
        assertEquals("abc", response.headers["X-Trace"])
    }
}
