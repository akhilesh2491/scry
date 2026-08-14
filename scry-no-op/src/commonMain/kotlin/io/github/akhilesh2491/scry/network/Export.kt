@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.network

/** Inert mirrors of `scry-network`'s exporters. File name matches for the facade. */

public fun NetworkTransaction.toCurl(): String = ""

public fun List<NetworkTransaction>.toHar(creatorVersion: String = "0.1.0"): String = ""
