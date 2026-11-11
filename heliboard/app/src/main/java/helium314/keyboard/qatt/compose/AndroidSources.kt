// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Paint
import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat

/** The Android side of the decisions of [SendFormatPolicy]: what the system and the input field can do. Every question can fail, then the answer is "no". */
object AndroidSources {
    /** The category an app declares (API 26), null if unknown or not readable. */
    fun categoryOf(context: Context, packageName: String): Int? = try {
        if (Build.VERSION.SDK_INT >= 26) context.packageManager.getApplicationInfo(packageName, 0).category.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED } else null
    } catch (e: Exception) { null }

    fun isChatOrSocial(context: Context, editorInfo: EditorInfo?, extra: Set<String> = emptySet()) =
        ChatApps.isChatOrSocial(editorInfo?.packageName, extra) { categoryOf(context, it) }

    /** Does the input field accept an image (PNG)? */
    fun acceptsImages(editorInfo: EditorInfo?): Boolean = try {
        editorInfo != null && EditorInfoCompat.getContentMimeTypes(editorInfo).any { it == "image/png" || it == "image/*" || it == "*/*" }
    } catch (e: Exception) { false }

    /** Can the fonts of the system show [text] (an empty text: QATT, PA and Chữ Nôm as set)? */
    fun systemShows(text: String, qatt: Boolean, pa: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        val paint = Paint()
        return GlyphSupport.systemShows(text, qatt, pa) { paint.hasGlyph(it) }
    }
}
