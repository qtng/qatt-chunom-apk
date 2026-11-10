// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs

/** Lets the user choose a font file (a keyboard cannot open a file chooser itself) and imports it as the font `EXTRA_FONT`. No network permission is needed. */
class FontImportActivity : Activity() {
    private var font: ComposeFont? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        font = ComposeFont.values().firstOrNull { it.name == intent.getStringExtra(EXTRA_FONT) }
        if (font == null) { finish(); return }
        if (savedInstanceState != null) return        // the chooser is already open
        val choose = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
        }
        try { startActivityForResult(choose, REQUEST) } catch (e: Exception) { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val f = font
        if (requestCode == REQUEST && resultCode == RESULT_OK && uri != null && f != null) {
            if (FontStore.import(this, uri, f)) {
                prefs().edit { putString("qatt_compose_font", f.name) }
                Toast.makeText(this, R.string.compose_font_installed, Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, R.string.compose_font_invalid, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object { const val EXTRA_FONT = "font"; private const val REQUEST = 1 }
}
