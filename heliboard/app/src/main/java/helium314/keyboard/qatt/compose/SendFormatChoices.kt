// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

/**
 * The choice of the user (Text, or Image in a style), per app (`EditorInfo.packageName`): a tap on Text or Image holds for that app until the user changes it;
 * an app without a choice gets the start value of [SendFormatPolicy]. Pure logic: [serialize] / [deserialize] turn it into one string for the settings.
 * At most [limit] apps are kept (the longest unused go first).
 */
class SendFormatChoices(private val limit: Int = 200) {
    private val choices = LinkedHashMap<String, SendChoice>()    // in the order of use, the last one is the most recent

    val size get() = choices.size
    val apps: List<String> get() = choices.keys.toList()

    operator fun get(packageName: String?): SendChoice? = packageName?.let { choices[it] }

    operator fun set(packageName: String?, choice: SendChoice) {
        if (packageName.isNullOrEmpty()) return
        choices.remove(packageName)
        choices[packageName] = choice
        while (choices.size > limit) choices.remove(choices.keys.first())
    }

    /** The app has no choice again. */
    fun forget(packageName: String?) { choices.remove(packageName) }

    fun clear() = choices.clear()

    /** "package=TEXT" or "package=IMAGE:WHITE_ON_BLACK" per line. */
    fun serialize(): String = choices.entries.joinToString("\n") { (k, v) -> "$k=" + if (v.format == SendFormat.TEXT) "TEXT" else "IMAGE:${v.style.name}" }

    companion object {
        fun deserialize(s: String?, limit: Int = 200): SendFormatChoices {
            val c = SendFormatChoices(limit)
            for (line in s.orEmpty().lines()) {
                val i = line.lastIndexOf('=')
                if (i <= 0) continue
                val value = line.substring(i + 1).split(':')
                val format = SendFormat.values().firstOrNull { it.name == value[0] } ?: continue
                val style = value.getOrNull(1)?.let { n -> ImageStyle.values().firstOrNull { it.name == n } } ?: ImageStyle.BLACK_ON_WHITE
                c[line.substring(0, i)] = SendChoice(format, style)
            }
            return c
        }
    }
}
