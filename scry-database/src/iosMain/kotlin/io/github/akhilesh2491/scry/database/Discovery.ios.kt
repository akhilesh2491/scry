@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.akhilesh2491.scry.database

import io.github.akhilesh2491.scry.core.PlatformContext
import platform.Foundation.NSFileManager

/**
 * Scans the app's Documents directory for SQLite files.
 *
 * Covers the common case — Room and SQLDelight both default to Documents on iOS
 * — but not databases placed elsewhere. Those should be registered explicitly
 * with `DatabasePlugin { database(...) }`, which is the recommended mode anyway.
 */
internal actual fun discoverDatabases(
    context: PlatformContext,
    allowWrites: Boolean,
): List<SqlDatabase> {
    val root = context.storageRoot
    val names = NSFileManager.defaultManager
        .contentsOfDirectoryAtPath(root, null)
        ?.filterIsInstance<String>()
        .orEmpty()

    return names
        .filter { name -> EXTENSIONS.any { name.endsWith(it) } }
        .filterNot { name -> SIDECARS.any { name.endsWith(it) } }
        .sorted()
        .mapNotNull { name ->
            runCatching {
                DriverSqlDatabase.openFile(name, "$root/$name", allowWrites)
            }.getOrNull()
        }
}

private val EXTENSIONS = listOf(".db", ".sqlite", ".sqlite3")
private val SIDECARS = listOf("-wal", "-shm", "-journal")
