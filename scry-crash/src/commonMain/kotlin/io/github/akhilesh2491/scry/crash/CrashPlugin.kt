package io.github.akhilesh2491.scry.crash

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.PlatformContext
import io.github.akhilesh2491.scry.core.ScryScope
import io.github.akhilesh2491.scry.core.ScryStore
import io.github.akhilesh2491.scry.ui.ScryUiPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Builder for [CrashPlugin]. */
public class CrashPluginBuilder internal constructor() {
    /** Install a default uncaught-exception handler. */
    public var captureCrashes: Boolean = true

    /** Watch the main thread and record an ANR when it stalls. */
    public var captureAnrs: Boolean = true

    /** How long the main thread may block before it counts as an ANR. */
    public var anrThreshold: Duration = 5.seconds

    internal var listener: ((CrashRecord) -> Unit)? = null

    /**
     * Called for every captured failure.
     *
     * The hook exists so teams can forward to Crashlytics or Bugsnag. Scry is a
     * debugging tool, not a crash-reporting backend, and pretending otherwise
     * would be the wrong thing to build.
     */
    public fun onCrash(listener: (CrashRecord) -> Unit) {
        this.listener = listener
    }
}

/**
 * Captures crashes and ANRs, with the context that makes them actionable.
 *
 * ```kotlin
 * plugin(CrashPlugin { anrThreshold = 4.seconds })
 * ```
 *
 * Scry never swallows a failure: the previously installed uncaught-exception
 * handler is always invoked afterwards, so Crashlytics and the platform's own
 * dialog behave exactly as they did before.
 */
public class CrashPlugin(
    configure: CrashPluginBuilder.() -> Unit = {},
) : ScryUiPlugin {

    internal val config: CrashPluginBuilder = CrashPluginBuilder().apply(configure)

    override val id: String = PLUGIN_ID
    override val displayName: String = "Crashes & ANRs"

    private val _records = MutableStateFlow<List<CrashRecord>>(emptyList())

    /** Captured failures, newest first. */
    public val records: StateFlow<List<CrashRecord>> = _records.asStateFlow()

    private val _badge = MutableStateFlow<String?>(null)
    override val badge: StateFlow<String?> = _badge.asStateFlow()

    private var store: ScryStore? = null
    private var scope: ScryScope? = null
    private var installedHandlers: AutoCloseable? = null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onInstall(scope: ScryScope) {
        this.scope = scope
        this.store = scope.store

        // Restore prior crashes first. A crash is the one event you cannot
        // inspect in the session it happened in, so surviving process death is
        // the entire point of persisting them.
        _records.value = scope.store.read(PLUGIN_ID).mapNotNull { record ->
            runCatching { json.decodeFromString<CrashRecord>(record.payload) }.getOrNull()
        }
        updateBadge()

        installedHandlers = installCrashHandlers(scope.context, this)
    }

    override fun onClear() {
        _records.value = emptyList()
        store?.clear(PLUGIN_ID)
        updateBadge()
    }

    /** Records a failure the app caught itself. */
    public fun recordHandled(throwable: Throwable, threadName: String = "unknown") {
        record(buildRecord(CrashKind.HANDLED, threadName, throwable))
    }

    /**
     * Builds a record, attaching context from every registered contributor.
     *
     * Context is gathered here — at failure time — because by the time anyone
     * opens the report the network log has rolled over and the device state has
     * changed.
     */
    internal fun buildRecord(
        kind: CrashKind,
        threadName: String,
        throwable: Throwable?,
        overrideStackTrace: String? = null,
    ): CrashRecord {
        val sections = scope?.contextRegistry?.collect().orEmpty().map {
            CrashSection(it.title, it.body)
        }
        return CrashRecord(
            id = "${nowMillis()}-${_records.value.size}",
            timestampMillis = nowMillis(),
            kind = kind,
            threadName = threadName,
            exceptionClass = throwable?.let { it::class.simpleName ?: "Throwable" } ?: kind.name,
            message = throwable?.message,
            stackTrace = overrideStackTrace ?: throwable?.stackTraceToString().orEmpty(),
            sections = sections,
        )
    }

    /** Stores a record and notifies the configured listener. */
    internal fun record(record: CrashRecord) {
        _records.value = listOf(record) + _records.value
        // Persist synchronously: the process may be seconds from death.
        store?.put(
            pluginId = PLUGIN_ID,
            id = record.id,
            payload = json.encodeToString(record),
            createdAtMillis = record.timestampMillis,
        )
        updateBadge()
        runCatching { config.listener?.invoke(record) }
    }

    private fun updateBadge() {
        _badge.value = _records.value.size.takeIf { it > 0 }?.toString()
    }

    @Composable
    override fun Content() {
        CrashScreen(plugin = this)
    }

    public companion object {
        public const val PLUGIN_ID: String = "scry.crash"
    }
}

/** Installs platform failure handlers. Returns a handle that removes them. */
internal expect fun installCrashHandlers(
    context: PlatformContext,
    plugin: CrashPlugin,
): AutoCloseable?

internal expect fun nowMillis(): Long

/**
 * Shares a crash report.
 *
 * Delegates to [io.github.akhilesh2491.scry.core.shareScryFile]; the platform
 * plumbing lives in core because HAR export needs exactly the same thing.
 */
public fun shareBugReport(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean = io.github.akhilesh2491.scry.core.shareScryFile(context, fileName, text)
