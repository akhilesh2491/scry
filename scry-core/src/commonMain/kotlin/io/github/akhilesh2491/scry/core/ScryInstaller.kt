package io.github.akhilesh2491.scry.core

/**
 * Java-friendly facade over the `Scry.install { }` DSL.
 *
 * ```java
 * Scry.installer(context)
 *     .retention(Retention.ofHours(12))
 *     .allowInReleaseBuilds(false)
 *     .addPlugin(new NetworkPlugin())
 *     .install();
 * ```
 *
 * Kotlin's trailing-lambda DSL is unusable from Java, and a meaningful share of
 * Android codebases still have Java in them. A KMP library that only Kotlin can
 * call gives up that audience for no reason — so the builder wraps the same
 * [ScryConfigBuilder] the DSL drives rather than duplicating any logic.
 */
public class ScryInstaller internal constructor(private val context: PlatformContext) {

    private var retention: Retention = Retention.DEFAULT
    private var allowInReleaseBuilds: Boolean = false
    private val plugins = mutableListOf<ScryPlugin>()
    private val extraRedactedHeaders = mutableListOf<String>()
    private val extraRedactedBodyKeys = mutableListOf<String>()
    private val launchers = LauncherConfigBuilder()

    public fun retention(retention: Retention): ScryInstaller = apply {
        this.retention = retention
    }

    public fun addPlugin(plugin: ScryPlugin): ScryInstaller = apply {
        plugins += plugin
    }

    /** See [ScryConfigBuilder.allowInReleaseBuilds]. */
    public fun allowInReleaseBuilds(allow: Boolean): ScryInstaller = apply {
        this.allowInReleaseBuilds = allow
    }

    /** Adds header names to mask, on top of [Redactor.DEFAULT_HEADERS]. */
    public fun redactHeaders(vararg names: String): ScryInstaller = apply {
        extraRedactedHeaders += names
    }

    /** Adds body keys to mask, on top of [Redactor.DEFAULT_BODY_KEYS]. */
    public fun redactBodyKeys(vararg keys: String): ScryInstaller = apply {
        extraRedactedBodyKeys += keys
    }

    /** See [LauncherConfig.bubble]. On by default. */
    public fun bubble(enabled: Boolean): ScryInstaller = apply {
        launchers.bubble = enabled
    }

    /** See [LauncherConfig.notification]. On by default. */
    public fun notification(enabled: Boolean): ScryInstaller = apply {
        launchers.notification = enabled
    }

    /** See [LauncherConfig.appShortcut]. On by default. */
    public fun appShortcut(enabled: Boolean): ScryInstaller = apply {
        launchers.appShortcut = enabled
    }

    /** See [LauncherConfig.launcherIcon] — read it before turning this on. */
    public fun launcherIcon(enabled: Boolean): ScryInstaller = apply {
        launchers.launcherIcon = enabled
    }

    /** See [LauncherConfig.shake]. Off by default. */
    public fun shake(enabled: Boolean): ScryInstaller = apply {
        launchers.shake = enabled
    }

    /** See [LauncherConfig.bubbleCorner]. */
    public fun bubbleCorner(corner: BubbleCorner): ScryInstaller = apply {
        launchers.bubbleCorner = corner
    }

    /**
     * Installs Scry.
     *
     * Returns null when refused because the build is not debuggable and
     * [allowInReleaseBuilds] was not set — same contract as [Scry.install].
     */
    public fun install(): ScryInstance? = Scry.install(context) {
        retention = this@ScryInstaller.retention
        allowInReleaseBuilds = this@ScryInstaller.allowInReleaseBuilds
        this@ScryInstaller.plugins.forEach { plugin(it) }
        redaction {
            redactHeaders(*this@ScryInstaller.extraRedactedHeaders.toTypedArray())
            redactBodyKeys(*this@ScryInstaller.extraRedactedBodyKeys.toTypedArray())
        }
        launchers {
            bubble = this@ScryInstaller.launchers.bubble
            notification = this@ScryInstaller.launchers.notification
            appShortcut = this@ScryInstaller.launchers.appShortcut
            launcherIcon = this@ScryInstaller.launchers.launcherIcon
            shake = this@ScryInstaller.launchers.shake
            bubbleCorner = this@ScryInstaller.launchers.bubbleCorner
        }
    }
}
