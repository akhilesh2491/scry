@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.crash

import io.github.akhilesh2491.scry.core.PlatformContext

/** File name matches the real module so the `CrashPluginKt` facade lines up. */
public fun shareBugReport(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean = false
