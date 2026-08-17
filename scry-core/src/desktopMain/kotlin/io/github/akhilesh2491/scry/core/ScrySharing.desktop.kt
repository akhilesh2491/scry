package io.github.akhilesh2491.scry.core

import java.awt.Desktop
import java.io.File

/**
 * Writes the export beside Scry's storage and shows it to the user.
 *
 * Desktop has no share sheet, so "sharing" means putting the file in front of
 * someone. Opening the file itself is the best outcome but the least reliable
 * one: `.har`, `.json` and `.sh` frequently have no registered handler, and
 * `Desktop.open` then throws. Falling back to revealing the file — and finally
 * to opening its folder — means the export is always findable, instead of the
 * button appearing to do nothing.
 */
public actual fun shareScryFile(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean = runCatching {
    val dir = File(context.scryStorageDirectory(), "reports").apply { mkdirs() }
    val file = File(dir, fileName).apply { writeText(text) }
    reveal(file)
    true
}.onFailure {
    // Logged, not swallowed: a silent `false` is indistinguishable from a button
    // that was never wired up, which is how the last failure here went unnoticed.
    System.err.println("Scry: could not share $fileName — $it")
}.getOrDefault(false)

/** Opens [file], else selects it in the file manager, else opens its folder. */
private fun reveal(file: File) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()

    val attempts = listOf<Pair<Desktop.Action, () -> Unit>>(
        Desktop.Action.OPEN to { desktop.open(file) },
        Desktop.Action.BROWSE_FILE_DIR to { desktop.browseFileDirectory(file) },
        Desktop.Action.OPEN to { desktop.open(file.parentFile) },
    )

    for ((action, attempt) in attempts) {
        if (!desktop.isSupported(action)) continue
        if (runCatching(attempt).isSuccess) return
    }
}
