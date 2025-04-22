/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.view

import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.ui.views.BubbleDependentView
import im.vector.app.features.home.room.detail.timeline.item.BaseEventItem
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout

interface TimelineMessageLayoutRenderer {
    fun <H: BaseEventItem.BaseHolder>renderMessageLayout(messageLayout: TimelineMessageLayout,
                                                         bubbleDependentView: BubbleDependentView<H>,
                                                         holder: H)

    // Variant to use from classes that do not use BaseEventItem.BaseHolder, and don't need the heavy bubble stuff
    fun <H: VectorEpoxyHolder>renderBaseMessageLayout(messageLayout: TimelineMessageLayout,
                                                      bubbleDependentView: BubbleDependentView<H>,
                                                      holder: H) {}
}

// Only render message layout for SC layouts - even if parent is not an ScBubble
fun <H: BaseEventItem.BaseHolder>TimelineMessageLayoutRenderer?.scOnlyRenderMessageLayout(messageLayout: TimelineMessageLayout,
                                                           bubbleDependentView: BubbleDependentView<H>,
                                                           holder: H) {
    if (messageLayout is TimelineMessageLayout.ScBubble) {
        scRenderMessageLayout(messageLayout, bubbleDependentView, holder)
    }
}

// Also render stub in case parent is no ScBubble
fun <H: BaseEventItem.BaseHolder>TimelineMessageLayoutRenderer?.scRenderMessageLayout(messageLayout: TimelineMessageLayout,
                                                                                    bubbleDependentView: BubbleDependentView<H>,
                                                                                    holder: H) {
    if (this == null) {
        renderStubMessageLayout(messageLayout, holder.viewStubContainer)
    } else {
        renderMessageLayout(messageLayout, bubbleDependentView, holder)
    }
}
