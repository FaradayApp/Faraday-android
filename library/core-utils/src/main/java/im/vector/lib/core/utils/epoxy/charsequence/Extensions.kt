/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.lib.core.utils.epoxy.charsequence

import android.text.TextUtils

/**
 * Extensions to wrap CharSequence to EpoxyCharSequence.
 */
fun CharSequence.toEpoxyCharSequence() = EpoxyCharSequence(this)

fun CharSequence.toMessageTextEpoxyCharSequence(): EpoxyCharSequence {
    var m = this
    if (m.isNotEmpty()) {
        // Remove last trailing newline: looks especially bad in message bubble
        if (m.last() == '\n') {
            m = m.subSequence(0, m.length-1)
        }
        // Add a narrow non-breakable space to work around wrap_content cutting italic text | https://stackoverflow.com/questions/4353836/italic-textview-with-wrap-contents-seems-to-clip-the-text-at-right-edge
        // (interestingly, this seems to be only relevant for the last character even for multi-line messages)
        m = TextUtils.concat(m, "\u202f")
    }
    return m.toEpoxyCharSequence()
}
