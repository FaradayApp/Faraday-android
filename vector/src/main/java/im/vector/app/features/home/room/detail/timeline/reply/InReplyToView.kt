/*
 * Copyright (c) 2020 New Vector Ltd
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

package im.vector.app.features.home.room.detail.timeline.reply

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.method.MovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import im.vector.app.R
import im.vector.app.core.extensions.setTextOrHide
import im.vector.app.core.extensions.tintBackground
import im.vector.app.databinding.ViewInReplyToBinding
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.item.BindingOptions
import im.vector.app.features.home.room.detail.timeline.item.MessageInformationData
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.tools.findPillsAndProcess
import im.vector.app.features.media.ImageContentRenderer
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.session.crypto.attachments.toElementToDecrypt
import org.matrix.android.sdk.api.session.room.model.message.MessageContentWithFormattedBody
import org.matrix.android.sdk.api.session.room.model.message.MessageImageInfoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageVideoContent
import org.matrix.android.sdk.api.session.room.model.message.getCaption
import org.matrix.android.sdk.api.session.room.model.message.getFileName
import org.matrix.android.sdk.api.session.room.model.message.getFileUrl
import org.matrix.android.sdk.api.session.room.model.message.getThumbnailUrl
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.getLastMessageContent
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * A View to render a replied-to event
 */
class InReplyToView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnClickListener {

    private lateinit var views: ViewInReplyToBinding

    var delegate: TimelineEventController.InReplyToClickCallback? = null

    init {
        setupView()
    }

    private var state: PreviewReplyUiState = PreviewReplyUiState.NoReply

