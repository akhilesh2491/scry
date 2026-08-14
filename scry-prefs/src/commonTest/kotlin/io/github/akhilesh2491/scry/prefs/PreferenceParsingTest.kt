package io.github.akhilesh2491.scry.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferenceParsingTest {

    @Test
    fun `parses each type back into its own type`() {
        assertEquals(
            PreferenceValue.IntValue(42),
            parseAs(PreferenceValue.IntValue(0), "42"),
        )
        assertEquals(
            PreferenceValue.LongValue(9_000_000_000),
            parseAs(PreferenceValue.LongValue(0), "9000000000"),
        )
        assertEquals(
            PreferenceValue.BooleanValue(true),
            parseAs(PreferenceValue.BooleanValue(false), "true"),
        )
    }

    @Test
    fun `refuses input that does not fit the original type`() {
        // Rewriting an Int key as a String is how the host app gets a
        // ClassCastException on its next read, so bad input must be rejected at
        // the dialog rather than coerced.
        assertNull(parseAs(PreferenceValue.IntValue(0), "not a number"))
        assertNull(parseAs(PreferenceValue.LongValue(0), "1.5"))
        assertNull(parseAs(PreferenceValue.BooleanValue(false), "yes"))
    }

    @Test
    fun `boolean parsing is strict`() {
        // toBooleanStrictOrNull, not toBoolean — the latter maps anything that
        // is not "true" to false, so a typo would silently flip a flag.
        assertNull(parseAs(PreferenceValue.BooleanValue(false), "True "))
        assertEquals(
            PreferenceValue.BooleanValue(false),
            parseAs(PreferenceValue.BooleanValue(true), "false"),
        )
    }

    @Test
    fun `string values accept anything including empty`() {
        assertEquals(
            PreferenceValue.StringValue(""),
            parseAs(PreferenceValue.StringValue("x"), ""),
        )
    }

    @Test
    fun `string sets split on commas and drop blanks`() {
        assertEquals(
            PreferenceValue.StringSetValue(setOf("a", "b", "c")),
            parseAs(PreferenceValue.StringSetValue(emptySet()), "a, b ,, c , "),
        )
    }

    @Test
    fun `unsupported values are never written back`() {
        assertNull(parseAs(PreferenceValue.Unsupported("?", "Blob"), "anything"))
    }

    @Test
    fun `type labels and display strings are populated for every variant`() {
        val all = listOf(
            PreferenceValue.StringValue("s"),
            PreferenceValue.IntValue(1),
            PreferenceValue.LongValue(1),
            PreferenceValue.FloatValue(1f),
            PreferenceValue.DoubleValue(1.0),
            PreferenceValue.BooleanValue(true),
            PreferenceValue.StringSetValue(setOf("a")),
            PreferenceValue.Unsupported("raw", "Blob"),
        )
        all.forEach {
            assertEquals(true, it.typeLabel.isNotBlank(), "typeLabel missing for $it")
            assertEquals(true, it.display.isNotBlank(), "display missing for $it")
        }
    }

    @Test
    fun `only unsupported values are non-editable`() {
        assertEquals(true, PreferenceValue.StringValue("x").isEditable)
        assertEquals(false, PreferenceValue.Unsupported("x", "Blob").isEditable)
    }
}
