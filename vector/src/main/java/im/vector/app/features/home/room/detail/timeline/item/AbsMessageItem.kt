/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.method.MovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.airbnb.epoxy.EpoxyAttribute
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.ui.views.SendStateImageView
import im.vector.app.features.home.avatar.AvatarRenderer
import im.vector.app.features.home.room.detail.timeline.MessageColorProvider
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.helper.MatrixItemColorProvider
import im.vector.app.features.home.room.detail.timeline.reply.InReplyToView
import im.vector.app.features.home.room.detail.timeline.reply.PreviewReplyUiState
import im.vector.app.features.home.room.detail.timeline.reply.ReplyPreviewRetriever
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.view.ScMessageBubbleWrapView
import im.vector.app.features.home.room.detail.timeline.view.canHideAvatars
import im.vector.app.features.home.room.detail.timeline.view.infoInBubbles
import org.matrix.android.sdk.api.session.threads.ThreadDetails
import org.matrix.android.sdk.api.util.MatrixItem

/**
 * Base timeline item that adds an optional information bar with the sender avatar, name, time, send state.
 * Adds associated click listeners (on avatar, displayname).
 */
abstract class AbsMessageItem<H : AbsMessageItem.Holder>(
        @LayoutRes layoutId: Int = R.layout.item_timeline_event_base
) : AbsBaseMessageItem<H>(layoutId) {

    private val replyViewUpdater = ReplyViewUpdater()

    override val baseAttributes: AbsBaseMessageItem.Attributes
        get() = attributes

    override fun isCacheable(): Boolean {
        return attributes.informationData.sendStateDecoration != SendStateDecoration.SENT
    }

    @EpoxyAttribute
    lateinit var attributes: Attributes

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var movementMethod: MovementMethod? = null

    @EpoxyAttribute
    var replyPreviewRetriever: ReplyPreviewRetriever? = null

    @EpoxyAttribute
    var inReplyToClickCallback: TimelineEventController.InReplyToClickCallback? = null

    private val _avatarClickListener = object : ClickListener {
        override fun invoke(p1: View) {
            attributes.avatarCallback?.onAvatarClicked(attributes.informationData)
        }
    }

    private val _threadClickListener = object : ClickListener {
        override fun invoke(p1: View) {
            attributes.threadCallback?.onThreadSummaryClicked(attributes.informationData.eventId, attributes.threadDetails?.isRootThread ?: false)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bind(holder: H) {
        super.bind(holder)

        if ((holder.view as? ScMessageBubbleWrapView)?.customBind(this, holder, attributes, _avatarClickListener) != true) {
        // Wrong indention for merge-ability

        if (attributes.informationData.messageLayout.showAvatar) {
            holder.avatarImageView.layoutParams = holder.avatarImageView.layoutParams?.apply {
                height = attributes.avatarSize
                width = attributes.avatarSize
            }
            attributes.avatarRenderer.render(attributes.informationData.matrixItem, holder.avatarImageView)
            holder.avatarImageView.setOnLongClickListener(attributes.itemLongClickListener)
            holder.avatarImageView.isVisible = true
            holder.avatarImageView.onClick(_avatarClickListener)
        } else {
            holder.avatarImageView.setOnClickListener(null)
            holder.avatarImageView.setOnLongClickListener(null)
            holder.avatarImageView.isVisible = false
        }
        if (attributes.informationData.messageLayout.showDisplayName) {
            holder.memberNameView.isVisible = true
            holder.memberNameView.text = attributes.informationData.memberName
            holder.memberNameView.setTextColor(attributes.getMemberNameColor())
            holder.memberNameView.onClick(attributes.memberClickListener)
            holder.memberNameView.setOnLongClickListener(attributes.itemLongClickListener)
        } else {
            holder.memberNameView.setOnClickListener(null)
            holder.memberNameView.setOnLongClickListener(null)
            holder.memberNameView.isVisible = false
        }
        if (attributes.informationData.messageLayout.showTimestamp) {
            holder.timeView.isVisible = true
            holder.timeView.text = attributes.informationData.time
        } else {
            holder.timeView.isVisible = false
        }

        // Render send state indicator
        holder.sendStateImageView.render(attributes.informationData.sendStateDecoration)
        holder.eventSendingIndicator.isVisible = attributes.informationData.sendStateDecoration == SendStateDecoration.SENDING_MEDIA

        // Wrong indention for merge-ability - end
        }

        // Threads
        if (attributes.areThreadMessagesEnabled) {
            holder.threadSummaryConstraintLayout.onClick(_threadClickListener)
            attributes.threadDetails?.let { threadDetails ->
                holder.threadSummaryConstraintLayout.isVisible = threadDetails.isRootThread
                holder.threadSummaryCounterTextView.text = "${threadDetails.numberOfThreads}"
                holder.threadSummaryInfoTextView.text = attributes.threadSummaryFormatted ?: attributes.decryptionErrorMessage

                val userId = threadDetails.threadSummarySenderInfo?.userId ?: return@let
                val displayName = threadDetails.threadSummarySenderInfo?.displayName
                val avatarUrl = threadDetails.threadSummarySenderInfo?.avatarUrl
                attributes.avatarRenderer.render(MatrixItem.UserItem(userId, displayName, avatarUrl), holder.threadSummaryAvatarImageView)
                updateHighlightedMessageHeight(holder, true)
            } ?: run {
                holder.threadSummaryConstraintLayout.isVisible = false
                updateHighlightedMessageHeight(holder, false)
            }
        }

        // Replies
        if (holder.replyToView != null) {
            replyViewUpdater.replyView = holder.replyToView
            val safeReplyPreviewRetriever = replyPreviewRetriever
            if (safeReplyPreviewRetriever == null) {
                holder.replyToView?.isVisible = false
            } else {
                safeReplyPreviewRetriever.addListener(attributes.informationData.eventId, replyViewUpdater)
            }
            holder.replyToView?.delegate = inReplyToClickCallback
        }
    }

    private fun updateHighlightedMessageHeight(holder: Holder, isExpanded: Boolean) {
        holder.checkableBackground.updateLayoutParams<RelativeLayout.LayoutParams> {
            if (isExpanded) {
                addRule(RelativeLayout.ALIGN_BOTTOM, holder.threadSummaryConstraintLayout.id)
            } else {
                addRule(RelativeLayout.ALIGN_BOTTOM, holder.informationBottom.id)
            }
        }
    }

    override fun unbind(holder: H) {
        attributes.avatarRenderer.clear(holder.avatarImageView)
        holder.avatarImageView.setOnClickListener(null)
        holder.avatarImageView.setOnLongClickListener(null)
        holder.memberNameView.setOnClickListener(null)
        holder.memberNameView.setOnLongClickListener(null)
        attributes.avatarRenderer.clear(holder.threadSummaryAvatarImageView)
        holder.threadSummaryConstraintLayout.setOnClickListener(null)
        replyPreviewRetriever?.removeListener(attributes.informationData.eventId, replyViewUpdater)
        replyViewUpdater.replyView = null
        super.unbind(holder)
    }

    override fun getInformationData(): MessageInformationData? = attributes.informationData

    abstract class Holder(@IdRes stubId: Int) : AbsBaseMessageItem.Holder(stubId) {

        val avatarImageView by bind<ImageView>(R.id.messageAvatarImageView)
        val memberNameView by bind<TextView>(R.id.messageMemberNameView)
        val timeView by bind<TextView>(R.id.messageTimeView)
        val sendStateImageView by bind<SendStateImageView>(R.id.messageSendStateImageView)
        val eventSendingIndicator by bind<ProgressBar>(R.id.eventSendingIndicator)
        val replyToView: InReplyToView? by lazy { view.findViewById(R.id.inReplyToContainer) }
        val threadSummaryConstraintLayout by bind<ConstraintLayout>(R.id.messageThreadSummaryConstraintLayout)
        val threadSummaryCounterTextView by bind<TextView>(R.id.messageThreadSummaryCounterTextView)
        val threadSummaryAvatarImageView by bind<ImageView>(R.id.messageThreadSummaryAvatarImageView)
        val threadSummaryInfoTextView by bind<TextView>(R.id.messageThreadSummaryInfoTextView)
    }

    /**
     * This class holds all the common attributes for timeline items.
     */
    data class Attributes(
            val avatarSize: Int,
            override val informationData: MessageInformationData,
            override val avatarRenderer: AvatarRenderer,
            override val messageColorProvider: MessageColorProvider,
            override val itemLongClickListener: View.OnLongClickListener? = null,
            override val itemClickListener: ClickListener? = null,
            val memberClickListener: ClickListener? = null,
            val callback: TimelineEventController.Callback? = null,
            override val reactionPillCallback: TimelineEventController.ReactionPillCallback? = null,
            val avatarCallback: TimelineEventController.AvatarCallback? = null,
            val threadCallback: TimelineEventController.ThreadCallback? = null,
            override val readReceiptsCallback: TimelineEventController.ReadReceiptsCallback? = null,
            val isNotice: Boolean = false,
            val emojiTypeFace: Typeface? = null,
            val decryptionErrorMessage: String? = null,
            val threadSummaryFormatted: String? = null,
            val threadDetails: ThreadDetails? = null,
            val areThreadMessagesEnabled: Boolean = false,
            val autoplayAnimatedImages: Boolean = false,
            val generateMissingVideoThumbnails: Boolean = false,
            override val reactionsSummaryEvents: ReactionsSummaryEvents? = null,
    ) : AbsBaseMessageItem.Attributes {

        // Have to override as it's used to diff epoxy items
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Attributes

            if (avatarSize != other.avatarSize) return false
            if (informationData != other.informationData) return false
            if (threadDetails != other.threadDetails) return false

            return true
        }

        override fun hashCode(): Int {
            var result = avatarSize
            result = 31 * result + informationData.hashCode()
            result = 31 * result + threadDetails.hashCode()

            return result
        }

        fun getMemberNameColor() = messageColorProvider.getMemberNameTextColor(
                informationData.matrixItem,
                MatrixItemColorProvider.UserInRoomInformation(
                        informationData.isDirect,
                        informationData.isPublic,
                        informationData.senderPowerLevel
                )
        )
    }


    inner class ReplyViewUpdater : ReplyPreviewRetriever.PreviewReplyRetrieverListener {
        var replyView: InReplyToView? = null

        override fun onStateUpdated(state: PreviewReplyUiState) {
            replyPreviewRetriever?.let {
                replyView?.render(
                        state,
                        it,
                        attributes.informationData,
                        movementMethod,
                        attributes.itemLongClickListener,
                        coroutineScope,
                        attributes.generateMissingVideoThumbnails
                )
            }
        }
    }
    override fun ignoreMessageGuideline(context: Context): Boolean {
        val messageLayout = attributes.informationData.messageLayout as? TimelineMessageLayout.ScBubble ?: return false
        return infoInBubbles(messageLayout) && canHideAvatars(attributes)
    }

}
