package io.github.akhilesh2491.scry.database

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.PlatformContext
import io.github.akhilesh2491.scry.core.ScryScope
import io.github.akhilesh2491.scry.ui.ScryUiPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Builder for [DatabasePlugin]. */
public class DatabasePluginBuilder internal constructor() {
    internal val providers: MutableList<DatabaseProvider> = mutableListOf()
    internal var autoDiscover: Boolean = true
    internal var allowFileModeWrites: Boolean = false

    /** Whether to find the app's database files automatically. On by default. */
    public fun autoDiscover(enabled: Boolean) {
        autoDiscover = enabled
    }

    /**
     * Allows writes to auto-discovered database files.
     *
     * Off by default. Scry opens discovered files on its own connection, and a
     * second writer on a non-WAL database can block the app. Prefer passing the
     * app's own connection via [database] instead of turning this on.
     */
    public fun allowFileModeWrites(allow: Boolean) {
        allowFileModeWrites = allow
    }

    /**
     * Registers a database Scry should use directly — instance mode.
     *
     * The recommended path: no lock contention, no stale reads, and encrypted
     * databases work because the app already holds the key.
     */
    public fun database(database: SqlDatabase) {
        providers += DatabaseProvider { listOf(database) }
    }

    public fun provider(provider: DatabaseProvider) {
        providers += provider
    }
}

/**
 * Browses and edits the app's SQLite databases.
 *
 * ```kotlin
 * plugin(DatabasePlugin())                       // discover files, read-only
 * plugin(DatabasePlugin { database(myDb) })      // instance mode, writable
 * ```
 */
public class DatabasePlugin(
    configure: DatabasePluginBuilder.() -> Unit = {},
) : ScryUiPlugin {

    private val builder = DatabasePluginBuilder().apply(configure)

    override val id: String = PLUGIN_ID
    override val displayName: String = "Database"

    private var context: PlatformContext? = null

    private val _databases = MutableStateFlow<List<SqlDatabase>>(emptyList())
    public val databases: StateFlow<List<SqlDatabase>> = _databases.asStateFlow()

    private val _badge = MutableStateFlow<String?>(null)
    override val badge: StateFlow<String?> = _badge.asStateFlow()

    override fun onInstall(scope: ScryScope) {
        context = scope.context
        refresh()
    }

    /**
     * Re-scans for database files.
     *
     * Lazy for the same reason preferences discovery is: Room and SQLDelight
     * create the file on first access, which is routinely long after
     * `Scry.install` has run.
     */
    public fun refresh() {
        // Close only what we opened; registered instances belong to the app.
        _databases.value.filterIsInstance<DriverSqlDatabase>().forEach { it.close() }

        val discovered = context
            ?.takeIf { builder.autoDiscover }
            ?.let { discoverDatabases(it, builder.allowFileModeWrites) }
            .orEmpty()
        val registered = builder.providers.flatMap { it.databases() }

        val byName = LinkedHashMap<String, SqlDatabase>()
        discovered.forEach { byName[it.name] = it }
        // Instance mode wins: it can write, and it sees uncommitted state.
        registered.forEach { byName[it.name] = it }

        _databases.value = byName.values.toList()
        _badge.value = byName.size.takeIf { it > 0 }?.toString()
    }

    /** Scry's "clear all" never touches the host app's databases. */
    override fun onClear(): Unit = Unit

    @Composable
    override fun Content() {
        DatabaseScreen(plugin = this)
    }

    public companion object {
        public const val PLUGIN_ID: String = "scry.database"
    }
}

internal expect fun discoverDatabases(
    context: PlatformContext,
    allowWrites: Boolean,
): List<SqlDatabase>
