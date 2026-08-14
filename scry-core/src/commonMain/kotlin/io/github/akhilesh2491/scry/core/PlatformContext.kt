package io.github.akhilesh2491.scry.core

/**
 * Platform handle Scry is installed against.
 *
 * `android.content.Context` on Android; a lightweight holder on desktop. Kept as
 * an `expect class` rather than an interface so the Android actual can be a
 * typealias — app developers pass their existing `Context` with no wrapping.
 */
public expect class PlatformContext

/** Absolute path to the directory Scry owns for its own files. */
internal expect fun PlatformContext.scryStorageDirectory(): String

/**
 * Whether the host application is debuggable.
 *
 * Drives the release-build guard in [Scry.install]. Desktop has no equivalent
 * notion, so it reports `true`.
 */
internal expect fun PlatformContext.isDebuggableBuild(): Boolean

/** Best-effort application identifier, used to namespace desktop storage. */
internal expect fun PlatformContext.applicationId(): String
