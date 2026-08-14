package io.github.akhilesh2491.scry.core

/**
 * Mirrors the real module's expect/actual split.
 *
 * A plain common function would land in `ScrySharingKt`, but the real actuals
 * compile to `ScrySharing_desktopKt` / `ScrySharing_androidKt`. Matching the
 * structure, not just the name, is what keeps Java callers source-compatible.
 */
public expect fun shareScryFile(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean
