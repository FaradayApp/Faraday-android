/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.content.res.Resources
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.extensions.setTextOrHide
import im.vector.app.core.files.LocalFilesHelper
import im.vector.app.core.glide.GlideApp
import im.vector.app.core.ui.views.AbstractFooteredTextView
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.features.home.room.detail.timeline.helper.ContentUploadStateTrackerBinder
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.style.granularRoundedCorners
import im.vector.app.features.home.room.detail.timeline.view.ScMessageBubbleWrapView
import im.vector.app.features.media.ImageContentRenderer
import kotlin.math.round
import org.matrix.android.sdk.api.session.room.model.message.MessageType

@EpoxyModelClass
abstract class MessageImageVideoItem : AbsMessageItem<MessageImageVideoItem.Holder>() {

    @EpoxyAttribute
    lateinit var mediaData: ImageContentRenderer.Data

    @EpoxyAttribute
    var playable: Boolean = false

    @EpoxyAttribute
    var mode = ImageContentRenderer.Mode.THUMBNAIL

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var clickListener: ClickListener? = null

    @EpoxyAttribute
    lateinit var imageContentRenderer: ImageContentRenderer

    @EpoxyAttribute
    lateinit var contentUploadStateTrackerBinder: ContentUploadStateTrackerBinder

    var lastAllowedFooterOverlay: Boolean = true
    var lastShowFooterBellow: Boolean = true
    var forceAllowFooterOverlay: Boolean? = null
    var showFooterBellow: Boolean = true