    private val maxThumbnailWidth = context.resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.reply_thumbnail_max_width)
    private val maxThumbnailHeight = context.resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.reply_thumbnail_height)

    /**
     * This methods is responsible for rendering the view according to the newState
     *
     * @param newState the newState representing the view
     */
    fun render(newState: PreviewReplyUiState,
               retriever: ReplyPreviewRetriever,
               roomInformationData: MessageInformationData,
               movementMethod: MovementMethod?,
               itemLongClickListener: OnLongClickListener?,
               coroutineScope: CoroutineScope,
               generateMissingVideoThumbnails: Boolean,
               force: Boolean = false
    ) {
        if (newState == state && !force) {
            return
        }

        state = newState

        when (newState) {
            PreviewReplyUiState.NoReply -> renderHidden()
            is PreviewReplyUiState.ReplyLoading -> renderLoading()
            is PreviewReplyUiState.Error -> renderError(newState)
            is PreviewReplyUiState.InReplyTo -> renderReplyTo(
                    newState,
                    retriever,
                    roomInformationData,
                    movementMethod,
                    coroutineScope,
                    generateMissingVideoThumbnails
            )
        }

        setOnLongClickListener(itemLongClickListener)
        // Somehow this one needs it additionally?
        views.replyTextView.setOnLongClickListener(itemLongClickListener)
    }

    override fun onClick(v: View?) {
        state.repliedToEventId?.let { delegate?.onRepliedToEventClicked(it) }
    }


    // PRIVATE METHODS ****************************************************************************************************************************************

    private fun setupView() {
        inflate(context, R.layout.view_in_reply_to, this)
        views = ViewInReplyToBinding.bind(this)

        setOnClickListener(this)
        // Somehow this one needs it additionally?
        views.replyTextView.setOnClickListener(this)
    }

    private fun hideViews() {
        views.replyMemberNameView.isVisible = false
        views.replyTextView.isVisible = false
        views.replyThumbnailView.isVisible = false
        renderFadeOut(null)
    }

    private fun renderHidden() {
        isVisible = false
    }

    private fun renderLoading() {
        hideViews()
        isVisible = true
        views.replyTextView.isVisible = true
        val color = ThemeUtils.getColor(context,im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
        views.replyTextView.text = SpannableString(context.getString(CommonStrings.in_reply_to_loading)).apply {
            setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
            setSpan(ForegroundColorSpan(color), 0, length, 0)
        }
        views.inReplyToBar.setBackgroundColor(color)
    }

    private fun renderError(state: PreviewReplyUiState.Error) {
        hideViews()
        isVisible = true
        Timber.w(state.throwable, "Error rendering reply")
        views.replyTextView.isVisible = true
        val color = ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
        views.replyTextView.text = SpannableString(context.getString(CommonStrings.in_reply_to_error)).apply {
            setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
            setSpan(ForegroundColorSpan(color), 0, length, 0)
        }
        views.inReplyToBar.setBackgroundColor(color)
    }

    private fun renderReplyTo(
            state: PreviewReplyUiState.InReplyTo,
            retriever: ReplyPreviewRetriever,
            roomInformationData: MessageInformationData,
            movementMethod: MovementMethod?,
            coroutineScope: CoroutineScope,
            generateMissingVideoThumbnails: Boolean,
    ) {
        hideViews()
        isVisible = true
        views.replyMemberNameView.isVisible = true
        views.replyMemberNameView.text = state.senderName
        val senderColor = retriever.getMemberNameColor(state.event, roomInformationData)
        views.replyMemberNameView.setTextColor(senderColor)
        views.inReplyToBar.setBackgroundColor(senderColor)
        if (state.event.root.isRedacted()) {
            renderRedacted()
        } else {
            renderFadeOut(roomInformationData)
            when (val content = state.event.getLastMessageContent()) {
                is MessageContentWithFormattedBody -> renderTextContent(content, retriever, movementMethod, coroutineScope)
                is MessageImageInfoContent -> renderImageThumbnailContent(content, state.event, retriever)
                is MessageVideoContent -> renderVideoThumbnailContent(content, state.event, retriever, generateMissingVideoThumbnails)
                else -> renderFallback(state.event, retriever)
            }
        }
    }

    private fun renderRedacted() {
        views.replyTextView.isVisible = true
        views.replyTextView.setText(CommonStrings.event_redacted)
    }

    private fun renderTextContent(
            content: MessageContentWithFormattedBody,
            retriever: ReplyPreviewRetriever,
            movementMethod: MovementMethod?,
            coroutineScope: CoroutineScope
    ) {
        views.replyTextView.isVisible = true

        val formattedBody = content.formattedBody

        val bindingOptions: BindingOptions?
        val text = if (formattedBody != null) {
            val compressed = retriever.htmlCompressor.compress(formattedBody)
            val renderedFormattedBody = retriever.htmlRenderer.render(compressed, retriever.pillsPostProcessor)
            val renderedBody = retriever.textRenderer.render(renderedFormattedBody)
            bindingOptions = retriever.spanUtils.getBindingOptions(renderedBody)
            // To be re-enabled if we want clickable urls in reply previews, which would conflict with going to the original event on clicking
            //val linkifiedBody = renderedBody.linkify(callback)
            //linkifiedBody
            renderedBody
        } else {
            bindingOptions = null
            content.body
        }
        val markwonPlugins = retriever.htmlRenderer.plugins

        if (formattedBody != null) {
            text.findPillsAndProcess(coroutineScope) { pillImageSpan ->
                pillImageSpan.bind(views.replyTextView)
            }
        }
        text.let { charSequence ->
            if (charSequence is Spanned) {
                markwonPlugins.forEach { plugin -> plugin.beforeSetText(views.replyTextView, charSequence) }
            }
        }

        views.replyTextView.movementMethod = movementMethod
        views.replyTextView.setTextWithEmojiSupport(text, bindingOptions)
        markwonPlugins.forEach { plugin -> plugin.afterSetText(views.replyTextView) }
    }

    private fun renderImageThumbnailContent(
            content: MessageImageInfoContent,
            event: TimelineEvent,
            retriever: ReplyPreviewRetriever,
    ) {
        val data = ImageContentRenderer.Data(
                eventId = event.eventId,
                filename = content.getFileName(),
                caption = content.getCaption(),
                mimeType = content.mimeType,
                url = content.getFileUrl(),
                elementToDecrypt = content.encryptedFileInfo?.toElementToDecrypt(),
                height = content.info?.height,
                maxHeight = maxThumbnailHeight,
                width = content.info?.width,
                maxWidth = maxThumbnailWidth,
                allowNonMxcUrls = false
        )

        renderThumbnailContent(data, retriever)
    }

    private fun renderVideoThumbnailContent(
            content: MessageVideoContent,
            event: TimelineEvent,
            retriever: ReplyPreviewRetriever,
            generateMissingVideoThumbnails: Boolean,
    ) {
        val thumbnailData = ImageContentRenderer.Data(
                eventId = event.eventId,
                filename = content.getFileName(),
                caption = content.getCaption(),
                mimeType = content.mimeType,
                url = content.videoInfo?.getThumbnailUrl(),
                elementToDecrypt = content.videoInfo?.thumbnailFile?.toElementToDecrypt(),
                height = content.videoInfo?.height,
                maxHeight = maxThumbnailHeight,
                width = content.videoInfo?.width,
                maxWidth = maxThumbnailWidth,
                allowNonMxcUrls = false,
                // Video fallback for generating thumbnails
                downloadFallbackIfThumbnailMissing = generateMissingVideoThumbnails,
                fallbackUrl = content.getFileUrl(),
                fallbackElementToDecrypt = content.encryptedFileInfo?.toElementToDecrypt()
        )
        renderThumbnailContent(thumbnailData, retriever)
    }

    private fun renderThumbnailContent(
            mediaData: ImageContentRenderer.Data,
            retriever: ReplyPreviewRetriever,
    ) {
        views.replyThumbnailView.isVisible = true
        retriever.imageContentRenderer.render(
                mediaData,
                ImageContentRenderer.Mode.THUMBNAIL,
                views.replyThumbnailView,
                animate = false
        )
        views.replyTextView.setTextOrHide(mediaData.caption)
    }

    private fun renderFallback(event: TimelineEvent, retriever: ReplyPreviewRetriever) {
        views.replyTextView.isVisible = true
        views.replyTextView.text = retriever.formatFallbackReply(event)
    }

    private fun AppCompatTextView.setTextWithEmojiSupport(message: CharSequence?, bindingOptions: BindingOptions?) {
        if (bindingOptions?.canUseTextFuture.orFalse() && message != null) {
            val textFuture = PrecomputedTextCompat.getTextFuture(message, TextViewCompat.getTextMetricsParams(this), null)
            setTextFuture(textFuture)
        } else {
            setTextFuture(null)
            text = message
        }
    }

    /**
     * @param informationData The information data of the parent message, for background fade rendering info. Null to force expand to full height.
     */
    private fun renderFadeOut(informationData: MessageInformationData?) {
        if (informationData != null) {
            views.expandableReplyView.setExpanded(false)
            val chatBgColor = ThemeUtils.getColor(context, android.R.attr.colorBackground)
            val bgColor = when (val layout = informationData.messageLayout) {
                is TimelineMessageLayout.ScBubble -> {
                    if (informationData.sentByMe && !layout.singleSidedLayout) {
                        ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.sc_message_bg_outgoing)
                    } else {
                        ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.sc_message_bg_incoming)
                    }
                }
                is TimelineMessageLayout.Bubble -> {
                    if (layout.isPseudoBubble) {
                        0
                    } else {
                        val backgroundColorAttr = if (informationData.sentByMe) im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound else im.vector.lib.ui.styles.R.attr.vctr_message_bubble_inbound
                        ThemeUtils.getColor(context, backgroundColorAttr)
                    }
                }
                is TimelineMessageLayout.Default -> {
                    ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_system)
                }
            }
            val fadeView = views.expandableReplyView.getChildAt(1)
            // In case of transparent bubbles, we need to cheat a bit with the fade effect
            fadeView.tintBackground(calculateEffectiveColor(bgColor, chatBgColor))
        } else {
            views.expandableReplyView.setExpanded(true)
        }
    }

    /**
     * In case of transparent bubbles, we need to calculate the effective color before applying the fade effect...
     */
    private fun calculateEffectiveColor(fg: Int, bg: Int): Int {
        val fgAlpha = Color.alpha(fg)
        if (fgAlpha == 0xff) {
            return fg
        }
        val opacity = fgAlpha / (0xff).toFloat()
        val r = (Color.red(bg) * (1 - opacity) + Color.red(fg) * opacity).roundToInt()
        val g = (Color.green(bg) * (1 - opacity) + Color.green(fg) * opacity).roundToInt()
        val b = (Color.blue(bg) * (1 - opacity) + Color.blue(fg) * opacity).roundToInt()
        return Color.rgb(r, g, b)
    }
}
