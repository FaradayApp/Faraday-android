/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.avatar

import android.graphics.drawable.Drawable
import com.amulyakhare.textdrawable.TextDrawable
import im.vector.app.core.resources.StringProvider
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.features.displayname.getBestName
import im.vector.app.features.home.room.detail.timeline.helper.MatrixItemColorProvider
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.content.ContentUrlResolver
import org.matrix.android.sdk.api.util.MatrixItem

object AvatarHelper {
    const val THUMBNAIL_SIZE = 250

    internal fun getContentDescription(stringProvider: StringProvider, matrixItem: MatrixItem) = when (matrixItem) {
        is MatrixItem.SpaceItem -> {
            stringProvider.getString(CommonStrings.avatar_of_space, matrixItem.getBestName())
        }

        is MatrixItem.RoomAliasItem,
        is MatrixItem.RoomItem -> {
            stringProvider.getString(CommonStrings.avatar_of_room, matrixItem.getBestName())
        }

        is MatrixItem.UserItem -> {
            stringProvider.getString(CommonStrings.avatar_of_user, matrixItem.getBestName())
        }

        is MatrixItem.EmoteItem -> {
            matrixItem.getBestName()
        }

        is MatrixItem.EveryoneInRoomItem,
        is MatrixItem.EventItem,
        is MatrixItem.AccountMatrixItem -> {
            null
        }
    }

    internal fun resolvedUrl(contentUrlResolver: ContentUrlResolver, avatarUrl: String?): String? =
            contentUrlResolver.resolveThumbnail(
                    avatarUrl,
                    THUMBNAIL_SIZE,
                    THUMBNAIL_SIZE,
                    ContentUrlResolver.ThumbnailMethod.SCALE
            )

    internal fun getPlaceholderDrawable(
            matrixItemColorProvider: MatrixItemColorProvider,
            dimensionConverter: DimensionConverter,
            matrixItem: MatrixItem,
            userInRoomInformation: MatrixItemColorProvider.UserInRoomInformation? = null,
    ): Drawable {
        val avatarColor = matrixItemColorProvider.getColor(matrixItem, userInRoomInformation)
        return TextDrawable.builder()
                .beginConfig()
                .bold()
                .endConfig()
                .let {
                    when (matrixItem) {
                        is MatrixItem.SpaceItem -> {
                            it.buildRoundRect(matrixItem.firstLetterOfDisplayName(), avatarColor, dimensionConverter.dpToPx(8))
                        }
                        else -> {
                            it.buildRound(matrixItem.firstLetterOfDisplayName(), avatarColor)
                        }
                    }
                }
    }
}
