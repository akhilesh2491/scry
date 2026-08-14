@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Inert mirrors of `scry-core`. Signatures match the real module exactly; every
 * body does nothing. See the module KDoc in `build.gradle.kts`.
 */

public class Redactor internal constructor(
    private val headerNames: Set<String>,
    private val bodyKeys: Set<String>,
) {
    public fun shouldRedactHeader(name: String): Boolean = false
    public fun redactHeaderValue(name: String, value: String): String = value
    public fun redactJsonBody(body: String): String = body

    public companion object {
        public val DEFAULT_HEADERS: Set<String> = emptySet()
        public val DEFAULT_BODY_KEYS: Set<String> = emptySet()
        public val DEFAULT: Redactor = Redactor(emptySet(), emptySet())
    }
}

public class RedactorBuilder internal constructor() {
    public fun redactHeaders(vararg names: String): Unit = Unit
    public fun redactBodyKeys(vararg keys: String): Unit = Unit
    public fun allowHeader(name: String): Unit = Unit
}

public class Retention private constructor(
    public val maxAge: Duration,
    public val maxEntries: Int,
    public val maxBytes: Long,
) {
    public companion object {
        @JvmStatic
        public val DEFAULT: Retention = Retention(24.hours, 1_000, 50L * 1024 * 1024)

        @JvmStatic
        public fun of(maxAge: Duration, maxEntries: Int, maxBytes: Long): Retention =
            Retention(maxAge, maxEntries, maxBytes)

        @JvmStatic
        public fun ofHours(hours: Long): Retention = DEFAULT
    }
}

public class StoredRecord(
    public val id: String,
    public val pluginId: String,
    public val createdAtMillis: Long,
    public val payload: String,
) {
    public val sizeBytes: Int get() = 0
}

public class ScryStore internal constructor() {
    public fun put(
        pluginId: String,
        id: String,
        payload: String,
        createdAtMillis: Long = 0L,
    ): Unit = Unit

    public fun read(pluginId: String, limit: Int = Int.MAX_VALUE): List<StoredRecord> = emptyList()
    public fun delete(id: String): Unit = Unit
    public fun clear(pluginId: String): Unit = Unit
    public fun clearAll(): Unit = Unit
    public fun count(pluginId: String): Int = 0
    public fun totalSizeBytes(): Long = 0L

    public companion object {
        public fun inMemory(retention: Retention = Retention.DEFAULT): ScryStore = ScryStore()
    }
}

public interface ScryEvent {
    public val timestampMillis: Long
}

public class ScryEventBus internal constructor() {
    public val events: Flow<ScryEvent> = emptyFlow()
    public fun publish(event: ScryEvent): Boolean = false

    public inline fun <reified T : ScryEvent> eventsOfType(): Flow<T> = emptyFlow()
}

public interface ScryPlugin {
    public val id: String
    public val displayName: String
    public val badge: StateFlow<String?>
    public fun onInstall(scope: ScryScope)
    public fun onClear()
}

public interface ScryScope {
    public val context: PlatformContext
    public val coroutineScope: CoroutineScope
    public val store: ScryStore
    public val events: ScryEventBus
    public val contextRegistry: ScryContextRegistry
    public val config: ScryConfig
}

public class ScryContextSection(public val title: String, public val body: String)

public fun interface ScryContextContributor {
    public fun contribute(): List<ScryContextSection>
}

public class ScryContextRegistry internal constructor() {
    public fun register(sourceId: String, contributor: ScryContextContributor): Unit = Unit
    public fun unregister(sourceId: String): Unit = Unit
    public fun collect(): List<ScryContextSection> = emptyList()
}

public class ScryConfig internal constructor(
    public val retention: Retention,
    public val plugins: List<ScryPlugin>,
    public val redactor: Redactor,
    public val allowInReleaseBuilds: Boolean,
)

public class ScryConfigBuilder internal constructor() {
    public var retention: Retention = Retention.DEFAULT
    public var allowInReleaseBuilds: Boolean = false
    public fun plugin(plugin: ScryPlugin): Unit = Unit
    public fun redaction(configure: RedactorBuilder.() -> Unit): Unit = Unit
}

public class ScryInstance internal constructor() {
    public val platformContext: PlatformContext get() = error("Scry is the no-op artifact")
    public val config: ScryConfig = ScryConfig(Retention.DEFAULT, emptyList(), Redactor.DEFAULT, false)
    public val store: ScryStore = ScryStore()
    public val events: ScryEventBus = ScryEventBus()
    public val contextRegistry: ScryContextRegistry = ScryContextRegistry()
    public val isVisible: StateFlow<Boolean> = MutableStateFlow(false)
    public val plugins: List<ScryPlugin> = emptyList()
}

public class ScryInstaller internal constructor() {
    public fun retention(retention: Retention): ScryInstaller = this
    public fun addPlugin(plugin: ScryPlugin): ScryInstaller = this
    public fun allowInReleaseBuilds(allow: Boolean): ScryInstaller = this
    public fun redactHeaders(vararg names: String): ScryInstaller = this
    public fun redactBodyKeys(vararg keys: String): ScryInstaller = this
    public fun install(): ScryInstance? = null
}

public object Scry {
    /**
     * Always null. Callers already handle this — the real [install] returns null
     * in a non-debuggable build — so the no-op needs no special handling at the
     * call site.
     */
    public val instance: ScryInstance? get() = null

    @JvmStatic
    public fun isInstalled(): Boolean = false

    @JvmStatic
    public fun install(
        context: PlatformContext,
        configure: ScryConfigBuilder.() -> Unit = {},
    ): ScryInstance? = null

    @JvmStatic
    public fun installer(context: PlatformContext): ScryInstaller = ScryInstaller()

    @JvmStatic
    public fun show(): Unit = Unit

    @JvmStatic
    public fun hide(): Unit = Unit

    @JvmStatic
    public fun clear(): Unit = Unit

    @JvmStatic
    public fun uninstall(): Unit = Unit
}

public object ScryTesting {
    @JvmStatic
    @JvmOverloads
    public fun scope(
        store: ScryStore = ScryStore.inMemory(),
        redactor: Redactor = Redactor.DEFAULT,
        retention: Retention = Retention.DEFAULT,
        context: PlatformContext? = null,
    ): ScryScope = NoOpScope

    private object NoOpScope : ScryScope {
        override val context: PlatformContext get() = error("Scry is the no-op artifact")
        override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
        override val store: ScryStore = ScryStore()
        override val events: ScryEventBus = ScryEventBus()
        override val contextRegistry: ScryContextRegistry = ScryContextRegistry()
        override val config: ScryConfig =
            ScryConfig(Retention.DEFAULT, emptyList(), Redactor.DEFAULT, false)
    }
}

public expect class PlatformContext
