// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.TextUtils
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * The panel of the composition field above the keyboard.
 *
 * Top row: the arrow to the left (discards and closes), the field, the selector of the format, the arrow up (sends).
 * Below: ⚙, the five candidates and a triangle down (when there are more candidates), which opens the rest of them as a grid in the place of the keyboard.
 * It only shows what [ComposeController] tells it and reports taps to it.
 *
 * Text: the field looks like a normal input and its cursor blinks. Image: the field looks like the image that will be sent (its colours or gradient), the cursor does not blink.
 */
class ComposePanelView(context: Context, private val fonts: ComposeFonts) : LinearLayout(context) {
    interface Listener {
        fun onBack()
        fun onSend()
        /** A tap into the field: [offset] is the place in the text of the field (not in what is shown) */
        fun onFieldTapped(offset: Int)
        /** [index]: the place in all candidates, also for the grid */
        fun onCandidate(index: Int)
        fun onFormatSelector(anchor: View)
        fun onSettings(anchor: View)
        fun onMoreToggle()
        /** The first-time setup: a script switched on or off (0 PA, 1 QATT, 2 Chữ Nôm), the confirmation */
        fun onSetupToggle(index: Int)
        fun onSetupConfirm()
    }

    var listener: Listener? = null

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val back = iconButton(R.drawable.ic_arrow_back, context.getString(R.string.compose_back)) { listener?.onBack() }
    val field = FieldView(context)
    private val format = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(10), 0, dp(10), 0)
        maxLines = 1
        isClickable = true
        setOnClickListener { listener?.onFormatSelector(it) }
    }
    private val send = iconButton(R.drawable.ic_compose_send, context.getString(R.string.compose_send)) { listener?.onSend() }
    private val settings = ImageButton(context, null, R.attr.suggestionWordStyle).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.SETTINGS.name, context))
        contentDescription = context.getString(R.string.compose_settings)
        setOnClickListener { listener?.onSettings(it) }
    }
    private val cells = List(CANDIDATES) { i -> CandidateCell(context).also { it.setOnClickListener { listener?.onCandidate(i) } } }
    private val more = iconButton(R.drawable.ic_compose_more, context.getString(R.string.compose_more)) { listener?.onMoreToggle() }
    private val candidateRow = LinearLayout(context)
    private val grid = GridLayout(context)
    private val gridScroll = ScrollView(context)
    private val setupScroll = ScrollView(context)
    private val setupBox = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        visibility = GONE
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(settings, LayoutParams(dp(44), dp(44)))
        top.addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), dp(4), dp(2), dp(4)) })
        top.addView(format, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(dp(2), 0, dp(2), 0) })
        top.addView(send, LayoutParams(dp(40), dp(40)).apply { setMargins(dp(4), 0, dp(4), 0) })
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        candidateRow.gravity = Gravity.CENTER_VERTICAL
        candidateRow.addView(back, LayoutParams(dp(40), dp(48)))
        for (c in cells) candidateRow.addView(c, LayoutParams(0, dp(48), 1f))
        candidateRow.addView(more, LayoutParams(dp(40), dp(48)))
        addView(candidateRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        setupScroll.addView(setupBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setupScroll.visibility = GONE
        addView(setupScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        gridScroll.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        gridScroll.visibility = GONE
        addView(gridScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        field.onTap = { shownOffset -> listener?.onFieldTapped(originalOffset(shownOffset)) }
        applyColors()
    }

    private var sendReady = false

    /** The send button is a round button, grey while the field is empty and coloured (the strong blue of the Ocean style) as soon as there is text. */
    fun setSendReady(ready: Boolean) { if (ready != sendReady) { sendReady = ready; styleSend() } }

    private fun styleSend() {
        val theme = colors
        send.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (sendReady) 0xFFFF6600.toInt() else (theme.get(ColorType.KEY_TEXT) and 0x00FFFFFF) or 0x22000000)
            setStroke(dp(1), if (sendReady) theme.get(ColorType.KEY_HINT_TEXT) else Color.TRANSPARENT)
        }
        send.setColorFilter(if (sendReady) 0xFFFFFFFF.toInt() else (theme.get(ColorType.KEY_TEXT) and 0x00FFFFFF) or 0x66000000)
        send.isEnabled = sendReady
    }

    private fun iconButton(icon: Int, description: String, onClick: () -> Unit) = ImageButton(context, null, R.attr.suggestionWordStyle).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(icon)
        contentDescription = description
        setOnClickListener { onClick() }
    }

    private val colors: Colors get() = Settings.getValues().mColors

    /** The colours of the keyboard theme (also after a change of the theme). */
    fun applyColors() {
        val theme = colors
        theme.setBackground(this, ColorType.STRIP_BACKGROUND)
        for (b in listOf(back, settings, more)) { theme.setColor(b, ColorType.TOOL_BAR_KEY); theme.setBackground(b, ColorType.STRIP_BACKGROUND) }
        format.setTextColor(theme.get(ColorType.KEY_TEXT))
        styleSend()
        val keyBackground = theme.get(ColorType.KEY_BACKGROUND)
        format.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(keyBackground)
        }
        for (c in cells) c.applyColors(theme)
    }

    // what the field shows and what it stands for: in an image the text is set in other lines (spaces and line breaks differ), so the place of the cursor is mapped by
    // the characters that are not white space: the n-th of them is the same in the text and in what is shown
    private var originalText = ""
    private var shownText = ""

    private fun significant(s: CharSequence, upTo: Int): Int { var n = 0; for (i in 0 until upTo.coerceAtMost(s.length)) if (!s[i].isWhitespace()) n++; return n }

    /** The place after the [n]-th character that is not white space; if the place before is white space in the source, behind all of it. */
    private fun placeAfter(s: String, n: Int, skipSpaceAfter: Boolean): Int {
        var p = 0
        if (n > 0) { var c = 0; p = s.length; for (i in s.indices) if (!s[i].isWhitespace() && ++c == n) { p = i + 1; break } }
        if (skipSpaceAfter) while (p < s.length && s[p].isWhitespace()) p++
        return p
    }

    private fun shownOffset(cursor: Int): Int {
        if (shownText == originalText) return cursor
        if (cursor >= originalText.length) return shownText.length
        return placeAfter(shownText, significant(originalText, cursor), cursor > 0 && originalText[cursor - 1].isWhitespace())
    }

    private fun originalOffset(shown: Int): Int {
        if (shownText == originalText) return shown
        if (shown >= shownText.length) return originalText.length
        return placeAfter(originalText, significant(shownText, shown), shown > 0 && shownText[shown - 1].isWhitespace())
    }

    /**
     * The text of the field with its fonts and the [cursor] in it; [composingStart] on: the word being typed (up to the cursor), underlined. With [image] the field shows the
     * text as the image will set it: the same lines (see [ImageRenderer.lines]), centred, so that the result can be seen while typing.
     */
    fun setContent(text: String, composingStart: Int, cursor: Int, image: Boolean = false) {
        // the empty lines after Enter are kept (the image drops them, but the cursor must be seen on the new line)
        val newlines = text.takeLastWhile { it == '\n' || it == ' ' }.count { it == '\n' }
        val shown = if (image) ImageRenderer.lines(text).joinToString("\n") + "\n".repeat(newlines) else text
        originalText = text
        shownText = shown
        val styled = fonts.styled(shown)
        val shownCursor = shownOffset(cursor)
        if (cursor > composingStart) {
            val start = shownOffset(composingStart).coerceAtMost(shownCursor)
            if (start < shownCursor) styled.setSpan(UnderlineSpan(), start, shownCursor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        field.gravity = if (image) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.START
        field.setStyledText(styled, shownCursor)
    }

    /** [selected]: the keys type into the field and the candidates are shown. */
    fun setLook(image: Boolean, style: ImageStyle, selected: Boolean) {
        field.setLook(colors, image, style, selected)
        candidateRow.visibility = if (selected) VISIBLE else GONE
        if (!selected) hideGrid()
    }

    fun setFormatLabel(label: String) { format.text = "$label ▾" }

    /** A status (the progress of a font download) in the empty field; null: the usual hint. */
    fun setStatus(text: String?) { field.hint = text ?: context.getString(R.string.compose_hint) }

    /** The first candidates (at most five); a place without one is empty. */
    fun setCandidates(list: List<Candidate>) {
        cells.forEachIndexed { i, cell -> cell.set(list.getOrNull(i), fonts) }
    }

    /** The triangle: shown when there are more candidates; pointing up while they are open. */
    fun setMore(available: Boolean, open: Boolean) {
        more.visibility = if (available) VISIBLE else INVISIBLE
        more.setImageResource(if (open) R.drawable.ic_compose_less else R.drawable.ic_compose_more)
        more.contentDescription = context.getString(if (open) R.string.compose_less else R.string.compose_more)
        colors.setColor(more, ColorType.TOOL_BAR_KEY)
    }

    /**
     * The candidates from place [firstIndex] on as a grid, [height] pixels high (the height of the keyboard, which is hidden meanwhile).
     * A tap reports the place in all candidates.
     */
    fun showGrid(list: List<Candidate>, firstIndex: Int, height: Int) {
        grid.removeAllViews()
        val totalWidth = if (this.width > 0) this.width else context.resources.displayMetrics.widthPixels
        val columns = (totalWidth / dp(72)).coerceIn(3, 8)
        grid.columnCount = columns
        val cellWidth = totalWidth / columns
        list.forEachIndexed { i, c ->
            val cell = CandidateCell(context)
            cell.applyColors(colors)
            cell.set(c, fonts)
            cell.setOnClickListener { listener?.onCandidate(firstIndex + i) }
            grid.addView(cell, GridLayout.LayoutParams().apply { width = cellWidth; this.height = dp(56) })
        }
        gridScroll.layoutParams = (gridScroll.layoutParams as LayoutParams).also { it.height = height.coerceAtLeast(dp(120)) }
        gridScroll.scrollTo(0, 0)
        gridScroll.visibility = VISIBLE
    }

    /**
     * The first-time setup in the place of the keyboard ([height] pixels high), all centred: which scripts to type. Nothing is chosen at first; OK is possible only when a
     * script is on. (What Space does follows from the choice, see [ComposeController].)
     */
    fun showSetup(scripts: BooleanArray, height: Int) {
        hideGrid()
        candidateRow.visibility = GONE
        setupBox.removeAllViews()
        setupBox.gravity = Gravity.CENTER_HORIZONTAL
        val text = colors.get(ColorType.KEY_TEXT)
        val accent = colors.get(ColorType.TOOL_BAR_KEY)
        fun pad(v: View, h: Int, vv: Int) = v.setPadding(dp(h), dp(vv), dp(h), dp(vv))
        setupBox.addView(TextView(context).apply {
            this.text = context.getString(R.string.compose_setup_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(text)
            gravity = Gravity.CENTER
            pad(this, 16, 14)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val chips = LinearLayout(context).apply { gravity = Gravity.CENTER }
        listOf("PA", "QATT", "喃").forEachIndexed { i, name ->
            val on = scripts[i]
            chips.addView(TextView(context).apply {
                this.text = (if (on) "✓ " else "") + name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(text)
                gravity = Gravity.CENTER
                pad(this, 20, 10)
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(if (on) (accent and 0x00FFFFFF) or 0x55000000 else Color.TRANSPARENT)
                    setStroke(dp(1), (text and 0x00FFFFFF) or (if (on) 0xAA000000.toInt() else 0x55000000))
                }
                setOnClickListener { listener?.onSetupToggle(i) }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(6), dp(4), dp(6), dp(4)) })
        }
        setupBox.addView(chips, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val ready = scripts.any { it }
        setupBox.addView(TextView(context).apply {
            this.text = context.getString(R.string.compose_setup_ok)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            setTextColor(if (ready) 0xFF1B1B1B.toInt() else text)
            alpha = if (ready) 1f else .35f
            pad(this, 36, 11)
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(if (ready) accent else Color.TRANSPARENT); setStroke(dp(1), (text and 0x00FFFFFF) or 0x55000000) }
            if (ready) setOnClickListener { listener?.onSetupConfirm() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), dp(22), dp(16), dp(8)) })
        setupScroll.layoutParams = (setupScroll.layoutParams as LayoutParams).also { it.height = height.coerceAtLeast(dp(220)) }
        setupScroll.visibility = VISIBLE
    }

    fun hideSetup() {
        if (setupScroll.visibility == VISIBLE) { setupScroll.visibility = GONE; setupBox.removeAllViews() }
    }

    fun hideGrid() {
        gridScroll.visibility = GONE
        grid.removeAllViews()
    }

    class CandidateCell(context: Context) : LinearLayout(context) {
        private val glyph = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val label = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            alpha = .6f
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(glyph, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            isClickable = true
            visibility = INVISIBLE
        }

        fun applyColors(colors: Colors) {
            glyph.setTextColor(colors.get(ColorType.KEY_TEXT))
            label.setTextColor(colors.get(ColorType.KEY_TEXT))
        }

        fun set(c: Candidate?, fonts: ComposeFonts) {
            if (c == null) { visibility = INVISIBLE; glyph.text = ""; label.text = ""; return }
            visibility = VISIBLE
            glyph.text = fonts.styled(c.text)
            label.text = when (c.kind) { Kind.PA -> "PA"; Kind.QATT -> "QATT"; Kind.NOM -> c.reading.orEmpty() }
        }
    }

    /** The field: text with the word being typed underlined, and the cursor (always at the end), blinking in Text, steady in Image. */
    class FieldView(context: Context) : TextView(context) {
        private var blinking = true
        private var caretOn = true
        private var caretColor = 0xFF000000.toInt()
        private val caretPaint = Paint()
        private val blink = object : Runnable {
            override fun run() {
                if (!blinking || !isAttachedToWindow) return
                caretOn = !caretOn
                invalidate()
                postDelayed(this, 530)
            }
        }

        init {
            val dp = context.resources.displayMetrics.density
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            minHeight = (44 * dp).toInt()
            setMaxLines(3)
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            isClickable = true
            hint = context.getString(R.string.compose_hint)
            typeface = Typeface.DEFAULT
        }

        /** The cursor (a place in the shown text) and a tap into the field (the place that was hit). */
        private var caret = 0
        var onTap: ((Int) -> Unit)? = null

        fun setStyledText(text: CharSequence, cursor: Int) {
            setText(text)
            caret = cursor.coerceIn(0, text.length)
            caretOn = true
            post { scrollToCaret() }
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP && onTap != null) {
                performClick()
                onTap?.invoke(if (layout == null || text.isEmpty()) 0 else getOffsetForPosition(event.x, event.y))
                return true
            }
            return super.onTouchEvent(event) || event.actionMasked == android.view.MotionEvent.ACTION_DOWN
        }

        override fun performClick(): Boolean { super.performClick(); return true }

        /** Scrolls so that the line with the cursor is seen. */
        private fun scrollToCaret() {
            val l = layout ?: return
            val visible = height - paddingTop - paddingBottom
            val bottom = l.getLineBottom(l.getLineForOffset(caret.coerceAtMost(text.length)))
            scrollTo(0, (bottom - visible).coerceAtLeast(0))
        }

        fun setLook(colors: Colors, image: Boolean, style: ImageStyle, selected: Boolean) {
            val dp = resources.displayMetrics.density
            val accent = colors.get(ColorType.TOOL_BAR_KEY)
            val border = if (selected) accent else colors.get(ColorType.KEY_HINT_TEXT)
            val keyFill = colors.get(ColorType.KEY_BACKGROUND)
            val bg = GradientDrawable().apply {
                cornerRadius = (if (image) 6 else 16) * dp
                val end = style.backgroundEnd
                if (image && end != null) {
                    orientation = GradientDrawable.Orientation.TL_BR
                    setColors(intArrayOf(style.background, end))
                } else setColor(if (image) style.background else keyFill)
                setStroke(((if (selected) 2 else 1) * dp).toInt(), border)
            }
            background = bg
            val fg = if (image) style.foreground else colors.get(ColorType.KEY_TEXT)
            setTextColor(fg)
            setHintTextColor((fg and 0x00FFFFFF) or 0x66000000)
            caretColor = fg
            blinking = selected
            caretOn = true
            removeCallbacks(blink)
            if (blinking) postDelayed(blink, 530)
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            removeCallbacks(blink)
            if (blinking) postDelayed(blink, 530)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(blink)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!caretOn) return
            val l = layout
            val dp = resources.displayMetrics.density
            val x: Float
            val top: Int
            val bottom: Int
            if (l == null || text.isEmpty()) {
                x = if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) width / 2f else paddingLeft.toFloat()
                top = paddingTop
                bottom = paddingTop + lineHeight
            } else {
                val at = caret.coerceAtMost(text.length)
                val line = l.getLineForOffset(at)
                val r = android.graphics.Rect()
                getLineBounds(line, r)                       // in the coordinates of the view: includes the padding and the vertical centring
                x = paddingLeft + l.getPrimaryHorizontal(at)
                top = r.top
                bottom = r.bottom
            }
            caretPaint.color = if (blinking) caretColor else (caretColor and 0x00FFFFFF) or 0x66000000   // steady and pale in Image
            canvas.drawRect(x, top.toFloat(), x + 2 * dp, bottom.toFloat(), caretPaint)
        }
    }

    companion object {
        const val CANDIDATES = 5
    }
}
