package io.github.akhilesh2491.scry.analytics

import io.github.akhilesh2491.scry.core.ScryTesting
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScryAnalyticsTest {

    @AfterTest
    fun tearDown() {
        ScryAnalytics.detach()
    }

    @Test
    fun `tracking before install does nothing`() {
        ScryAnalytics.track("begin_checkout", mapOf("cart_value" to 1))
        ScryAnalytics.screenEntered("Checkout")
        ScryAnalytics.checkNow()

        assertNull(AnalyticsPlugin.installed())
    }

    @Test
    fun `the facade feeds the installed plugin`() {
        val plugin = AnalyticsPlugin { settleMillis = 0 }
        plugin.onInstall(ScryTesting.scope())

        ScryAnalytics.screenEntered("Checkout")
        ScryAnalytics.track("begin_checkout", mapOf("cart_value" to 49.9))

        assertEquals(plugin, AnalyticsPlugin.installed())
        assertEquals("Checkout", plugin.events.value.single().screen)
    }

    @Test
    fun `tracking after detach does nothing`() {
        val plugin = AnalyticsPlugin { settleMillis = 0 }
        plugin.onInstall(ScryTesting.scope())

        ScryAnalytics.detach()
        ScryAnalytics.track("begin_checkout")

        assertTrue(plugin.events.value.isEmpty())
    }
}
