package io.github.akhilesh2491.scry.prefs

import io.github.akhilesh2491.scry.core.PlatformContext
import java.util.prefs.Preferences

/**
 * Desktop backing store: a `java.util.prefs` node under the application id.
 *
 * Everything is read back as a String because `java.util.prefs` does not record
 * the type it was written with. Rather than guess — and risk turning the string
 * `"1"` into an Int on a round trip — values are surfaced as strings and written
 * back as strings.
 */
internal class JavaPreferencesStore(
    override val name: String,
    private val node: Preferences,
) : KeyValueStore {

    override fun keys(): List<String> = node.keys().sorted()

    override fun read(key: String): PreferenceValue? =
        node.get(key, null)?.let { PreferenceValue.StringValue(it) }

    override fun write(key: String, value: PreferenceValue) {
        node.put(key, value.display)
        node.flush()
    }

    override fun remove(key: String) {
        node.remove(key)
        node.flush()
    }

    override fun clear() {
        node.clear()
        node.flush()
    }
}

internal actual fun discoverStores(context: PlatformContext): List<KeyValueStore> = listOf(
    JavaPreferencesStore(
        name = context.applicationId,
        node = Preferences.userRoot().node(context.applicationId),
    ),
)
