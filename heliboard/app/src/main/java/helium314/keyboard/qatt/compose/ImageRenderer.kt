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
 * Normally ([square] false) the text fills the image: at most [FREE_PER_LINE] characters a line, [FREE_PAD] pixels around it, centred; the image is at least
 * [FREE_ASPECT] times as wide as it is high (it gets wider if the text alone is narrower).
 *
 * Some apps crop such images to a small square (the Claude app; [SQUARE_APPS]); for them ([square] true) the text is set as a block in an imaginary square,
 * centred and scaled so that its widest line fills the width of the square ([PAD_H] at the sides, [PAD_V] above and below). The square is centred on a canvas whose
 * aspect ratio is [ASPECT] : 1 for one line and gets 10 % smaller with every further line; a line holds at most 5 + L characters (a QATT or PA cell counts as one),
 * where L is the number of lines (6, 7, 8, ...). Only if the block is too high for the square, the image becomes higher (the aspect ratio gives way). A square crop
 * then shows the whole text; an app that shows the full width does not get a huge image.
 */
object ImageRenderer {
    private const val BASE_SIZE = 72f          // pixels: the size the text is measured at
    private const val TARGET_SIDE = 480f       // the side of the square a text is scaled to
    private const val MAX_SIZE = 160f          // a short text is not blown up beyond this size (the square gets smaller instead)
    private const val ASPECT = 2.5f            // width : height with one line
    private const val ASPECT_STEP = 0.9f       // every further line: the aspect ratio times this
    private const val MIN_PER_LINE = 6         // characters a line holds when there is one line; every further line adds one
    private const val MAX_LINES = 12
    private const val PAD_H = 2
    private const val PAD_V = 12
    private const val LINE_GAP = 4f            // the distance between two lines is the character height (the text size) plus this, in pixels
    private const val FOLDER = "compose"

    private const val FREE_SIZE = 96f          // pixels: the text size of the normal image
    private const val FREE_PER_LINE = 12
    private const val FREE_PAD = 12
    private const val FREE_ASPECT = 3f

    /** The apps that crop an image to a small square: they get the square layout. More are added when they become known. */
    val SQUARE_APPS = setOf("com.anthropic.claude")

    fun isSquareApp(packageName: String?) = packageName != null && packageName in SQUARE_APPS

    /** The lines: at most [perLine] cells each (see [LineWrap]). */
    fun wrap(text: String, perLine: Int): List<String> = LineWrap.wrap(text, perLine)

    /** The lines and their number L: the smallest L for which the text wraps into at most L lines of at most 5 + L characters. */
    fun lines(text: String, square: Boolean): List<String> {
        if (!square) return wrap(text, FREE_PER_LINE)
        var l = 1
        var lines = wrap(text, MIN_PER_LINE)
        while (lines.size > l && l < MAX_LINES) { l++; lines = wrap(text, MIN_PER_LINE - 1 + l) }
        return lines
    }

    private fun layoutOf(text: CharSequence, size: Float, color: Int, width: Int, align: Layout.Alignment, spacing: Float = 1f): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color }
        @Suppress("DEPRECATION")
        return StaticLayout(text, paint, width, align, spacing, 0f, false)
    }

    private fun widestLine(l: StaticLayout): Float {
        var w = 0f
        for (i in 0 until l.lineCount) w = maxOf(w, l.getLineWidth(i))
        return w
    }

    fun render(fonts: ComposeFonts, text: CharSequence, style: ImageStyle, square: Boolean): Bitmap {
        if (!square) return renderFree(fonts, text, style)
        val textLines = lines(text.toString(), true)
        val styled = fonts.styled(textLines.joinToString("\n"))
        // scale the text so that its widest line fills the square (up to MAX_SIZE)
        val natural = widestLine(layoutOf(styled, BASE_SIZE, style.foreground, 10_000, Layout.Alignment.ALIGN_NORMAL)).coerceAtLeast(1f)
        val size = BASE_SIZE * minOf((TARGET_SIDE - 2 * PAD_H) / natural, MAX_SIZE / BASE_SIZE)
        val blockWidth = ceil(widestLine(layoutOf(styled, size, style.foreground, 10_000, Layout.Alignment.ALIGN_NORMAL))).toInt().coerceAtLeast(1)
        // the line height is the character height (the text size) plus LINE_GAP, not the (much higher) line height of the font
        val probe = layoutOf(styled, size, style.foreground, blockWidth, Layout.Alignment.ALIGN_CENTER)
        val fontLine = (probe.getLineBottom(0) - probe.getLineTop(0)).coerceAtLeast(1)
        val layout = layoutOf(styled, size, style.foreground, blockWidth, Layout.Alignment.ALIGN_CENTER, (size + LINE_GAP) / fontLine)
        val side = blockWidth + 2 * PAD_H                                  // the imaginary square: as wide as the text plus the side padding
        val height = maxOf(side, layout.height + 2 * PAD_V)                // higher only if the block does not fit
        val aspect = ASPECT * Math.pow(ASPECT_STEP.toDouble(), (textLines.size - 1).toDouble()).toFloat()
        val width = ceil(side * aspect).toInt()
        return draw(layout, blockWidth, width, height, style)
    }

    /** The normal image: the text at [FREE_SIZE] with [FREE_PAD] around it, at least [FREE_ASPECT] : 1, the text centred both ways. */
    private fun renderFree(fonts: ComposeFonts, text: CharSequence, style: ImageStyle): Bitmap {
        val styled = fonts.styled(lines(text.toString(), false).joinToString("\n"))
        val size = FREE_SIZE
        val blockWidth = ceil(widestLine(layoutOf(styled, size, style.foreground, 10_000, Layout.Alignment.ALIGN_NORMAL))).toInt().coerceAtLeast(1)
        val probe = layoutOf(styled, size, style.foreground, blockWidth, Layout.Alignment.ALIGN_CENTER)
        val fontLine = (probe.getLineBottom(0) - probe.getLineTop(0)).coerceAtLeast(1)
        val layout = layoutOf(styled, size, style.foreground, blockWidth, Layout.Alignment.ALIGN_CENTER, (size + LINE_GAP) / fontLine)
        val height = layout.height + 2 * FREE_PAD
        val width = maxOf(blockWidth + 2 * FREE_PAD, ceil(height * FREE_ASPECT).toInt())
        return draw(layout, blockWidth, width, height, style)
    }

    private fun draw(layout: StaticLayout, blockWidth: Int, width: Int, height: Int, style: ImageStyle): Bitmap {
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
