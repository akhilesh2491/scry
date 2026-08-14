@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.prefs

import android.content.SharedPreferences

/**
 * Returns a store that reads and writes nothing.
 *
 * File name mirrors the real module's so the JVM facade class
 * (`SharedPreferencesStore_androidKt`) matches for Java callers.
 */
public fun SharedPreferences.asScryStore(name: String): KeyValueStore = NoOpStore(name)

private class NoOpStore(override val name: String) : KeyValueStore {
    override val isMutable: Boolean = false
    override fun keys(): List<String> = emptyList()
    override fun read(key: String): PreferenceValue? = null
    override fun write(key: String, value: PreferenceValue): Unit = Unit
    override fun remove(key: String): Unit = Unit
    override fun clear(): Unit = Unit
}
