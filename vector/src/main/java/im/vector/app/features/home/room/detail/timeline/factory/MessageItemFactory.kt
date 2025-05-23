/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.factory

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import dagger.Lazy
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.extensions.getVectorLastMessageContent
import im.vector.app.core.files.LocalFilesHelper
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.resources.StringProvider
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.core.utils.containsOnlyEmojis
import im.vector.app.core.utils.customToPseudoEmoji
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.helper.AudioMessagePlaybackTracker
import im.vector.app.features.home.room.detail.timeline.helper.AvatarSizeProvider
import im.vector.app.features.home.room.detail.timeline.helper.ContentDownloadStateTrackerBinder
import im.vector.app.features.home.room.detail.timeline.helper.ContentUploadStateTrackerBinder
import im.vector.app.features.home.room.detail.timeline.helper.LocationPinProvider
import im.vector.app.features.home.room.detail.timeline.helper.MessageInformationDataFactory
import im.vector.app.features.home.room.detail.timeline.helper.MessageItemAttributesFactory
import im.vector.app.features.home.room.detail.timeline.helper.TimelineMediaSizeProvider
import im.vector.app.features.home.room.detail.timeline.item.AbsMessageItem
import im.vector.app.features.home.room.detail.timeline.item.BaseEventItem
import im.vector.app.features.home.room.detail.timeline.item.MessageAudioItem
import im.vector.app.features.home.room.detail.timeline.item.MessageAudioItem_
import im.vector.app.features.home.room.detail.timeline.item.MessageFileItem
import im.vector.app.features.home.room.detail.timeline.item.MessageFileItem_
import im.vector.app.features.home.room.detail.timeline.item.MessageImageVideoItem
import im.vector.app.features.home.room.detail.timeline.item.MessageImageVideoItem_
import im.vector.app.features.home.room.detail.timeline.item.MessageInformationData
import im.vector.app.features.home.room.detail.timeline.item.MessageLocationItem
import im.vector.app.features.home.room.detail.timeline.item.MessageLocationItem_
import im.vector.app.features.home.room.detail.timeline.item.MessageTextItem
import im.vector.app.features.home.room.detail.timeline.item.MessageTextItem_
import im.vector.app.features.home.room.detail.timeline.item.MessageVoiceItem
import im.vector.app.features.home.room.detail.timeline.item.MessageVoiceItem_
import im.vector.app.features.home.room.detail.timeline.item.PollItem
import im.vector.app.features.home.room.detail.timeline.item.PollItem_
import im.vector.app.features.home.room.detail.timeline.item.RedactedMessageItem
import im.vector.app.features.home.room.detail.timeline.item.RedactedMessageItem_
import im.vector.app.features.home.room.detail.timeline.item.VerificationRequestItem
import im.vector.app.features.home.room.detail.timeline.item.VerificationRequestItem_
import im.vector.app.features.home.room.detail.timeline.render.EventTextRenderer
import im.vector.app.features.home.room.detail.timeline.render.ProcessBodyOfReplyToEventUseCase
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.tools.createLinkMovementMethod
import im.vector.app.features.home.room.detail.timeline.tools.linkify
import im.vector.app.features.html.EventHtmlRenderer
import im.vector.app.features.html.PillsPostProcessor
import im.vector.app.features.html.SpanUtils
import im.vector.app.features.html.VectorHtmlCompressor
import im.vector.app.features.location.INITIAL_MAP_ZOOM_IN_TIMELINE
import im.vector.app.features.location.UrlMapProvider
import im.vector.app.features.location.toLocationData
import im.vector.app.features.media.ImageContentRenderer
import im.vector.app.features.media.VideoContentRenderer
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.voice.AudioWaveformView
import im.vector.app.features.voicebroadcast.isVoiceBroadcast
import im.vector.app.features.voicebroadcast.model.MessageVoiceBroadcastInfoContent
import im.vector.lib.core.utils.epoxy.charsequence.toEpoxyCharSequence
import im.vector.lib.core.utils.epoxy.charsequence.toMessageTextEpoxyCharSequence
import im.vector.lib.core.utils.timer.Clock
import im.vector.lib.strings.CommonStrings
import me.gujun.android.span.span
import org.matrix.android.sdk.api.MatrixUrls.isMxcUrl
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.crypto.attachments.toElementToDecrypt
import org.matrix.android.sdk.api.session.events.model.RelationType
import org.matrix.android.sdk.api.session.events.model.content.EncryptedEventContent
import org.matrix.android.sdk.api.session.events.model.isThread
import org.matrix.android.sdk.api.session.events.model.toContent
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.room.getTimelineEvent
import org.matrix.android.sdk.api.session.room.model.message.MessageAudioContent
import org.matrix.android.sdk.api.session.room.model.message.MessageBeaconInfoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageContentWithFormattedBody
import org.matrix.android.sdk.api.session.room.model.message.MessageEmoteContent
import org.matrix.android.sdk.api.session.room.model.message.MessageEndPollContent
import org.matrix.android.sdk.api.session.room.model.message.MessageFileContent
import org.matrix.android.sdk.api.session.room.model.message.MessageImageInfoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageLocationContent
import org.matrix.android.sdk.api.session.room.model.message.MessageNoticeContent
import org.matrix.android.sdk.api.session.room.model.message.MessagePollContent
import org.matrix.android.sdk.api.session.room.model.message.MessageTextContent
import org.matrix.android.sdk.api.session.room.model.message.MessageType
import org.matrix.android.sdk.api.session.room.model.message.MessageVerificationRequestContent
import org.matrix.android.sdk.api.session.room.model.message.MessageVideoContent
import org.matrix.android.sdk.api.session.room.model.message.asMessageAudioEvent
import org.matrix.android.sdk.api.session.room.model.message.getCaption
import org.matrix.android.sdk.api.session.room.model.message.getFileName
import org.matrix.android.sdk.api.session.room.model.message.getFileUrl
import org.matrix.android.sdk.api.session.room.model.message.getThumbnailUrl
import org.matrix.android.sdk.api.session.room.model.relation.ReplyToContent
import org.matrix.android.sdk.api.session.room.timeline.getRelationContent
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.api.util.MimeTypes
import timber.log.Timber
import javax.inject.Inject

