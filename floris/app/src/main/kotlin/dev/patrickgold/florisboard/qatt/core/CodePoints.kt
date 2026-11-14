// SPDX-License-Identifier: Apache-2.0

package dev.patrickgold.florisboard.qatt.core

/** The code points of a string (String.codePoints() needs API 24, the app supports API 21). */
internal fun String.codePointList(): List<Int> {
    val out = ArrayList<Int>(length)
    var i = 0
    while (i < length) { val cp = codePointAt(i); out.add(cp); i += Character.charCount(cp) }
    return out
}
