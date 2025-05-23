/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.media

import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.view.isVisible
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.error.ErrorFormatter
import im.vector.app.core.files.LocalFilesHelper
import im.vector.app.features.session.coroutineScope
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.matrix.android.sdk.api.session.crypto.attachments.ElementToDecrypt
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject

class VideoContentRenderer @Inject constructor(
        private val localFilesHelper: LocalFilesHelper,
        private val activeSessionHolder: ActiveSessionHolder,
        private val errorFormatter: ErrorFormatter
) {

    private val sessionScope: CoroutineScope
        get() = activeSessionHolder.getActiveSession().coroutineScope

    @Parcelize
    data class Data(
            override val eventId: String,
            override val filename: String,
            override val caption: String?,
            override val mimeType: String?,
            override val url: String?,
            override val elementToDecrypt: ElementToDecrypt?,
            val thumbnailMediaData: ImageContentRenderer.Data,
            // If true will load non mxc url, be careful to set it only for video sent by you
            override val allowNonMxcUrls: Boolean = false
    ) : AttachmentData

    fun render(
            data: Data,
            thumbnailView: ImageView,
            loadingView: ProgressBar,
            videoView: VideoView,
            errorView: TextView
    ) {
        val contentUrlResolver = activeSessionHolder.getActiveSession().contentUrlResolver()

        if (data.elementToDecrypt != null) {
            Timber.v("Decrypt video")
            videoView.isVisible = false

            if (data.url == null) {
                loadingView.isVisible = false
                errorView.isVisible = true
                errorView.setText(CommonStrings.unknown_error)
            } else if (localFilesHelper.isLocalFile(data.url) && data.allowNonMxcUrls) {
                thumbnailView.isVisible = false
                loadingView.isVisible = false
                videoView.isVisible = true
                videoView.setVideoPath(URLEncoder.encode(data.url, Charsets.US_ASCII.displayName()))
                videoView.start()
            } else {
                thumbnailView.isVisible = true
                loadingView.isVisible = true

                sessionScope.launch {
                    val result = runCatching {
                        activeSessionHolder.getActiveSession().fileService()
                                .downloadFile(
                                        fileName = data.filename,
                                        mimeType = data.mimeType,
                                        url = data.url,
                                        elementToDecrypt = data.elementToDecrypt
                                )
                    }
                    withContext(Dispatchers.Main) {
                        result.fold(
                                { data ->
                                    thumbnailView.isVisible = false
                                    loadingView.isVisible = false
                                    videoView.isVisible = true

                                    videoView.setVideoPath(data.path)
                                    videoView.start()
                                },
                                {
                                    loadingView.isVisible = false
                                    errorView.isVisible = true
                                    errorView.text = errorFormatter.toHumanReadable(it)
                                }
                        )
                    }
                }
            }
        } else {
            val resolvedUrl = contentUrlResolver.resolveFullSize(data.url)
                    ?: data.url?.takeIf { localFilesHelper.isLocalFile(data.url) && data.allowNonMxcUrls }

            if (resolvedUrl == null) {
                thumbnailView.isVisible = false
                loadingView.isVisible = false
                errorView.isVisible = true
                errorView.setText(CommonStrings.unknown_error)
            } else {
                // Temporary code, some remote videos are not played by videoview setVideoUri
                // So for now we download them then play
                thumbnailView.isVisible = true
                loadingView.isVisible = true

                sessionScope.launch {
                    val result = runCatching {
                        activeSessionHolder.getActiveSession().fileService()
                                .downloadFile(
                                        fileName = data.filename,
                                        mimeType = data.mimeType,
                                        url = data.url,
                                        elementToDecrypt = null
                                )
                    }
                    withContext(Dispatchers.Main) {
                        result.fold(
                                { data ->
                                    thumbnailView.isVisible = false
                                    loadingView.isVisible = false
                                    videoView.isVisible = true

                                    videoView.setVideoPath(data.path)
                                    videoView.start()
                                },
                                {
                                    loadingView.isVisible = false
                                    errorView.isVisible = true
                                    errorView.text = errorFormatter.toHumanReadable(it)
                                }
                        )
                    }
                }
            }
        }
    }
}
