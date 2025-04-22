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

import android.content.Context
import android.view.Gravity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SnapHelper
import com.airbnb.epoxy.Carousel
import com.airbnb.epoxy.Carousel.SnapHelperFactory
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import im.vector.app.core.resources.StringProvider
import im.vector.app.features.home.avatar.AvatarRenderer
import im.vector.app.features.home.room.list.UnreadCounterBadgeView
import im.vector.app.features.settings.VectorPreferences
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class SpaceBarController @Inject constructor(
        val stringProvider: StringProvider,
        private val avatarRenderer: AvatarRenderer,
        private val vectorPreferences: VectorPreferences,
) : EpoxyController() {

    private var data: SpaceBarData = SpaceBarData()
    private var topLevelUnreadCounts: UnreadCounterBadgeView.State.Count = UnreadCounterBadgeView.State.Count(0, false, 0, false)

    interface SpaceBarListener {
        fun onSpaceBarSelectSpace(space: RoomSummary?)
        fun onSpaceBarLongPressSpace(space: RoomSummary?): Boolean
    }
    var spaceRoomListener: SpaceBarListener? = null

    private var carousel: Carousel? = null

    override fun buildModels() {
        val host = this
        data.spaces?.let {
            addSpaces(host, it, data.selectedSpace)
        }
    }

    private fun addSpaces(host: SpaceBarController, spaces: List<RoomSummary?>, selectedSpace: RoomSummary?) {
        spaceBarCarousel {
            enableSnap(host.vectorPreferences.preferSpecificSpacePagerSpace())
            id("spaces_carousel")
            padding(
                    Carousel.Padding(
                            0,
                            0,
                            0,
                            0,
                            0,
                    )
            )
            onBind { _, view, _ ->
                host.carousel = view
                view.post {
                    host.scrollToSpace(selectedSpace?.roomId)
                }
            }

            onUnbind { _, _ ->
                host.carousel = null
            }

            withModelsFrom(spaces) { spaceSummary ->
                val onClick = host.spaceRoomListener?.let { it::onSpaceBarSelectSpace }
                val onLongClick = host.spaceRoomListener?.let { it::onSpaceBarLongPressSpace }

                if (spaceSummary == null) {
                    SpaceBarItem_()
                            .id("de.spiritcroc.riotx.spacebarhome")
                            .avatarRenderer(host.avatarRenderer)
                            .matrixItem(null)
                            .unreadNotificationCount(host.topLevelUnreadCounts.count)
                            .showHighlighted(host.topLevelUnreadCounts.highlighted)
                            .unreadCount(host.topLevelUnreadCounts.unread)
                            .markedUnread(host.topLevelUnreadCounts.markedUnread)
                            .selected(selectedSpace == null)
                            .itemLongClickListener { _ -> onLongClick?.invoke(null) ?: false }
                            .itemClickListener { onClick?.invoke(null) }
                } else {
                    SpaceBarItem_()
                            .id(spaceSummary.roomId)
                            .avatarRenderer(host.avatarRenderer)
                            .matrixItem(spaceSummary.toMatrixItem())
                            .unreadNotificationCount(spaceSummary.notificationCount)
                            .showHighlighted(spaceSummary.highlightCount > 0)
                            .unreadCount(spaceSummary.unreadCount ?: 0)
                            .markedUnread(spaceSummary.markedUnread)
                            .selected(spaceSummary.roomId == selectedSpace?.roomId)
                            .itemLongClickListener { _ -> onLongClick?.invoke(spaceSummary) ?: false }
                            .itemClickListener { onClick?.invoke(spaceSummary) }
                }
            }
        }
    }

    fun submitData(data: SpaceBarData) {
        this.data = data
        requestModelBuild()
    }

    fun submitHomeUnreadCounts(counts: UnreadCounterBadgeView.State.Count) {
        this.topLevelUnreadCounts = counts
        requestModelBuild()
    }

    fun selectSpace(space: RoomSummary?) {
        this.data = this.data.copy(selectedSpace = space)
        requestModelBuild()
        scrollToSpace(space?.roomId)
    }

    fun scrollToSpace(spaceId: String?) {
        val position = this.data.spaces?.indexOfFirst { spaceId == it?.roomId } ?: -1
        if (position >= 0) {
            scrollToSpacePosition(position)
        }
    }

    fun scrollToSpacePosition(position: Int) {
        val safeCarousel = carousel ?: return
        var effectivePosition = position
        val lm = safeCarousel.layoutManager as? LinearLayoutManager
        if (lm != null) {
            // Scroll to an element such that the new selection is roughly in the middle
            val firstVisible = lm.findFirstCompletelyVisibleItemPosition()
            val visibleRange = lm.findLastCompletelyVisibleItemPosition() - firstVisible + 1
            if (vectorPreferences.preferSpecificSpacePagerSpace()) { // scroll to smallest space possible
                effectivePosition = when {
                    position < 1 -> 0 // show home only if it is selected
                    firstVisible < 1 && position <= visibleRange -> 1 + visibleRange // hide home
                    position > firstVisible + visibleRange -> position // make selection visible by scrolling right
                    else -> max(1, position - visibleRange) // make selection visible by scrolling left, as right as possible without scrolling to home
                }
            } else { // center current space
                val overshoot = visibleRange / 2
                val currentMiddle = firstVisible + overshoot
                if (currentMiddle < position) {
                    effectivePosition = position + overshoot
                } else if (currentMiddle > position) {
                    effectivePosition = position - overshoot
                }
            }
            // List limits
            effectivePosition = max(0, min(effectivePosition, lm.itemCount-1))
        }
        safeCarousel.smoothScrollToPosition(effectivePosition)
    }
}

private inline fun <T> SpaceBarCarouselModelBuilder.withModelsFrom(
        items: List<T>,
        modelBuilder: (T) -> EpoxyModel<*>
) {
    models(items.map { modelBuilder(it) })
}

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class SpaceBarCarousel(context: Context?) : Carousel(context) {
    private var mEnableSnap: Boolean = false
    @ModelProp // Couldn't get ModelProp to work without explicit setter
    fun setEnableSnap(value: Boolean) {
        mEnableSnap = value
    }
    override fun getSnapHelperFactory(): SnapHelperFactory? {
        return if (mEnableSnap) SpaceBarSnapHelperFactory() else null
    }
}

internal class SpaceBarSnapHelperFactory: SnapHelperFactory() {
    override fun buildSnapHelper(context: Context?): SnapHelper {
        return GravitySnapHelper(Gravity.START)
    }
}
