// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

/** Is the app of the input field a chat or social app (`EditorInfo.packageName`)? First a list of package names, then the category the app declares. */
object ChatApps {
    /** `ApplicationInfo.CATEGORY_SOCIAL` (API 26) */
    const val CATEGORY_SOCIAL = 4

    /** Package names (from memory: check on a device). The user can add more in the settings (`extra`). */
    val PACKAGES = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.facebook.katana", "com.facebook.lite", "com.instagram.android", "com.instagram.barcelona", "com.twitter.android",
        "com.discord", "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.viber.voip", "jp.naver.line.android",
        "com.snapchat.android", "com.zing.zalo", "com.reddit.frontpage", "com.Slack", "com.microsoft.teams", "com.tencent.mm",
    )

    /**
     * [category]: the declared category of a package, null if unknown. It may fail (a keyboard cannot always read other apps, Android 11+): that counts as unknown.
     */
    fun isChatOrSocial(packageName: String?, extra: Set<String> = emptySet(), category: (String) -> Int? = { null }): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (packageName in PACKAGES || packageName in extra) return true
        return try { category(packageName) == CATEGORY_SOCIAL } catch (e: Exception) { false }
    }
}
