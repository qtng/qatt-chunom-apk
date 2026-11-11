// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Diagonal stripes of [colors], each [width] pixels wide (the row of Lucky draw in the dropdown). */
class StripesDrawable(private val colors: List<Int>, private val width: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val h = b.height().toFloat()
        canvas.save()
        canvas.clipRect(b)
        var x = b.left - h                                   // the stripes lean to the right: start one height further left
        var i = 0
        while (x < b.right) {
            paint.color = colors[i % colors.size]
            path.reset()
            path.moveTo(x, b.bottom.toFloat()); path.lineTo(x + h, b.top.toFloat())
            path.lineTo(x + h + width, b.top.toFloat()); path.lineTo(x + width, b.bottom.toFloat()); path.close()
            canvas.drawPath(path, paint)
            x += width; i++
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
