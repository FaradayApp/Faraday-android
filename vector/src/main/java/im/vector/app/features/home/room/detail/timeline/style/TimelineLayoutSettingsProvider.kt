/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.style

import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.themes.BubbleThemeUtils
import javax.inject.Inject

class TimelineLayoutSettingsProvider @Inject constructor(/*private val vectorPreferences: VectorPreferences,*/ private val bubbleThemeUtils: BubbleThemeUtils) {

    fun getLayoutSettings(): TimelineLayoutSettings {
        return when (bubbleThemeUtils.getBubbleStyle()) {
            BubbleThemeUtils.BUBBLE_STYLE_NONE -> TimelineLayoutSettings.MODERN
            BubbleThemeUtils.BUBBLE_STYLE_ELEMENT -> TimelineLayoutSettings.BUBBLE
            else -> TimelineLayoutSettings.SC_BUBBLE
        }
        /*
        return if (vectorPreferences.useMessageBubblesLayout()) {
            TimelineLayoutSettings.BUBBLE
        } else {
            TimelineLayoutSettings.MODERN
        }
         */
    }
}
