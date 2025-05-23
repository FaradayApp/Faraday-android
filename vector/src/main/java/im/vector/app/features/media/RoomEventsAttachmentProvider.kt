/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.media

import im.vector.app.core.date.VectorDateFormatter
import im.vector.app.core.resources.StringProvider
import im.vector.lib.attachmentviewer.AttachmentInfo
import kotlinx.coroutines.CoroutineScope
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.crypto.attachments.toElementToDecrypt
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.file.FileService
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageImageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageStickerContent
import org.matrix.android.sdk.api.session.room.model.message.MessageVideoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageWithAttachmentContent
import org.matrix.android.sdk.api.session.room.model.message.getCaption
import org.matrix.android.sdk.api.session.room.model.message.getFileName
import org.matrix.android.sdk.api.session.room.model.message.getFileUrl
import org.matrix.android.sdk.api.session.room.model.message.getThumbnailUrl
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.util.MimeTypes
import java.io.File

class RoomEventsAttachmentProvider(
        attachments: List<TimelineEvent>,
        imageContentRenderer: ImageContentRenderer,
        dateFormatter: VectorDateFormatter,
        fileService: FileService,
        coroutineScope: CoroutineScope,
        stringProvider: StringProvider
) : BaseAttachmentProvider<TimelineEvent>(
        attachments = attachments,
        imageContentRenderer = imageContentRenderer,
        fileService = fileService,
        coroutineScope = coroutineScope,
        dateFormatter = dateFormatter,
        stringProvider = stringProvider
) {

    override fun getAttachmentInfoAt(position: Int): AttachmentInfo {
        return getItem(position).let {
            val clearContent = it.root.getClearContent()
            val content = clearContent.toModel<MessageContent>()
                    ?: clearContent.toModel<MessageStickerContent>()
                            as? MessageWithAttachmentContent
            if (content is MessageImageContent) {
                val data = ImageContentRenderer.Data(
                        eventId = it.eventId,
                        filename = content.getFileName(),
                        caption = content.getCaption(),
                        mimeType = content.mimeType,
                        url = content.getFileUrl(),
                        elementToDecrypt = content.encryptedFileInfo?.toElementToDecrypt(),
                        maxHeight = -1,
                        maxWidth = -1,
                        width = null,
                        height = null,
                        allowNonMxcUrls = it.root.sendState.isSending()

                )
                if (content.mimeType == MimeTypes.Gif || content.mimeType == MimeTypes.Webp) {
                    AttachmentInfo.AnimatedImage(
                            uid = it.eventId,
                            url = content.url ?: "",
                            data = data
                    )
                } else {
                    AttachmentInfo.Image(
                            uid = it.eventId,
                            url = content.url ?: "",
                            data = data
                    )
                }
            } else if (content is MessageStickerContent) {
                val data = ImageContentRenderer.Data(
                        eventId = it.eventId,
                        filename = content.getFileName(),
                        caption = content.getCaption(),
                        mimeType = content.mimeType,
                        url = content.getFileUrl(),
                        elementToDecrypt = content.encryptedFileInfo?.toElementToDecrypt(),
                        maxHeight = -1,
                        maxWidth = -1,
                        width = null,
                        height = null,
                        allowNonMxcUrls = false

                )
                if (content.mimeType == MimeTypes.Gif) {
                    AttachmentInfo.AnimatedImage(
                            uid = it.eventId,
                            url = content.url ?: "",
                            data = data
                    )
                } else {
                    AttachmentInfo.Image(
                            uid = it.eventId,
                            url = content.url ?: "",
                            data = data
                    )
                }
            } else if (content is MessageVideoContent) {
                val thumbnailData = ImageContentRenderer.Data(
                        eventId = it.eventId,
                        filename = content.getFileName(),
                        caption = content.getCaption(),
                        mimeType = content.mimeType,
                        url = content.videoInfo?.getThumbnailUrl(),
                        elementToDecrypt = content.videoInfo?.thumbnailFile?.toElementToDecrypt(),
                        height = content.videoInfo?.height,
                        maxHeight = -1,
                        width = content.videoInfo?.width,
                        maxWidth = -1,
                        allowNonMxcUrls = it.root.sendState.isSending()
                )
                val data = VideoContentRenderer.Data(
                        eventId = it.eventId,
                        filename = content.getFileName(),
                        caption = content.getCaption(),
                        mimeType = content.mimeType,
                        url = content.getFileUrl(),
                        elementToDecrypt = content.encryptedFileInfo?.toElementToDecrypt(),
                        thumbnailMediaData = thumbnailData,
                        allowNonMxcUrls = it.root.sendState.isSending()
                )
                AttachmentInfo.Video(
                        uid = it.eventId,
                        url = content.getFileUrl() ?: "",
                        data = data,
                        thumbnail = AttachmentInfo.Image(
                                uid = it.eventId,
                                url = content.videoInfo?.getThumbnailUrl() ?: "",
                                data = thumbnailData

                        )
                )
            } else {
                AttachmentInfo.Image(
                        uid = it.eventId,
                        url = "",
                        data = null
                )
            }
        }
    }

    override fun getTimelineEventAtPosition(position: Int): TimelineEvent? {
        return getItem(position)
    }

    override suspend fun getFileForSharing(position: Int): File? {
        return getItem(position)
                .let { timelineEvent ->
                    timelineEvent.root.getClearContent().toModel<MessageContent>() as? MessageWithAttachmentContent
                }
                ?.let { messageContent ->
                    tryOrNull {
                        fileService.downloadFile(
                                fileName = messageContent.body,
                                mimeType = messageContent.mimeType,
                                url = messageContent.getFileUrl(),
                                elementToDecrypt = messageContent.encryptedFileInfo?.toElementToDecrypt()
                        )
                    }
                }
    }
}
