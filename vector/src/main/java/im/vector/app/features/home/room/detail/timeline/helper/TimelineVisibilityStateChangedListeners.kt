/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.helper

import com.airbnb.epoxy.VisibilityState
import de.spiritcroc.matrixsdk.util.DbgUtil
import de.spiritcroc.matrixsdk.util.Dimber
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent

class ReadMarkerVisibilityStateChangedListener(private val callback: TimelineEventController.Callback?) :
        VectorEpoxyModel.OnVisibilityStateChangedListener {

    private val rmDimber = Dimber("ReadMarkerDbg", DbgUtil.DBG_READ_MARKER)

    override fun onVisibilityStateChanged(visibilityState: Int) {
        rmDimber.i { "onVisibilityChanged: $visibilityState" }
        if (visibilityState in listOf(VisibilityState.VISIBLE,
                        VisibilityState.FOCUSED_VISIBLE,
                        VisibilityState.UNFOCUSED_VISIBLE,
                        VisibilityState.FULL_IMPRESSION_VISIBLE)) {
            callback?.onReadMarkerVisible()
        }
    }
}

class TimelineEventVisibilityStateChangedListener(
        private val callback: TimelineEventController.Callback?,
        private val event: TimelineEvent
) :
        VectorEpoxyModel.OnVisibilityStateChangedListener {

    override fun onVisibilityStateChanged(visibilityState: Int) {
        if (visibilityState == VisibilityState.VISIBLE) {
            callback?.onEventVisible(event)
        } else if (visibilityState == VisibilityState.INVISIBLE) {
            callback?.onEventInvisible(event)
        }
    }
}

class MergedTimelineEventVisibilityStateChangedListener(
        private val callback: TimelineEventController.Callback?,
        private val events: List<TimelineEvent>
) :
        VectorEpoxyModel.OnVisibilityStateChangedListener {

    override fun onVisibilityStateChanged(visibilityState: Int) {
        if (visibilityState == VisibilityState.VISIBLE) {
            events.forEach { callback?.onEventVisible(it) }
        } else if (visibilityState == VisibilityState.INVISIBLE) {
            events.forEach { callback?.onEventInvisible(it) }
        }
    }
}
