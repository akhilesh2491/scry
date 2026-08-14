@file:Suppress("UNUSED_PARAMETER")

package io.github.akhilesh2491.scry.core

/** Inert mirror of the real iOS [PlatformContext]; same shape, no bundle lookups. */
public actual class PlatformContext(
    public val applicationId: String = DEFAULT_APPLICATION_ID,
    public val storageRoot: String = "",
) {
    public companion object {
        public const val DEFAULT_APPLICATION_ID: String = "scry-ios-app"
        public fun defaultApplicationId(): String = DEFAULT_APPLICATION_ID
        public fun defaultStorageRoot(): String = ""
    }
}
