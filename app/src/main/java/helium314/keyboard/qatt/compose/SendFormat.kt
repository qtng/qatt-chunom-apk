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
    GOLD_ON_DARK(0xFFE8BD55.toInt(), 0xFF16181F.toInt()),
    RED_ON_CREAM(0xFFB3261E.toInt(), 0xFFF8F0DC.toInt()),
    PINK_GRADIENT(0xFFFFFFFF.toInt(), 0xFFEC6FA6.toInt(), 0xFFB0245E.toInt()),
    SUNSET_GRADIENT(0xFFFFFFFF.toInt(), 0xFFF2994A.toInt(), 0xFF6A3093.toInt()),
    OCEAN_GRADIENT(0xFFFFFFFF.toInt(), 0xFF1FA2A6.toInt(), 0xFF1F4E9C.toInt());

    val isGradient get() = backgroundEnd != null
}

/**
 * The fonts of the field and of the images: [file] is the big font (QATT, Chữ Nôm and the Latin of Quốc Ngữ in one file), [pa] the PA font (PA is not in the big fonts).
 * Only QATT Pen is in the app ([bundled]); the others are imported by the user ([downloadName] is the file name on cdn.qatt.org and chunom.org).
 */
enum class ComposeFont(val label: String, val file: String, val pa: String, val bundled: Boolean) {
    PEN("QATT Pen", "qatt-pen.ttf", "pa-pen.ttf", true),
    MIGOI("Migoi", "migoi.ttf", "pa-migoi.ttf", false),
    PHONGTHAO("Phong Thảo", "phongthao.ttf", "pa-phongthao.ttf", false);

    val downloadName get() = file
}

/** What the selector shows: Text, or an Image in a style. */
data class SendChoice(val format: SendFormat, val style: ImageStyle = ImageStyle.BLACK_ON_WHITE) {
    companion object {
        val TEXT = SendChoice(SendFormat.TEXT)
        val IMAGE = SendChoice(SendFormat.IMAGE)
        /** The entries of the dropdown, in this order: Text, then an Image in each style. */
        val ALL = listOf(TEXT) + ImageStyle.values().map { SendChoice(SendFormat.IMAGE, it) }
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
