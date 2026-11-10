// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

/**
 * The choice of Text or Image of the user, per app (`EditorInfo.packageName`): a tap on Text or Image holds for that app until the user changes it;
 * an app without a choice gets the start value of [SendFormatPolicy]. Pure logic: [serialize] / [deserialize] turn it into one string for the settings.
 * At most [limit] apps are kept (the longest unused go first).
 */
class SendFormatChoices(private val limit: Int = 200) {
    private val choices = LinkedHashMap<String, SendFormat>()    // in the order of use, the last one is the most recent

    val size get() = choices.size
    val apps: List<String> get() = choices.keys.toList()

    operator fun get(packageName: String?): SendFormat? = packageName?.let { choices[it] }

    operator fun set(packageName: String?, format: SendFormat) {
        if (packageName.isNullOrEmpty()) return
        choices.remove(packageName)
        choices[packageName] = format
        while (choices.size > limit) choices.remove(choices.keys.first())
    }

    /** The app has no choice again. */
    fun forget(packageName: String?) { choices.remove(packageName) }

    fun clear() = choices.clear()

    /** "package=TEXT" per line. */
    fun serialize(): String = choices.entries.joinToString("\n") { "${it.key}=${it.value.name}" }

    companion object {
        fun deserialize(s: String?, limit: Int = 200): SendFormatChoices {
            val c = SendFormatChoices(limit)
            for (line in s.orEmpty().lines()) {
                val i = line.lastIndexOf('=')
                if (i <= 0) continue
                val format = SendFormat.values().firstOrNull { it.name == line.substring(i + 1) } ?: continue
                c[line.substring(0, i)] = format
            }
            return c
        }
    }
}
