package io.github.akhilesh2491.scry.network

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

public actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
