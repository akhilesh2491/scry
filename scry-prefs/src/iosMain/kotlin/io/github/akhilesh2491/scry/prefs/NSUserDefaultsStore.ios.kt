package io.github.akhilesh2491.scry.prefs

import io.github.akhilesh2491.scry.core.PlatformContext
import platform.Foundation.NSUserDefaults

/** Exposes an [NSUserDefaults] suite to Scry. */
public fun NSUserDefaults.asScryStore(name: String): KeyValueStore =
    NSUserDefaultsStore(name, this)

internal class NSUserDefaultsStore(
    override val name: String,
    private val defaults: NSUserDefaults,
) : KeyValueStore {

    override fun keys(): List<String> =
        defaults.dictionaryRepresentation().keys.filterIsInstance<String>().sorted()

    /**
     * Reads a value, inferring its type.
     *
     * `NSUserDefaults` erases to `Any?`, and numbers all arrive as `NSNumber`
     * bridged to Kotlin. Booleans are checked before numbers because a bridged
     * boolean is also a valid `Long` — getting that order wrong turns every
     * flag into `0`/`1` and loses the switch in the editor.
     */
    override fun read(key: String): PreferenceValue? =
        when (val raw = defaults.objectForKey(key)) {
            null -> null
            is Boolean -> PreferenceValue.BooleanValue(raw)
            is Int -> PreferenceValue.IntValue(raw)
            is Long -> PreferenceValue.LongValue(raw)
            is Float -> PreferenceValue.FloatValue(raw)
            is Double -> PreferenceValue.DoubleValue(raw)
            is String -> PreferenceValue.StringValue(raw)
            is List<*> -> PreferenceValue.StringSetValue(
                raw.filterIsInstance<String>().toSet(),
            )
            else -> PreferenceValue.Unsupported(
                rendered = raw.toString(),
                typeName = raw::class.simpleName ?: "?",
            )
        }

    override fun write(key: String, value: PreferenceValue) {
        when (value) {
            is PreferenceValue.StringValue -> defaults.setObject(value.value, key)
            is PreferenceValue.IntValue -> defaults.setInteger(value.value.toLong(), key)
            is PreferenceValue.LongValue -> defaults.setInteger(value.value, key)
            is PreferenceValue.FloatValue -> defaults.setFloat(value.value, key)
            is PreferenceValue.DoubleValue -> defaults.setDouble(value.value, key)
            is PreferenceValue.BooleanValue -> defaults.setBool(value.value, key)
            is PreferenceValue.StringSetValue -> defaults.setObject(value.value.toList(), key)
            is PreferenceValue.Unsupported -> return
        }
        defaults.synchronize()
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }

    override fun clear() {
        keys().forEach { defaults.removeObjectForKey(it) }
        defaults.synchronize()
    }
}

/**
 * Exposes the standard defaults suite.
 *
 * Named suites cannot be discovered — `NSUserDefaults` offers no way to
 * enumerate them — so an app using one should register it explicitly with
 * `store(NSUserDefaults(suiteName = "…").asScryStore("…"))`.
 */
internal actual fun discoverStores(context: PlatformContext): List<KeyValueStore> = listOf(
    NSUserDefaultsStore("NSUserDefaults (standard)", NSUserDefaults.standardUserDefaults),
)
