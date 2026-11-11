// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

/** What a big font file must look like to be imported. */
object FontFile {
    /** a font is at least this large (the big fonts are 27 to 42 MB); anything smaller is not one */
    const val MIN_SIZE = 1_000_000L

    /** Is this a TrueType or OpenType file (the first four bytes)? */
    fun looksLikeFont(header: ByteArray): Boolean = header.size >= 4 && (
        header.take(4) == listOf<Byte>(0, 1, 0, 0) ||
        String(header, 0, 4, Charsets.ISO_8859_1).let { it == "OTTO" || it == "true" })
}
