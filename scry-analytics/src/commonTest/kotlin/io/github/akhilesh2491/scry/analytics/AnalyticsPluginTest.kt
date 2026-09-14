package io.github.akhilesh2491.scry.analytics

import io.github.akhilesh2491.scry.core.REDACTED
import io.github.akhilesh2491.scry.core.ScreenChangedEvent
import io.github.akhilesh2491.scry.core.ScryStore
import io.github.akhilesh2491.scry.core.ScryTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsPluginTest {

    @AfterTest
    fun tearDown() {
        ScryAnalytics.detach()
    }

    @Test
    fun `events are recorded newest first`() {
        val plugin = AnalyticsPlugin()

        plugin.track("first")
        plugin.track("second")

        assertEquals(listOf("second", "first"), plugin.events.value.map { it.name })
    }

    @Test
    fun `parameter types survive capture`() {
        val plugin = AnalyticsPlugin()

        plugin.track("purchase", mapOf("value" to 49.9, "currency" to "USD", "first" to true, "coupon" to null))

        val params = plugin.events.value.single().params
        assertEquals(AnalyticsValue.Number(49.9), params["value"])
        assertEquals(AnalyticsValue.Text("USD"), params["currency"])
        assertEquals(AnalyticsValue.Bool(true), params["first"])
        assertEquals(AnalyticsValue.Null, params["coupon"])
    }

    @Test
    fun `sensitive parameters are redacted on the way in`() {
        val plugin = AnalyticsPlugin()
        plugin.onInstall(ScryTesting.scope())

        plugin.track("login", mapOf("user_token" to "abc123", "method" to "email"))

        val params = plugin.events.value.single().params
        assertEquals(AnalyticsValue.Text(REDACTED), params["user_token"])
        assertEquals(AnalyticsValue.Text("email"), params["method"])
    }

    @Test
    fun `the event list is capped`() {
        val plugin = AnalyticsPlugin { maxEvents = 3 }

        repeat(10) { plugin.track("event_$it") }

        assertEquals(3, plugin.events.value.size)
        assertEquals("event_9", plugin.events.value.first().name)
    }

    @Test
    fun `events are attributed to the current screen`() {
        val plugin = AnalyticsPlugin()

        plugin.screenEntered("Checkout")
        plugin.track("begin_checkout")

        assertEquals("Checkout", plugin.events.value.single().screen)
        assertEquals("Checkout", plugin.visits.value.first().screen)
    }

    @Test
    fun `a screen change on the bus moves attribution`() = runTest {
        val plugin = AnalyticsPlugin { settleMillis = 0 }
        val scope = ScryTesting.scope()
        plugin.onInstall(scope)

        // Real dispatcher and real time: the subscription is set up on the
        // install scope, which is not the test scheduler, and the bus drops
        // events published before a subscriber exists — so the publish is
        // retried until it lands rather than raced once.
        withContext(Dispatchers.Default) {
            withTimeout(TIMEOUT_MILLIS) {
                while (plugin.currentScreen.value != "Cart") {
                    scope.events.publish(
                        ScreenChangedEvent(screen = "Cart", source = "scry.perf", timestampMillis = 1),
                    )
                    delay(10)
                }
            }
        }

        plugin.track("view_cart")
        assertEquals("Cart", plugin.events.value.single().screen)
    }

    @Test
    fun `re-entering a screen opens a new visit`() {
        val plugin = AnalyticsPlugin()

        plugin.screenEntered("Cart")
        plugin.screenEntered("Checkout")
        plugin.screenEntered("Cart")

        assertEquals(listOf("Cart", "Checkout", "Cart", AnalyticsPlugin.ROOT_SCREEN), plugin.visits.value.map { it.screen })
    }

    @Test
    fun `re-announcing the same screen is ignored`() {
        val plugin = AnalyticsPlugin()

        plugin.screenEntered("Cart")
        plugin.screenEntered("Cart")

        assertEquals(2, plugin.visits.value.size) // the root visit plus Cart
    }

    @Test
    fun `the badge counts issues over events`() {
        val plugin = AnalyticsPlugin {
            settleMillis = 0
            expect("Cart") { event("view_cart") }
        }

        plugin.screenEntered("Cart")
        plugin.track("session_ping")
        plugin.track("scrolled")
        assertEquals("2", plugin.badge.value)

        // One MISSING_EVENT outranks two events: a red badge should say how much
        // is wrong, not how much was captured.
        plugin.settleCurrentVisit()
        assertEquals("1", plugin.badge.value)
    }

    @Test
    fun `clearing drops everything but the current screen`() {
        val plugin = AnalyticsPlugin()
        plugin.screenEntered("Cart")
        plugin.track("view_cart")

        plugin.onClear()

        assertTrue(plugin.events.value.isEmpty())
        assertTrue(plugin.issues.value.isEmpty())
        assertNull(plugin.badge.value)
        assertEquals("Cart", plugin.visits.value.single().screen)
    }

    @Test
    fun `visits survive a restart when persistence is on`() {
        val store = ScryStore.inMemory()

        val first = AnalyticsPlugin { persist = true; settleMillis = 0 }
        first.onInstall(ScryTesting.scope(store = store))
        first.screenEntered("Cart")
        first.track("view_cart")
        ScryAnalytics.detach()

        val second = AnalyticsPlugin { persist = true; settleMillis = 0 }
        second.onInstall(ScryTesting.scope(store = store))

        assertEquals(listOf("view_cart"), second.events.value.map { it.name })
    }

    private companion object {
        /** Generous: this bounds a genuinely asynchronous handoff, not a delay. */
        const val TIMEOUT_MILLIS = 5_000L
    }
}
