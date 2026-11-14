// SPDX-License-Identifier: Apache-2.0

package dev.patrickgold.florisboard.qatt

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.FlorisApplication

/** A language of the keyboard: the layout file (assets/extensions/org.florisboard.k3.foundation/keyboard/), and whether Telex is used. */
data class QattLanguage(val code: String, val file: String, val telex: Boolean = false)

/** The languages of the keyboard; a tap on the language button goes to the next one. The choice is kept. */
object QattLanguages {
    val all = listOf(
        QattLanguage("DE", "qwertz.xml"),
        QattLanguage("EN", "qwerty.xml"),
        QattLanguage("VI", "qwerty_vi.xml", telex = true),
        QattLanguage("FR", "azerty.xml"),
        QattLanguage("ES", "spanish.xml"),
    )

    private fun prefs() = FlorisApplication.getHack().getSharedPreferences("qatt", Context.MODE_PRIVATE)

    var current by mutableStateOf(
        runCatching { all.firstOrNull { it.code == prefs().getString("lang", null) } }.getOrNull() ?: all[0]
    )
        private set

    /** The next language (the globe key next to the comma). Not kept while the composition field is open: it closes with the language it opened with. */
    fun next() {
        current = all[(all.indexOf(current) + 1) % all.size]
        if (!qattCompose.open) runCatching { prefs().edit().putString("lang", current.code).apply() }
        reload()
    }

    /** Switches to [language] without keeping it (the composition field uses Vietnamese while it is open). */
    fun use(language: QattLanguage) {
        if (current == language) return
        current = language
        reload()
    }

    val vietnamese get() = all.first { it.telex }

    private fun reload() { runCatching { FlorisApplication.getHack().imeController.reloadKeyboard() } }
}
