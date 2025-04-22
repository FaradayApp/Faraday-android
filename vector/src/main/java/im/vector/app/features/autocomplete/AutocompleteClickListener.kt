/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.autocomplete

import im.vector.app.features.autocomplete.member.AutocompleteEmojiDataItem

/**
 * Simple generic listener interface.
 */
interface AutocompleteClickListener<T> {

    fun onItemClick(t: T)

    fun onLoadMoreClick(item: AutocompleteEmojiDataItem.Expand) {}

    fun maxShowSizeOverride(): Int? = null
}
