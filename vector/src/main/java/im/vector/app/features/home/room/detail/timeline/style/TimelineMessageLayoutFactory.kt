/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.style

import android.content.res.Resources
import im.vector.app.core.extensions.getVectorLastMessageContent
import im.vector.app.core.extensions.localDateTime
import im.vector.app.core.resources.LocaleProvider
import im.vector.app.core.resources.isRTL
import im.vector.app.features.home.room.detail.timeline.factory.TimelineItemFactoryParams
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.themes.BubbleThemeUtils
import im.vector.app.features.voicebroadcast.VoiceBroadcastConstants.STATE_ROOM_VOICE_BROADCAST_INFO
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageImageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageNoticeContent
import org.matrix.android.sdk.api.session.room.model.message.MessageType
import org.matrix.android.sdk.api.session.room.model.message.MessageVerificationRequestContent
import org.matrix.android.sdk.api.session.room.model.message.MessageWithAttachmentContent
import org.matrix.android.sdk.api.session.room.model.message.getCaption
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.getLastMessageContent
import org.matrix.android.sdk.api.session.room.timeline.isEdition
import org.matrix.android.sdk.api.session.room.timeline.isReply
import org.matrix.android.sdk.api.session.room.timeline.isReplyRenderedInThread
import org.matrix.android.sdk.api.session.room.timeline.isRootThread
import javax.inject.Inject

