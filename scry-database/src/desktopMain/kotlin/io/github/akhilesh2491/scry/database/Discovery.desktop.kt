package io.github.akhilesh2491.scry.database

import io.github.akhilesh2491.scry.core.PlatformContext
import java.io.File

/**
 * Scans the desktop storage root for SQLite files.
 *
 * Desktop apps put databases wherever they like, so this only covers Scry's own
 * storage directory. Anything else should be registered explicitly with
 * `DatabasePlugin { database(...) }` — guessing at paths across the filesystem
 * would be both unreliable and intrusive.
 */
internal actual fun discoverDatabases(
    context: PlatformContext,
    allowWrites: Boolean,
): List<SqlDatabase> {
    val root = File(context.storageRoot, context.applicationId)
    val files = root.listFiles { f ->
        f.isFile && EXTENSIONS.any { f.name.endsWith(it) } &&
            SIDECARS.none { s -> f.name.endsWith(s) }
    } ?: return emptyList()

    return files.sortedBy { it.name }.mapNotNull { file ->
        runCatching {
            DriverSqlDatabase.openFile(file.name, file.absolutePath, allowWrites)
        }.getOrNull()
    }
}

private val EXTENSIONS = listOf(".db", ".sqlite", ".sqlite3")
private val SIDECARS = listOf("-wal", "-shm", "-journal")
