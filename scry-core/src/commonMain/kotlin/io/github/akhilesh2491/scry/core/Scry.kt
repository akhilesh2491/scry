package io.github.akhilesh2491.scry.core

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.jvm.JvmStatic

/**
 * A live Scry installation.
 *
 * Held by [Scry] after [Scry.install]. Exposed so tests (and hosts that want more
 * than one instance) can drive Scry without touching the singleton.
 */
public class ScryInstance internal constructor(
    public val config: ScryConfig,
    public val store: ScryStore,
    public val events: ScryEventBus,
    /** The platform handle Scry was installed with. */
    public val platformContext: PlatformContext,
) {
    /** Diagnostic context contributed by plugins, collected into crash reports. */
    public val contextRegistry: ScryContextRegistry = ScryContextRegistry()

    internal val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + CoroutineName("Scry"))

    private val _isVisible = MutableStateFlow(false)

    /** Whether the Scry UI is currently being shown. Observed by the UI host. */
    public val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /** Registered plugins, in display order. */
    public val plugins: List<ScryPlugin> get() = config.plugins

    internal fun installPlugins() {
        val scope = object : ScryScope {
            override val context: PlatformContext = this@ScryInstance.platformContext
            override val coroutineScope: CoroutineScope = this@ScryInstance.coroutineScope
            override val store: ScryStore = this@ScryInstance.store
            override val events: ScryEventBus = this@ScryInstance.events
            override val contextRegistry: ScryContextRegistry = this@ScryInstance.contextRegistry
            override val config: ScryConfig = this@ScryInstance.config
        }
        config.plugins.forEach { it.onInstall(scope) }
    }

    internal fun show() {
        _isVisible.value = true
    }

    internal fun hide() {
        _isVisible.value = false
    }

    internal fun clear() {
        config.plugins.forEach { it.onClear() }
        store.clearAll()
    }

    internal fun shutdown() {
        coroutineScope.cancel()
        store.close()
    }
}

/**
 * Entry point for the host application.
 *
 * A process-wide singleton on purpose: `Scry.show()` needs to work from a shake
 * detector, a notification tap, and a debug menu button without any of them
 * holding a reference. Plugins get [ScryScope] instead and never touch this.
 */
public object Scry {

    private var current: ScryInstance? = null

    /** The live installation, or `null` if [install] has not been called. */
    public val instance: ScryInstance? get() = current

    /** Whether Scry is installed and active. */
    @JvmStatic
    public fun isInstalled(): Boolean = current != null

    /**
     * Installs Scry.
     *
     * Returns the created instance, or `null` when installation was refused
     * because the build is not debuggable and [ScryConfigBuilder.allowInReleaseBuilds]
     * was not set. Returning `null` rather than throwing is deliberate: the
     * guard must never be the reason a release build crashes.
     *
     * Calling this twice replaces the previous installation.
     */
    @JvmStatic
    public fun install(
        context: PlatformContext,
        configure: ScryConfigBuilder.() -> Unit = {},
    ): ScryInstance? {
        val config = ScryConfigBuilder().apply(configure).build()

        if (!config.allowInReleaseBuilds && !context.isDebuggableBuild()) {
            return null
        }

        current?.shutdown()

        val store = ScryStore.open(
            directory = context.scryStorageDirectory(),
            retention = config.retention,
        )
        val instance = ScryInstance(
            config = config,
            store = store,
            events = ScryEventBus(),
            platformContext = context,
        )
        instance.installPlugins()
        current = instance
        return instance
    }

    /**
     * Java-friendly builder equivalent of the `install { }` DSL.
     *
     * See [ScryInstaller]. Kotlin callers should prefer [install].
     */
    @JvmStatic
    public fun installer(context: PlatformContext): ScryInstaller = ScryInstaller(context)

    /** Shows the Scry UI. No-op if Scry is not installed. */
    @JvmStatic
    public fun show() {
        current?.show()
    }

    /** Hides the Scry UI. No-op if Scry is not installed. */
    @JvmStatic
    public fun hide() {
        current?.hide()
    }

    /** Clears all captured data across every plugin. No-op if not installed. */
    @JvmStatic
    public fun clear() {
        current?.clear()
    }

    /** Tears down the installation and releases its resources. */
    @JvmStatic
    public fun uninstall() {
        current?.shutdown()
        current = null
    }
}
