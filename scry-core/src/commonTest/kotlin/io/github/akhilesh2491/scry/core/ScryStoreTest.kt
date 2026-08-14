package io.github.akhilesh2491.scry.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class ScryStoreTest {

    private val stores = mutableListOf<ScryStore>()

    private fun store(retention: Retention): ScryStore =
        ScryStore.inMemory(retention).also { stores += it }

    @AfterTest
    fun tearDown() {
        stores.forEach { it.close() }
        stores.clear()
    }

    @Test
    fun `stores and reads back a record`() {
        val store = store(Retention.DEFAULT)
        store.put(pluginId = "p", id = "1", payload = "hello")

        val records = store.read("p")
        assertEquals(1, records.size)
        assertEquals("hello", records[0].payload)
        assertEquals("p", records[0].pluginId)
    }

    // Timestamps must sit inside the retention window, otherwise the write-path
    // eviction removes them immediately — which is correct behaviour, but makes
    // for a confusing test.
    private val now = currentTimeMillis()

    @Test
    fun `read returns newest first`() {
        val store = store(Retention.DEFAULT)
        store.put("p", "old", "1", createdAtMillis = now - 2_000)
        store.put("p", "new", "2", createdAtMillis = now - 1_000)

        assertEquals(listOf("new", "old"), store.read("p").map { it.id })
    }

    @Test
    fun `records are isolated per plugin`() {
        val store = store(Retention.DEFAULT)
        store.put("a", "1", "x")
        store.put("b", "2", "y")

        assertEquals(1, store.read("a").size)
        assertEquals(1, store.read("b").size)
        store.clear("a")
        assertEquals(0, store.read("a").size)
        assertEquals(1, store.read("b").size)
    }

    @Test
    fun `retention evicts oldest beyond maxEntries`() {
        val store = store(Retention.of(24.hours, maxEntries = 3, maxBytes = 1_000_000))
        repeat(6) { index ->
            store.put("p", id = "id$index", payload = "x", createdAtMillis = now - 6_000 + index)
        }

        val remaining = store.read("p").map { it.id }
        assertEquals(3, remaining.size)
        assertEquals(listOf("id5", "id4", "id3"), remaining)
    }

    @Test
    fun `retention evicts records older than maxAge`() {
        val store = store(Retention.of(50.milliseconds, maxEntries = 100, maxBytes = 1_000_000))
        // Timestamped far in the past, so it is already outside the window when
        // the next write triggers enforcement.
        store.put("p", "ancient", "x", createdAtMillis = 1L)
        store.put("p", "fresh", "y")

        assertEquals(listOf("fresh"), store.read("p").map { it.id })
    }

    @Test
    fun `retention trims until under maxBytes`() {
        val payload = "y".repeat(100)
        val store = store(Retention.of(24.hours, maxEntries = 1_000, maxBytes = 250))
        repeat(10) { index ->
            store.put("p", "id$index", payload, createdAtMillis = now - 10_000 + index)
        }

        assertTrue(
            store.totalSizeBytes() <= 250,
            "expected total <= 250 but was ${store.totalSizeBytes()}",
        )
        // 250 bytes / 100-byte payloads leaves room for two, newest kept.
        assertEquals(listOf("id9", "id8"), store.read("p").map { it.id })
    }

    @Test
    fun `put with existing id replaces rather than duplicates`() {
        val store = store(Retention.DEFAULT)
        store.put("p", "same", "first")
        store.put("p", "same", "second")

        val records = store.read("p")
        assertEquals(1, records.size)
        assertEquals("second", records[0].payload)
    }

    @Test
    fun `clearAll removes every plugin's records`() {
        val store = store(Retention.DEFAULT)
        store.put("a", "1", "x")
        store.put("b", "2", "y")

        store.clearAll()

        assertEquals(0, store.read("a").size)
        assertEquals(0, store.read("b").size)
        assertEquals(0L, store.totalSizeBytes())
    }

    @Test
    fun `operations after close are inert rather than throwing`() {
        val store = store(Retention.DEFAULT)
        store.put("p", "1", "x")
        store.close()

        // Scry must never crash the host app, including on a shutdown race.
        store.put("p", "2", "y")
        assertEquals(emptyList(), store.read("p"))
        assertEquals(0, store.count("p"))
    }
}
