@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.perf

/**
 * Exports nothing. File name matches the real one so the JVM facade class —
 * `PerfExportKt`, which is what Java callers name — is identical.
 */

public fun PerfSession.toJson(): String = ""

public fun List<PerfSession>.toJson(): String = ""

public fun List<PerfSession>.toCsv(): String = ""
