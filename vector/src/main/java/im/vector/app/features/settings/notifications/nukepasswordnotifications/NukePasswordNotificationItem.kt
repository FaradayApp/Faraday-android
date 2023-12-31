/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.settings.notifications.nukepasswordnotifications


import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick

@EpoxyModelClass
abstract class NukePasswordNotificationItem : VectorEpoxyModel<NukePasswordNotificationItem.Holder>(R.layout.item_nuke_notification) {

    @EpoxyAttribute lateinit var nukeNotificationModel: NukeNotificationModel
    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash) var listener: ClickListener? = null

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.rootView.onClick(listener)
        holder.dateTextView.text = nukeNotificationModel.date
        holder.userTextView.text = nukeNotificationModel.user
        holder.titleTextView.text = nukeNotificationModel.text
        holder.notificationViewedImageView.isVisible = !nukeNotificationModel.viewed
    }

    class Holder : VectorEpoxyHolder() {
        val dateTextView by bind<TextView>(R.id.dateTextView)
        val userTextView by bind<TextView>(R.id.userTextView)
        val titleTextView by bind<TextView>(R.id.titleTextView)
        val rootView by bind<ConstraintLayout>(R.id.itemLayout)
        val notificationViewedImageView by bind<AppCompatImageView>(R.id.notificationViewedImageView)
    }
}
