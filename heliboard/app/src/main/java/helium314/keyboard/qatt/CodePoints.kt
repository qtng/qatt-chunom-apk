// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt

/** The code points of a string (String.codePoints() needs API 24, the app supports API 21). */
internal fun String.codePointList(): List<Int> {
    val out = ArrayList<Int>(length)
    var i = 0
    while (i < length) { val cp = codePointAt(i); out.add(cp); i += Character.charCount(cp) }
    return out
}
