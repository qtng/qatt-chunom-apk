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
        fun onFieldTapped()
        /** [index]: the place in all candidates, also for the grid */
        fun onCandidate(index: Int)
        fun onFormatSelector(anchor: View)
        fun onSettings(anchor: View)
        fun onMoreToggle()
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

    init {
        orientation = VERTICAL
        visibility = GONE
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(settings, LayoutParams(dp(44), dp(44)))
        top.addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), dp(4), dp(2), dp(4)) })
        top.addView(format, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(dp(2), 0, dp(2), 0) })
        top.addView(send, LayoutParams(dp(44), dp(44)))
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        candidateRow.gravity = Gravity.CENTER_VERTICAL
        candidateRow.addView(back, LayoutParams(dp(40), dp(48)))
        for (c in cells) candidateRow.addView(c, LayoutParams(0, dp(48), 1f))
        candidateRow.addView(more, LayoutParams(dp(40), dp(48)))
        addView(candidateRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        gridScroll.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        gridScroll.visibility = GONE
        addView(gridScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        field.setOnClickListener { listener?.onFieldTapped() }
        applyColors()
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
        for (b in listOf(back, send, settings, more)) { theme.setColor(b, ColorType.TOOL_BAR_KEY); theme.setBackground(b, ColorType.STRIP_BACKGROUND) }
        format.setTextColor(theme.get(ColorType.KEY_TEXT))
        val keyBackground = theme.get(ColorType.KEY_BACKGROUND)
        format.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(keyBackground)
        }
        for (c in cells) c.applyColors(theme)
    }

    /**
     * The text of the field with its fonts; [composingStart] on: the word being typed, underlined. With [image] the field shows the text as the image will set it:
     * the same lines (at most 5 + L characters a line, see [ImageRenderer.lines]), centred, so that the result can be seen while typing.
     */
    fun setContent(text: String, composingStart: Int, image: Boolean = false) {
        // the empty lines after Enter are kept (the image drops them, but the cursor must be seen on the new line)
        val newlines = text.takeLastWhile { it == '\n' || it == ' ' }.count { it == '\n' }
        val shown = if (image) ImageRenderer.lines(text).joinToString("\n") + "\n".repeat(newlines) else text
        val styled = fonts.styled(shown)
        val typed = text.length - composingStart                      // characters of the word being typed
        if (typed > 0) {
            var start = shown.length
            var left = typed
            while (start > 0 && left > 0) { start--; if (shown[start] != '\n') left-- }
            styled.setSpan(UnderlineSpan(), start, shown.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        field.gravity = if (image) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.START
        field.setStyledText(styled)
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

        fun setStyledText(text: CharSequence) {
            setText(text)
            caretOn = true
            post { scrollToEnd() }
        }

        private fun scrollToEnd() {
            val l = layout ?: return
            val visible = height - paddingTop - paddingBottom
            scrollTo(0, (l.getLineTop(l.lineCount) - visible).coerceAtLeast(0))
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
                x = paddingLeft.toFloat()
                top = paddingTop
                bottom = paddingTop + lineHeight
            } else {
                val line = l.getLineForOffset(text.length)
                val r = android.graphics.Rect()
                getLineBounds(line, r)                       // in the coordinates of the view: includes the padding and the vertical centring
                x = paddingLeft + l.getPrimaryHorizontal(text.length)
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
