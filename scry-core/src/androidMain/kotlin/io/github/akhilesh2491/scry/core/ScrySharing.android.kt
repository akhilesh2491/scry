package io.github.akhilesh2491.scry.core

import android.content.ClipData
import android.content.Intent
import android.util.Log
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
    val mimeType = mimeTypeFor(fileName)

    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_TITLE, fileName)
        // The platform normally migrates EXTRA_STREAM into ClipData on its way
        // out of the process so the read grant attaches. Setting it explicitly
        // removes the dependence on that migration surviving the chooser wrap.
        clipData = ClipData.newRawUri(fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    android.startActivity(
        Intent.createChooser(send, "Share $fileName").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )
    true
}.onFailure {
    // Logged, not swallowed. The reasons an export fails here — a missing
    // FileProvider path, no app willing to receive the type — are invisible from
    // the UI, and a silent `false` sent the last one undiagnosed for a release.
    Log.w("Scry", "Could not share $fileName", it)
}.getOrDefault(false)

/**
 * MIME type by extension.
 *
 * Everything Scry exports is text, but declaring the specific type is what makes
 * a JSON viewer or a spreadsheet app appear in the chooser at all — `text/plain`
 * for a `.csv` offers you a notes app.
 */
private fun mimeTypeFor(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "json", "har" -> "application/json"
        "csv" -> "text/csv"
        "sh" -> "text/x-shellscript"
        else -> "text/plain"
    }
