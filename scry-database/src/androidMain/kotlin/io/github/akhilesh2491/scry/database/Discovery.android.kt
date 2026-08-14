package io.github.akhilesh2491.scry.database

import io.github.akhilesh2491.scry.core.PlatformContext
import java.io.File

/**
 * Finds the app's SQLite files under `databases/`.
 *
 * Filters out the `-wal`, `-shm` and `-journal` sidecars: they are part of the
 * database, not separate ones, and opening a WAL file as a database produces a
 * confusing error rather than anything useful.
 */
internal actual fun discoverDatabases(
    context: PlatformContext,
    allowWrites: Boolean,
): List<SqlDatabase> {
    val dir = File(context.androidContext.applicationInfo.dataDir, "databases")
    val files = dir.listFiles { f ->
        f.isFile && SIDECAR_SUFFIXES.none { suffix -> f.name.endsWith(suffix) }
    } ?: return emptyList()

    return files.sortedBy { it.name }.mapNotNull { file ->
        runCatching {
            DriverSqlDatabase.openFile(
                name = file.name,
                path = file.absolutePath,
                writable = allowWrites,
            )
        }.getOrNull()
    }
}

private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")
