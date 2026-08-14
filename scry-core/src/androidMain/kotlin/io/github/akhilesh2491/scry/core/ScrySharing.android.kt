package io.github.akhilesh2491.scry.core

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * A [FileProvider] scoped to Scry.
 *
 * A dedicated subclass so the authority can be derived from the host app id
 * without colliding with a provider the app already declares — two providers
 * sharing an authority is an install-time failure, which would be a spectacular
 * way for a debugging library to break someone's app.
 */
public class ScryFileProvider : FileProvider(R.xml.scry_file_paths)

/**
 * Writes the export to app-private cache and opens the share sheet.
 *
 * A file rather than `EXTRA_TEXT`: a HAR or a crash report carrying network
 * bodies runs to hundreds of kilobytes, and a large intent extra throws
 * `TransactionTooLargeException`.
 */
public actual fun shareScryFile(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean = runCatching {
    val android = context.androidContext
    val dir = File(android.cacheDir, "scry-reports").apply { mkdirs() }
    val file = File(dir, fileName).apply { writeText(text) }

    val uri = FileProvider.getUriForFile(
        android,
        "${android.packageName}.scry.fileprovider",
        file,
    )

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    android.startActivity(
        Intent.createChooser(send, "Share $fileName").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    true
}.getOrDefault(false)