    override fun bind(holder: Holder) {
        forceAllowFooterOverlay = null
        super.bind(holder)

        val isImageMessage = attributes.informationData.messageType in listOf(MessageType.MSGTYPE_IMAGE, MessageType.MSGTYPE_STICKER_LOCAL)
        val autoplayAnimatedImages = attributes.autoplayAnimatedImages

        val bubbleWrapView = (holder.view as? ScMessageBubbleWrapView)
        val host = this
        val onImageSizeListener = object: ImageContentRenderer.OnImageSizeListener {
            override fun onImageSizeUpdated(width: Int, height: Int) {
                bubbleWrapView ?: return
                // Image size change -> different footer space situation possible
                val footerMeasures = bubbleWrapView.getFooterMeasures(attributes.informationData)
                forceAllowFooterOverlay = shouldAllowFooterOverlay(footerMeasures, width, height)
                val newShowFooterBellow = shouldShowFooterBellow(footerMeasures, width, height)
                if (lastAllowedFooterOverlay != forceAllowFooterOverlay || newShowFooterBellow != lastShowFooterBellow) {
                    showFooterBellow = newShowFooterBellow
                    bubbleWrapView.renderMessageLayout(attributes.informationData.messageLayout, host, holder)
                }
            }
        }
        val animate = playable && isImageMessage && autoplayAnimatedImages
        // Do not use thumbnails for animated GIFs - sometimes thumbnails do not animate while the original GIF does
        val effectiveMode = if (animate && mode == ImageContentRenderer.Mode.THUMBNAIL) ImageContentRenderer.Mode.ANIMATED_THUMBNAIL else mode

        val messageLayout = baseAttributes.informationData.messageLayout
        val dimensionConverter = DimensionConverter(holder.view.resources)
        val cornerRoundnessDp: Int
        val imageCornerTransformation: Transformation<Bitmap>
        when (messageLayout) {
            is TimelineMessageLayout.ScBubble -> {
                cornerRoundnessDp = round(messageLayout.bubbleAppearance.getBubbleRadiusDp(holder.view.context)).toInt()
                imageCornerTransformation = RoundedCorners(dimensionConverter.dpToPx(cornerRoundnessDp))
            }
            is TimelineMessageLayout.Bubble   -> {
                cornerRoundnessDp = 8
                imageCornerTransformation = messageLayout.cornersRadius.granularRoundedCorners()
            }
            else -> {
                cornerRoundnessDp = 8
                imageCornerTransformation = RoundedCorners(dimensionConverter.dpToPx(cornerRoundnessDp))
            }
        }
        imageContentRenderer.render(mediaData, effectiveMode, holder.imageView, cornerRoundnessDp, imageCornerTransformation, onImageSizeListener, animate = animate)
        if (!attributes.informationData.sendState.hasFailed()) {
            contentUploadStateTrackerBinder.bind(
                    attributes.informationData.eventId,
                    LocalFilesHelper(holder.view.context).isLocalFile(mediaData.url),
                    holder.progressLayout
            )
        } else {
            holder.progressLayout.isVisible = false
        }
        holder.imageView.onClick(clickListener)
        holder.imageView.setOnLongClickListener(attributes.itemLongClickListener)
        ViewCompat.setTransitionName(holder.imageView, "imagePreview_${id()}")
        holder.mediaContentView.onClick(attributes.itemClickListener)
        holder.mediaContentView.setOnLongClickListener(attributes.itemLongClickListener)

        holder.captionView.setTextOrHide(mediaData.caption)

        holder.playContentView.visibility = if (animate) {
            View.GONE
        } else if (playable) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun hasCaption() = !mediaData.caption.isNullOrBlank()

    private fun needsRealBubble() = hasCaption() || attributes.informationData.isReply

    override fun unbind(holder: Holder) {
        GlideApp.with(holder.view.context.applicationContext).clear(holder.imageView)
        imageContentRenderer.clear(holder.imageView)
        contentUploadStateTrackerBinder.unbind(attributes.informationData.eventId)
        holder.imageView.setOnClickListener(null)
        holder.imageView.setOnLongClickListener(null)
        super.unbind(holder)
    }

    override fun getViewStubId() = STUB_ID

    private fun shouldAllowFooterOverlay(footerMeasures: Array<Int>, imageWidth: Int, imageHeight: Int): Boolean {
        if (hasCaption()) {
            return true
        }
        val footerWidth = footerMeasures[0]
        val footerHeight = footerMeasures[1]
        // We need enough space in both directions to remain within the image bounds.
        // Furthermore, we do not want to hide a too big area, so check the total covered area as well.
        return imageWidth > 1.5*footerWidth && imageHeight > 1.5*footerHeight && (imageWidth * imageHeight > 4 * footerWidth * footerHeight)
    }

    private fun shouldShowFooterBellow(footerMeasures: Array<Int>, imageWidth: Int, imageHeight: Int): Boolean {
        if (hasCaption()) {
            return false
        }
        // Only show footer bellow if the width is not the limiting factor (or it will get cut).
        // Otherwise, we can not be sure in this place that we'll have enough space on the side
        // Also, prefer footer on the side if possible (i.e. enough height available)
        val footerWidth = footerMeasures[0]
        val footerHeight = footerMeasures[1]
        return imageWidth > 1.5*footerWidth && imageHeight < 1.5*footerHeight
    }

    override fun allowFooterOverlay(holder: Holder, bubbleWrapView: ScMessageBubbleWrapView): Boolean {
        if (hasCaption()) {
            return true
        }
        val rememberedAllowFooterOverlay = forceAllowFooterOverlay
        if (rememberedAllowFooterOverlay != null) {
            lastAllowedFooterOverlay = rememberedAllowFooterOverlay
            return rememberedAllowFooterOverlay
        }
        val imageWidth = holder.imageView.width
        val imageHeight = holder.imageView.height
        if (imageWidth == 0 && imageHeight == 0) {
            // Not initialised yet, assume true
            lastAllowedFooterOverlay = true
            return true
        }
        // If the footer covers most of the image, or is even larger than the image, move it outside
        val footerMeasures = bubbleWrapView.getFooterMeasures(baseAttributes.informationData)
        lastAllowedFooterOverlay = shouldAllowFooterOverlay(footerMeasures, imageWidth, imageHeight)
        return lastAllowedFooterOverlay
    }

    override fun allowFooterBelow(holder: Holder): Boolean {
        if (hasCaption()) {
            return true
        }
        val showBellow = showFooterBellow
        lastShowFooterBellow = showBellow
        return showBellow
    }

    override fun getScBubbleMargin(resources: Resources): Int {
        if (needsRealBubble()) {
            return super.getScBubbleMargin(resources)
        }
        return 0
    }

    override fun needsFooterReservation(): Boolean {
        return hasCaption()
    }

    override fun reserveFooterSpace(holder: Holder, width: Int, height: Int) {
        (holder.captionView as? AbstractFooteredTextView)?.apply {
            footerWidth = width
            footerHeight = height
        }
    }

    override fun applyScBubbleStyle(messageLayout: TimelineMessageLayout.ScBubble, holder: Holder) {
        // Case: ImageContentRenderer.processSize only sees width=height=0 -> width of the ImageView not adapted to the actual content
        // -> Align image within ImageView to same side as message bubbles
        holder.imageView.scaleType = if (messageLayout.reverseBubble) ImageView.ScaleType.FIT_END else ImageView.ScaleType.FIT_START
        // Case: Message information (sender name + date) makes the containing view wider than the ImageView
        // -> Align ImageView within its parent to the same side as message bubbles
        (holder.imageView.layoutParams as ConstraintLayout.LayoutParams).horizontalBias = if (messageLayout.reverseBubble) 1f else 0f

        // Image outline
        when {
            // Don't show it for non-bubble layouts, don't show for Stickers, ...
            // Also only supported for default corner radius
            !(messageLayout.isRealBubble || messageLayout.isPseudoBubble) || needsRealBubble() || mode != ImageContentRenderer.Mode.THUMBNAIL -> {
                holder.mediaContentView.background = null
            }
            attributes.informationData.sentByMe -> {
                holder.mediaContentView.setBackgroundResource(messageLayout.bubbleAppearance.imageBorderOutgoing)
            }
            else -> {
                holder.mediaContentView.setBackgroundResource(messageLayout.bubbleAppearance.imageBorderIncoming)
            }
        }
    }

    class Holder : AbsMessageItem.Holder(STUB_ID) {
        val progressLayout by bind<ViewGroup>(R.id.messageMediaUploadProgressLayout)
        val imageView by bind<ImageView>(R.id.messageThumbnailView)
        val captionView by bind<TextView>(R.id.messageCaptionView)
        val playContentView by bind<ImageView>(R.id.messageMediaPlayView)
        val mediaContentView by bind<ViewGroup>(R.id.messageContentMedia)
    }

    companion object {
        private val STUB_ID = R.id.messageContentMediaStub
    }
}
