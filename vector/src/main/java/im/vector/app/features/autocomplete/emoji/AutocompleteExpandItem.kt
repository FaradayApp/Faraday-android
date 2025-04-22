/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.autocomplete.emoji

import android.content.res.ColorStateList
import android.graphics.Typeface
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonPlurals
import im.vector.lib.strings.CommonStrings

@EpoxyModelClass // Re-using item_autocomplete_emoji to avoid class-cast exceptions like https://github.com/SchildiChat/SchildiChat-android-rageshakes/issues/1040
abstract class AutocompleteExpandItem : VectorEpoxyModel<AutocompleteEmojiItem.Holder>(R.layout.item_autocomplete_emoji) {

    @EpoxyAttribute
    var count: Int? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var onClickListener: ClickListener? = null

    override fun bind(holder: AutocompleteEmojiItem.Holder) {
        super.bind(holder)
        holder.titleView.isVisible = false
        holder.emojiText.isVisible = false
        holder.emoteImage.isVisible = true
        holder.emojiNameText.isVisible = true
        holder.emoteImage.setImageResource(R.drawable.ic_expand_more)
        holder.emoteImage.imageTintList = ColorStateList.valueOf(ThemeUtils.getColor(holder.emoteImage.context, im.vector.lib.ui.styles.R.attr.vctr_content_secondary))
        holder.emojiText.typeface = Typeface.DEFAULT
        count.let {
            if (it == null) {
                holder.emojiNameText.setText(CommonStrings.room_profile_section_more)
            } else {
                holder.emojiNameText.text = holder.emojiNameText.resources.getQuantityString(CommonPlurals.message_reaction_show_more, it, it)
            }
        }
        holder.emojiKeywordText.isVisible = false
        holder.view.onClick(onClickListener)
    }

}
