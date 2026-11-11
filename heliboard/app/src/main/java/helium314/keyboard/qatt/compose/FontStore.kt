// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.Context
import android.net.Uri
import java.io.File

/** The big fonts the user imported (Migoi, Phong Thảo, or a newer QATT Pen) live in the private folder `fonts` of the app; QATT Pen is also in the assets. */
object FontStore {
    fun file(context: Context, font: ComposeFont) = File(File(context.filesDir, "fonts"), font.file)

    /** The imported file if there is one. */
    fun imported(context: Context, font: ComposeFont): File? = file(context, font).takeIf { it.isFile && it.length() >= FontFile.MIN_SIZE }

    fun isInstalled(context: Context, font: ComposeFont) = font.bundled || imported(context, font) != null

    /** Copies the file the user chose as the font [font]. Returns whether it was taken. */
    fun import(context: Context, uri: Uri, font: ComposeFont): Boolean = try {
        val target = file(context, font)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, font.file + ".tmp")
        context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return false
        val header = temp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        if (temp.length() < FontFile.MIN_SIZE || !FontFile.looksLikeFont(header)) { temp.delete(); false }
        else { target.delete(); temp.renameTo(target) }
    } catch (e: Exception) { false }

    fun remove(context: Context, font: ComposeFont) { file(context, font).delete() }
}
