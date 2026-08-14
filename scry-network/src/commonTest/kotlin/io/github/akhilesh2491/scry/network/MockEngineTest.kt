package io.github.akhilesh2491.scry.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockEngineTest {

    private fun engine() = MockEngine()

    private fun rule(
        id: String = "1",
        pattern: String = "*://api.example.com/orders*",
        method: String? = null,
        enabled: Boolean = true,
        action: MockAction = MockAction.Respond(statusCode = 503, body = "{}"),
    ) = MockRule(id = id, urlPattern = pattern, method = method, enabled = enabled, action = action)

    // --- glob matching ------------------------------------------------------

    @Test
    fun `star matches any run of characters`() {
        val r = rule()
        assertTrue(r.matches("GET", "https://api.example.com/orders"))
        assertTrue(r.matches("GET", "https://api.example.com/orders/42?full=true"))
        assertFalse(r.matches("GET", "https://api.example.com/users"))
    }

    @Test
    fun `question mark matches exactly one character`() {
        val r = rule(pattern = "https://x.test/v?/items")
        assertTrue(r.matches("GET", "https://x.test/v1/items"))
        assertFalse(r.matches("GET", "https://x.test/v10/items"))
    }

    @Test
    fun `url metacharacters are escaped rather than treated as regex`() {
        // Every real URL contains dots, and many contain + or (. If the glob were
        // compiled naively, "api.example.com" would also match "apiXexample.com".
        val r = rule(pattern = "https://api.example.com/*")
        assertFalse(r.matches("GET", "https://apiXexample.com/orders"))
        assertTrue(r.matches("GET", "https://api.example.com/orders"))

        val plus = rule(pattern = "https://x.test/a+b")
        assertTrue(plus.matches("GET", "https://x.test/a+b"))
        assertFalse(plus.matches("GET", "https://x.test/aaab"))
    }

    @Test
    fun `matching is case-insensitive on the url and the method`() {
        val r = rule(pattern = "*API.EXAMPLE.com/orders*", method = "post")
        assertTrue(r.matches("POST", "https://api.example.com/orders"))
    }

    @Test
    fun `a null method matches any verb`() {
        val r = rule(method = null)
        assertTrue(r.matches("GET", "https://api.example.com/orders"))
        assertTrue(r.matches("DELETE", "https://api.example.com/orders"))
    }

    @Test
    fun `a disabled rule never matches`() {
        assertFalse(rule(enabled = false).matches("GET", "https://api.example.com/orders"))
    }

    // --- resolution ---------------------------------------------------------

    @Test
    fun `no configuration means no outcome`() {
        assertNull(engine().resolve("GET", "https://api.example.com/orders"))
    }

    @Test
    fun `a matching rule returns its response`() {
        val engine = engine().apply { addRule(rule()) }
        val outcome = engine.resolve("GET", "https://api.example.com/orders")

        val respond = assertIs<MockOutcome.Respond>(outcome)
        assertEquals(503, respond.action.statusCode)
    }

    @Test
    fun `offline wins over every rule`() {
        // Offline is the coarse "what does my app do with no network" switch; a
        // rule that still returned 200 during it would defeat the purpose.
        val engine = engine().apply {
            addRule(rule(action = MockAction.Respond(statusCode = 200)))
            setOffline(true)
        }
        assertIs<MockOutcome.Fail>(engine.resolve("GET", "https://api.example.com/orders"))
    }

    @Test
    fun `throttling alone delays without replacing the response`() {
        val engine = engine().apply { setThrottle(ThrottleProfile.SLOW_3G) }
        val outcome = engine.resolve("GET", "https://anything.test/x")

        val proceed = assertIs<MockOutcome.Proceed>(outcome)
        assertEquals(ThrottleProfile.SLOW_3G.delayMillis, proceed.delayMillis)
    }

    @Test
    fun `rule delay and throttle delay add up`() {
        val engine = engine().apply {
            setThrottle(ThrottleProfile.FAST_4G)
            addRule(rule(action = MockAction.Respond(statusCode = 200, delayMillis = 500)))
        }
        val respond = assertIs<MockOutcome.Respond>(
            engine.resolve("GET", "https://api.example.com/orders"),
        )
        assertEquals(500 + ThrottleProfile.FAST_4G.delayMillis, respond.action.delayMillis)
    }

    @Test
    fun `a delay action passes the request through`() {
        val engine = engine().apply {
            addRule(rule(action = MockAction.Delay(delayMillis = 900)))
        }
        val proceed = assertIs<MockOutcome.Proceed>(
            engine.resolve("GET", "https://api.example.com/orders"),
        )
        assertEquals(900, proceed.delayMillis)
    }

    @Test
    fun `the first matching rule wins`() {
        val engine = engine().apply {
            addRule(rule(id = "a", action = MockAction.Respond(statusCode = 418)))
            addRule(rule(id = "b", action = MockAction.Respond(statusCode = 500)))
        }
        val respond = assertIs<MockOutcome.Respond>(
            engine.resolve("GET", "https://api.example.com/orders"),
        )
        assertEquals(418, respond.action.statusCode)
    }

    @Test
    fun `isActive reflects whether anything would intercept`() {
        val engine = engine()
        assertFalse(engine.isActive)

        engine.addRule(rule(enabled = false))
        assertFalse(engine.isActive, "a disabled rule is not active")

        engine.setRuleEnabled("1", true)
        assertTrue(engine.isActive)
    }

    @Test
    fun `adding a rule with an existing id replaces it`() {
        val engine = engine().apply {
            addRule(rule(id = "same", action = MockAction.Respond(statusCode = 200)))
            addRule(rule(id = "same", action = MockAction.Respond(statusCode = 404)))
        }
        assertEquals(1, engine.configuration.value.rules.size)
        assertEquals(
            404,
            assertIs<MockAction.Respond>(engine.configuration.value.rules.single().action).statusCode,
        )
    }

    // --- sharing ------------------------------------------------------------

    @Test
    fun `configuration round-trips through json`() {
        // The whole point: configure a scenario on a device, commit the JSON,
        // and have a colleague reproduce exactly the same failure.
        val original = engine().apply {
            setThrottle(ThrottleProfile.EDGE)
            addRule(rule(id = "r1", action = MockAction.Respond(statusCode = 503, body = "down")))
            addRule(rule(id = "r2", pattern = "*/health", action = MockAction.Fail("nope")))
        }
        val restored = engine()

        assertTrue(restored.importJson(original.exportJson()))

        assertEquals(ThrottleProfile.EDGE, restored.configuration.value.throttle)
        assertEquals(listOf("r1", "r2"), restored.configuration.value.rules.map { it.id })
        assertIs<MockAction.Fail>(restored.configuration.value.rules[1].action)
    }

    @Test
    fun `malformed json is rejected without throwing`() {
        val engine = engine()
        assertFalse(engine.importJson("{ not json"))
        assertFalse(engine.importJson(""))
        assertTrue(engine.configuration.value.rules.isEmpty(), "state must be left untouched")
    }

    @Test
    fun `display name falls back to the pattern`() {
        assertEquals("*://api.example.com/orders*", rule().displayName)
        assertEquals("Orders down", rule().copy(name = "Orders down").displayName)
    }
}
