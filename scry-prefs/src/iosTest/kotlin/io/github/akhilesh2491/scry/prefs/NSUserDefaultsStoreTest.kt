package io.github.akhilesh2491.scry.prefs

import io.github.akhilesh2491.scry.core.PlatformContext
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NSUserDefaultsStoreTest {

    private val suite = NSUserDefaults(suiteName = "scry-test-suite")
    private val store = suite.asScryStore("scry-test-suite")

    @AfterTest
    fun tearDown() {
        store.clear()
    }

    @Test
    fun `round-trips every supported type`() {
        store.write("s", PreferenceValue.StringValue("ada"))
        store.write("i", PreferenceValue.IntValue(7))
        store.write("l", PreferenceValue.LongValue(9_000_000_000))
        store.write("d", PreferenceValue.DoubleValue(1.25))
        store.write("b", PreferenceValue.BooleanValue(true))

        assertEquals("ada", (store.read("s") as PreferenceValue.StringValue).value)
        assertEquals(9_000_000_000, (store.read("l") as PreferenceValue.LongValue).value)
        assertEquals(1.25, (store.read("d") as PreferenceValue.DoubleValue).value)
    }

    @Test
    fun `booleans survive as booleans — not as numbers`() {
        // NSUserDefaults bridges a stored boolean to something that is also a
        // valid Long. Reading it as a number would turn every feature flag into
        // 0/1 and lose the switch in the editor, so the type check order matters.
        store.write("flag", PreferenceValue.BooleanValue(true))

        val read = store.read("flag")
        assertTrue(read is PreferenceValue.BooleanValue, "got ${read?.typeLabel}")
        assertEquals(true, read.value)
    }

    @Test
    fun `keys lists what was written and remove deletes it`() {
        store.write("alpha", PreferenceValue.StringValue("1"))
        store.write("beta", PreferenceValue.StringValue("2"))
        assertTrue(store.keys().containsAll(listOf("alpha", "beta")))

        store.remove("alpha")
        assertNull(store.read("alpha"))
        assertEquals("2", (store.read("beta") as PreferenceValue.StringValue).value)
    }

    @Test
    fun `discovery exposes the standard suite`() {
        val stores = discoverStores(PlatformContext(applicationId = "scry-ios-test"))
        assertTrue(stores.isNotEmpty(), "the standard NSUserDefaults suite must be discoverable")
    }
}
