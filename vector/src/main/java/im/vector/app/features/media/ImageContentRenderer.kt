/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.media

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.target.Target
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.files.LocalFilesHelper
import im.vector.app.core.glide.GlideApp
import im.vector.app.core.glide.GlideRequest
import im.vector.app.core.glide.GlideRequests
import im.vector.app.core.ui.model.Size
import im.vector.app.core.utils.DimensionConverter
import kotlinx.parcelize.Parcelize
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.content.ContentUrlResolver
import org.matrix.android.sdk.api.session.crypto.attachments.ElementToDecrypt
import org.matrix.android.sdk.api.session.media.PreviewUrlData
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min

interface AttachmentData : Parcelable {
    val eventId: String
    val filename: String
    val caption: String?
    val mimeType: String?
    val url: String?
    val elementToDecrypt: ElementToDecrypt?

    // If true will load non mxc url, be careful to set it only for attachments sent by you
    val allowNonMxcUrls: Boolean
}

private const val URL_PREVIEW_IMAGE_MIN_FULL_WIDTH_PX = 600
private const val URL_PREVIEW_IMAGE_MIN_FULL_HEIGHT_PX = 315

class ImageContentRenderer @Inject constructor(
        private val localFilesHelper: LocalFilesHelper,
        private val activeSessionHolder: ActiveSessionHolder,
        private val dimensionConverter: DimensionConverter,
) {

    @Parcelize
    data class Data(
            override val eventId: String,
            override val filename: String,
            override val caption: String?,
            override val mimeType: String?,
            override val url: String?,
            override val elementToDecrypt: ElementToDecrypt?,
            val height: Int?,
            val maxHeight: Int,
            val width: Int?,
            val maxWidth: Int,
            // If true will load non mxc url, be careful to set it only for images sent by you
            override val allowNonMxcUrls: Boolean = false,
            // Fallback for videos: generate preview from video
            val downloadFallbackIfThumbnailMissing: Boolean = false,
            val fallbackUrl: String? = null,
            val fallbackElementToDecrypt: ElementToDecrypt? = null,
    ) : AttachmentData

    enum class Mode {
        FULL_SIZE,
        ANIMATED_THUMBNAIL,
        THUMBNAIL,
        STICKER
    }

    /**
     * For url preview.
     */
    fun render(previewUrlData: PreviewUrlData, imageView: ImageView, hideOnFail: Boolean = false): Boolean {
        val contentUrlResolver = activeSessionHolder.getActiveSession().contentUrlResolver()
        val imageUrl = contentUrlResolver.resolveFullSize(previewUrlData.mxcUrl) ?: return false
        /*
        val maxHeight = dimensionConverter.resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.preview_url_view_image_max_height)
        val height = previewUrlData.imageHeight ?: URL_PREVIEW_IMAGE_MIN_FULL_HEIGHT_PX
        val width = previewUrlData.imageWidth ?: URL_PREVIEW_IMAGE_MIN_FULL_WIDTH_PX
        if (height < URL_PREVIEW_IMAGE_MIN_FULL_HEIGHT_PX || width < URL_PREVIEW_IMAGE_MIN_FULL_WIDTH_PX) {
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        } else {
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        */
        GlideApp.with(imageView)
                .load(imageUrl)
                .fitCenter()
                .listener(object: RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        Timber.e("Rendering url $imageUrl failed: $e")
                        if (hideOnFail) {
                            imageView.isGone = true
                        }
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        if (hideOnFail) {
                            imageView.isVisible = true
                        }
                        return false
                    }
                })
                //.override(width, height.coerceAtMost(maxHeight))
                .into(imageView)
        return true
    }

    /**
     * For gallery.
     */
    fun render(data: Data, imageView: ImageView, size: Int) {
        // a11y
        imageView.contentDescription = data.filename

        createGlideRequest(data, Mode.THUMBNAIL, imageView, Size(size, size))
                .placeholder(R.drawable.ic_image)
                .into(imageView)
    }

    fun render(data: Data, mode: Mode, imageView: ImageView, cornerRoundnessDp: Int = 3, cornerTransformation: Transformation<Bitmap> = RoundedCorners(dimensionConverter.dpToPx(cornerRoundnessDp)), onImageSizeListener: OnImageSizeListener? = null, animate: Boolean = false) {
        val size = processSize(data, mode)
        imageView.updateLayoutParams {
            width = size.width
            height = size.height
        }
        onImageSizeListener?.onImageSizeUpdated(size.width, size.height)
        // a11y
        imageView.contentDescription = data.filename

        var request = createGlideRequest(data, mode, imageView, size)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        Timber.e(e, "Glide image render failed")
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        //if ((data.width == null || data.height == null || data.width == 0 || data.height == 0)) {
                            val updatedData = data.copy(width = resource.intrinsicWidth, height = resource.intrinsicHeight)
                            val newSize = processSize(updatedData, mode)
                            imageView.updateLayoutParams {
                                width = newSize.width
                                height = newSize.height
                            }
                            onImageSizeListener?.onImageSizeUpdated(newSize.width, newSize.height)
                        //}
                        return false
                    }
                })
        request = if (animate && mode == Mode.ANIMATED_THUMBNAIL) {
            // Glide seems to already do some dp to px calculation for animated gifs?
            request.optionalTransform(RoundedCorners(cornerRoundnessDp))
        } else {
            request.dontAnimate()
                    .optionalTransform(cornerTransformation)
        }
        request
                .into(imageView)
    }

    fun clear(imageView: ImageView) {
        // It can be called after recycler view is destroyed, just silently catch
        // We'd better keep ref to requestManager, but we don't have it
        tryOrNull {
            GlideApp
                    .with(imageView).clear(imageView)
        }
    }

    /**
     * Used by Attachment Viewer.
     */
    fun render(data: Data, contextView: View, target: CustomViewTarget<*, Drawable>) {
        val req = if (data.elementToDecrypt != null) {
            // Encrypted image
            GlideApp
                    .with(contextView)
                    .load(data)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
        } else {
            // Clear image
            val resolvedUrl = resolveUrl(data)
            GlideApp
                    .with(contextView)
                    .load(resolvedUrl)
        }

        req
                .optionalFitCenter()
                .into(target)
    }

    fun renderForSharedElementTransition(data: Data, imageView: ImageView, callback: ((Boolean) -> Unit)? = null) {
        // a11y
        imageView.contentDescription = data.filename

        val req = if (data.elementToDecrypt != null) {
            // Encrypted image
            GlideApp
                    .with(imageView)
                    .load(data)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
        } else {
            // Clear image
            val resolvedUrl = resolveUrl(data)
            GlideApp
                    .with(imageView)
                    .load(resolvedUrl)
        }

        req.listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
            ): Boolean {
                callback?.invoke(false)
                return false
            }

            override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
            ): Boolean {
                callback?.invoke(true)
                return false
            }
        })
                .optionalFitCenter()
                .into(imageView)
    }

    private fun createGlideRequest(data: Data, mode: Mode, imageView: ImageView, size: Size): GlideRequest<Drawable> {
        return createGlideRequest(data, mode, GlideApp.with(imageView), size)
    }

    fun createGlideRequest(data: Data, mode: Mode, glideRequests: GlideRequests, size: Size = processSize(data, mode)): GlideRequest<Drawable> {
        return if (data.elementToDecrypt != null || (data.url == null && data.fallbackUrl != null)) {
            // Encrypted image, or video without thumbnail url
            glideRequests
                    .load(data)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
        } else {
            // Clear image
            val contentUrlResolver = activeSessionHolder.getActiveSession().contentUrlResolver()
            val resolvedUrl = when (mode) {
                Mode.FULL_SIZE,
                Mode.ANIMATED_THUMBNAIL,
                Mode.STICKER -> resolveUrl(data)
                Mode.THUMBNAIL -> contentUrlResolver.resolveThumbnail(data.url, size.width, size.height, ContentUrlResolver.ThumbnailMethod.SCALE)
            }
            // Fallback to base url
                    ?: data.url.takeIf { it?.startsWith("content://") == true }

            glideRequests
                    .load(resolvedUrl)
                    .apply {
                        if (mode == Mode.THUMBNAIL) {
                            error(
                                    glideRequests.load(resolveUrl(data))
                            )
                        }
                    }
        }
    }

    private fun resolveUrl(data: Data) =
            (activeSessionHolder.getActiveSession().contentUrlResolver().resolveFullSize(data.url)
                    ?: data.url?.takeIf { localFilesHelper.isLocalFile(data.url) && data.allowNonMxcUrls })

    private fun processSize(data: Data, mode: Mode): Size {
        var maxImageWidth = data.maxWidth
        var maxImageHeight = data.maxHeight
        val width = data.width ?: maxImageWidth
        val height = data.height ?: maxImageHeight

        // Avoid excessive upscaling
        val maxUpscale = dimensionConverter.dpToPx(4)
        if (width in 1 until maxImageWidth) {
            maxImageWidth = min(maxImageWidth, width*maxUpscale)
        }
        if (height in 1 until maxImageHeight) {
            maxImageHeight = min(maxImageHeight, height*maxUpscale)
        }

        var finalWidth = -1
        var finalHeight = -1

        // if the image size is known
        // compute the expected height
        if (width > 0 && height > 0) {
            when (mode) {
                Mode.FULL_SIZE -> {
                    finalHeight = height
                    finalWidth = width
                }
                Mode.ANIMATED_THUMBNAIL,
                Mode.THUMBNAIL -> {
                    finalHeight = min(maxImageWidth * height / width, maxImageHeight)
                    finalWidth = finalHeight * width / height
                }
                Mode.STICKER -> {
                    // limit on width
                    finalWidth = min(dimensionConverter.dpToPx(width), maxImageWidth * 3 / 4)
                    finalHeight = finalWidth * height / width
                }
            }
        }
        // ensure that some values are properly initialized
        if (finalHeight < 0) {
            finalHeight = maxImageHeight
        }
        if (finalWidth < 0) {
            finalWidth = maxImageWidth
        }
        return Size(finalWidth, finalHeight)
    }

    interface OnImageSizeListener {
        fun onImageSizeUpdated(width: Int, height: Int)
    }
}
