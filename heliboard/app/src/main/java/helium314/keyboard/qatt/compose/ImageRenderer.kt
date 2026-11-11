// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import helium314.keyboard.qatt.PaText
import helium314.keyboard.qatt.QattText
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * Draws the text of the field as an image (the colours of an [ImageStyle]) and gives it a content address for `commitContent`.
 *
 * Apps crop or shrink such images (Messenger and the Claude app show a square, other apps the full width), so the text is set as a block in an imaginary square:
 * at most [PER_LINE] characters a line (a QATT or PA cell counts as one), the block centred in a square, and the square centred on a canvas with the aspect ratio
 * [ASPECT] : 1. A square crop then shows the whole text; an app that shows the full width does not get a huge image.
 */
object ImageRenderer {
    private const val TEXT_SIZE = 72f          // pixels
    private const val PER_LINE = 5
    private const val ASPECT = 2.5f
    private const val PADDING = 12
    private const val FOLDER = "compose"

    /** The cells of [text]: a QATT or PA cell with its joiners and tone marks, else one character. */
    private fun cells(text: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val q = QattText.CELL_ANY.find(text, i)?.takeIf { it.range.first == i }?.value?.length ?: 0
            val p = PaText.CELL_ANY.find(text, i)?.takeIf { it.range.first == i }?.value?.length ?: 0
            val n = maxOf(q, p, Character.charCount(text.codePointAt(i)))
            out.add(text.substring(i, i + n))
            i += n
        }
        return out
    }

    /** The lines: at most [PER_LINE] cells each, broken between words where it can be, else inside a long word. */
    fun wrap(text: String): List<String> {
        val lines = ArrayList<String>()
        for (paragraph in text.trimEnd().split('\n')) {
            var line = ArrayList<String>()
            fun flush() { lines.add(line.joinToString("")); line = ArrayList() }
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                val w = cells(word)
                if (w.size > PER_LINE) {                      // a long word: start on a new line, then chunks
                    if (line.isNotEmpty()) flush()
                    w.chunked(PER_LINE).forEach { chunk -> line = ArrayList(chunk); flush() }
                    continue
                }
                if (line.isNotEmpty() && line.size + 1 + w.size > PER_LINE) flush()
                if (line.isNotEmpty()) line.add(" ")
                line.addAll(w)
            }
            flush()
        }
        return lines
    }

    fun render(fonts: ComposeFonts, text: CharSequence, style: ImageStyle): Bitmap {
        val wrapped = wrap(text.toString()).joinToString("\n")
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TEXT_SIZE; color = style.foreground }
        val styled = fonts.styled(wrapped)
        @Suppress("DEPRECATION")
        val wide = StaticLayout(styled, paint, 10_000, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        var widest = 0f
        for (i in 0 until wide.lineCount) widest = maxOf(widest, wide.getLineWidth(i))
        val blockWidth = ceil(widest).toInt().coerceAtLeast(1)
        @Suppress("DEPRECATION")
        val layout = StaticLayout(styled, paint, blockWidth, Layout.Alignment.ALIGN_CENTER, 1.15f, 0f, false)
        val side = maxOf(blockWidth, layout.height.coerceAtLeast(1)) + 2 * PADDING          // the imaginary square
        val width = ceil(side * ASPECT).toInt()
        val height = side
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val end = style.backgroundEnd
        if (end == null) canvas.drawColor(style.background)
        else canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), style.background, end, Shader.TileMode.CLAMP)   // top left to bottom right
        })
        canvas.translate((width - blockWidth) / 2f, (height - layout.height) / 2f)
        layout.draw(canvas)
        return bitmap
    }

    /** Writes the image into the cache and returns its address (the provider of the manifest); images older than an hour are deleted. */
    fun save(context: Context, bitmap: Bitmap, authority: String): Uri {
        val dir = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > 60 * 60 * 1000) it.delete() }
        val file = File(dir, "qatt-$now.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, authority, file)
    }
}
