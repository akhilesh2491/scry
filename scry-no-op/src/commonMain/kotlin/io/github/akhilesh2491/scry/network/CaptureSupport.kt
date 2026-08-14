package io.github.akhilesh2491.scry.network

/** Mirrors `CaptureSupport.kt` so the `CaptureSupportKt` facade matches. */

public class CapturedBody(
    public val text: String,
    public val truncated: Boolean,
    public val originalSize: Long,
)

public expect fun nowMillis(): Long

public fun newTransactionId(): String = ""

public fun String.truncateForCapture(maxBytes: Int): CapturedBody =
    CapturedBody(this, truncated = false, originalSize = 0L)
