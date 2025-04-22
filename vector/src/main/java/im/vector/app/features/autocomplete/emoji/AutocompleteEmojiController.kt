/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.autocomplete.emoji

import android.graphics.Typeface
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.TypedEpoxyController
import im.vector.app.EmojiCompatFontProvider
import im.vector.app.features.autocomplete.AutocompleteClickListener
import im.vector.app.features.autocomplete.member.AutocompleteEmojiDataItem
import im.vector.app.features.home.avatar.AvatarHelper
import im.vector.app.features.reactions.data.EmojiItem
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.content.ContentUrlResolver
import javax.inject.Inject

class AutocompleteEmojiController @Inject constructor(
        private val fontProvider: EmojiCompatFontProvider,
        private val session: Session
) : TypedEpoxyController<List<AutocompleteEmojiDataItem>>() {

    var emojiTypeface: Typeface? = fontProvider.typeface

    private val fontProviderListener = object : EmojiCompatFontProvider.FontProviderListener {
        override fun compatibilityFontUpdate(typeface: Typeface?) {
            emojiTypeface = typeface
        }
    }

    var listener: AutocompleteClickListener<EmojiItem>? = null

    override fun buildModels(data: List<AutocompleteEmojiDataItem>?) {
        if (data.isNullOrEmpty()) {
            return
        }
        val max = listener?.maxShowSizeOverride() ?: MAX
        data
                .take(max)
                .forEach { item ->
                    when (item) {
                        is AutocompleteEmojiDataItem.Header -> buildHeaderItem(item)
                        is AutocompleteEmojiDataItem.Emoji  -> buildEmojiItem(item.emojiItem)
                        is AutocompleteEmojiDataItem.Expand -> buildExpandItem(item)
                    }
                }

        if (data.size > max) {
            autocompleteMoreResultItem {
                id("more_result")
            }
        }
    }

    private fun buildHeaderItem(header: AutocompleteEmojiDataItem.Header) {
        autocompleteEmojiHeaderItem {
            id("h/${header.id}")
            title(header.title)
        }
    }

    private fun buildEmojiItem(emojiItem: EmojiItem) {
        val host = this
        autocompleteEmojiItem {
            id("e/${emojiItem.name}/${emojiItem.mxcUrl}")
            emojiItem(emojiItem)
            // For caching reasons, we use the AvatarRenderer's thumbnail size here
            emoteUrl(
                    host.session.contentUrlResolver().resolveThumbnail(
                            emojiItem.mxcUrl,
                            AvatarHelper.THUMBNAIL_SIZE, AvatarHelper.THUMBNAIL_SIZE, ContentUrlResolver.ThumbnailMethod.SCALE
                    )
            )
            emojiTypeFace(host.emojiTypeface)
            onClickListener { host.listener?.onItemClick(emojiItem) }
        }
    }

    private fun buildExpandItem(item: AutocompleteEmojiDataItem.Expand) {
        val host = this
        autocompleteExpandItem {
            id("x/${item.loadMoreKey}/${item.loadMoreKeySecondary}")
            count(item.count)
            onClickListener { host.listener?.onLoadMoreClick(item) }
        }
    }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        fontProvider.addListener(fontProviderListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        fontProvider.removeListener(fontProviderListener)
    }

    companion object {
        // Count of standard emoji matches
        const val STANDARD_EMOJI_MAX = 7
        // Count of emojis for the current room's image pack
        const val CUSTOM_THIS_ROOM_MAX = 8
        // Count of emojis per other image pack
        const val CUSTOM_OTHER_ROOM_MAX = 5
        // Count of emojis for global account data
        const val CUSTOM_ACCOUNT_MAX = 5
        // Count of other image packs
        const val MAX_CUSTOM_OTHER_ROOMS = 15
        // Total max
        const val MAX = 50
        // Total max after expanding a section
        const val MAX_EXPAND = 10000
        // Internal IDs
        const val ACCOUNT_DATA_EMOTE_ID = "de.spiritcroc.riotx.ACCOUNT_DATA_EMOTES"
        const val STANDARD_EMOJI_ID = "de.spiritcroc.riotx.STANDARD_EMOJI"
    }
}
