/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.spaces.manage

import com.airbnb.epoxy.TypedEpoxyController
import com.airbnb.epoxy.VisibilityState
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Incomplete
import im.vector.app.core.epoxy.errorWithRetryItem
import im.vector.app.core.epoxy.loadingItem
import im.vector.app.core.error.ErrorFormatter
import im.vector.app.core.resources.StringProvider
import im.vector.app.core.ui.list.genericFooterItem
import im.vector.app.features.home.avatar.AvatarRenderer
import im.vector.lib.core.utils.epoxy.charsequence.toEpoxyCharSequence
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.room.model.SpaceChildInfo
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject

class SpaceManageRoomsController @Inject constructor(
        private val avatarRenderer: AvatarRenderer,
        private val errorFormatter: ErrorFormatter,
        private val stringProvider: StringProvider
) : TypedEpoxyController<SpaceManageRoomViewState>() {

    interface Listener {
        fun toggleSelection(childInfo: SpaceChildInfo)
        fun retry()
        fun loadAdditionalItemsIfNeeded()
    }

    var listener: Listener? = null
    private val matchFilter = SpaceChildInfoMatchFilter()

    override fun buildModels(data: SpaceManageRoomViewState?) {
        val host = this
        val roomListAsync = data?.childrenInfo
        if (roomListAsync is Incomplete) {
            loadingItem { id("loading") }
            return
        }
        if (roomListAsync is Fail) {
            errorWithRetryItem {
                id("Api Error")
                text(host.errorFormatter.toHumanReadable(roomListAsync.error))
                listener { host.listener?.retry() }
            }
            return
        }

        val roomList = roomListAsync?.invoke()?.children ?: return

        val directChildren = roomList.filter {
            it.parentRoomId == data.spaceId
            /** Only direct children **/
        }
        matchFilter.filter = data.currentFilter
        val filteredResult = directChildren.filter { matchFilter.test(it) }

        if (filteredResult.isEmpty()) {
            genericFooterItem {
                id("empty_result")
                text(host.stringProvider.getString(CommonStrings.no_result_placeholder).toEpoxyCharSequence())
            }
        } else {
            filteredResult.forEach { childInfo ->
                roomManageSelectionItem {
                    id(childInfo.childRoomId)
                    matrixItem(
                            data.knownRoomSummaries.firstOrNull { it.roomId == childInfo.childRoomId }?.toMatrixItem()
                                    ?: childInfo.toMatrixItem()
                    )
                    avatarRenderer(host.avatarRenderer)
                    suggested(childInfo.suggested ?: false)
                    selected(data.selectedRooms.contains(childInfo.childRoomId))
                    itemClickListener {
                        host.listener?.toggleSelection(childInfo)
                    }
                }
            }
        }
        val nextToken = roomListAsync.invoke()?.nextToken
        if (nextToken != null) {
            // show loading item
            val paginationStatus = data.paginationStatus
            if (paginationStatus is Fail) {
                errorWithRetryItem {
                    id("error_$nextToken")
                    text(host.errorFormatter.toHumanReadable(paginationStatus.error))
                    listener { host.listener?.loadAdditionalItemsIfNeeded() }
                }
            } else {
                loadingItem {
                    id("pagination_$nextToken")
                    showLoader(true)
                    onVisibilityStateChanged { _, _, visibilityState ->
                        // Do something with the new visibility state
                        if (visibilityState == VisibilityState.VISIBLE) {
                            // we can trigger a seamless load of additional items
                            host.listener?.loadAdditionalItemsIfNeeded()
                        }
                    }
                }
            }
        }
    }
}
