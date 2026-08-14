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
    }
}
