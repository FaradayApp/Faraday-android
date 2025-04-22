package im.vector.app.core.ui.views

import android.content.res.Resources
import im.vector.app.R
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.features.home.room.detail.timeline.item.MessageInformationData
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.view.ScMessageBubbleWrapView

interface BubbleDependentView<H: VectorEpoxyHolder> {

    fun getScBubbleMargin(resources: Resources): Int = resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.dual_bubble_one_side_without_avatar_margin)
    fun getViewStubMinimumWidth(holder: H): Int = 0

    fun allowFooterOverlay(holder: H, bubbleWrapView: ScMessageBubbleWrapView): Boolean = false
    // Whether to show the footer aligned below the viewStub - requires enough width!
    fun allowFooterBelow(holder: H): Boolean = true
    fun needsFooterReservation(): Boolean = false
    fun reserveFooterSpace(holder: H, width: Int, height: Int) {}
    fun getInformationData(): MessageInformationData? = null

    // TODO: overwrite for remaining setBubbleLayout()s where necessary: ReadReceiptsItem, MessageImageVideoItem
    fun applyScBubbleStyle(messageLayout: TimelineMessageLayout.ScBubble, holder: H) {}

    /*
    fun messageBubbleAllowed(context: Context): Boolean {
        return false
    }

    fun shouldReverseBubble(): Boolean {
        return false
    }

    fun pseudoBubbleAllowed(): Boolean {
        return false
    }

    fun setBubbleLayout(holder: H, bubbleStyle: String, bubbleStyleSetting: String, reverseBubble: Boolean)
     */
}

/*
// This function belongs to BubbleDependentView, but turned out to raise a NoSuchMethodError since recently
// when called from an onImageSizeUpdated listener
fun <H>updateMessageBubble(context: Context, view: BubbleDependentView<H>, holder: H) {
    val bubbleStyleSetting = BubbleThemeUtils.getBubbleStyle(context)
    val bubbleStyle = when {
        view.messageBubbleAllowed(context)                                                      -> {
            bubbleStyleSetting
        }
        bubbleStyleSetting == BubbleThemeUtils.BUBBLE_STYLE_BOTH && view.pseudoBubbleAllowed()  -> {
            BubbleThemeUtils.BUBBLE_STYLE_BOTH_HIDDEN
        }
        bubbleStyleSetting == BubbleThemeUtils.BUBBLE_STYLE_START && view.pseudoBubbleAllowed() -> {
            BubbleThemeUtils.BUBBLE_STYLE_START_HIDDEN
        }
        else                                                                               -> {
            BubbleThemeUtils.BUBBLE_STYLE_NONE
        }
    }
    val reverseBubble = view.shouldReverseBubble() && BubbleThemeUtils.drawsDualSide(bubbleStyle)

    view.setBubbleLayout(holder, bubbleStyle, bubbleStyleSetting, reverseBubble)
}

fun setFlatRtl(layout: ViewGroup, direction: Int, childDirection: Int, depth: Int = 1) {
    layout.layoutDirection = direction
    for (child in layout.children) {
        if (depth > 1 && child is ViewGroup) {
            setFlatRtl(child, direction, childDirection, depth-1)
        } else {
            child.layoutDirection = childDirection
        }
    }
}
 */