class MessageItemFactory @Inject constructor(
        private val localFilesHelper: LocalFilesHelper,
        private val colorProvider: ColorProvider,
        private val dimensionConverter: DimensionConverter,
        private val timelineMediaSizeProvider: TimelineMediaSizeProvider,
        private val htmlRenderer: Lazy<EventHtmlRenderer>,
        private val htmlCompressor: VectorHtmlCompressor,
        private val textRendererFactory: EventTextRenderer.Factory,
        private val stringProvider: StringProvider,
        private val imageContentRenderer: ImageContentRenderer,
        private val messageInformationDataFactory: MessageInformationDataFactory,
        private val messageItemAttributesFactory: MessageItemAttributesFactory,
        private val contentUploadStateTrackerBinder: ContentUploadStateTrackerBinder,
        private val contentDownloadStateTrackerBinder: ContentDownloadStateTrackerBinder,
        private val defaultItemFactory: DefaultItemFactory,
        private val noticeItemFactory: NoticeItemFactory,
        private val avatarSizeProvider: AvatarSizeProvider,
        private val pillsPostProcessorFactory: PillsPostProcessor.Factory,
        private val lightweightSettingsStorage: LightweightSettingsStorage,
        private val spanUtils: SpanUtils,
        private val session: Session,
        private val clock: Clock,
        private val audioMessagePlaybackTracker: AudioMessagePlaybackTracker,
        private val locationPinProvider: LocationPinProvider,
        private val vectorPreferences: VectorPreferences,
        private val urlMapProvider: UrlMapProvider,
        private val liveLocationShareMessageItemFactory: LiveLocationShareMessageItemFactory,
        private val pollItemViewStateFactory: PollItemViewStateFactory,
        private val voiceBroadcastItemFactory: VoiceBroadcastItemFactory,
        private val processBodyOfReplyToEventUseCase: ProcessBodyOfReplyToEventUseCase,
) {

    // TODO inject this properly?
    private var roomId: String = ""

    private val pillsPostProcessor by lazy {
        pillsPostProcessorFactory.create(roomId)
    }

    private val textRenderer by lazy {
        textRendererFactory.create(roomId)
    }

    private val useRichTextEditorStyle: Boolean
        get() = vectorPreferences.isRichTextEditorEnabled()

    fun create(params: TimelineItemFactoryParams): VectorEpoxyModel<*>? {
        val event = params.event
        val highlight = params.isHighlighted
        val callback = params.callback
        event.root.eventId ?: return null
        roomId = event.roomId
        val informationData = messageInformationDataFactory.create(params)
        val threadDetails = if (params.isFromThreadTimeline()) null else event.root.threadDetails

        if (event.root.isRedacted()) {
            // message is redacted
            val attributes = messageItemAttributesFactory.create(null, informationData, callback, params.reactionsSummaryEvents, threadDetails)
            return buildRedactedItem(attributes, highlight)
        }

        val messageContent = event.getVectorLastMessageContent()
        if (messageContent == null) {
            val malformedText = stringProvider.getString(CommonStrings.malformed_message)
            return defaultItemFactory.create(malformedText, informationData, highlight, callback)
        }
        if (messageContent.relatesTo?.type == RelationType.REPLACE ||
                event.isEncrypted() && event.root.content.toModel<EncryptedEventContent>()?.relatesTo?.type == RelationType.REPLACE
        ) {
            // This is an edit event, we should display it when debugging as a notice event
            return noticeItemFactory.create(params)
        }

        if (lightweightSettingsStorage.areThreadMessagesEnabled() && !params.isFromThreadTimeline() && event.root.isThread()) {
            // This is a thread event and we will [debug] display it when we are in the main timeline
            return noticeItemFactory.create(params)
        }

        // always hide summary when we are on thread timeline
        val attributes = messageItemAttributesFactory.create(messageContent, informationData, callback, params.reactionsSummaryEvents, threadDetails)

        //        val all = event.root.toContent()
        //        val ev = all.toModel<Event>()
        val messageItem = when (messageContent) {
            is MessageEmoteContent -> buildEmoteMessageItem(messageContent, informationData, highlight, callback, attributes)
            is MessageTextContent -> buildItemForTextContent(messageContent, informationData, highlight, callback, attributes)
            is MessageImageInfoContent -> buildImageMessageItem(messageContent, informationData, highlight, callback, attributes)
            is MessageNoticeContent -> buildNoticeMessageItem(messageContent, informationData, highlight, callback, attributes)
            is MessageVideoContent -> buildVideoMessageItem(messageContent, informationData, highlight, callback, attributes)
            is MessageFileContent -> buildFileMessageItem(messageContent, highlight, callback, attributes)
            is MessageAudioContent -> buildAudioContent(params, messageContent, informationData, highlight, callback, attributes)
            is MessageVerificationRequestContent -> buildVerificationRequestMessageItem(messageContent, informationData, highlight, callback, attributes)
            is MessagePollContent -> buildPollItem(messageContent, informationData, highlight, callback, attributes, isEnded = false)
            is MessageEndPollContent -> buildEndedPollItem(event.getRelationContent()?.eventId, informationData, highlight, callback, attributes)
            is MessageLocationContent -> buildLocationItem(messageContent, informationData, highlight, callback, attributes)
            is MessageBeaconInfoContent -> liveLocationShareMessageItemFactory.create(event, highlight, attributes)
            is MessageVoiceBroadcastInfoContent -> voiceBroadcastItemFactory.create(params, messageContent, highlight, attributes)
            else -> buildNotHandledMessageItem(messageContent, informationData, highlight, callback, attributes)
        }
        return messageItem?.apply {
            layout(informationData.messageLayout.layoutRes)
        }
    }

    private fun buildLocationItem(
            locationContent: MessageLocationContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): MessageLocationItem? {
        val width = timelineMediaSizeProvider.getMaxSize().first
        val height = dimensionConverter.dpToPx(MESSAGE_LOCATION_ITEM_HEIGHT_IN_DP)

        val locationUrl = locationContent.toLocationData()?.let {
            urlMapProvider.buildStaticMapUrl(it, INITIAL_MAP_ZOOM_IN_TIMELINE, width, height)
        }

        val pinMatrixItem = if (locationContent.isSelfLocation()) informationData.matrixItem else null

        return MessageLocationItem_()
                .attributes(attributes)
                .locationUrl(locationUrl)
                .mapWidth(width)
                .mapHeight(height)
                .pinMatrixItem(pinMatrixItem)
                .locationPinProvider(locationPinProvider)
                .highlighted(highlight)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun buildPollItem(
            pollContent: MessagePollContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
            isEnded: Boolean,
    ): PollItem {
        val pollViewState = pollItemViewStateFactory.create(
                pollContent = pollContent,
                pollResponseData = informationData.pollResponseAggregatedSummary,
                isSent = informationData.sendState.isSent(),
        )

        return PollItem_()
                .attributes(attributes)
                .eventId(informationData.eventId)
                .pollTitle(createPollQuestion(informationData, pollViewState.question, callback))
                .canVote(pollViewState.canVote)
                .votesStatus(pollViewState.votesStatus)
                .optionViewStates(pollViewState.optionViewStates.orEmpty())
                .edited(informationData.hasBeenEdited)
                .ended(isEnded)
                .highlighted(highlight)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .callback(callback)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun buildEndedPollItem(
            pollStartEventId: String?,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): PollItem {
        val pollStartEvent = if (pollStartEventId?.isNotEmpty() == true) {
            session.roomService().getRoom(roomId)?.getTimelineEvent(pollStartEventId)
        } else {
            null
        }

        val editedContent = pollStartEvent?.annotations?.editSummary?.latestEdit?.getClearContent()?.toModel<MessagePollContent>()?.newContent
        val latestContent = editedContent ?: pollStartEvent?.root?.getClearContent()
        val pollContent = latestContent?.toModel<MessagePollContent>()

        return if (pollContent == null) {
            val title = stringProvider.getString(CommonStrings.message_reply_to_ended_poll_preview).toEpoxyCharSequence()
            PollItem_()
                    .attributes(attributes)
                    .eventId(informationData.eventId)
                    .pollTitle(title)
                    .optionViewStates(emptyList())
                    .edited(informationData.hasBeenEdited)
                    .ended(true)
                    .hasContent(false)
                    .highlighted(highlight)
                    .leftGuideline(avatarSizeProvider.leftGuideline)
                    .callback(callback)
        } else {
            buildPollItem(
                    pollContent,
                    informationData,
                    highlight,
                    callback,
                    attributes,
                    isEnded = true,
            )
        }
    }

    private fun createPollQuestion(
            informationData: MessageInformationData,
            question: String,
            callback: TimelineEventController.Callback?,
    ) = if (informationData.hasBeenEdited) {
        annotateWithEdited(question, callback, informationData)
    } else {
        question
    }.toEpoxyCharSequence()

    private fun buildAudioMessageItem(
            params: TimelineItemFactoryParams,
            messageContent: MessageAudioContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes
    ): MessageAudioItem {
        val fileUrl = getAudioFileUrl(messageContent, informationData)
        val playbackControlButtonClickListener = createOnPlaybackButtonClickListener(messageContent, informationData, params)
        val duration = messageContent.audioInfo?.duration ?: 0

        return MessageAudioItem_()
                .attributes(attributes)
                .filename(messageContent.getFileName())
                .caption(messageContent.getCaption())
                .duration(messageContent.audioInfo?.duration ?: 0)
                .playbackControlButtonClickListener(playbackControlButtonClickListener)
                .audioMessagePlaybackTracker(audioMessagePlaybackTracker)
                .izLocalFile(localFilesHelper.isLocalFile(fileUrl))
                .fileSize(messageContent.audioInfo?.size ?: 0L)
                .onSeek { params.callback?.onAudioSeekBarMovedTo(informationData.eventId, duration, it) }
                .mxcUrl(fileUrl)
                .contentUploadStateTrackerBinder(contentUploadStateTrackerBinder)
                .contentDownloadStateTrackerBinder(contentDownloadStateTrackerBinder)
                .highlighted(highlight)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun getAudioFileUrl(
            messageContent: MessageAudioContent,
            informationData: MessageInformationData,
    ) = messageContent.getFileUrl()?.let {
        if (informationData.sentByMe && !informationData.sendState.isSent()) {
            it
        } else {
            it.takeIf { it.isMxcUrl() }
        }
    } ?: ""

    private fun createOnPlaybackButtonClickListener(
            messageContent: MessageAudioContent,
            informationData: MessageInformationData,
            params: TimelineItemFactoryParams,
    ) = object : ClickListener {
        override fun invoke(view: View) {
            params.callback?.onVoiceControlButtonClicked(informationData.eventId, messageContent)
        }
    }

    private fun buildVoiceMessageItem(
            params: TimelineItemFactoryParams,
            messageContent: MessageAudioContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes
    ): BaseEventItem<*>? {
        // Do not display voice broadcast messages
        if (params.event.root.asMessageAudioEvent().isVoiceBroadcast()) {
            return noticeItemFactory.create(params)
        }

        val fileUrl = getAudioFileUrl(messageContent, informationData)
        val playbackControlButtonClickListener = createOnPlaybackButtonClickListener(messageContent, informationData, params)

        val waveformTouchListener: MessageVoiceItem.WaveformTouchListener = object : MessageVoiceItem.WaveformTouchListener {
            override fun onWaveformTouchedUp(percentage: Float) {
                val duration = messageContent.audioInfo?.duration ?: 0
                params.callback?.onVoiceWaveformTouchedUp(informationData.eventId, duration, percentage)
            }

            override fun onWaveformMovedTo(percentage: Float) {
                val duration = messageContent.audioInfo?.duration ?: 0
                params.callback?.onVoiceWaveformMovedTo(informationData.eventId, duration, percentage)
            }
        }

        return MessageVoiceItem_()
                .attributes(attributes)
                .duration(messageContent.audioWaveformInfo?.duration ?: 0)
                .waveform(messageContent.audioWaveformInfo?.waveform?.toFft().orEmpty())
                .playbackControlButtonClickListener(playbackControlButtonClickListener)
                .waveformTouchListener(waveformTouchListener)
                .audioMessagePlaybackTracker(audioMessagePlaybackTracker)
                .izLocalFile(localFilesHelper.isLocalFile(fileUrl))
                .mxcUrl(fileUrl)
                .contentUploadStateTrackerBinder(contentUploadStateTrackerBinder)
                .contentDownloadStateTrackerBinder(contentDownloadStateTrackerBinder)
                .highlighted(highlight)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun buildVerificationRequestMessageItem(
            messageContent: MessageVerificationRequestContent,
            @Suppress("UNUSED_PARAMETER")
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): VerificationRequestItem? {
        // If this request is not sent by me or sent to me, we should ignore it in timeline
        val myUserId = session.myUserId
        if (informationData.senderId != myUserId && messageContent.toUserId != myUserId) {
            return null
        }

        val otherUserId = if (informationData.sentByMe) messageContent.toUserId else informationData.senderId
        val otherUserName = if (informationData.sentByMe) {
            session.roomService().getRoomMember(messageContent.toUserId, roomId)?.displayName
        } else {
            informationData.memberName
        }
        return VerificationRequestItem_()
                .attributes(
                        VerificationRequestItem.Attributes(
                                otherUserId = otherUserId,
                                otherUserName = otherUserName.toString(),
                                referenceId = informationData.eventId,
                                informationData = informationData,
                                avatarRenderer = attributes.avatarRenderer,
                                messageColorProvider = attributes.messageColorProvider,
                                itemLongClickListener = attributes.itemLongClickListener,
                                itemClickListener = attributes.itemClickListener,
                                reactionPillCallback = attributes.reactionPillCallback,
                                readReceiptsCallback = attributes.readReceiptsCallback,
                                emojiTypeFace = attributes.emojiTypeFace,
                                reactionsSummaryEvents = attributes.reactionsSummaryEvents,
                        )
                )
                .clock(clock)
                .callback(callback)
                .highlighted(highlight)
                .leftGuideline(avatarSizeProvider.leftGuideline)
    }

    private fun buildFileMessageItem(
            messageContent: MessageFileContent,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): MessageFileItem {
        val mxcUrl = messageContent.getFileUrl() ?: ""
        return MessageFileItem_()
                .attributes(attributes)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .izLocalFile(localFilesHelper.isLocalFile(messageContent.getFileUrl()))
                .izDownloaded(session.fileService().isFileInCache(messageContent))
                .mxcUrl(mxcUrl)
                .contentUploadStateTrackerBinder(contentUploadStateTrackerBinder)
                .contentDownloadStateTrackerBinder(contentDownloadStateTrackerBinder)
                .highlighted(highlight)
                .filename(messageContent.getFileName())
                .caption(messageContent.getCaption())
                .iconRes(R.drawable.ic_paperclip)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun buildAudioContent(
            params: TimelineItemFactoryParams,
            messageContent: MessageAudioContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ) = if (messageContent.voiceMessageIndicator != null) {
        buildVoiceMessageItem(params, messageContent, informationData, highlight, callback, attributes)
    } else {
        buildAudioMessageItem(params, messageContent, informationData, highlight, callback, attributes)
    }

    private fun buildNotHandledMessageItem(
            messageContent: MessageContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes
    ): MessageTextItem? {
        // For compatibility reason we should display the body
        return buildMessageTextItem(
                messageContent.body,
                false,
                informationData,
                highlight,
                callback,
                attributes,
        )
    }

    private fun buildImageMessageItem(
            messageContent: MessageImageInfoContent,
            @Suppress("UNUSED_PARAMETER")
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): MessageImageVideoItem? {
        val (maxWidth, maxHeight) = timelineMediaSizeProvider.getMaxSize()
        val data = ImageContentRenderer.Data(
                eventId = informationData.eventId,
                filename = messageContent.getFileName(),
                caption = messageContent.getCaption(),
                mimeType = messageContent.mimeType,
                url = messageContent.getFileUrl(),
                elementToDecrypt = messageContent.encryptedFileInfo?.toElementToDecrypt(),
                height = messageContent.info?.height,
                maxHeight = maxHeight,
                width = messageContent.info?.width,
                maxWidth = maxWidth,
                allowNonMxcUrls = informationData.sendState.isSending()
        )

        // The webp library doesn't allow us to not animate images.
        // Furthermore, its corner transformations are wrong when not using the animated case for rendering.
        val forcePlay = messageContent.mimeType == MimeTypes.Webp
        val playable = messageContent.mimeType == MimeTypes.Gif || forcePlay
        // don't show play button because detecting animated webp isn't possible via mimetype
        val playableIfAutoplay = playable || messageContent.mimeType == MimeTypes.Webp

        return MessageImageVideoItem_()
                .attributes(attributes.takeUnless { forcePlay && !it.autoplayAnimatedImages } ?: attributes.copy(autoplayAnimatedImages = true))
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .imageContentRenderer(imageContentRenderer)
                .contentUploadStateTrackerBinder(contentUploadStateTrackerBinder)
                .playable(playable)
                .highlighted(highlight)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
                .mediaData(data)
                .apply {
                    val inMemory = if (messageContent.msgType == MessageType.MSGTYPE_STICKER_LOCAL) {
                        mode(ImageContentRenderer.Mode.STICKER)
                        listOf(data)
                    } else {
                        emptyList()
                    }
                    // Big image viewer doesn't support webp
                    if (messageContent.mimeType != MimeTypes.Webp) {
                        clickListener { view ->
                            callback?.onImageMessageClicked(messageContent, data, view, inMemory)
                        }
                    }
                }.apply {
                    if (playableIfAutoplay && vectorPreferences.autoplayAnimatedImages()) {
                        mode(ImageContentRenderer.Mode.ANIMATED_THUMBNAIL)
                    }
                }
    }

    private fun buildVideoMessageItem(
            messageContent: MessageVideoContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): MessageImageVideoItem? {
        val (maxWidth, maxHeight) = timelineMediaSizeProvider.getMaxSize()
        val thumbnailData = ImageContentRenderer.Data(
                eventId = informationData.eventId,
                filename = messageContent.getFileName(),
                caption = messageContent.getCaption(),
                mimeType = messageContent.mimeType,
                url = messageContent.videoInfo?.getThumbnailUrl(),
                elementToDecrypt = messageContent.videoInfo?.thumbnailFile?.toElementToDecrypt(),
                height = messageContent.videoInfo?.height,
                maxHeight = maxHeight,
                width = messageContent.videoInfo?.width,
                maxWidth = maxWidth,
                allowNonMxcUrls = informationData.sendState.isSending(),
                // Video fallback for generating thumbnails
                downloadFallbackIfThumbnailMissing = attributes.generateMissingVideoThumbnails,
                fallbackUrl = messageContent.getFileUrl(),
                fallbackElementToDecrypt = messageContent.encryptedFileInfo?.toElementToDecrypt()
        )

        val videoData = VideoContentRenderer.Data(
                eventId = informationData.eventId,
                filename = messageContent.getFileName(),
                caption = messageContent.getCaption(),
                mimeType = messageContent.mimeType,
                url = messageContent.getFileUrl(),
                elementToDecrypt = messageContent.encryptedFileInfo?.toElementToDecrypt(),
                thumbnailMediaData = thumbnailData
        )

        return MessageImageVideoItem_()
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .attributes(attributes)
                .imageContentRenderer(imageContentRenderer)
                .contentUploadStateTrackerBinder(contentUploadStateTrackerBinder)
                .playable(true)
                .highlighted(highlight)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
                .mediaData(thumbnailData)
                .clickListener { view -> callback?.onVideoMessageClicked(messageContent, videoData, view.findViewById(R.id.messageThumbnailView)) }
    }

    private fun buildItemForTextContent(
            messageContent: MessageTextContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): VectorEpoxyModel<*>? {
        val matrixFormattedBody = messageContent.matrixFormattedBody
        return if (matrixFormattedBody != null) {
            val replyToContent = messageContent.relatesTo?.inReplyTo
            buildFormattedTextItem(matrixFormattedBody, informationData, highlight, callback, attributes, replyToContent)
        } else {
            buildMessageTextItem(messageContent.body, false, informationData, highlight, callback, attributes)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun buildFormattedTextItem(
            matrixFormattedBody: String,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
            replyToContent: ReplyToContent?,
    ): MessageTextItem? {
        /*
        val processedBody = matrixFormattedBodyreplyToContent
                ?.let { processBodyOfReplyToEventUseCase.execute(roomId, matrixFormattedBody, it) }
                ?: matrixFormattedBody
         */
        val compressed = htmlCompressor.compress(matrixFormattedBody)
        val renderedFormattedBody = htmlRenderer.get().render(compressed, pillsPostProcessor) as? Spanned ?: compressed
        val pseudoEmojiCompressed = customToPseudoEmoji(compressed)
        val pseudoEmojiBody = htmlRenderer.get().render(pseudoEmojiCompressed, pillsPostProcessor) as? Spanned ?: pseudoEmojiCompressed
        return buildMessageTextItem(
                renderedFormattedBody,
                true,
                informationData,
                highlight,
                callback,
                attributes,
                pseudoEmojiBody,
        )
    }

    private fun buildMessageTextItem(
            body: CharSequence,
            isFormatted: Boolean,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
            emojiCheckCharSequence: CharSequence? = null,
    ): MessageTextItem? {
        val renderedBody = textRenderer.render(body)
        val bindingOptions = spanUtils.getBindingOptions(renderedBody)
        val linkifiedBody = renderedBody.linkify(callback)

        // Only for checking if it's a emoji-only message
        val pseudoEmojiBody = (emojiCheckCharSequence ?: linkifiedBody).replace(Regex("""\s"""), "")

        return MessageTextItem_()
                .message(
                    if (informationData.hasBeenEdited) {
                        annotateWithEdited(linkifiedBody, callback, informationData)
                    } else {
                        linkifiedBody
                    }.toMessageTextEpoxyCharSequence()
                )
                .useBigFont(pseudoEmojiBody.length <= MAX_NUMBER_OF_EMOJI_FOR_BIG_FONT * 2 && containsOnlyEmojis(pseudoEmojiBody))
                .bindingOptions(bindingOptions)
                .markwonPlugins(htmlRenderer.get().plugins)
                .searchForPills(isFormatted)
                .previewUrlRetriever(callback?.getPreviewUrlRetriever())
                .imageContentRenderer(imageContentRenderer)
                .previewUrlCallback(callback)
                .useRichTextEditorStyle(useRichTextEditorStyle)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .attributes(attributes)
                .highlighted(highlight)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun annotateWithEdited(
            linkifiedBody: CharSequence,
            callback: TimelineEventController.Callback?,
            informationData: MessageInformationData,
    ): Spannable {
        val spannable = SpannableStringBuilder()
        spannable.append(linkifiedBody)
        val editedSuffix = stringProvider.getString(CommonStrings.edited_suffix)
        spannable.append(" ").append(editedSuffix)
        val color = colorProvider.getColorFromAttribute(im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
        val editStart = spannable.lastIndexOf(editedSuffix)
        val editEnd = editStart + editedSuffix.length
        spannable.setSpan(
                ForegroundColorSpan(color),
                editStart,
                editEnd,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        )

        // Note: text size is set to 14sp
        spannable.setSpan(
                AbsoluteSizeSpan(dimensionConverter.spToPx(13)),
                editStart,
                editEnd,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        )

        spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        callback?.onEditedDecorationClicked(informationData)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        // nop
                    }
                },
                editStart,
                editEnd,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun buildNoticeMessageItem(
            messageContent: MessageNoticeContent,
            @Suppress("UNUSED_PARAMETER")
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attrs: AbsMessageItem.Attributes,
    ): VectorEpoxyModel<*>? {
        val attributes = attrs.copy(isNotice = true)

        val formattedBody: CharSequence
        val htmlBody = messageContent.getHtmlBody()

        if (attributes.informationData.messageLayout is TimelineMessageLayout.ScBubble) {
            // Just use normal text rendering for notices
            val noticeAsTextContent = messageContent.toContent().toModel<MessageTextContent>()
            if (noticeAsTextContent != null) {
                return buildItemForTextContent(
                        noticeAsTextContent,
                        informationData,
                        highlight,
                        callback,
                        attributes
                )
            }
            Timber.w("Could not parse notice as text item, using legacy fallback")

            // SchildiChat likes to not overwrite message formatting for notices, as opposed to upstream
            formattedBody = htmlBody
        } else {

        // Upstream impl for non-SC-bubbles

        formattedBody = span {
            text = htmlBody
            textColor = colorProvider.getColorFromAttribute(im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
            textStyle = "italic"
        }

        }

        val bindingOptions = spanUtils.getBindingOptions(htmlBody)
        val message = formattedBody.linkify(callback)

        return MessageTextItem_()
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .previewUrlRetriever(callback?.getPreviewUrlRetriever())
                .imageContentRenderer(imageContentRenderer)
                .previewUrlCallback(callback)
                .attributes(attributes)
                .message(message.toMessageTextEpoxyCharSequence())
                .bindingOptions(bindingOptions)
                .highlighted(highlight)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun buildEmoteMessageItem(
            messageContent: MessageEmoteContent,
            informationData: MessageInformationData,
            highlight: Boolean,
            callback: TimelineEventController.Callback?,
            attributes: AbsMessageItem.Attributes,
    ): MessageTextItem? {
        val formattedBody = SpannableStringBuilder()
        formattedBody.append("* ${informationData.memberName} ")
        formattedBody.append(messageContent.getHtmlBody())
        val bindingOptions = spanUtils.getBindingOptions(formattedBody)
        val message = formattedBody.linkify(callback)

        return MessageTextItem_()
                .message(
                        if (informationData.hasBeenEdited) {
                            annotateWithEdited(message, callback, informationData)
                        } else {
                            message
                        }.toMessageTextEpoxyCharSequence()
                )
                .bindingOptions(bindingOptions)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .previewUrlRetriever(callback?.getPreviewUrlRetriever())
                .imageContentRenderer(imageContentRenderer)
                .previewUrlCallback(callback)
                .attributes(attributes)
                .highlighted(highlight)
                .movementMethod(createLinkMovementMethod(callback))
                .replyPreviewRetriever(callback?.getReplyPreviewRetriever())
                .inReplyToClickCallback(callback)
    }

    private fun MessageContentWithFormattedBody.getHtmlBody(): CharSequence {
        return matrixFormattedBody
                ?.let { htmlCompressor.compress(it) }
                ?.let { htmlRenderer.get().render(it, pillsPostProcessor) }
                ?: body
    }

    private fun buildRedactedItem(
            attributes: AbsMessageItem.Attributes,
            highlight: Boolean,
    ): RedactedMessageItem? {
        return RedactedMessageItem_()
                .layout(attributes.informationData.messageLayout.layoutRes)
                .leftGuideline(avatarSizeProvider.leftGuideline)
                .attributes(attributes)
                .highlighted(highlight)
    }

    private fun List<Int?>?.toFft(): List<Int>? {
        return this
                ?.filterNotNull()
                ?.map {
                    // Value comes from AudioWaveformView.MAX_FFT, and 1024 is the max value in the Matrix spec
                    it * AudioWaveformView.MAX_FFT / 1024
                }
    }

    companion object {
        private const val MAX_NUMBER_OF_EMOJI_FOR_BIG_FONT = 5
        const val MESSAGE_LOCATION_ITEM_HEIGHT_IN_DP = 200
    }
}
