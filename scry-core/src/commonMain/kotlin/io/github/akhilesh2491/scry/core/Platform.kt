package io.github.akhilesh2491.scry.core

/** Wall-clock time in epoch milliseconds. */
internal expect fun currentTimeMillis(): Long

/**
 * Minimal mutual-exclusion primitive.
 *
 * [ScryStore] holds a single SQLite connection, which is not thread-safe, and it
 * is written to from whatever thread made a network call. A plain lock is used
 * rather than a coroutine `Mutex` so store operations stay non-suspending and
 * callable from interceptors that are not in a coroutine at all.
 */
internal expect class ScryLock() {
    fun <T> withLock(block: () -> T): T
}
