package io.github.akhilesh2491.scry.core

import kotlinx.coroutines.flow.StateFlow

/**
 * A unit of debugging functionality.
 *
 * Every built-in Scry feature — network, preferences, database — is a plugin
 * implemented against this same interface. If this interface cannot express a
 * built-in, the interface is wrong, not the built-in.
 *
 * Plugins are authored in Kotlin, so this surface is deliberately Kotlin-idiomatic
 * (flows, coroutines). The *installation* surface that app developers touch
 * ([Scry.install], [ScryConfig]) is the one held to strict Java interop rules.
 */
public interface ScryPlugin {

    /** Stable, unique identifier. Convention: `"scry.network"`, `"acme.myplugin"`. */
    public val id: String

    /** Human-readable name shown in the Scry plugin list. */
    public val displayName: String

    /**
     * Optional short string shown next to the plugin in the list — a count of
     * captured items, an error indicator. `null` means no badge.
     */
    public val badge: StateFlow<String?>

    /**
     * Called once when Scry is installed, before any UI exists.
     *
     * Everything the plugin needs arrives via [scope]. Plugins must not reach for
     * global state; that is what keeps third-party plugins testable and what lets
     * Scry run more than one instance in tests.
     */
    public fun onInstall(scope: ScryScope)

    /** Called when the user clears Scry's data. Drop everything you have stored. */
    public fun onClear()
}

/**
 * Everything a plugin is given at install time.
 *
 * Deliberately a small, concrete set. A plugin that needs something not present
 * here is a signal that this type needs to grow — not that the plugin should
 * reach around it.
 */
public interface ScryScope {

    /** Platform handle: `Context` on Android, a no-op holder on desktop. */
    public val context: PlatformContext

    /**
     * Coroutine scope tied to the Scry instance lifetime. Cancelled when Scry is
     * uninstalled, so plugins do not need their own lifecycle handling.
     */
    public val coroutineScope: kotlinx.coroutines.CoroutineScope

    /** Shared persistent storage. See [ScryStore]. */
    public val store: ScryStore

    /** Cross-plugin event bus. See [ScryEventBus]. */
    public val events: ScryEventBus

    /** Where to contribute diagnostic context. See [ScryContextRegistry]. */
    public val contextRegistry: ScryContextRegistry

    /** The resolved configuration Scry was installed with. */
    public val config: ScryConfig
}
