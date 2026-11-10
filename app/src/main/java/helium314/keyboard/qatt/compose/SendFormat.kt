// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

enum class SendFormat { TEXT, IMAGE }

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

    /** The format shown in the selector. */
    fun selected(choice: SendFormat?, chatApp: Boolean, acceptsImages: Boolean, systemShowsText: Boolean): SendFormat =
        choice ?: startValue(chatApp, acceptsImages, systemShowsText)

    /** What is sent: Image falls back to Text where the field accepts no images. */
    fun effective(selected: SendFormat, acceptsImages: Boolean): SendFormat = if (selected == SendFormat.IMAGE && !acceptsImages) SendFormat.TEXT else selected
}
