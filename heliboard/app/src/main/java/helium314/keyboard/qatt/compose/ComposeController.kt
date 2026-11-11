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
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.common.ColorType
import android.widget.TextView
import android.widget.ListPopupWindow
import android.widget.BaseAdapter
import android.util.TypedValue
import android.graphics.drawable.GradientDrawable
import androidx.core.content.edit
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import android.view.inputmethod.InputMethodSubtype
import java.util.Locale
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
    // the first time the field is used it asks which scripts to type and what Space does (in the place of the keyboard)
    private var setupPending = false
    private val setupScripts = BooleanArray(3)           // PA, QATT, Chữ Nôm: nothing chosen at first
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
    private var swallowRepeats = false      // Backspace closed the field: the repeats of that key press are ignored
    private var lastSelStart = -1
    private var lastSelEnd = -1

    /** The height of the visible panel (0 if hidden): the touchable region of the keyboard window has to include it. */
    fun panelHeight(): Int = panel?.takeIf { it.isShown }?.height ?: 0

    /** The pencil is pinned to the bar once, for installations that already had a toolbar setting. */
    private fun pinPencilOnce() {
        if (prefs.getBoolean(PREF_PINNED_DONE, false)) return
        prefs.edit { putBoolean(PREF_PINNED_DONE, true) }
        addPinnedKey(prefs, ToolbarKey.COMPOSE)
    }

    /** The input view is (re)created: the panel is put into its frame, above the bar. */
    fun attach(inputView: View) {
        pinPencilOnce()
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
        lastSelStart = editorInfo?.initialSelStart ?: -1; lastSelEnd = editorInfo?.initialSelEnd ?: -1
        chatApp = AndroidSources.isChatOrSocial(ime, editorInfo)
        acceptsImages = AndroidSources.acceptsImages(editorInfo)
        if (active) refresh()
    }

    /** The cursor of the app moved. If the user did that (not our own sending) while the field was open, the field closes (its text is kept for the next time). */
    fun onAppSelectionChanged(newSelStart: Int, newSelEnd: Int) {
        val moved = newSelStart != lastSelStart || newSelEnd != lastSelEnd
        val known = lastSelStart >= 0
        lastSelStart = newSelStart; lastSelEnd = newSelEnd
        // only a real move of the cursor (a tap into the app) counts, not a repeated report (e.g. after the panel changed the size of the window)
        if (active && selected && known && moved && System.currentTimeMillis() > ignoreSelectionUntil) hide()
    }

    fun toggle() { if (active) close() else open() }

    // ---------- the language of the keyboard: Vietnamese while the field is open, the language from before when it closes ----------

    /** The language the keyboard had when the field opened: it comes back when the field closes. */
    private var languageBefore: InputMethodSubtype? = null
    /** The other language of the globe key in the field: the one from before, or (if that was Vietnamese) another one; Vietnamese is the first. */
    private var otherLanguage: InputMethodSubtype? = null

    private fun isVietnamese() = try { RichInputMethodManager.getInstance().currentSubtypeLocale.language == "vi" } catch (_: Exception) { true }

    private fun vietnameseSubtype(): InputMethodSubtype? =
        SubtypeSettings.getEnabledSubtypes(false).firstOrNull { it.locale().language == "vi" }
            ?: SubtypeSettings.getResourceSubtypesForLocale(Locale.forLanguageTag("vi")).firstOrNull()

    private fun switchToVietnamese() = guarded("language") {
        val current = RichInputMethodManager.getInstance().currentSubtype.rawSubtype
        languageBefore = current
        otherLanguage = if (!isVietnamese()) current
            else SubtypeSettings.getEnabledSubtypes(false).firstOrNull { it.locale().language != "vi" }
                ?: SubtypeSettings.getResourceSubtypesForLocale(Locale.US).firstOrNull()
        fieldLanguageKey = true                               // the globe key is on the keyboard while the field is open, also with only one language
        val vi = vietnameseSubtype()
        if (!isVietnamese() && vi != null) ime.switchToSubtype(vi)
        else helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().reloadKeyboard()
    }

    /** The globe key in the field: Vietnamese and the other language, back and forth. */
    private fun toggleLanguage() = guarded("language") {
        val target = if (isVietnamese()) otherLanguage else vietnameseSubtype()
        if (target != null) ime.switchToSubtype(target)
    }

    private fun restoreLanguage() = guarded("language") {
        if (!fieldLanguageKey) return@guarded
        fieldLanguageKey = false
        val before = languageBefore
        languageBefore = null; otherLanguage = null
        if (before != null && before != RichInputMethodManager.getInstance().currentSubtype.rawSubtype) ime.switchToSubtype(before)
        else helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().reloadKeyboard()
    }

    private fun open() {
        luckyStyle = null                                    // Lucky draw draws again
        switchToVietnamese()
        setupPending = !prefs.getBoolean(PREF_SETUP_DONE, false)
        if (setupPending) setupScripts.fill(false)
        active = true
        selected = true
        ignoreSelectionUntil = System.currentTimeMillis() + 600
        refresh()
    }

    private fun close() {
        if (fieldHolder.isInitialized()) field.clear()
        active = false
        selected = false
        expanded = false
        refresh()
        restoreLanguage()
    }

    /** Closes the field without losing its text (the user tapped into the app): the keyboard is normal again, the pencil opens the field with the text. */
    private fun hide() {
        active = false
        selected = false
        expanded = false
        refresh()
        restoreLanguage()
    }

    private fun select() { selected = true; ignoreSelectionUntil = System.currentTimeMillis() + 600; refresh() }
    private fun deselect() { selected = false; expanded = false; refresh() }

    // ---------- keys ----------

    /** A key of the keyboard. Returns true if the field took it (then the app does not get it). */
    fun onEvent(event: Event): Boolean {
        // a held Backspace that closed the field must stop there: its repeats are not for the app (a new press is)
        if (event.keyCode == KeyCode.DELETE) {
            if (swallowRepeats && !active) { if (event.isKeyRepeat) return true else swallowRepeats = false }
        } else swallowRepeats = false
        if (!active || !selected || setupPending) return false
        expanded = false
        val f = field
        f.telex = isVietnamese()
        when {
            event.keyCode == KeyCode.DELETE -> if (f.isEmpty) { swallowRepeats = true; close(); return true } else f.backspace()
            event.keyCode == KeyCode.ARROW_LEFT -> f.moveLeft()
            event.keyCode == KeyCode.ARROW_RIGHT -> f.moveRight()
            event.keyCode == KeyCode.MOVE_START_OF_LINE || event.keyCode == KeyCode.MOVE_START_OF_PAGE -> f.moveToStart()
            event.keyCode == KeyCode.MOVE_END_OF_LINE || event.keyCode == KeyCode.MOVE_END_OF_PAGE -> f.moveToEnd()
            event.keyCode == KeyCode.LANGUAGE_SWITCH -> { toggleLanguage(); return true }
            event.keyCode == KeyCode.ARROW_UP || event.keyCode == KeyCode.ARROW_DOWN -> {}      // no lines to move between yet; the app must not move
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
        field.telex = isVietnamese()
        field.type(text)
        refresh()
        return true
    }

    // ---------- the panel ----------

    override fun onBack() = close()

    override fun onFieldTapped(offset: Int) {
        field.setCursor(offset)
        select()
    }

    override fun onCandidate(index: Int) {
        val c = field.pick(index) ?: return
        if (c.kind == Kind.NOM) learn(c.text)
        expanded = false
        refresh()
    }

    override fun onSetupToggle(index: Int) = guarded("setup toggle") {
        ignoreSelectionUntil = System.currentTimeMillis() + 1500
        setupScripts[index] = !setupScripts[index]
        refresh()
    }

    /**
     * Switches the scripts: at least one is always on (if the user switched all off, Chữ Nôm comes back), and Space always converts to a script that is on
     * (if its script went off, it follows the rule below).
     */
    private fun saveScripts(s: ComposeSettings, pa: Boolean, qatt: Boolean, nom: Boolean) {
        val nomOn = nom || (!pa && !qatt)
        val spaceOk = when (s.space) { SpaceMode.PA -> pa; SpaceMode.QATT -> qatt; SpaceMode.NOM -> nomOn; SpaceMode.NONE -> false }
        saveSettings(s.copy(pa = pa, qatt = qatt, nom = nomOn, space = if (spaceOk) s.space else spaceModeFor(pa, qatt, nomOn)))
    }

    /** What Space does after the setup: Chữ Nôm if it is on; else the one script that is on; else (PA and QATT) PA. */
    private fun spaceModeFor(pa: Boolean, qatt: Boolean, nom: Boolean) = when {
        nom -> SpaceMode.NOM
        qatt && !pa -> SpaceMode.QATT
        else -> SpaceMode.PA
    }

    override fun onSetupConfirm() = guarded("setup confirm") {
        if (setupScripts.none { it }) return@guarded
        ignoreSelectionUntil = System.currentTimeMillis() + 1500        // the keyboard comes back: the window changes, the app may report its cursor again
        val (pa, qatt, nom) = setupScripts.toList()
        saveSettings(field.settings.copy(pa = pa, qatt = qatt, nom = nom, space = spaceModeFor(pa, qatt, nom)))
        prefs.edit { putBoolean(PREF_SETUP_DONE, true) }
        setupPending = false
        refresh()
    }

    /** A failure in the setup must not take the keyboard down: it is logged and shown as a message. */
    private inline fun guarded(what: String, block: () -> Unit) {
        try { block() } catch (e: Exception) {
            android.util.Log.e("QattCompose", what, e)
            Toast.makeText(ime, "$what: ${e}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onMoreToggle() {
        if (!expanded && !setupPending) keyboardWrapper?.height?.takeIf { it > 0 }?.let { keyboardHeight = it }   // the grid is as high as the keyboard
        expanded = !expanded
        refresh()
    }

    override fun onSend() {
        val text = field.text
        if (text.isBlank()) return
        val choice = currentChoice()
        val connection = ime.inputLogic.mConnection
        var sent = false
        if (SendFormatPolicy.effective(choice.format, acceptsImages) == SendFormat.IMAGE) sent = sendImage(text, styleOf(choice))
        if (!sent) connection.commitText(text, 1)
        ignoreSelectionUntil = System.currentTimeMillis() + 800
        close()                         // the field is gone and the keyboard is normal again
    }

    private fun sendImage(text: String, style: ImageStyle): Boolean = try {
        val editorInfo = ime.currentInputEditorInfo ?: return false
        val bitmap = ImageRenderer.render(fonts, text, style, ImageRenderer.isSquareApp(packageName), panel?.fieldLines())
        val uri = ImageRenderer.save(ime, bitmap, ime.getString(R.string.qatt_compose_provider_authority))
        val info = InputContentInfoCompat(uri, ClipDescription("QATT", arrayOf("image/png")), null)
        ime.inputLogic.mConnection.commitContent(info, editorInfo)
    } catch (e: Exception) { false }

    override fun onFormatSelector(anchor: View) {
        val current = currentChoice()
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val popup = ListPopupWindow(context)
        popup.anchorView = anchor
        popup.isModal = true
        popup.width = (230 * density).toInt()
        popup.setAdapter(object : BaseAdapter() {
            override fun getCount() = SendChoice.ALL.size
            override fun getItem(position: Int) = SendChoice.ALL[position]
            override fun getItemId(position: Int) = position.toLong()
            // an Image is not possible where the field accepts no images: grey and not to be tapped
            override fun areAllItemsEnabled() = acceptsImages
            override fun isEnabled(position: Int) = acceptsImages || SendChoice.ALL[position].format == SendFormat.TEXT
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val c = SendChoice.ALL[position]
                val row = (convertView as? TextView) ?: TextView(context)
                row.text = (if (c == current) "✓  " else "     ") + longLabel(c)
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                row.setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                row.setShadowLayer(0f, 0f, 0f, 0)
                if (c.lucky) {                               // Lucky draw: stripes in the colours of all its styles
                    row.setTextColor(0xFFFFFFFF.toInt())
                    row.setShadowLayer(3 * density, 0f, density, 0x99000000.toInt())
                    row.background = StripesDrawable(ImageStyle.LUCKY_POOL.map { if (it.isGradient) it.background else it.foreground }, 14 * density)
                } else if (c.format == SendFormat.IMAGE) {   // the row shows the colours of its style
                    row.setTextColor(c.style.foreground)
                    row.background = GradientDrawable().apply {
                        val end = c.style.backgroundEnd
                        if (end != null) { orientation = GradientDrawable.Orientation.LEFT_RIGHT; colors = intArrayOf(c.style.background, end) }
                        else setColor(c.style.background)
                    }
                } else {                                    // Text: the colours of the keyboard (the popup itself may be white)
                    val theme = Settings.getValues().mColors
                    row.setBackgroundColor(theme.get(ColorType.KEY_BACKGROUND))
                    row.setTextColor(theme.get(ColorType.KEY_TEXT))
                }
                row.alpha = if (isEnabled(position)) 1f else .35f
                return row
            }
        })
        popup.setOnItemClickListener { _, _, position, _ ->
            packageName?.let { choices[it] = SendChoice.ALL[position]; prefs.edit { putString(PREF_CHOICES, choices.serialize()) } }
            if (SendChoice.ALL[position].lucky) drawLucky()          // chosen now: a style at once
            popup.dismiss()
            refresh()
        }
        popup.show()
    }

    override fun onSettings(anchor: View) {
        val s = field.settings
        val menu = PopupMenu(anchor.context, anchor)
        menu.menu.add(Menu.NONE, 1, 1, R.string.compose_pa_candidates).apply { isCheckable = true; isChecked = s.pa }
        menu.menu.add(Menu.NONE, 2, 2, R.string.compose_qatt_candidates).apply { isCheckable = true; isChecked = s.qatt }
        menu.menu.add(Menu.NONE, 3, 3, R.string.compose_nom_candidates).apply { isCheckable = true; isChecked = s.nom }
        val modes = listOf(SpaceMode.PA to R.string.compose_space_pa, SpaceMode.QATT to R.string.compose_space_qatt, SpaceMode.NOM to R.string.compose_space_nom)
        modes.forEachIndexed { i, (mode, label) -> menu.menu.add(1, 10 + i, 10 + i, label).apply { isCheckable = true; isChecked = s.space == mode } }
        menu.menu.setGroupCheckable(1, true, true)
        ComposeFont.values().forEachIndexed { i, font ->
            menu.menu.add(2, 30 + i, 30 + i, fontLabel(font)).apply { isCheckable = true; isChecked = fonts.family == font && FontStore.isInstalled(ime, font) }
        }
        menu.menu.setGroupCheckable(2, true, true)
        val icon = prefs.getString(PREF_ICON, "nom")
        val icons = listOf("nom" to R.string.compose_icon_nom, "note_alt" to R.string.compose_icon_note_alt, "bubble" to R.string.compose_icon_bubble)
        icons.forEachIndexed { i, (id, label) -> menu.menu.add(3, 50 + i, 34, label).apply { isCheckable = true; isChecked = icon == id } }
        menu.menu.setGroupCheckable(3, true, true)
        menu.menu.add(Menu.NONE, 22, 40, ime.getString(R.string.compose_update_font, fonts.family.label, fonts.family.sizeMb, fontState(fonts.family)))
        menu.menu.add(Menu.NONE, 24, 41, R.string.compose_import_font)
        if (FontStore.imported(ime, ComposeFont.PEN) != null) menu.menu.add(Menu.NONE, 23, 35, R.string.compose_font_builtin)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> saveScripts(s, !s.pa, s.qatt, s.nom)
                2 -> saveScripts(s, s.pa, !s.qatt, s.nom)
                3 -> saveScripts(s, s.pa, s.qatt, !s.nom)
                in 10..12 -> saveSettings(s.copy(space = modes[item.itemId - 10].first))
                in 30..32 -> chooseFont(ComposeFont.values()[item.itemId - 30])
                in 50..53 -> {
                    prefs.edit { putString(PREF_ICON, icons[item.itemId - 50].first) }
                    helium314.keyboard.keyboard.internal.KeyboardIconsSet.needsReload = true
                    helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().reloadKeyboard()
                }
                22 -> updateFont(fonts.family)
                24 -> launchImport(fonts.family)
                23 -> { FontStore.remove(ime, ComposeFont.PEN); updater.forgetEtag(ComposeFont.PEN); fonts.family = ComposeFont.PEN; prefs.edit { putString(PREF_FONT, ComposeFont.PEN.name) } }
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

    // ---------- Lucky draw: a coloured style by chance, drawn anew every time the field opens ----------

    private var luckyStyle: ImageStyle? = null

    private fun drawLucky(): ImageStyle {
        val last = prefs.getString(PREF_LUCKY_LAST, null)?.let { n -> ImageStyle.values().firstOrNull { it.name == n } }
        return ImageStyle.drawLucky(last).also { luckyStyle = it; prefs.edit { putString(PREF_LUCKY_LAST, it.name) } }
    }

    /** The style an Image is drawn in: the chosen one, for Lucky draw the one drawn this time. */
    private fun styleOf(c: SendChoice): ImageStyle = if (c.lucky) luckyStyle ?: drawLucky() else c.style

    private fun longLabel(c: SendChoice) = ime.getString(if (c.format == SendFormat.TEXT) R.string.compose_format_text else if (c.lucky) R.string.compose_style_lucky else when (c.style) {
        ImageStyle.BLACK_ON_WHITE -> R.string.compose_format_image_bw
        ImageStyle.WHITE_ON_BLACK -> R.string.compose_format_image_wb
        ImageStyle.INK_ON_RICE_PAPER -> R.string.compose_style_ink_on_rice_paper
        ImageStyle.GOLD_ON_DARK -> R.string.compose_style_gold_on_dark
        ImageStyle.MOSS_GRADIENT -> R.string.compose_style_moss_gradient
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
        // where the field accepts no images the text is sent: the field looks like Text then, whatever was chosen
        val image = SendFormatPolicy.effective(choice.format, acceptsImages) == SendFormat.IMAGE
        p.setContent(f.text, f.composingStart, f.cursor, image, ImageRenderer.isSquareApp(packageName))
        p.setLook(image, styleOf(choice), selected)
        p.setFormatLabel(ime.getString(if (!image) R.string.compose_format_text else R.string.compose_format_image))
        p.setSendReady(f.text.isNotBlank())
        val all = if (selected) f.allCandidates() else emptyList()
        val places = f.settings.slots
        val more = all.size > places
        if (!more) expanded = false
        p.setCandidates(all.take(places))
        p.setMore(more, expanded)
        if (setupPending) {                         // the first-time setup replaces the keyboard
            keyboardWrapper?.visibility = View.GONE
            p.showSetup(setupScripts, keyboardHeight)
            return
        }
        p.hideSetup()
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
        qatt = prefs.getBoolean(PREF_QATT, false),   // default: PA and Chữ Nôm, Space converts to Chữ Nôm
        nom = prefs.getBoolean(PREF_NOM, true),
        space = SpaceMode.values().firstOrNull { it.name == prefs.getString(PREF_SPACE, "") } ?.takeIf { it != SpaceMode.NONE } ?: SpaceMode.NOM,
        usage = usage,
    )

    private fun saveSettings(s: ComposeSettings) {
        field.settings = s
        prefs.edit { putBoolean(PREF_PA, s.pa); putBoolean(PREF_QATT, s.qatt); putBoolean(PREF_NOM, s.nom); putString(PREF_SPACE, s.space.name) }
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
        /** True while the field is open: the keyboard shows the globe key (see SettingsValues.isLanguageSwitchKeyEnabled). */
        @JvmStatic var fieldLanguageKey = false

        private const val PREF_CHOICES = "qatt_compose_send_choices"
        private const val PREF_SETUP_DONE = "qatt_compose_setup_done"
        private const val PREF_NOM = "qatt_compose_nom"
        private const val PREF_PA = "qatt_compose_pa"
        private const val PREF_QATT = "qatt_compose_qatt"
        private const val PREF_SPACE = "qatt_compose_space"
        private const val PREF_PINNED_DONE = "qatt_compose_pinned_done"
        private const val PREF_USAGE = "qatt_compose_usage"
        private const val PREF_FONT = "qatt_compose_font"
        private const val PREF_ICON = "qatt_compose_icon"
        private const val PREF_LUCKY_LAST = "qatt_compose_lucky_last"
        private const val MAX_USAGE = 1000
    }
}
