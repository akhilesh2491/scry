package io.github.akhilesh2491.scry.network.okhttp

import io.github.akhilesh2491.scry.core.ScryTesting
import io.github.akhilesh2491.scry.network.MockAction
import io.github.akhilesh2491.scry.network.MockRule
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.network.ThrottleProfile
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Mocking through the OkHttp adapter, end to end. */
class MockingTest {

    private lateinit var server: MockWebServer
    private lateinit var plugin: NetworkPlugin

    @BeforeTest
    fun setUp() {
        server = MockWebServer().also { it.start() }
        plugin = NetworkPlugin().also { it.onInstall(ScryTesting.scope()) }
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun client() =
        OkHttpClient.Builder().addInterceptor(ScryInterceptor(plugin)).build()

    private fun get(path: String = "/orders") =
        client().newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `a matching rule short-circuits the network`() {
        // No response is enqueued: if the request reached the server the test
        // would hang or fail, which is exactly the proof wanted.
        plugin.mocks.addRule(
            MockRule(
                id = "1",
                urlPattern = "*/orders",
                action = MockAction.Respond(statusCode = 503, body = """{"down":true}"""),
            ),
        )

        get().use { response ->
            assertEquals(503, response.code)
            assertEquals("""{"down":true}""", response.body?.string())
            assertEquals("true", response.header("Scry-Mocked"), "mocked responses must be labelled")
        }
        assertEquals(0, server.requestCount, "the network must not have been touched")
    }

    @Test
    fun `a non-matching request still reaches the server`() {
        plugin.mocks.addRule(
            MockRule(id = "1", urlPattern = "*/orders", action = MockAction.Respond(statusCode = 503)),
        )
        server.enqueue(MockResponse.Builder().code(200).body("real").build())

        get("/users").use { assertEquals("real", it.body?.string()) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `offline mode fails every request`() {
        plugin.mocks.setOffline(true)

        assertFailsWith<Exception> { get() }
        assertEquals(0, server.requestCount)
        assertTrue(plugin.transactions.value.single().error!!.contains("Offline"))
    }

    @Test
    fun `an injected failure is recorded and rethrown`() {
        plugin.mocks.addRule(
            MockRule(id = "1", urlPattern = "*", action = MockAction.Fail("simulated outage")),
        )

        val failure = assertFailsWith<Exception> { get() }
        assertTrue(failure.message!!.contains("simulated outage"))
        assertTrue(plugin.transactions.value.single().error!!.contains("simulated outage"))
    }

    @Test
    fun `throttling delays without changing the response`() {
        plugin.mocks.setThrottle(ThrottleProfile.SLOW_3G)
        server.enqueue(MockResponse.Builder().code(200).body("slow").build())

        val started = System.currentTimeMillis()
        get().use { assertEquals("slow", it.body?.string()) }
        val elapsed = System.currentTimeMillis() - started

        assertTrue(
            elapsed >= ThrottleProfile.SLOW_3G.delayMillis,
            "expected at least ${ThrottleProfile.SLOW_3G.delayMillis}ms, took ${elapsed}ms",
        )
        assertEquals(1, server.requestCount, "throttling must not replace the response")
    }

    @Test
    fun `disabling a rule restores real traffic`() {
        plugin.mocks.addRule(
            MockRule(id = "1", urlPattern = "*", action = MockAction.Respond(statusCode = 503)),
        )
        plugin.mocks.setRuleEnabled("1", false)
        server.enqueue(MockResponse.Builder().code(200).body("real").build())

        get().use { assertEquals(200, it.code) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `mocked responses are captured like real ones`() {
        // A mocked call still belongs in the transaction list — otherwise you
        // cannot tell whether your rule fired.
        plugin.mocks.addRule(
            MockRule(id = "1", urlPattern = "*", action = MockAction.Respond(statusCode = 418)),
        )
        get().use { it.body?.string() }

        val captured = plugin.transactions.value.single()
        assertEquals(418, captured.responseCode)
        assertTrue(captured.responseHeaders.any { it.name == "Scry-Mocked" })
    }
}
