/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.autocomplete.emoji

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.extensions.setTextOrHide
import im.vector.app.core.glide.GlideApp
import im.vector.app.features.reactions.data.EmojiItem
import im.vector.app.features.themes.ThemeUtils
import org.matrix.android.sdk.api.extensions.orFalse

@EpoxyModelClass
abstract class AutocompleteEmojiItem : VectorEpoxyModel<AutocompleteEmojiItem.Holder>(R.layout.item_autocomplete_emoji) {

    @EpoxyAttribute
    lateinit var emojiItem: EmojiItem

    @EpoxyAttribute
    var emoteUrl: String? = null

    @EpoxyAttribute
    var emojiTypeFace: Typeface? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var onClickListener: ClickListener? = null

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.titleView.isVisible = false
        holder.emojiNameText.isVisible = true
        if (emoteUrl?.isNotEmpty().orFalse()) {
            holder.emojiText.isVisible = false
            holder.emoteImage.isVisible = true
            holder.emoteImage.imageTintList = null
            GlideApp.with(holder.emoteImage)
                    .load(emoteUrl)
                    .centerCrop()
                    .into(holder.emoteImage)
        } else {
            holder.emojiText.text = emojiItem.emoji
            holder.emojiText.isVisible = true
            holder.emoteImage.isVisible = false
        }
        holder.emojiText.typeface = emojiTypeFace ?: Typeface.DEFAULT
        holder.emojiNameText.text = emojiItem.name
        holder.emojiKeywordText.setTextOrHide(emojiItem.keywords.joinToString())
        holder.view.onClick(onClickListener)
    }

    class Holder : VectorEpoxyHolder() {
        val emojiText by bind<TextView>(R.id.itemAutocompleteEmoji)
        val emoteImage by bind<ImageView>(R.id.itemAutocompleteEmote)
        val emojiNameText by bind<TextView>(R.id.itemAutocompleteEmojiName)
        val emojiKeywordText by bind<TextView>(R.id.itemAutocompleteEmojiSubname)
        val titleView by bind<TextView>(R.id.headerItemAutocompleteTitle)
    }
}
