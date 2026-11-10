// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.ClipDescription
import android.content.Intent
import android.widget.Toast
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.qatt.NomDictionary
import helium314.keyboard.qatt.VietnameseParser

/**
 * Connects the composition field to the keyboard: the key events of the keyboard go to the field while it is open and selected, the panel shows it, and the arrow
 * sends its text (or an image of it) to the app. Everything it decides is in [ComposeField], [SendFormatPolicy] and the other classes of this package.
 *
 * States: not [active] (the panel is hidden); active and [selected] (the keys type into the field, the bar shows its candidates); active, not selected (the keys type into
 * the app again, the bar shows the normal suggestions, the field stays with its text).
 */
class ComposeController(private val ime: LatinIME) : ComposePanelView.Listener {
    private val prefs get() = ime.prefs()
    private val fonts by lazy { ComposeFonts(ime) }
    private val parser by lazy { VietnameseParser("qatt") }
    private val nom by lazy { ime.assets.open("nom/nom.txt").bufferedReader().use { NomDictionary().load(it) } }
    private val fieldHolder = lazy { ComposeField(parser, nom, loadSettings()) }   // only when the field is first opened: it loads the dictionary
    private val field get() = fieldHolder.value
    private val choices by lazy { SendFormatChoices.deserialize(prefs.getString(PREF_CHOICES, "")) }
    private val usage by lazy { loadUsage() }
    private val updater by lazy { FontUpdater(ime) { prefs } }

    private var panel: ComposePanelView? = null
    private var stripContainer: View? = null
    private var keyboardWrapper: View? = null
    private var keyboardHeight = 0
    private var expanded = false      // the rest of the candidates as a grid in the place of the keyboard

    var active = false
        private set
    var selected = false
        private set

    // the input field the keyboard is in now
    private var packageName: String? = null
    private var chatApp = false
    private var acceptsImages = false
    private var ignoreSelectionUntil = 0L

    /** The input view is (re)created: the panel is put into its frame, above the bar. */
    fun attach(inputView: View) {
        val frame = inputView.findViewById<ViewGroup>(R.id.main_keyboard_frame) ?: return
        panel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val p = ComposePanelView(inputView.context, fonts).also { it.listener = this }
        frame.addView(p, 0)
        panel = p
        stripContainer = inputView.findViewById(R.id.strip_container)
        keyboardWrapper = inputView.findViewById(R.id.keyboard_view_wrapper)
        expanded = false
        refresh()
    }

