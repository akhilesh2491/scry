package io.github.akhilesh2491.scry.network

import kotlin.random.Random

/**
 * Helpers every capture adapter needs.
 *
 * Public because adapters live in separate modules (`scry-network-ktor`,
 * `scry-network-okhttp`, and the iOS adapter in M2) and must produce ids and
 * timestamps in the same shape as each other.
 */

/** Wall-clock time in epoch milliseconds. */
public expect fun nowMillis(): Long

/**
 * Id for one captured exchange.
 *
 * Time plus randomness rather than a UUID: this runs on every request, there is
 * no multiplatform UUID in the stdlib worth the dependency, and a collision only
 * costs one overwritten row in a debug tool.
 */
public fun newTransactionId(): String = "${nowMillis()}-${Random.nextInt(0, 1_000_000)}"

/** A captured body, possibly shortened to fit the configured cap. */
public class CapturedBody(
    public val text: String,
    public val truncated: Boolean,
    public val originalSize: Long,
)

/** Shortens [this] to [maxBytes] characters, flagging that it happened. */
public fun String.truncateForCapture(maxBytes: Int): CapturedBody =
    if (length <= maxBytes) {
        CapturedBody(this, truncated = false, originalSize = length.toLong())
    } else {
        CapturedBody(substring(0, maxBytes), truncated = true, originalSize = length.toLong())
    }
