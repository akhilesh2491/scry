package io.github.akhilesh2491.scry.prefs

import android.content.Context
import android.content.SharedPreferences
import io.github.akhilesh2491.scry.core.PlatformContext
import java.io.File

/**
 * Exposes an Android [SharedPreferences] instance to Scry.
 *
 * Use this for stores discovery cannot handle correctly — most importantly
 * `EncryptedSharedPreferences`, where reading the file directly yields
 * ciphertext but the live instance decrypts transparently.
 *
 * ```kotlin
 * plugin(PreferencesPlugin { store(securePrefs.asScryStore("secure")) })
 * ```
 */
public fun SharedPreferences.asScryStore(name: String): KeyValueStore =
    SharedPreferencesStore(name, this)

internal class SharedPreferencesStore(
    override val name: String,
    private val prefs: SharedPreferences,
) : KeyValueStore {

    override fun keys(): List<String> = prefs.all.keys.sorted()

    override fun read(key: String): PreferenceValue? =
        prefs.all[key]?.let { raw ->
            when (raw) {
                is String -> PreferenceValue.StringValue(raw)
                is Int -> PreferenceValue.IntValue(raw)
                is Long -> PreferenceValue.LongValue(raw)
                is Float -> PreferenceValue.FloatValue(raw)
                is Boolean -> PreferenceValue.BooleanValue(raw)
                is Set<*> -> PreferenceValue.StringSetValue(raw.filterIsInstance<String>().toSet())
                else -> PreferenceValue.Unsupported(
                    rendered = raw.toString(),
                    typeName = raw::class.simpleName ?: "?",
                )
            }
        }

    override fun write(key: String, value: PreferenceValue) {
        prefs.edit().apply {
            when (value) {
                is PreferenceValue.StringValue -> putString(key, value.value)
                is PreferenceValue.IntValue -> putInt(key, value.value)
                is PreferenceValue.LongValue -> putLong(key, value.value)
                is PreferenceValue.FloatValue -> putFloat(key, value.value)
                is PreferenceValue.BooleanValue -> putBoolean(key, value.value)
                is PreferenceValue.StringSetValue -> putStringSet(key, value.value)
                // SharedPreferences has no Double; stored as the Float the UI offered.
                is PreferenceValue.DoubleValue -> putFloat(key, value.value.toFloat())
                is PreferenceValue.Unsupported -> return
            }
        }.apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun observe(onChange: (String?) -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            onChange(key)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/**
 * Finds every `SharedPreferences` file the app owns.
 *
 * Reads the names from the `shared_prefs` directory but opens each through
 * `getSharedPreferences`, so values come from the same in-memory instance the
 * app is using — parsing the XML ourselves would show stale data whenever a
 * write had not yet been flushed to disk.
 */
internal actual fun discoverStores(context: PlatformContext): List<KeyValueStore> {
    val android = context.androidContext
    val dir = File(android.applicationInfo.dataDir, "shared_prefs")
    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") } ?: return emptyList()

    return files
        .map { it.name.removeSuffix(".xml") }
        .sorted()
        .map { name ->
            SharedPreferencesStore(name, android.getSharedPreferences(name, Context.MODE_PRIVATE))
        }
}
