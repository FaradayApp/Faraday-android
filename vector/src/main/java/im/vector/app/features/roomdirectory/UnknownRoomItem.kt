/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.roomdirectory

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
import im.vector.app.features.home.avatar.AvatarRenderer
import org.matrix.android.sdk.api.util.MatrixItem

@EpoxyModelClass
abstract class UnknownRoomItem : VectorEpoxyModel<UnknownRoomItem.Holder>(R.layout.item_unknown_room) {

    @EpoxyAttribute
    lateinit var avatarRenderer: AvatarRenderer

    @EpoxyAttribute
    lateinit var matrixItem: MatrixItem

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var globalListener: ClickListener? = null

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.rootView.onClick(globalListener)
        avatarRenderer.render(matrixItem, holder.avatarView)
        holder.nameView.text = matrixItem.displayName
    }

    class Holder : VectorEpoxyHolder() {
        val rootView by bind<ViewGroup>(R.id.itemUnknownRoomLayout)
        val avatarView by bind<ImageView>(R.id.itemUnknownRoomAvatar)
        val nameView by bind<TextView>(R.id.itemUnknownRoomName)
    }
}
