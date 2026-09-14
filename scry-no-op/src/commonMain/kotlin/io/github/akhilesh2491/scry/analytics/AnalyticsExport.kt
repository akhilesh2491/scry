@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.analytics

/**
 * Exports nothing. File name matches the real one so the JVM facade class —
 * `AnalyticsExportKt`, which is what Java callers name — is identical.
 */

public fun List<ScreenVisit>.toJson(): String = ""

public fun List<ScreenVisit>.toCsv(): String = ""
