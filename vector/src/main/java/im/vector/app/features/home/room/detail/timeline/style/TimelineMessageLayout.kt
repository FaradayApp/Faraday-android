/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.style

import android.os.Parcelable
import androidx.annotation.DrawableRes
import im.vector.app.R
import im.vector.app.features.home.room.detail.timeline.view.infoInBubbles
import im.vector.app.features.themes.ScBubbleAppearance
import kotlinx.parcelize.Parcelize

sealed interface TimelineMessageLayout : Parcelable {

    val layoutRes: Int
    val showAvatar: Boolean
    val showDisplayName: Boolean
    val showTimestamp: Boolean

    open fun showsE2eDecorationInFooter(): Boolean = false

    @Parcelize
    data class Default(
            override val showAvatar: Boolean,
            override val showDisplayName: Boolean,
            override val showTimestamp: Boolean,
            // Keep defaultLayout generated on epoxy items
            override val layoutRes: Int = 0,
    ) : TimelineMessageLayout

    @Parcelize
    data class Bubble(
            override val showAvatar: Boolean,
            override val showDisplayName: Boolean,
            override val showTimestamp: Boolean = true,
            val addTopMargin: Boolean = false,
            val isIncoming: Boolean,
            val isPseudoBubble: Boolean,
            val cornersRadius: CornersRadius,
            val timestampInsideMessage: Boolean,
            val addMessageOverlay: Boolean,
            override val layoutRes: Int = if (isIncoming) {
                R.layout.item_timeline_event_bubble_incoming_base
            } else {
                R.layout.item_timeline_event_bubble_outgoing_base
            },
    ) : TimelineMessageLayout {

        @Parcelize
        data class CornersRadius(
                val topStartRadius: Float,
                val topEndRadius: Float,
                val bottomStartRadius: Float,
                val bottomEndRadius: Float,
        ) : Parcelable
    }

    @Parcelize
    data class ScBubble(
            override val showAvatar: Boolean,
            override val showDisplayName: Boolean,
            override val showTimestamp: Boolean = true,
            val bubbleAppearance: ScBubbleAppearance,
            val isIncoming: Boolean,
            val reverseBubble: Boolean,
            val singleSidedLayout: Boolean,
            val isRealBubble: Boolean,
            val isPseudoBubble: Boolean,
            val isNotice: Boolean,
            val timestampAsOverlay: Boolean,
            override val layoutRes: Int = if (isIncoming) {
                R.layout.item_timeline_event_sc_bubble_incoming_base
            } else {
                R.layout.item_timeline_event_sc_bubble_outgoing_base
            },
            @DrawableRes
            val bubbleDrawable: Int = if (isPseudoBubble) {
                0
            } else if (showAvatar) { // tail
                if (reverseBubble) { // outgoing
                    bubbleAppearance.textBubbleOutgoing
                } else { // incoming
                    bubbleAppearance.textBubbleIncoming
                }
            } else { // notail
                if (reverseBubble) { // outgoing
                    bubbleAppearance.textBubbleOutgoingNoTail
                } else { // incoming
                    bubbleAppearance.textBubbleIncomingNoTail
                }
            }
    ) : TimelineMessageLayout {
        override fun showsE2eDecorationInFooter(): Boolean = infoInBubbles(this)
    }

}
