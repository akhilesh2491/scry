@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.crash

import io.github.akhilesh2491.scry.core.ScryPlugin
import io.github.akhilesh2491.scry.core.ScryScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Inert mirrors of `scry-crash`. */

public enum class CrashKind { CRASH, COROUTINE, ANR, HANDLED }

public class CrashSection(public val title: String, public val body: String)

public class CrashRecord(
    public val id: String,
    public val timestampMillis: Long,
    public val kind: CrashKind,
    public val threadName: String,
    public val exceptionClass: String,
    public val message: String?,
    public val stackTrace: String,
    public val sections: List<CrashSection> = emptyList(),
) {
    public val headline: String get() = ""
    public fun toReportText(): String = ""
}

public class CrashPluginBuilder internal constructor() {
    public var captureCrashes: Boolean = false
    public var captureAnrs: Boolean = false
    public var anrThreshold: Duration = 5.seconds
    public fun onCrash(listener: (CrashRecord) -> Unit): Unit = Unit
}

/**
 * Installs no handlers and starts no watchdog thread.
 *
 * Worth stating plainly: the real plugin replaces the process-wide uncaught
 * exception handler and runs a background polling thread for the life of the
 * app. Neither belongs in a shipped build.
 */
public class CrashPlugin(
    configure: CrashPluginBuilder.() -> Unit = {},
) : ScryPlugin {
    override val id: String = PLUGIN_ID
    override val displayName: String = "Crashes & ANRs"
    override val badge: StateFlow<String?> = MutableStateFlow(null)

    public val records: StateFlow<List<CrashRecord>> = MutableStateFlow(emptyList())

    override fun onInstall(scope: ScryScope): Unit = Unit
    override fun onClear(): Unit = Unit
    public fun recordHandled(throwable: Throwable, threadName: String = "unknown"): Unit = Unit

    public companion object {
        public const val PLUGIN_ID: String = "scry.crash"
    }
}