    /** A new input field (not just a restart of the same one): the field is closed and the app is looked at. */
    fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
        if (!restarting) close()
        packageName = editorInfo?.packageName
        chatApp = AndroidSources.isChatOrSocial(ime, editorInfo)
        acceptsImages = AndroidSources.acceptsImages(editorInfo)
        if (active) refresh()
    }

    /** The cursor of the app moved. If the user did that (not our own sending) while the field was selected, the keys go to the app again. */
    fun onAppSelectionChanged() {
        if (active && selected && System.currentTimeMillis() > ignoreSelectionUntil) deselect()
    }

    fun toggle() { if (active) close() else open() }

    private fun open() {
        active = true
        selected = true
        refresh()
    }

    private fun close() {
        if (fieldHolder.isInitialized()) field.clear()
        active = false
        selected = false
        expanded = false
        refresh()
    }

    private fun select() { selected = true; refresh() }
    private fun deselect() { selected = false; expanded = false; refresh() }

    // ---------- keys ----------

    /** A key of the keyboard. Returns true if the field took it (then the app does not get it). */
    fun onEvent(event: Event): Boolean {
        if (!active || !selected) return false
        expanded = false
        val f = field
        when {
            event.keyCode == KeyCode.DELETE -> f.backspace()
            event.isFunctionalKeyEvent || event.codePoint < 0 -> return false      // Shift, the switches of the keyboard, …
            event.codePoint == '\n'.code -> f.enter()
            event.codePoint == ' '.code -> f.space()
            else -> f.type(event.codePoint)
        }
        refresh()
        return true
    }

    /** Text from the keyboard that is more than a key (an emoji, a text of a popup key). */
    fun onTextInput(text: String?): Boolean {
        if (!active || !selected || text == null) return false
        expanded = false
        field.type(text)
        refresh()
        return true
    }

    // ---------- the panel ----------

    override fun onBack() = close()

    override fun onFieldTapped() = select()

    override fun onCandidate(index: Int) {
        val c = field.pick(index) ?: return
        if (c.kind == Kind.NOM) learn(c.text)
        expanded = false
        refresh()
    }

    override fun onMoreToggle() {
        if (!expanded) keyboardWrapper?.height?.takeIf { it > 0 }?.let { keyboardHeight = it }   // the grid is as high as the keyboard
        expanded = !expanded
        refresh()
    }

    override fun onSend() {
        val text = field.text
        if (text.isBlank()) return
        val choice = currentChoice()
        val connection = ime.inputLogic.mConnection
        var sent = false
        if (SendFormatPolicy.effective(choice.format, acceptsImages) == SendFormat.IMAGE) sent = sendImage(text, choice.style)
        if (!sent) connection.commitText(text, 1)
        ignoreSelectionUntil = System.currentTimeMillis() + 800
        field.clear()
        refresh()
    }

    private fun sendImage(text: String, style: ImageStyle): Boolean = try {
        val editorInfo = ime.currentInputEditorInfo ?: return false
        val bitmap = ImageRenderer.render(fonts, text, style)
        val uri = ImageRenderer.save(ime, bitmap, ime.getString(R.string.qatt_compose_provider_authority))
        val info = InputContentInfoCompat(uri, ClipDescription("QATT", arrayOf("image/png")), null)
        ime.inputLogic.mConnection.commitContent(info, editorInfo)
    } catch (e: Exception) { false }

    override fun onFormatSelector(anchor: View) {
        val current = currentChoice()
        val menu = PopupMenu(anchor.context, anchor)
        SendChoice.ALL.forEachIndexed { i, c ->
            menu.menu.add(Menu.NONE, i, i, longLabel(c)).apply { isCheckable = true; isChecked = c == current }
        }
        menu.menu.setGroupCheckable(Menu.NONE, true, true)
        menu.setOnMenuItemClickListener { item ->
            packageName?.let { choices[it] = SendChoice.ALL[item.itemId]; prefs.edit { putString(PREF_CHOICES, choices.serialize()) } }
            refresh()
            true
        }
        menu.show()
    }

    override fun onSettings(anchor: View) {
        val s = field.settings
        val menu = PopupMenu(anchor.context, anchor)
        menu.menu.add(Menu.NONE, 1, 1, R.string.compose_pa_candidates).apply { isCheckable = true; isChecked = s.pa }
        menu.menu.add(Menu.NONE, 2, 2, R.string.compose_qatt_candidates).apply { isCheckable = true; isChecked = s.qatt }
        val modes = listOf(
            SpaceMode.NONE to R.string.compose_space_none, SpaceMode.PA to R.string.compose_space_pa,
            SpaceMode.QATT to R.string.compose_space_qatt, SpaceMode.NOM to R.string.compose_space_nom,
        )
        modes.forEachIndexed { i, (mode, label) -> menu.menu.add(1, 10 + i, 10 + i, label).apply { isCheckable = true; isChecked = s.space == mode } }
        menu.menu.setGroupCheckable(1, true, true)
        ComposeFont.values().forEachIndexed { i, font ->
            menu.menu.add(2, 30 + i, 30 + i, fontLabel(font)).apply { isCheckable = true; isChecked = fonts.family == font && FontStore.isInstalled(ime, font) }
        }
        menu.menu.setGroupCheckable(2, true, true)
        menu.menu.add(Menu.NONE, 22, 22, ime.getString(R.string.compose_update_font, fonts.family.label, fonts.family.sizeMb, fontState(fonts.family)))
        menu.menu.add(Menu.NONE, 24, 24, R.string.compose_import_font)
        if (FontStore.imported(ime, ComposeFont.PEN) != null) menu.menu.add(Menu.NONE, 23, 23, R.string.compose_font_builtin)
        menu.menu.add(Menu.NONE, 20, 20, R.string.compose_forget_choices)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> saveSettings(s.copy(pa = !s.pa))
                2 -> saveSettings(s.copy(qatt = !s.qatt))
                in 10..13 -> saveSettings(s.copy(space = modes[item.itemId - 10].first))
                in 30..32 -> chooseFont(ComposeFont.values()[item.itemId - 30])
                22 -> updateFont(fonts.family)
                24 -> launchImport(fonts.family)
                23 -> { FontStore.remove(ime, ComposeFont.PEN); updater.forgetEtag(ComposeFont.PEN); fonts.family = ComposeFont.PEN; prefs.edit { putString(PREF_FONT, ComposeFont.PEN.name) } }
                20 -> { choices.clear(); prefs.edit { putString(PREF_CHOICES, "") } }
            }
            refresh()
            true
        }
        menu.show()
    }

    private fun chooseFont(font: ComposeFont) {
        if (FontStore.isInstalled(ime, font)) {
            fonts.family = font
            prefs.edit { putString(PREF_FONT, font.name) }
        } else updateFont(font)   // not in the app yet: it is downloaded and then chosen
    }

    /** Downloads the font (or finds that the one in the app is the newest), the progress is shown in the empty field. */
    private fun updateFont(font: ComposeFont) {
        val started = updater.update(font, { percent ->
            panel?.setStatus(ime.getString(R.string.compose_font_downloading, font.label, if (percent >= 0) "$percent %" else "…"))
        }) { result ->
            panel?.setStatus(null)
            val message = when (result) {
                is FontDownloader.Result.Current -> ime.getString(R.string.compose_font_current, font.label)
                is FontDownloader.Result.Downloaded -> {
                    fonts.family = font
                    prefs.edit { putString(PREF_FONT, font.name) }
                    ime.getString(R.string.compose_font_installed)
                }
                is FontDownloader.Result.Failed -> ime.getString(R.string.compose_font_failed, result.reason)
            }
            Toast.makeText(ime, message, Toast.LENGTH_SHORT).show()
            refresh()
        }
        if (!started) Toast.makeText(ime, R.string.compose_font_busy, Toast.LENGTH_SHORT).show()
        else panel?.setStatus(ime.getString(R.string.compose_font_downloading, font.label, "…"))
    }

    /** The user chooses a font file for [font]: a new version of one that is in the app, or one that is not (the keyboard has no network of its own). */
    private fun launchImport(font: ComposeFont) {
        ime.startActivity(Intent(ime, FontImportActivity::class.java).putExtra(FontImportActivity.EXTRA_FONT, font.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** "built in" or "imported 10 Nov 2026" */
    private fun fontState(f: ComposeFont): String {
        val file = FontStore.imported(ime, f) ?: return ime.getString(if (f.bundled) R.string.compose_font_state_builtin else R.string.compose_font_state_none)
        return ime.getString(R.string.compose_font_state_imported, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(file.lastModified())))
    }

    // ---------- state shown ----------

    private fun currentChoice(): SendChoice {
        val shows = AndroidSources.systemShows(field.text, field.settings.qatt, field.settings.pa)
        return SendFormatPolicy.selected(choices[packageName], chatApp, acceptsImages, shows)
    }

    private fun longLabel(c: SendChoice) = ime.getString(if (c.format == SendFormat.TEXT) R.string.compose_format_text else when (c.style) {
        ImageStyle.BLACK_ON_WHITE -> R.string.compose_format_image_bw
        ImageStyle.WHITE_ON_BLACK -> R.string.compose_format_image_wb
        ImageStyle.INK_ON_RICE_PAPER -> R.string.compose_style_ink_on_rice_paper
        ImageStyle.GOLD_ON_DARK -> R.string.compose_style_gold_on_dark
        ImageStyle.RED_ON_CREAM -> R.string.compose_style_red_on_cream
        ImageStyle.PINK_GRADIENT -> R.string.compose_style_pink_gradient
        ImageStyle.SUNSET_GRADIENT -> R.string.compose_style_sunset_gradient
        ImageStyle.OCEAN_GRADIENT -> R.string.compose_style_ocean_gradient
    })

    /** "Font: Migoi", with "(download, 42 MB)" while the big font is not in the app. */
    private fun fontLabel(f: ComposeFont) = ime.getString(R.string.compose_font, f.label) +
        if (FontStore.isInstalled(ime, f)) "" else " " + ime.getString(R.string.compose_font_download, f.sizeMb)

    private fun refresh() {
        val p = panel ?: return
        if (!active) {
            p.visibility = View.GONE
            stripContainer?.visibility = View.VISIBLE
            keyboardWrapper?.visibility = View.VISIBLE
            return
        }
        p.visibility = View.VISIBLE
        stripContainer?.visibility = if (selected) View.GONE else View.VISIBLE
        val f = field
        fonts.family = ComposeFont.values().firstOrNull { it.name == prefs.getString(PREF_FONT, "") } ?: fonts.family
        val choice = currentChoice()
        p.setContent(f.text, f.composingStart)
        p.setLook(choice.format == SendFormat.IMAGE, choice.style, selected)
        p.setFormatLabel(ime.getString(if (choice.format == SendFormat.TEXT) R.string.compose_format_text else R.string.compose_format_image))
        val all = if (selected) f.allCandidates() else emptyList()
        val places = f.settings.slots
        val more = all.size > places
        if (!more) expanded = false
        p.setCandidates(all.take(places))
        p.setMore(more, expanded)
        // the rest of the candidates replaces the keyboard
        if (expanded && selected && more) {
            keyboardWrapper?.visibility = View.GONE
            p.showGrid(all.drop(places), places, keyboardHeight)
        } else {
            keyboardWrapper?.visibility = View.VISIBLE
            p.hideGrid()
        }
    }

    // ---------- settings ----------

    private fun loadSettings() = ComposeSettings(
        pa = prefs.getBoolean(PREF_PA, true),
        qatt = prefs.getBoolean(PREF_QATT, true),
        space = SpaceMode.values().firstOrNull { it.name == prefs.getString(PREF_SPACE, "") } ?: SpaceMode.NONE,
        usage = usage,
    )

    private fun saveSettings(s: ComposeSettings) {
        field.settings = s
        prefs.edit { putBoolean(PREF_PA, s.pa); putBoolean(PREF_QATT, s.qatt); putString(PREF_SPACE, s.space.name) }
    }

    // how often each Chữ Nôm glyph was chosen: the order of the candidates (one line "glyph TAB count" each)
    private fun loadUsage(): MutableMap<String, Int> = HashMap<String, Int>().also { map ->
        for (line in prefs.getString(PREF_USAGE, "").orEmpty().lines()) {
            val i = line.lastIndexOf('\t')
            val n = line.substring(i + 1).toIntOrNull()
            if (i > 0 && n != null) map[line.substring(0, i)] = n
        }
    }

    private fun learn(glyph: String) {
        usage[glyph] = (usage[glyph] ?: 0) + 1
        if (usage.size > MAX_USAGE) usage.entries.minByOrNull { it.value }?.let { usage.remove(it.key) }
        field.settings = field.settings.copy(usage = usage)
        prefs.edit { putString(PREF_USAGE, usage.entries.joinToString("\n") { "${it.key}\t${it.value}" }) }
    }

    companion object {
        private const val PREF_CHOICES = "qatt_compose_send_choices"
        private const val PREF_PA = "qatt_compose_pa"
        private const val PREF_QATT = "qatt_compose_qatt"
        private const val PREF_SPACE = "qatt_compose_space"
        private const val PREF_USAGE = "qatt_compose_usage"
        private const val PREF_FONT = "qatt_compose_font"
        private const val MAX_USAGE = 1000
    }
}
