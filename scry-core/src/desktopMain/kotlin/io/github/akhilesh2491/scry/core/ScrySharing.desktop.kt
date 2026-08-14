package io.github.akhilesh2491.scry.core

import java.awt.Desktop
import java.io.File

/** Writes the export beside Scry's storage and opens it in the default application. */
public actual fun shareScryFile(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean = runCatching {
    val dir = File(context.scryStorageDirectory(), "reports").apply { mkdirs() }
    val file = File(dir, fileName).apply { writeText(text) }
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(file)
    }
    true
}.getOrDefault(false)
