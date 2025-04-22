/*
 * Copyright 2019 New Vector Ltd
 * Copyright 2023 SpiritCroc
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

import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.VectorEpoxyModel

// We were getting ClassCastExceptions for the holder of AutocompleteHeaderItem, which sometimes seemed to still be an AutocompleteEmojiItem...
// https://github.com/SchildiChat/SchildiChat-android-rageshakes/issues/1040
// So let's build some HeaderItem using the same holder
@EpoxyModelClass
abstract class AutocompleteEmojiHeaderItem : VectorEpoxyModel<AutocompleteEmojiItem.Holder>(R.layout.item_autocomplete_emoji) {

    @EpoxyAttribute var title: String? = null

    override fun bind(holder: AutocompleteEmojiItem.Holder) {
        super.bind(holder)

        holder.titleView.isVisible = true
        holder.emojiText.isVisible = false
        holder.emoteImage.isVisible = false
        holder.emojiKeywordText.isVisible = false
        holder.emojiNameText.isVisible = false
        holder.titleView.text = title
    }
}
