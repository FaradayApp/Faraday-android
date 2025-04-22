/*
 * Copyright (c) 2022 New Vector Ltd
 * Copyright (c) 2022 SpiritCroc
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

package im.vector.app.features.home.room.list.home.spacebar

import android.content.res.ColorStateList
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.platform.CheckableConstraintLayout
import im.vector.app.features.displayname.getBestName
import im.vector.app.features.home.avatar.AvatarRenderer
import im.vector.app.features.home.room.list.UnreadCounterBadgeView
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.util.MatrixItem

@EpoxyModelClass
abstract class SpaceBarItem : VectorEpoxyModel<SpaceBarItem.Holder>(R.layout.space_bar_item) {

    @EpoxyAttribute lateinit var avatarRenderer: AvatarRenderer
    @EpoxyAttribute var matrixItem: MatrixItem? = null
    @EpoxyAttribute var unreadNotificationCount: Int = 0
    @EpoxyAttribute var unreadCount: Int = 0
    @EpoxyAttribute var markedUnread: Boolean = false
    @EpoxyAttribute var showHighlighted: Boolean = false
    @EpoxyAttribute var selected: Boolean = false

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var itemLongClickListener: View.OnLongClickListener? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var itemClickListener: ClickListener? = null

    override fun bind(holder: Holder) {
        super.bind(holder)

        holder.rootView.onClick(itemClickListener)
        holder.rootView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            itemLongClickListener?.onLongClick(it) ?: false
        }

        matrixItem.let {
            if (it == null) {
                holder.avatarImageView.setImageResource(R.drawable.ic_space_home)
                holder.avatarImageView.imageTintList = ColorStateList.valueOf(ThemeUtils.getColor(holder.avatarImageView.context, im.vector.lib.ui.styles.R.attr.vctr_content_primary))
                holder.avatarImageView.contentDescription = holder.rootView.context.getString(CommonStrings.group_details_home)
            } else {
                holder.avatarImageView.imageTintList = null
                avatarRenderer.render(it, holder.avatarImageView)
                holder.avatarImageView.contentDescription = it.getBestName()
            }
        }
        holder.unreadCounterBadgeView.render(UnreadCounterBadgeView.State.Count(unreadNotificationCount, showHighlighted, unreadCount, markedUnread))
        holder.rootView.isChecked = selected
    }

    override fun unbind(holder: Holder) {
        holder.rootView.setOnClickListener(null)
        holder.rootView.setOnLongClickListener(null)
        avatarRenderer.clear(holder.avatarImageView)
        super.unbind(holder)
    }

    class Holder : VectorEpoxyHolder() {
        val unreadCounterBadgeView by bind<UnreadCounterBadgeView>(R.id.spaceUnreadCounterBadgeView)
        val avatarImageView by bind<ImageView>(R.id.spaceImageView)
        val rootView by bind<CheckableConstraintLayout>(R.id.spaceRoot)
    }
}