class TimelineMessageLayoutFactory @Inject constructor(
        private val session: Session,
        private val layoutSettingsProvider: TimelineLayoutSettingsProvider,
        private val localeProvider: LocaleProvider,
        private val resources: Resources,
        private val bubbleThemeUtils: BubbleThemeUtils,
        private val vectorPreferences: VectorPreferences
) {

    companion object {
        // Can be rendered in bubbles, other types will fallback to default
        private val EVENT_TYPES_WITH_BUBBLE_LAYOUT = setOf(
                EventType.MESSAGE,
                EventType.ENCRYPTED,
                EventType.STICKER,
        ) +
                EventType.POLL_START.values +
                EventType.POLL_END.values +
                EventType.STATE_ROOM_BEACON_INFO.values

        // Can't be rendered in bubbles, so get back to default layout
        private val MSG_TYPES_WITHOUT_BUBBLE_LAYOUT = setOf(
                MessageType.MSGTYPE_VERIFICATION_REQUEST
        )
        private val EVENT_TYPES_WITHOUT_SC_BUBBLE_LAYOUT = setOf(
                STATE_ROOM_VOICE_BROADCAST_INFO,
        )

        // Use the bubble layout but without borders
        private val MSG_TYPES_WITH_PSEUDO_BUBBLE_LAYOUT = setOf(
                MessageType.MSGTYPE_IMAGE,
                MessageType.MSGTYPE_VIDEO,
                MessageType.MSGTYPE_STICKER_LOCAL,
                //MessageType.MSGTYPE_EMOTE,
                MessageType.MSGTYPE_BEACON_INFO,
                MessageType.MSGTYPE_LOCATION,
                MessageType.MSGTYPE_BEACON_LOCATION_DATA,
        )
        private val MSG_TYPES_WITH_TIMESTAMP_INSIDE_MESSAGE = setOf(
                MessageType.MSGTYPE_IMAGE,
                MessageType.MSGTYPE_VIDEO,
                MessageType.MSGTYPE_STICKER_LOCAL,
                MessageType.MSGTYPE_BEACON_INFO,
                MessageType.MSGTYPE_LOCATION,
                MessageType.MSGTYPE_BEACON_LOCATION_DATA,
        )
    }

    private val cornerRadius: Float by lazy {
        resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_corner_radius).toFloat()
    }

    private val isRTL: Boolean by lazy {
        localeProvider.isRTL()
    }

    fun create(params: TimelineItemFactoryParams): TimelineMessageLayout {
        val event = params.event
        val nextDisplayableEvent = params.nextDisplayableEvent
        val prevDisplayableEvent = params.prevDisplayableEvent
        val isSentByMe = event.root.senderId == session.myUserId

        val date = event.root.localDateTime()
        val nextDate = nextDisplayableEvent?.root?.localDateTime()
        val addDaySeparator = date.toLocalDate() != nextDate?.toLocalDate()

        val isNextMessageReceivedMoreThanOneHourAgo = nextDate?.isBefore(date.minusMinutes(60))
                ?: false

        val showInformation = addDaySeparator ||
                event.senderInfo.avatarUrl != nextDisplayableEvent?.senderInfo?.avatarUrl ||
                event.senderInfo.disambiguatedDisplayName != nextDisplayableEvent?.senderInfo?.disambiguatedDisplayName ||
                nextDisplayableEvent.root.getClearType() !in listOf(EventType.MESSAGE, EventType.STICKER, EventType.ENCRYPTED) ||
                isNextMessageReceivedMoreThanOneHourAgo ||
                isTileTypeMessage(nextDisplayableEvent) ||
                nextDisplayableEvent.isRootThread() ||
                event.isRootThread() ||
                nextDisplayableEvent.isEdition()

        val messageLayout = when (layoutSettingsProvider.getLayoutSettings()) {
            TimelineLayoutSettings.SC_BUBBLE -> {
                if (event.shouldNeverUseScLayout()) {
                    buildModernLayout(showInformation)
                } else {
                    val messageContent = event.getLastMessageContent()
                    val isBubble = event.shouldBuildBubbleLayout()
                    val singleSidedLayout = bubbleThemeUtils.getBubbleStyle() == BubbleThemeUtils.BUBBLE_STYLE_START
                    val pseudoBubble = messageContent.isPseudoBubble(event, params = params)
                    val showTimestamp = showInformation || !singleSidedLayout || vectorPreferences.alwaysShowTimeStamps()
                    return TimelineMessageLayout.ScBubble(
                            showAvatar = showInformation,
                            // Display names not required if
                            // - !showInformation -> multiple messages in a row, already had name before
                            // - redundantDisplayName -> message content already includes the display name (-> m.emote)
                            // Display name still required for single sided layout if timestamp is shown (empty space looks bad otherwise)
                            showDisplayName = showInformation && ((singleSidedLayout && showTimestamp) || !messageContent.redundantDisplayName()),
                            showTimestamp = showTimestamp,
                            bubbleAppearance = bubbleThemeUtils.getBubbleAppearance(),
                            isIncoming = !isSentByMe,
                            isNotice = messageContent is MessageNoticeContent,
                            reverseBubble = isSentByMe && !singleSidedLayout,
                            singleSidedLayout = singleSidedLayout,
                            isRealBubble = isBubble && !pseudoBubble,
                            isPseudoBubble = pseudoBubble,
                            timestampAsOverlay = messageContent.timestampInsideMessage()
                    )
                }
            }
            TimelineLayoutSettings.MODERN -> {
                buildModernLayout(showInformation)
            }
            TimelineLayoutSettings.BUBBLE -> {
                val shouldBuildBubbleLayout = event.shouldBuildBubbleLayout()
                if (shouldBuildBubbleLayout) {
                    val isFirstFromThisSender = nextDisplayableEvent == null || !nextDisplayableEvent.shouldBuildBubbleLayout() ||
                            nextDisplayableEvent.root.senderId != event.root.senderId || addDaySeparator

                    val isLastFromThisSender = prevDisplayableEvent == null || !prevDisplayableEvent.shouldBuildBubbleLayout() ||
                            prevDisplayableEvent.root.senderId != event.root.senderId ||
                            prevDisplayableEvent.root.localDateTime().toLocalDate() != date.toLocalDate()

                    val cornersRadius = buildCornersRadius(
                            isIncoming = !isSentByMe,
                            isFirstFromThisSender = isFirstFromThisSender,
                            isLastFromThisSender = isLastFromThisSender
                    )

                    val messageContent = event.getVectorLastMessageContent()
                    TimelineMessageLayout.Bubble(
                            showAvatar = showInformation && !isSentByMe,
                            showDisplayName = showInformation && !isSentByMe,
                            addTopMargin = isFirstFromThisSender && isSentByMe,
                            isIncoming = !isSentByMe,
                            cornersRadius = cornersRadius,
                            isPseudoBubble = messageContent.isPseudoBubble(event),
                            timestampInsideMessage = messageContent.timestampInsideMessage(),
                            addMessageOverlay = messageContent.shouldAddMessageOverlay(),
                    )
                } else {
                    buildModernLayout(showInformation)
                }
            }
        }
        return messageLayout
    }

    /**
     * Just a dumb layout setting, so we get basic ScBubble settings in strictly-non-bubble classes as well
     */
    fun createDummy(): TimelineMessageLayout {
        return when (layoutSettingsProvider.getLayoutSettings()) {
            TimelineLayoutSettings.SC_BUBBLE -> {
                val singleSidedLayout = bubbleThemeUtils.getBubbleStyle() == BubbleThemeUtils.BUBBLE_STYLE_START
                return TimelineMessageLayout.ScBubble(
                        showAvatar = false,
                        showDisplayName = false,
                        showTimestamp = true,
                        bubbleAppearance = bubbleThemeUtils.getBubbleAppearance(),
                        isIncoming = false,
                        isNotice = false,
                        reverseBubble = false,
                        singleSidedLayout = singleSidedLayout,
                        isRealBubble = false,
                        isPseudoBubble = false,
                        timestampAsOverlay = false
                )
            }
            else -> TimelineMessageLayout.Default(
                    showAvatar = false,
                    showDisplayName = false,
                    showTimestamp = vectorPreferences.alwaysShowTimeStamps()
            )
        }
    }

    private fun MessageContent?.isPseudoBubble(event: TimelineEvent, ignoreReply: Boolean = false, params: TimelineItemFactoryParams? = null): Boolean {
        if (this == null) return false
        if (event.root.isRedacted()) return false
        val isReply = if (params?.isFromThreadTimeline().orFalse()) event.isReplyRenderedInThread() else event.isReply()
        if  (!ignoreReply && isReply) return false
        if (this is MessageWithAttachmentContent && !getCaption().isNullOrBlank()) return false
        return this.msgType in MSG_TYPES_WITH_PSEUDO_BUBBLE_LAYOUT
    }

    private fun MessageContent?.redundantDisplayName(): Boolean {
        if (this == null) return false
        return msgType == MessageType.MSGTYPE_EMOTE
    }

    private fun MessageContent?.timestampInsideMessage(): Boolean {
        return when {
            this == null -> false
            else -> msgType in MSG_TYPES_WITH_TIMESTAMP_INSIDE_MESSAGE
        }
    }

    private fun MessageContent?.shouldAddMessageOverlay(): Boolean {
        return when {
            this == null || msgType == MessageType.MSGTYPE_BEACON_INFO -> false
            else -> msgType in MSG_TYPES_WITH_TIMESTAMP_INSIDE_MESSAGE
        }
    }

    private fun TimelineEvent.shouldBuildBubbleLayout(): Boolean {
        if (root.isRedacted()) {
            // Redacted messages always go into bubbles
            return true
        }
        val type = root.getClearType()
        if (type in EVENT_TYPES_WITH_BUBBLE_LAYOUT) {
            val messageContent = getVectorLastMessageContent()
            if (messageContent.isPseudoBubble(this, true)) return true
            return messageContent?.msgType !in MSG_TYPES_WITHOUT_BUBBLE_LAYOUT
        }
        return false
    }

    private fun TimelineEvent.shouldNeverUseScLayout(): Boolean {
        if (root.getClearType() in EVENT_TYPES_WITHOUT_SC_BUBBLE_LAYOUT) {
            return true
        }
        val messageContent = getLastMessageContent()
        return messageContent?.msgType in MSG_TYPES_WITHOUT_BUBBLE_LAYOUT
    }

    private fun buildModernLayout(showInformation: Boolean): TimelineMessageLayout.Default {
        return TimelineMessageLayout.Default(
                showAvatar = showInformation,
                showDisplayName = showInformation,
                showTimestamp = showInformation || vectorPreferences.alwaysShowTimeStamps()
        )
    }

    private fun buildCornersRadius(
            isIncoming: Boolean,
            isFirstFromThisSender: Boolean,
            isLastFromThisSender: Boolean
    ): TimelineMessageLayout.Bubble.CornersRadius {
        return if ((isIncoming && !isRTL) || (!isIncoming && isRTL)) {
            TimelineMessageLayout.Bubble.CornersRadius(
                    topStartRadius = if (isFirstFromThisSender) cornerRadius else 0f,
                    topEndRadius = cornerRadius,
                    bottomStartRadius = if (isLastFromThisSender) cornerRadius else 0f,
                    bottomEndRadius = cornerRadius
            )
        } else {
            TimelineMessageLayout.Bubble.CornersRadius(
                    topStartRadius = cornerRadius,
                    topEndRadius = if (isFirstFromThisSender) cornerRadius else 0f,
                    bottomStartRadius = cornerRadius,
                    bottomEndRadius = if (isLastFromThisSender) cornerRadius else 0f
            )
        }
    }

    /**
     * Tiles type message never show the sender information (like verification request), so we should repeat it for next message
     * even if same sender.
     */
    private fun isTileTypeMessage(event: TimelineEvent?): Boolean {
        return when (event?.root?.getClearType()) {
            EventType.KEY_VERIFICATION_DONE,
            EventType.KEY_VERIFICATION_CANCEL -> true
            EventType.MESSAGE -> {
                event.getVectorLastMessageContent() is MessageVerificationRequestContent
            }
            else -> false
        }
    }
}
