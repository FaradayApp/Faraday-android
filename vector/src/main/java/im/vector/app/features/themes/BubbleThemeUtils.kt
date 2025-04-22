package im.vector.app.features.themes

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Parcelable
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.preference.PreferenceManager
import im.vector.app.R
import im.vector.app.features.home.room.detail.timeline.item.AnonymousReadReceipt
import kotlinx.parcelize.Parcelize
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import timber.log.Timber
import javax.inject.Inject

/**
 * Util class for managing themes.
 */
class BubbleThemeUtils @Inject constructor(private val context: Context) {
    companion object {
        const val BUBBLE_STYLE_KEY = "BUBBLE_STYLE_KEY"
        const val BUBBLE_ROUNDNESS_KEY = "SETTINGS_SC_BUBBLE_ROUNDED_CORNERS"
        const val BUBBLE_ROUNDNESS_DEFAULT = "default"
        const val BUBBLE_ROUNDNESS_R1 = "r1"
        const val BUBBLE_ROUNDNESS_R2 = "r2"
        const val BUBBLE_TAIL_KEY = "SETTINGS_SC_BUBBLE_TAIL"

        const val BUBBLE_STYLE_NONE = "none"
        const val BUBBLE_STYLE_ELEMENT = "element"
        const val BUBBLE_STYLE_START = "start"
        const val BUBBLE_STYLE_BOTH = "both"
        const val BUBBLE_TIME_TOP = "top"
        const val BUBBLE_TIME_BOTTOM = "bottom"

        fun getVisibleAnonymousReadReceipts(readReceipt: AnonymousReadReceipt?, sentByMe: Boolean): AnonymousReadReceipt {
            readReceipt ?: return AnonymousReadReceipt.NONE
            // TODO
            return if (sentByMe && (/*TODO setting?*/ true || readReceipt == AnonymousReadReceipt.PROCESSING)) {
                readReceipt
            } else {
                AnonymousReadReceipt.NONE
            }
        }

        fun anonymousReadReceiptForEvent(event: TimelineEvent): AnonymousReadReceipt {
            return if (event.root.sendState == SendState.SYNCED || event.root.sendState == SendState.SENT) {
                /*if (event.readByOther) {
                    AnonymousReadReceipt.READ
                } else {
                    AnonymousReadReceipt.SENT
                }*/
                AnonymousReadReceipt.NONE
            } else {
                AnonymousReadReceipt.PROCESSING
            }
        }
    }

    fun getBubbleStyle(): String {
        val bubbleStyle = PreferenceManager.getDefaultSharedPreferences(context).getString(BUBBLE_STYLE_KEY, BUBBLE_STYLE_BOTH)!!
        if (bubbleStyle !in listOf(BUBBLE_STYLE_NONE, BUBBLE_STYLE_START, BUBBLE_STYLE_BOTH, BUBBLE_STYLE_ELEMENT)) {
            Timber.e("Ignoring invalid bubble style setting: $bubbleStyle")
            // Invalid setting, fallback to default
            return BUBBLE_STYLE_BOTH
        }
        return bubbleStyle
    }

    fun setBubbleStyle(value: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(BUBBLE_STYLE_KEY, value).apply()
    }

    fun getBubbleRoundnessSetting(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(BUBBLE_ROUNDNESS_KEY, BUBBLE_ROUNDNESS_DEFAULT) ?: BUBBLE_ROUNDNESS_DEFAULT
    }

    fun setBubbleRoundnessSetting(value: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(BUBBLE_ROUNDNESS_KEY, value).apply()
    }

    fun getBubbleAppearance(): ScBubbleAppearance {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val baseAppearance = when (getBubbleRoundnessSetting()) {
            BUBBLE_ROUNDNESS_R1 -> r1ScBubbleAppearance
            BUBBLE_ROUNDNESS_R2 -> r2ScBubbleAppearance
            else                -> defaultScBubbleAppearance
        }
        return if (prefs.getBoolean(BUBBLE_TAIL_KEY, true)) {
            baseAppearance
        } else {
            baseAppearance.copy(
                    textBubbleOutgoing = baseAppearance.textBubbleOutgoingNoTail,
                    textBubbleIncoming = baseAppearance.textBubbleIncomingNoTail
            )
        }
    }
}

fun guessTextWidth(view: TextView): Float {
    return guessTextWidth(view, view.text)
}

fun guessTextWidth(view: TextView, text: CharSequence): Float {
    return guessTextWidth(view.textSize, text, view.typeface) + view.paddingLeft + view.paddingRight
}

fun guessTextWidth(textSize: Float, text: CharSequence, typeface: Typeface? = null): Float {
    val paint = Paint()
    paint.textSize = textSize
    typeface?.let { paint.typeface = it }
    return paint.measureText(text, 0, text.length)
}

@Parcelize
data class ScBubbleAppearance(
        @DimenRes
        val roundness: Int,
        @DrawableRes
        val textBubbleOutgoing: Int,
        @DrawableRes
        val textBubbleIncoming: Int,
        @DrawableRes
        val textBubbleOutgoingNoTail: Int,
        @DrawableRes
        val textBubbleIncomingNoTail: Int,
        @DrawableRes
        val timestampOverlay: Int,
        @DrawableRes
        val imageBorderOutgoing: Int,
        @DrawableRes
        val imageBorderIncoming: Int,
) : Parcelable {
    fun getBubbleRadiusPx(context: Context): Int {
        return context.resources.getDimensionPixelSize(roundness)
    }
    fun getBubbleRadiusDp(context: Context): Float {
        return (context.resources.getDimension(roundness) / context.resources.displayMetrics.density)
    }
}

val defaultScBubbleAppearance = ScBubbleAppearance(
        im.vector.lib.ui.styles.R.dimen.sc_bubble_radius,
        R.drawable.msg_bubble_text_outgoing,
        R.drawable.msg_bubble_text_incoming,
        R.drawable.msg_bubble_text_outgoing_notail,
        R.drawable.msg_bubble_text_incoming_notail,
        R.drawable.timestamp_overlay,
        R.drawable.background_image_border_outgoing,
        R.drawable.background_image_border_incoming,
)

val r1ScBubbleAppearance = ScBubbleAppearance(
        im.vector.lib.ui.styles.R.dimen.sc_bubble_r1_radius,
        R.drawable.msg_bubble_r1_text_outgoing,
        R.drawable.msg_bubble_r1_text_incoming,
        R.drawable.msg_bubble_r1_text_outgoing_notail,
        R.drawable.msg_bubble_r1_text_incoming_notail,
        R.drawable.timestamp_overlay_r1,
        R.drawable.background_image_border_outgoing_r1,
        R.drawable.background_image_border_incoming_r1,
)


val r2ScBubbleAppearance = ScBubbleAppearance(
        im.vector.lib.ui.styles.R.dimen.sc_bubble_r2_radius,
        R.drawable.msg_bubble_r2_text_outgoing,
        R.drawable.msg_bubble_r2_text_incoming,
        R.drawable.msg_bubble_r2_text_outgoing_notail,
        R.drawable.msg_bubble_r2_text_incoming_notail,
        R.drawable.timestamp_overlay_r2,
        R.drawable.background_image_border_outgoing_r2,
        R.drawable.background_image_border_incoming_r2,
)
