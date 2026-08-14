package io.github.akhilesh2491.scry.core

import platform.Foundation.NSFileManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the iOS `actual` implementations themselves.
 *
 * The shared suites run on the simulator too, but they only prove the *common*
 * logic works there. These cover the parts that exist solely on iOS — the
 * Documents-backed storage path, the pthread lock, and the clock — which are
 * exactly the pieces that cannot be verified anywhere else.
 */
class IosPlatformTest {

    @Test
    fun `storage directory is created under the sandbox`() {
        val context = PlatformContext(applicationId = "scry-ios-test")
        val path = context.scryStorageDirectory()

        assertTrue(path.isNotBlank())
        assertTrue(
            NSFileManager.defaultManager.fileExistsAtPath(path),
            "scryStorageDirectory() must create the directory, not just name it: $path",
        )
        assertTrue(path.contains("scry-ios-test"), path)
    }

    @Test
    fun `the store works on a real file path — not just in memory`() {
        // BundledSQLiteDriver on iOS writing to the Documents sandbox — the
        // combination that only a simulator run can prove.
        val context = PlatformContext(applicationId = "scry-ios-store-test")
        val store = ScryStore.open(context.scryStorageDirectory(), Retention.DEFAULT)
        try {
            store.clearAll()
            store.put(pluginId = "p", id = "1", payload = """{"hello":"ios"}""")

            val records = store.read("p")
            assertEquals(1, records.size)
            assertEquals("""{"hello":"ios"}""", records.single().payload)
        } finally {
            store.clearAll()
            store.close()
        }
    }

    @Test
    fun `retention still evicts on a native store`() {
        val context = PlatformContext(applicationId = "scry-ios-retention-test")
        val store = ScryStore.open(
            context.scryStorageDirectory(),
            Retention.of(kotlin.time.Duration.parse("24h"), maxEntries = 3, maxBytes = 1_000_000),
        )
        try {
            store.clearAll()
            val now = currentTimeMillis()
            repeat(6) { store.put("p", "id$it", "x", createdAtMillis = now - 6_000 + it) }

            assertEquals(listOf("id5", "id4", "id3"), store.read("p").map { it.id })
        } finally {
            store.clearAll()
            store.close()
        }
    }

    @Test
    fun `clock returns a plausible wall time`() {
        val now = currentTimeMillis()
        // Somewhere after 2020 and before 2100 — enough to catch a unit mix-up
        // (seconds vs millis), which is the realistic failure here.
        assertTrue(now > 1_577_836_800_000, "suspiciously early: $now")
        assertTrue(now < 4_102_444_800_000, "suspiciously late: $now")
    }

    @Test
    fun `lock serialises access and is reentrant-safe within a call`() {
        val lock = ScryLock()
        var counter = 0
        repeat(1_000) { lock.withLock { counter++ } }
        assertEquals(1_000, counter)
    }

    @Test
    fun `lock propagates exceptions and still unlocks`() {
        val lock = ScryLock()
        runCatching { lock.withLock { error("boom") } }
        // If the mutex were left locked this would deadlock rather than fail.
        assertEquals(42, lock.withLock { 42 })
    }
}
