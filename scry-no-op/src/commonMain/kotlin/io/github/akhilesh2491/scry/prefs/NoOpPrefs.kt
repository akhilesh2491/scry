@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.prefs

import io.github.akhilesh2491.scry.core.ScryPlugin
import io.github.akhilesh2491.scry.core.ScryScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Inert mirrors of `scry-prefs`. */

public sealed interface PreferenceValue {

    public data class StringValue(val value: String) : PreferenceValue
    public data class IntValue(val value: Int) : PreferenceValue
    public data class LongValue(val value: Long) : PreferenceValue
    public data class FloatValue(val value: Float) : PreferenceValue
    public data class DoubleValue(val value: Double) : PreferenceValue
    public data class BooleanValue(val value: Boolean) : PreferenceValue
    public data class StringSetValue(val value: Set<String>) : PreferenceValue
    public data class Unsupported(val rendered: String, val typeName: String) : PreferenceValue

    public val typeLabel: String get() = ""
    public val display: String get() = ""
    public val isEditable: Boolean get() = false
}

public interface KeyValueStore {
    public val name: String
    public val isMutable: Boolean get() = false
    public fun keys(): List<String>
    public fun read(key: String): PreferenceValue?
    public fun write(key: String, value: PreferenceValue)
    public fun remove(key: String)
    public fun clear()
    public fun observe(onChange: (String?) -> Unit): AutoCloseable? = null
}

public fun interface KeyValueStoreProvider {
    public fun stores(): List<KeyValueStore>
}

public class PreferencesPluginBuilder internal constructor() {
    public fun autoDiscover(enabled: Boolean): Unit = Unit
    public fun store(store: KeyValueStore): Unit = Unit
    public fun provider(provider: KeyValueStoreProvider): Unit = Unit
}

/**
 * Discovers nothing and exposes nothing.
 *
 * The most important stub in this artifact: the real plugin can read and *write*
 * the host app's preferences. That must not be reachable in a shipped build.
 */
public class PreferencesPlugin(
    configure: PreferencesPluginBuilder.() -> Unit = {},
) : ScryPlugin {
    override val id: String = PLUGIN_ID
    override val displayName: String = "Preferences"
    override val badge: StateFlow<String?> = MutableStateFlow(null)

    public val stores: StateFlow<List<KeyValueStore>> = MutableStateFlow(emptyList())

    override fun onInstall(scope: ScryScope): Unit = Unit
    override fun onClear(): Unit = Unit
    public fun refresh(): Unit = Unit

    public companion object {
        public const val PLUGIN_ID: String = "scry.prefs"
    }
}
