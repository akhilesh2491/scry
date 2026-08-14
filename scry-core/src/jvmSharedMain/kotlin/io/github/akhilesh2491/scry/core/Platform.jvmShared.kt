package io.github.akhilesh2491.scry.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual class ScryLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
