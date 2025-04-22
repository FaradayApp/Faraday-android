/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail

import android.view.View
import de.spiritcroc.recyclerview.widget.BetterLinearLayoutManager
import im.vector.app.core.platform.DefaultListUpdateCallback
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.item.ItemWithEvents
import org.matrix.android.sdk.api.extensions.tryOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

class ScrollOnNewMessageCallback(
        private val layoutManager: BetterLinearLayoutManager,
        private val timelineEventController: TimelineEventController,
        private val parentView: View
) : DefaultListUpdateCallback {

    private val newTimelineEventIds = CopyOnWriteArrayList<String>()
    private var forceScroll = false
    var initialForceScroll = false
        set(value) {
            field = value
            if (!value) {
                layoutManager.setPreferredAnchorPosition(-1)
            }
        }
    var initialForceScrollEventId: String? = null

    fun addNewTimelineEventIds(eventIds: List<String>) {
        // Disable initial force scroll
        initialForceScroll = false
        // Update force scroll id when sticking to the bottom - TODO try this if staying at bottom is not reliable as well
        /*
        if (eventIds.isNotEmpty()) {
            initialForceScrollEventId.let {
                if (it != null && it == timelineEventController.timeline?.getInitialEventId()) {
                    initialForceScrollEventId = eventIds[0]
                }
            }
        }
         */
        newTimelineEventIds.addAll(0, eventIds)
    }

    fun forceScrollOnNextUpdate() {
        forceScroll = true
    }

    override fun onInserted(position: Int, count: Int) {
        if (initialForceScroll) {
            var scrollToEvent = initialForceScrollEventId
            var scrollOffset = 0
            if (initialForceScrollEventId == null) {
                scrollToEvent = timelineEventController.timeline?.getTargetEventId()
                scrollOffset = timelineEventController.timeline?.getTargetEventOffset() ?: 0
            }
            if (scrollToEvent == null) {
                layoutManager.scrollToPositionWithOffset(0, 0)
                layoutManager.setPreferredAnchorPosition(0)
            } else {
                timelineEventController.searchPositionOfEvent(scrollToEvent)?.let {
                    // Scroll such that the scrolled-to event is moved down (1-TARGET_SCROLL_OUT_FACTOR) of the screen.
                    // To do that, we actually scroll the view above out by TARGET_SCROLL_OUT_FACTOR (since we can only control the distance
                    // from the bottom of the view, not the top).
                    val scrollToPosition = max(it + scrollOffset + 1, 0)
                    layoutManager.scrollToPositionWithOffset(scrollToPosition, (parentView.measuredHeight * TimelineFragment.TARGET_SCROLL_OUT_FACTOR).toInt())
                    layoutManager.setPreferredAnchorPosition(scrollToPosition)
                }
            }
            return
        }
        if (position != 0) {
            return
        }
        if (forceScroll) {
            forceScroll = false
            layoutManager.scrollToPositionWithOffset(0, 0)
            return
        }
        if (layoutManager.findFirstVisibleItemPosition() > 1) {
            return
        }
        val firstNewItem = tryOrNull {
            timelineEventController.adapter.getModelAtPosition(position)
        } as? ItemWithEvents ?: return
        val firstNewItemIds = firstNewItem.getEventIds().firstOrNull() ?: return
        val indexOfFirstNewItem = newTimelineEventIds.indexOf(firstNewItemIds)
        if (indexOfFirstNewItem != -1) {
            while (newTimelineEventIds.lastOrNull() != firstNewItemIds) {
                newTimelineEventIds.removeLastOrNull()
            }
            layoutManager.scrollToPositionWithOffset(0, 0)
        }
    }
}
