package io.github.akhilesh2491.scry.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Marker for anything published on the [ScryEventBus].
 *
 * Plugins define their own event types. Cross-plugin correlation is the point:
 * the crash plugin attaching the last N network calls to a bug report only works
 * if it can observe events it did not produce.
 */
public interface ScryEvent {
    /** Wall-clock time the event occurred, epoch millis. */
    public val timestampMillis: Long
}

/**
 * In-memory pub/sub between plugins.
 *
 * Buffered and non-blocking: a slow subscriber drops events rather than
 * suspending the app's threads. Scry must never be the reason an app stutters,
 * so back-pressure onto the publisher is not an option here.
 */
public class ScryEventBus internal constructor(
    replayCapacity: Int = 0,
    bufferCapacity: Int = 256,
) {
    private val flow = MutableSharedFlow<ScryEvent>(
        replay = replayCapacity,
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** All events, as they are published. */
    public val events: Flow<ScryEvent> = flow.asSharedFlow()

    /** Publishes without suspending. Returns false if the event was dropped. */
    public fun publish(event: ScryEvent): Boolean = flow.tryEmit(event)

    /** Events of a single type. */
    public inline fun <reified T : ScryEvent> eventsOfType(): Flow<T> =
        events.filterIsInstance<T>()
}
