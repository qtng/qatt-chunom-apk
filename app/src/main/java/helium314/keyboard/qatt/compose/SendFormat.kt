// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

enum class SendFormat { TEXT, IMAGE }

/** The colours of an image (ARGB): the text and the background. */
enum class ImageStyle(val foreground: Int, val background: Int) {
    BLACK_ON_WHITE(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    WHITE_ON_BLACK(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
}

/** What the selector shows: Text, or an Image in a style. */
data class SendChoice(val format: SendFormat, val style: ImageStyle = ImageStyle.BLACK_ON_WHITE) {
    companion object {
        val TEXT = SendChoice(SendFormat.TEXT)
        val IMAGE = SendChoice(SendFormat.IMAGE)
        /** The entries of the dropdown, in this order. */
        val ALL = listOf(TEXT, SendChoice(SendFormat.IMAGE, ImageStyle.BLACK_ON_WHITE), SendChoice(SendFormat.IMAGE, ImageStyle.WHITE_ON_BLACK))
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
