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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/** Draws the text of the field as an image (the colours of an [ImageStyle]) and gives it a content address for `commitContent`. */
object ImageRenderer {
    private const val TEXT_SIZE = 72f          // pixels
    // Messenger shows an image from the keyboard at about 30 % of the screen width and scales it down to that: a wide image has tiny text.
    // So the text is wrapped at a narrow width (about ten Latin letters or five Chinese characters a line) to keep it readable after the scaling.
    private const val MAX_WIDTH = 520
    private const val PADDING = 40
    private const val FOLDER = "compose"

    fun render(fonts: ComposeFonts, text: CharSequence, style: ImageStyle): Bitmap {
        val trimmed = text.toString().trimEnd()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TEXT_SIZE; color = style.foreground }
        @Suppress("DEPRECATION")
        val layout = StaticLayout(fonts.styled(trimmed), paint, MAX_WIDTH - 2 * PADDING, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        var widest = 0f
        for (i in 0 until layout.lineCount) widest = maxOf(widest, layout.getLineWidth(i))
        val width = ceil(widest).toInt().coerceAtLeast(1) + 2 * PADDING
        val height = layout.height.coerceAtLeast(1) + 2 * PADDING
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val end = style.backgroundEnd
        if (end == null) canvas.drawColor(style.background)
        else canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), style.background, end, Shader.TileMode.CLAMP)   // top left to bottom right
        })
        canvas.translate(PADDING.toFloat(), PADDING.toFloat())
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
