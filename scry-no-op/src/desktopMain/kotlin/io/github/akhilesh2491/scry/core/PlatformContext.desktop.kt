@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.core

public actual class PlatformContext @JvmOverloads public constructor(
    public val applicationId: String = DEFAULT_APPLICATION_ID,
    public val storageRoot: String = defaultStorageRoot(),
) {
    public companion object {
        public const val DEFAULT_APPLICATION_ID: String = "scry-desktop-app"
        public fun defaultStorageRoot(): String = ""
    }
}
