/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel

@EpoxyModelClass
abstract class DaySeparatorItem : VectorEpoxyModel<DaySeparatorItem.Holder>(R.layout.item_timeline_event_day_separator) {

    @EpoxyAttribute lateinit var formattedDay: String

    private var shouldBeVisible: Boolean = true

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.dayTextView.text = formattedDay
        // Just as background space reservation. Use same text for proper measures.
        holder.dayTextCutoutView.text = formattedDay
        // As we may hide this for floating dates, ensure we un-hide it on bind
        holder.dayTextView.isVisible = shouldBeVisible
    }

    fun shouldBeVisible(shouldBeVisible: Boolean, holder: Holder?) {
        holder?.dayTextView?.isVisible = shouldBeVisible
        this.shouldBeVisible = shouldBeVisible
    }

    companion object {
        fun asFloatingDate(holder: Holder) {
            holder.dayTextView.isVisible = true
            holder.separatorView.isVisible = false
        }
    }

    class Holder : VectorEpoxyHolder() {
        val dayTextView by bind<TextView>(R.id.itemDayTextView)
        val dayTextCutoutView by bind<TextView>(R.id.itemDayTextCutoutView)
        val separatorView by bind<View>(R.id.itemDayTextSeparatorView)
    }
}
