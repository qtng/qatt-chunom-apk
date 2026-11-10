// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import java.util.concurrent.Executors

/**
 * Downloads or updates a big font in the background ([FontDownloader]) when the user asks for it. The ETag of the version in the app is kept, so that an update
 * that finds nothing new costs a few bytes: for the font that is built in, it is the file `<font>.etag` in the assets (written by tools/fonts/fetch_fonts.py).
 * The callbacks come on the main thread.
 */
class FontUpdater(private val context: Context, private val prefs: () -> SharedPreferences) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var running: ComposeFont? = null
        private set

    private fun etagKey(font: ComposeFont) = ETAG_PREFIX + font.name

    /** The version of the font in the app, if it is known: after an import from a file it is not. */
    fun knownEtag(font: ComposeFont): String? = when {
        FontStore.imported(context, font) != null -> prefs().getString(etagKey(font), null)
        font.bundled -> try { context.assets.open("fonts/${font.file}.etag").bufferedReader().use { it.readText().trim() } } catch (e: Exception) { null }
        else -> null
    }

    /** The file was replaced by another way (an import or going back to the built-in one): its version is not known any more. */
    fun forgetEtag(font: ComposeFont) { prefs().edit { remove(etagKey(font)) } }

    /** Starts the download. Returns false if another one is running. [onProgress]: percent, -1 if the size is not known. */
    fun update(font: ComposeFont, onProgress: (Int) -> Unit, onDone: (FontDownloader.Result) -> Unit): Boolean {
        synchronized(this) { if (running != null) return false; running = font }
        val etag = knownEtag(font)
        executor.execute {
            val result = FontDownloader().download(FontDownloader.urlOf(font), FontStore.file(context, font), etag, { done, total ->
                val percent = if (total > 0) (100 * done / total).toInt() else -1
                main.post { onProgress(percent) }
            })
            if (result is FontDownloader.Result.Downloaded) {
                prefs().edit { if (result.etag != null) putString(etagKey(font), result.etag) else remove(etagKey(font)) }
            }
            main.post { running = null; onDone(result) }
        }
        return true
    }

    companion object { private const val ETAG_PREFIX = "qatt_compose_font_etag_" }
}
