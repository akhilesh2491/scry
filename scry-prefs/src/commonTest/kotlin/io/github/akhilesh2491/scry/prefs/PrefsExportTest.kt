package io.github.akhilesh2491.scry.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefsExportTest {

    private class FakeStore(
        override val name: String,
        override val isMutable: Boolean = true,
        private val values: LinkedHashMap<String, PreferenceValue>,
    ) : KeyValueStore {
        override fun keys(): List<String> = values.keys.toList()
        override fun read(key: String): PreferenceValue? = values[key]
        override fun write(key: String, value: PreferenceValue) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() = values.clear()
    }

    private fun store(vararg entries: Pair<String, PreferenceValue>) =
        FakeStore("prefs", values = linkedMapOf(*entries))

    @Test
    fun `writes each value as a JSON literal of its own type`() {
        // The point of exporting types: `"enabled": "true"` and `"enabled": true`
        // are different bugs, and a flat text dump cannot tell them apart.
        val json = store(
            "name" to PreferenceValue.StringValue("ada"),
            "count" to PreferenceValue.IntValue(3),
            "enabled" to PreferenceValue.BooleanValue(true),
            "tags" to PreferenceValue.StringSetValue(linkedSetOf("a", "b")),
        ).jsonExport()

        assertTrue(json.contains("\"value\": \"ada\""), json)
        assertTrue(json.contains("\"value\": 3"), json)
        assertTrue(json.contains("\"value\": true"), json)
        assertTrue(json.contains("\"value\": [\"a\", \"b\"]"), json)
    }

    @Test
    fun `escapes quotes and backslashes and control bytes`() {
        // Preference values are whatever the app stored — a JSON blob, a Windows
        // path, a newline, a stray control byte. Any of them unescaped produces a
        // file no parser will read.
        val json = store(
            "payload" to PreferenceValue.StringValue("say \"hi\"\nC:\\tmp" + Char(7)),
        ).jsonExport()

        assertTrue(json.contains("""say \"hi\"\nC:\\tmp"""), json)
        // The raw byte must not survive into the file.
        assertTrue(!json.contains(Char(7)), "control byte written verbatim")
    }

    @Test
    fun `records the store name and whether it is writable`() {
        val json = FakeStore("secure", isMutable = false, values = linkedMapOf()).jsonExport()

        assertTrue(json.contains("\"store\": \"secure\""), json)
        assertTrue(json.contains("\"readOnly\": true"), json)
    }

    @Test
    fun `exports only the keys it is given`() {
        val exported = store(
            "a" to PreferenceValue.StringValue("1"),
            "b" to PreferenceValue.StringValue("2"),
        ).jsonExport(keys = listOf("a"))

        assertTrue(exported.contains("\"key\": \"a\""), exported)
        assertTrue(!exported.contains("\"key\": \"b\""), exported)
    }

    @Test
    fun `an empty store still produces parseable JSON`() {
        val json = store().jsonExport()

        assertEquals("{\n  \"store\": \"prefs\",\n  \"readOnly\": false,\n  \"entries\": [\n  ]\n}", json)
    }
}
