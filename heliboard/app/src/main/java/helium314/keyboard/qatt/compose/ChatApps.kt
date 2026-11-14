// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

/** Is the app of the input field a chat or social app (`EditorInfo.packageName`)? First parts of the package name, then a list of package names, then the category the app declares. */
object ChatApps {
    /** `ApplicationInfo.CATEGORY_SOCIAL` (API 26) */
    const val CATEGORY_SOCIAL = 4

    /**
     * Parts of a package name (lower case) that make an app a chat or social app: "facebook" matches com.facebook.katana, com.facebook.orca (Messenger), com.facebook.lite, …
     * Only names that are rare in other packages; X is found by "twitter" (its package is still com.twitter.android), not by "x".
     */
    val NAME_PARTS = listOf(
        "facebook", "instagram", "messenger", "whatsapp", "telegram", "discord", "snapchat", "twitter", "viber", ".zalo", "linkedin", "pinterest", "tumblr", "mastodon",
    )

    /** Packages whose name has no such part (from memory: check on a device). The user can add more in the settings (`extra`). */
    val PACKAGES = setOf(
        "org.thoughtcrime.securesms", "com.google.android.apps.messaging", "com.samsung.android.messaging", "jp.naver.line.android",
        "com.reddit.frontpage", "com.Slack", "com.microsoft.teams", "com.tencent.mm", "com.zhiliaoapp.musically",
    )

    /**
     * [category]: the declared category of a package, null if unknown. It may fail (a keyboard cannot always read other apps, Android 11+): that counts as unknown.
     */
    fun isChatOrSocial(packageName: String?, extra: Set<String> = emptySet(), category: (String) -> Int? = { null }): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (packageName in PACKAGES || packageName in extra) return true
        val lower = packageName.lowercase()
        if (NAME_PARTS.any { it in lower }) return true
        return try { category(packageName) == CATEGORY_SOCIAL } catch (e: Exception) { false }
    }
}
