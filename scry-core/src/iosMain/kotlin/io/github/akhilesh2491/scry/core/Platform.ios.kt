@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.akhilesh2491.scry.core

import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

internal actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * A pthread mutex.
 *
 * Kotlin/Native has no lock in the common stdlib, and the alternative —
 * atomicfu's `SynchronizedObject` — would add a dependency to every consumer.
 * A real lock is required rather than assuming single-threaded access: the store
 * is written from whatever thread made the network call.
 *
 * The mutex is never destroyed. Locks live as long as the Scry instance that
 * owns them, so freeing would only ever happen at process exit; tracking that
 * with a cleaner would cost more than the single allocation it reclaims.
 */
internal actual class ScryLock actual constructor() {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().also {
        pthread_mutex_init(it.ptr, null)
    }

    actual fun <T> withLock(block: () -> T): T {
        pthread_mutex_lock(mutex.ptr)
        try {
            return block()
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }
}
