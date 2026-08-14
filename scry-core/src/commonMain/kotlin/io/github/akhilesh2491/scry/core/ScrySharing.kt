package io.github.akhilesh2491.scry.core

/**
 * Writes [text] to a Scry-owned file and hands it to the platform's share flow.
 *
 * Lives in core because more than one plugin needs it — crash reports and HAR
 * exports are the same operation with different payloads — and because the
 * Android implementation carries a `FileProvider`, a manifest entry and a
 * resource file that should exist exactly once.
 *
 * Returns false if sharing was unavailable or failed. Never throws: an export
 * that crashes the app is worse than an export that does not happen.
 */
public expect fun shareScryFile(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean
