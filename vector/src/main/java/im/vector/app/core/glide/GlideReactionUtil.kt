package im.vector.app.core.glide

import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.matrix.android.sdk.api.MatrixUrls.isMxcUrl
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.content.ContentUrlResolver
import timber.log.Timber

@Suppress("UNUSED_PARAMETER")
fun renderReactionImage(reactionUrl: String?,
                        reactionKey: String?,
                        size: Int,
                        session: Session,
                        textView: TextView,
                        imageView: ImageView) {
    val effectiveReactionUrl = when {
        !reactionUrl.isNullOrEmpty()      -> reactionUrl
        reactionKey?.isMxcUrl().orFalse() -> reactionKey
        else                              -> null
    }
    if (effectiveReactionUrl.isNullOrEmpty()) {
        textView.isVisible = true
        imageView.isVisible = false
    } else {
        // Not all thumbnail providers allow GIFs!
        //val url = session.contentUrlResolver().resolveThumbnail(effectiveReactionUrl, size, size, ContentUrlResolver.ThumbnailMethod.SCALE)
        val url = session.contentUrlResolver().resolveFullSize(effectiveReactionUrl)
        if (url == null) {
            textView.isVisible = true
            imageView.isVisible = false
        } else {
            textView.isVisible = false
            imageView.isVisible = true
            GlideApp.with(imageView)
                    .load(url)
                    .centerCrop()
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                            Timber.w("Reaction image load failed for $effectiveReactionUrl: $e")
                            textView.isVisible = true
                            imageView.isVisible = false
                            return false
                        }
                        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            return false
                        }
                    })
                    .into(imageView)
        }
    }
}
