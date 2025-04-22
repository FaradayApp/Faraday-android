/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.utils

import com.vanniktech.emoji.isOnlyEmojis

/**
 * Replace custom emojis with some other arbitrary emoji, so we can maintain our logic
 * for when to display emoji-only messages larger than others.
 */
fun customToPseudoEmoji(str: String): String {
    return str.replace(Regex("""<img\s+([^>]*)data-mx-emoticon([^>]*)>"""), "\uD83D\uDC40")
}

/**
 * Test if a string contains emojis.
 * It seems that the regex [emoji_regex]+ does not work.
 * Some characters like ?, # or digit are accepted.
 *
 * @param str the body to test
 * @return true if the body contains only emojis
 */
fun containsOnlyEmojis(str: String?): Boolean {
    // Now rely on vanniktech library
    // Emojis sent from desktop such as thumbs-up or down are sent with a variant selection symbol "\ufe0f",
    // that the library identifies as non-emoji character, so remove that manually before.
    // However, use the old check as well since the "white frowning face" emoji gets lost otherwise...?
    return str.isOnlyEmojis() || str?.replace("\ufe0f", "").isOnlyEmojis()
}

/**
 * Same as split, but considering emojis.
 */
fun CharSequence.splitEmoji(): List<CharSequence> {
    val result = mutableListOf<CharSequence>()

    var index = 0

    while (index < length) {
        val firstChar = get(index)

        if (firstChar.code == 0x200e) {
            // Left to right mark. What should I do with it?
        } else if (firstChar.code in 0xD800..0xDBFF && index + 1 < length) {
            // We have the start of a surrogate pair
            val secondChar = get(index + 1)

            if (secondChar.code in 0xDC00..0xDFFF) {
                // We have an emoji
                result.add("$firstChar$secondChar")
                index++
            } else {
                // Not sure what we have here...
                result.add("$firstChar")
            }
        } else {
            // Regular char
            result.add("$firstChar")
        }

        index++
    }

    return result
}
