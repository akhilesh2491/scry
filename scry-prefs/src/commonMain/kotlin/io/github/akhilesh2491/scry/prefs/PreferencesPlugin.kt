package io.github.akhilesh2491.scry.prefs

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.PlatformContext
import io.github.akhilesh2491.scry.core.ScryScope
import io.github.akhilesh2491.scry.ui.ScryUiPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Builder for [PreferencesPlugin]. */
public class PreferencesPluginBuilder internal constructor() {
    internal val providers: MutableList<KeyValueStoreProvider> = mutableListOf()
    internal var autoDiscover: Boolean = true

    /**
     * Whether to find the platform's stores automatically.
     *
     * On by default — the common case is "show me my app's preferences", and
     * requiring registration for that would be pointless ceremony. Turn it off
     * when you want to expose only specific stores.
     */
    public fun autoDiscover(enabled: Boolean) {
        autoDiscover = enabled
    }

    /** Adds a store Scry cannot discover on its own. */
    public fun store(store: KeyValueStore) {
        providers += KeyValueStoreProvider { listOf(store) }
    }

    /** Adds a provider of several stores. */
    public fun provider(provider: KeyValueStoreProvider) {
        providers += provider
    }
}

/**
 * Views and edits the app's key-value stores.
 *
 * ```kotlin
 * Scry.install(this) {
 *     plugin(PreferencesPlugin())            // discovers every SharedPreferences file
 *     plugin(PreferencesPlugin {             // or be explicit
 *         autoDiscover(false)
 *         store(encryptedPrefs.asScryStore("secure"))
 *     })
 * }
 * ```
 *
 * Editing is the reason this exists. Reading preferences is a `cat` away over
 * ADB; flipping an onboarding flag to re-test a flow without reinstalling is not.
 */
public class PreferencesPlugin(
    configure: PreferencesPluginBuilder.() -> Unit = {},
) : ScryUiPlugin {

    private val builder = PreferencesPluginBuilder().apply(configure)

    override val id: String = PLUGIN_ID
    override val displayName: String = "Preferences"

    private var context: PlatformContext? = null

    private val _stores = MutableStateFlow<List<KeyValueStore>>(emptyList())

    /** Discovered and registered stores, as of the last [refresh]. */
    public val stores: StateFlow<List<KeyValueStore>> = _stores.asStateFlow()

    private val _badge = MutableStateFlow<String?>(null)
    override val badge: StateFlow<String?> = _badge.asStateFlow()

    override fun onInstall(scope: ScryScope) {
        context = scope.context
        refresh()
    }

    /**
     * Re-scans for stores.
     *
     * Discovery must be lazy, not a one-shot at install time. Apps create
     * preference files whenever they first touch them — frequently long after
     * `Application.onCreate` — and a store that did not exist during
     * installation would otherwise never appear. The UI calls this each time the
     * store list is shown.
     */
    public fun refresh() {
        val discovered = context
            ?.takeIf { builder.autoDiscover }
            ?.let { discoverStores(it) }
            .orEmpty()
        val registered = builder.providers.flatMap { it.stores() }

        // Registered stores win on name collision: an explicitly supplied
        // instance (an EncryptedSharedPreferences, say) can read values that the
        // file-scanning discovery would surface as ciphertext.
        val byName = LinkedHashMap<String, KeyValueStore>()
        discovered.forEach { byName[it.name] = it }
        registered.forEach { byName[it.name] = it }

        _stores.value = byName.values.toList()
        _badge.value = byName.size.takeIf { it > 0 }?.toString()
    }

    /**
     * Nothing to clear.
     *
     * "Clear all" in Scry wipes Scry's own captured data. Wiping the host app's
     * preferences from the same button would be a destructive surprise — the
     * per-store reset in the UI is deliberate and confirmable instead.
     */
    override fun onClear(): Unit = Unit

    @Composable
    override fun Content() {
        PreferencesScreen(plugin = this)
    }

    public companion object {
        public const val PLUGIN_ID: String = "scry.prefs"
    }
}

/** Platform store discovery. */
internal expect fun discoverStores(context: PlatformContext): List<KeyValueStore>
