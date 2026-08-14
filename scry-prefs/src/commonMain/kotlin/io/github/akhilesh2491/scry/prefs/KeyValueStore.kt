package io.github.akhilesh2491.scry.prefs

/**
 * A typed value in a key-value store.
 *
 * Modelled as a sealed hierarchy rather than `Any?` because editing is the point:
 * the UI has to know a value is a `Boolean` to offer a switch instead of a text
 * field, and writing back has to hit the correctly typed setter — Android's
 * `SharedPreferences` throws `ClassCastException` on a type change, not a
 * conversion.
 */
public sealed interface PreferenceValue {

    public data class StringValue(val value: String) : PreferenceValue
    public data class IntValue(val value: Int) : PreferenceValue
    public data class LongValue(val value: Long) : PreferenceValue
    public data class FloatValue(val value: Float) : PreferenceValue
    public data class DoubleValue(val value: Double) : PreferenceValue
    public data class BooleanValue(val value: Boolean) : PreferenceValue
    public data class StringSetValue(val value: Set<String>) : PreferenceValue

    /**
     * A value Scry can display but not safely write back.
     *
     * Showing it read-only beats hiding it: "the key exists and holds something
     * unexpected" is frequently the answer you came looking for.
     */
    public data class Unsupported(val rendered: String, val typeName: String) : PreferenceValue

    /** Short label for the type badge in the UI. */
    public val typeLabel: String
        get() = when (this) {
            is StringValue -> "String"
            is IntValue -> "Int"
            is LongValue -> "Long"
            is FloatValue -> "Float"
            is DoubleValue -> "Double"
            is BooleanValue -> "Boolean"
            is StringSetValue -> "Set<String>"
            is Unsupported -> typeName
        }

    /** Human-readable rendering for lists and text fields. */
    public val display: String
        get() = when (this) {
            is StringValue -> value
            is IntValue -> value.toString()
            is LongValue -> value.toString()
            is FloatValue -> value.toString()
            is DoubleValue -> value.toString()
            is BooleanValue -> value.toString()
            is StringSetValue -> value.joinToString(", ")
            is Unsupported -> rendered
        }

    /** Whether Scry can write this type back. */
    public val isEditable: Boolean get() = this !is Unsupported
}

/**
 * One inspectable key-value store — an Android `SharedPreferences` file, a
 * DataStore, `NSUserDefaults`, a desktop preferences node.
 *
 * Implement this to expose a store Scry does not know about. The plugin's whole
 * job is turning a list of these into a UI, so a custom backend gets exactly the
 * same treatment as the built-ins.
 */
public interface KeyValueStore {

    /** Display name, typically the file or suite name. */
    public val name: String

    /**
     * False for stores Scry can read but not modify.
     *
     * The UI hides every mutation affordance rather than letting an edit fail
     * after the user has typed it.
     */
    public val isMutable: Boolean get() = true

    public fun keys(): List<String>

    public fun read(key: String): PreferenceValue?

    /** Writes [value]. Only called for [PreferenceValue.isEditable] values. */
    public fun write(key: String, value: PreferenceValue)

    public fun remove(key: String)

    /** Removes every key. */
    public fun clear()

    /**
     * Registers for change notifications, if the backend supports them.
     *
     * Returns a handle to stop observing, or null when the backend cannot report
     * changes — in which case the UI falls back to re-reading on demand.
     */
    public fun observe(onChange: (String?) -> Unit): AutoCloseable? = null
}

/** Supplies [KeyValueStore]s to the preferences plugin. */
public fun interface KeyValueStoreProvider {
    public fun stores(): List<KeyValueStore>
}
