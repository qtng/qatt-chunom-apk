// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

enum class SendFormat { TEXT, IMAGE }

/**
 * The look of an image: the colour of the text and of the background; a background with an [backgroundEnd] is a gradient from the top left to the bottom right.
 * The names are stored in the settings: do not rename, add at the end.
 */
enum class ImageStyle(val foreground: Int, val background: Int, val backgroundEnd: Int? = null) {
    BLACK_ON_WHITE(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    WHITE_ON_BLACK(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
    INK_ON_RICE_PAPER(0xFF1B1B1B.toInt(), 0xFFF7EEDB.toInt(), 0xFFE2C48C.toInt()),   // black ink on rice paper that warms to old parchment: the classic Asian look
    LACQUER_GRADIENT(0xFFFFFFFF.toInt(), 0xFFC8372D.toInt(), 0xFF5E0B12.toInt()),   // "Lacquer" (sơn mài): vermilion to deep oxblood; it took the place of GOLD_ON_DARK (Gold leaf), see SendFormatChoices
    MOSS_GRADIENT(0xFFFFFFFF.toInt(), 0xFF7E9A4E.toInt(), 0xFF2F4A24.toInt()),   // "Moss garden": it took the place of RED_ON_CREAM (Cinnabar), see SendFormatChoices
    PINK_GRADIENT(0xFFFFFFFF.toInt(), 0xFFEC6FA6.toInt(), 0xFFB0245E.toInt()),
    SUNSET_GRADIENT(0xFFFFFFFF.toInt(), 0xFFF2994A.toInt(), 0xFF6A3093.toInt()),
    OCEAN_GRADIENT(0xFFFFFFFF.toInt(), 0xFF1FA2A6.toInt(), 0xFF1F4E9C.toInt());

    val isGradient get() = backgroundEnd != null

    companion object {
        /** The styles of Lucky draw: the coloured ones (the surprise lives on the colours). */
        val LUCKY_POOL = listOf(LACQUER_GRADIENT, MOSS_GRADIENT, PINK_GRADIENT, SUNSET_GRADIENT, OCEAN_GRADIENT, INK_ON_RICE_PAPER)

        /** A style of [LUCKY_POOL] drawn by chance, never [last] again (two times the same in a row would look broken). */
        fun drawLucky(last: ImageStyle?, random: kotlin.random.Random = kotlin.random.Random.Default): ImageStyle =
            LUCKY_POOL.filter { it != last }.random(random)
    }
}

/**
 * The fonts of the field and of the images: [file] is the big font (QATT, Chữ Nôm and the Latin of Quốc Ngữ in one file), [pa] the PA font (PA is not in the big fonts).
 * Only QATT Pen is in the app ([bundled]); the others are downloaded from cdn.qatt.org on the user's wish ([downloadName] is the file name there, [sizeMb] its size) or imported from a file.
 */
enum class ComposeFont(val label: String, val file: String, val pa: String, val bundled: Boolean, val sizeMb: Int) {
    PEN("QATT Pen", "qatt-pen.ttf", "pa-pen.ttf", true, 27),
    MIGOI("Migoi", "migoi.ttf", "pa-migoi.ttf", false, 42),
    PHONGTHAO("Phong Thảo", "phongthao.ttf", "pa-phongthao.ttf", false, 41);

    val downloadName get() = file
}

/** What the selector shows: Text, or an Image in a style, or an Image in a style drawn by chance every time the field opens ([lucky], "Lucky draw"). */
data class SendChoice(val format: SendFormat, val style: ImageStyle = ImageStyle.BLACK_ON_WHITE, val lucky: Boolean = false) {
    companion object {
        val TEXT = SendChoice(SendFormat.TEXT)
        val IMAGE = SendChoice(SendFormat.IMAGE)
        val LUCKY = SendChoice(SendFormat.IMAGE, lucky = true)
        /** The entries of the dropdown, in this order: an Image in each style (rice paper, the "classic" one, last), Lucky draw, Text last. */
        val ALL = (ImageStyle.values().filter { it != ImageStyle.INK_ON_RICE_PAPER } + ImageStyle.INK_ON_RICE_PAPER).map { SendChoice(SendFormat.IMAGE, it) } + LUCKY + TEXT
    }
}

/**
 * Which format the field is sent in. [choice] is the user's tap on Text or Image (stored by the caller, null until the user has chosen); as long as there is none,
 * the start value is decided for every input field:
 *  1. Image if the app is a chat or social app and the field accepts images (the recipient often cannot show the fonts),
 *  2. else Text if the system can show the characters,
 *  3. else Image if the field accepts images, else Text.
 */
object SendFormatPolicy {
    fun startValue(chatApp: Boolean, acceptsImages: Boolean, systemShowsText: Boolean): SendFormat = when {
        chatApp && acceptsImages -> SendFormat.IMAGE
        systemShowsText -> SendFormat.TEXT
        acceptsImages -> SendFormat.IMAGE
        else -> SendFormat.TEXT
    }

    /** What the selector shows: the choice of the user, else the start value (in the default style, a setting). */
    fun selected(choice: SendChoice?, chatApp: Boolean, acceptsImages: Boolean, systemShowsText: Boolean, defaultStyle: ImageStyle = ImageStyle.BLACK_ON_WHITE): SendChoice =
        choice ?: SendChoice(startValue(chatApp, acceptsImages, systemShowsText), defaultStyle)

    /** What is sent: Image falls back to Text where the field accepts no images. */
    fun effective(selected: SendFormat, acceptsImages: Boolean): SendFormat = if (selected == SendFormat.IMAGE && !acceptsImages) SendFormat.TEXT else selected
}
