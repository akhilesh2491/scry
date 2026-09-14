package io.github.akhilesh2491.scry.analytics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsSpecTest {

    @AfterTest
    fun tearDown() {
        ScryAnalytics.detach()
    }

    /** The spec used by most of these: one screen, one event, three params. */
    private fun checkoutPlugin(configure: AnalyticsPluginBuilder.() -> Unit = {}) =
        AnalyticsPlugin {
            settleMillis = 0
            expect("Checkout") {
                event("begin_checkout") {
                    param("cart_value", AnalyticsParamType.NUMBER)
                    param("currency", AnalyticsParamType.STRING, oneOf = setOf("USD", "EUR"))
                    param("coupon", required = false)
                }
                forbid("debug_ping")
            }
            configure()
        }

    @Test
    fun `a correct event passes`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("begin_checkout", mapOf("cart_value" to 49.9, "currency" to "USD"))
        plugin.settleCurrentVisit()

        assertTrue(plugin.issues.value.isEmpty(), plugin.issues.value.toString())
        assertEquals(AnalyticsVerdict.PASS, plugin.visits.value.first().verdict)
    }

    @Test
    fun `a required event that never fires is missing`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.settleCurrentVisit()

        assertEquals(AnalyticsIssueKind.MISSING_EVENT, plugin.issues.value.single().kind)
        assertEquals(AnalyticsVerdict.FAIL, plugin.visits.value.first().verdict)
    }

    @Test
    fun `a required param that is absent is reported`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("begin_checkout", mapOf("cart_value" to 49.9))

        val issue = plugin.issues.value.single()
        assertEquals(AnalyticsIssueKind.MISSING_PARAM, issue.kind)
        assertEquals("currency", issue.paramName)
    }

    @Test
    fun `an explicit null counts as absent rather than as a type error`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("begin_checkout", mapOf("cart_value" to 49.9, "currency" to null))

        assertEquals(AnalyticsIssueKind.MISSING_PARAM, plugin.issues.value.single().kind)
    }

    @Test
    fun `an optional param may be absent`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("begin_checkout", mapOf("cart_value" to 49.9, "currency" to "EUR"))

        assertTrue(plugin.issues.value.isEmpty())
    }

    @Test
    fun `a number sent as a string is a type error`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("begin_checkout", mapOf("cart_value" to "49.9", "currency" to "USD"))

        val issue = plugin.issues.value.single()
        assertEquals(AnalyticsIssueKind.WRONG_TYPE, issue.kind)
        assertEquals("cart_value", issue.paramName)
        assertEquals("NUMBER", issue.expected)
    }

    @Test
    fun `a value outside oneOf is reported`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("begin_checkout", mapOf("cart_value" to 49.9, "currency" to "GBP"))

        val issue = plugin.issues.value.single()
        assertEquals(AnalyticsIssueKind.DISALLOWED_VALUE, issue.kind)
        assertEquals("GBP", issue.actual)
    }

    @Test
    fun `a forbidden event is reported`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("debug_ping")

        assertEquals(AnalyticsIssueKind.FORBIDDEN_EVENT, plugin.issues.value.first().kind)
    }

    @Test
    fun `a repeated atMostOnce event is reported`() {
        val plugin = AnalyticsPlugin {
            settleMillis = 0
            expect("Cart") { event("screen_view") { atMostOnce = true } }
        }
        plugin.screenEntered("Cart")

        plugin.track("screen_view")
        plugin.track("screen_view")
        plugin.settleCurrentVisit()

        val issue = plugin.issues.value.single()
        assertEquals(AnalyticsIssueKind.DUPLICATE_EVENT, issue.kind)
        assertEquals("2", issue.actual)
    }

    @Test
    fun `undeclared events are ignored unless flagged`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.track("scrolled")

        assertTrue(plugin.issues.value.isEmpty())
    }

    @Test
    fun `undeclared events are reported when flagging is on`() {
        val plugin = checkoutPlugin { flagUnexpectedEvents = true }
        plugin.screenEntered("Checkout")

        plugin.track("scrolled")

        assertEquals(AnalyticsIssueKind.UNEXPECTED_EVENT, plugin.issues.value.single().kind)
    }

    @Test
    fun `a screen with no expectation is never judged`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Settings")

        plugin.track("anything", mapOf("shape" to "unexpected"))
        plugin.settleCurrentVisit()

        assertTrue(plugin.issues.value.isEmpty())
        assertEquals(AnalyticsVerdict.NO_SPEC, plugin.visits.value.first().verdict)
    }

    @Test
    fun `leaving a screen judges the visit being left`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.screenEntered("Confirmation")

        val checkout = plugin.visits.value.first { it.screen == "Checkout" }
        assertEquals(AnalyticsVerdict.FAIL, checkout.verdict)
        assertEquals(AnalyticsIssueKind.MISSING_EVENT, checkout.issues.single().kind)
    }

    @Test
    fun `settling twice does not duplicate issues`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")

        plugin.settleCurrentVisit()
        plugin.settleCurrentVisit()
        plugin.screenEntered("Confirmation")

        assertEquals(1, plugin.issues.value.size)
    }

    @Test
    fun `a bad event arriving after the verdict flips it to fail`() {
        val plugin = checkoutPlugin()
        plugin.screenEntered("Checkout")
        plugin.track("begin_checkout", mapOf("cart_value" to 49.9, "currency" to "USD"))
        plugin.settleCurrentVisit()
        assertEquals(AnalyticsVerdict.PASS, plugin.visits.value.first().verdict)

        plugin.track("begin_checkout", mapOf("currency" to "USD"))

        assertEquals(AnalyticsVerdict.FAIL, plugin.visits.value.first().verdict)
    }
}
