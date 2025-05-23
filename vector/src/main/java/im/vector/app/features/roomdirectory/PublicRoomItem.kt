/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.roomdirectory

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.extensions.setTextOrHide
import im.vector.app.core.platform.ButtonStateView
import im.vector.app.features.home.avatar.AvatarRenderer
import org.matrix.android.sdk.api.util.MatrixItem

@EpoxyModelClass
abstract class PublicRoomItem : VectorEpoxyModel<PublicRoomItem.Holder>(R.layout.item_public_room) {

    @EpoxyAttribute
    lateinit var avatarRenderer: AvatarRenderer

    @EpoxyAttribute
    lateinit var matrixItem: MatrixItem

    @EpoxyAttribute
    var roomAlias: String? = null

    @EpoxyAttribute
    var roomTopic: String? = null

    @EpoxyAttribute
    var nbOfMembers: Int = 0

    @EpoxyAttribute
    var joinState: JoinState = JoinState.NOT_JOINED

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var globalListener: ClickListener? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var joinListener: ClickListener? = null

    @SuppressLint("SetTextI18n")
    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.rootView.onClick(globalListener)

        avatarRenderer.render(matrixItem, holder.avatarView)
        holder.nameView.text = matrixItem.displayName
        holder.aliasView.setTextOrHide(roomAlias)
        holder.topicView.setTextOrHide(roomTopic)
        // TODO Use formatter for big numbers?
        holder.counterView.text = "$nbOfMembers"

        holder.buttonState.render(
                when (joinState) {
                    JoinState.NOT_JOINED -> ButtonStateView.State.Button
                    JoinState.JOINING -> ButtonStateView.State.Loading
                    JoinState.JOINED -> ButtonStateView.State.Loaded
                    JoinState.JOINING_ERROR -> ButtonStateView.State.Error
                }
        )

        holder.buttonState.commonClicked = { joinListener?.invoke(it) }
    }

    class Holder : VectorEpoxyHolder() {
        val rootView by bind<ViewGroup>(R.id.itemPublicRoomLayout)

        val avatarView by bind<ImageView>(R.id.itemPublicRoomAvatar)
        val nameView by bind<TextView>(R.id.itemPublicRoomName)
        val aliasView by bind<TextView>(R.id.itemPublicRoomAlias)
        val topicView by bind<TextView>(R.id.itemPublicRoomTopic)
        val counterView by bind<TextView>(R.id.itemPublicRoomMembersCount)

        val buttonState by bind<ButtonStateView>(R.id.itemPublicRoomButtonState)
    }
}
