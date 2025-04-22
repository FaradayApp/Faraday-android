/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.autocomplete.emoji

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.R
import im.vector.app.features.autocomplete.AutocompleteClickListener
import im.vector.app.features.autocomplete.RecyclerViewPresenter
import im.vector.app.features.autocomplete.member.AutocompleteEmojiDataItem
import im.vector.app.features.reactions.data.EmojiDataSource
import im.vector.app.features.reactions.data.EmojiItem
import im.vector.app.features.settings.VectorPreferences
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.query.QueryStateEventValue
import org.matrix.android.sdk.api.query.QueryStringValue
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.accountdata.UserAccountDataTypes
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.getStateEvent
import org.matrix.android.sdk.api.session.room.model.RoomEmoteContent
import kotlin.math.min

class AutocompleteEmojiPresenter @AssistedInject constructor(
        context: Context,
        @Assisted val roomId: String,
        private val session: Session,
        private val vectorPreferences: VectorPreferences,
        private val emojiDataSource: EmojiDataSource,
        private val controller: AutocompleteEmojiController
) :
        RecyclerViewPresenter<EmojiItem>(context), AutocompleteClickListener<EmojiItem> {

    private val room by lazy { session.getRoom(roomId)!! }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val expandedSections = HashSet<Pair<String, String?>>()
    private var lastQuery: CharSequence? = null

    init {
        controller.listener = this
    }

    fun clear() {
        coroutineScope.coroutineContext.cancelChildren()
        controller.listener = null
        expandedSections.clear()
    }

    @AssistedFactory
    interface Factory {
        fun create(roomId: String): AutocompleteEmojiPresenter
    }

    override fun instantiateAdapter(): RecyclerView.Adapter<*> {
        return controller.adapter
    }

    override fun onItemClick(t: EmojiItem) {
        dispatchClick(t)
    }

    override fun onLoadMoreClick(item: AutocompleteEmojiDataItem.Expand) {
        expandedSections.add(Pair(item.loadMoreKey, item.loadMoreKeySecondary))
        //Timber.d("Load more emojis for ${item.loadMoreKey}/${item.loadMoreKeySecondary} ${expandedSections.contains(Pair(item.loadMoreKey, item.loadMoreKeySecondary))}")
        onQuery(lastQuery)
    }

    override fun maxShowSizeOverride(): Int? {
        if (expandedSections.isNotEmpty()) {
            return AutocompleteEmojiController.MAX_EXPAND
        }
        return null
    }

    override fun onQuery(query: CharSequence?) {
        if (query?.isNotEmpty() != true && lastQuery?.isEmpty() != true) {
            expandedSections.clear()
        }
        lastQuery = query
        coroutineScope.launch {
            // Plain emojis
            val fullStandardEmojis: List<EmojiItem>
            val data = if (query.isNullOrBlank()) {
                // Return common emojis
                fullStandardEmojis = emojiDataSource.getQuickReactions()
                fullStandardEmojis
            } else {
                fullStandardEmojis = emojiDataSource.filterWith(query.toString())
                fullStandardEmojis.maybeLimit(AutocompleteEmojiController.STANDARD_EMOJI_MAX, AutocompleteEmojiController.STANDARD_EMOJI_ID, null)
            }.toAutocompleteItems().toMutableList()

            if (fullStandardEmojis.size > data.size) {
                 data += listOf(AutocompleteEmojiDataItem.Expand(AutocompleteEmojiController.STANDARD_EMOJI_ID, null, fullStandardEmojis.size - data.size))
            }

            // Custom emotes: This room's emotes
            val currentRoomEmotes = room.getAllEmojiItems(query)
            val allEmoteData = currentRoomEmotes.toAutocompleteItems().let {
                if (it.isNotEmpty()) {
                    listOf(AutocompleteEmojiDataItem.Header(roomId, context.getString(CommonStrings.custom_emotes_this_room))) + it
                } else {
                    emptyList()
                }
            }
            val emoteData = allEmoteData.maybeLimit(AutocompleteEmojiController.CUSTOM_THIS_ROOM_MAX, roomId, null).toMutableList()
            val emoteUrls = HashSet<String>()
            emoteUrls.addAll(currentRoomEmotes.map { it.mxcUrl })
            if (allEmoteData.size > emoteData.size) {
                emoteData += listOf(AutocompleteEmojiDataItem.Expand(roomId, null, allEmoteData.size - emoteData.size))
            }
            // Global emotes (only while searching)
            if (!query.isNullOrBlank()) {
                // Account emotes
                val allUserPack = session.accountDataService().getUserAccountDataEvent(UserAccountDataTypes.TYPE_USER_EMOTES)?.content
                        ?.toModel<RoomEmoteContent>().getEmojiItems(query)
                val userPack = allUserPack.maybeLimit(AutocompleteEmojiController.CUSTOM_ACCOUNT_MAX, AutocompleteEmojiController.ACCOUNT_DATA_EMOTE_ID, null)
                if (userPack.isNotEmpty()) {
                    emoteUrls.addAll(userPack.map { it.mxcUrl })
                    emoteData += listOf(
                            AutocompleteEmojiDataItem.Header(
                                    AutocompleteEmojiController.ACCOUNT_DATA_EMOTE_ID,
                                    context.getString(CommonStrings.custom_emotes_account_data)
                            )
                    )
                    emoteData += userPack.toAutocompleteItems()
                    if (allUserPack.size > userPack.size) {
                        emoteData += listOf(AutocompleteEmojiDataItem.Expand(AutocompleteEmojiController.ACCOUNT_DATA_EMOTE_ID, null, allUserPack.size - userPack.size))
                    }
                }
                // Global emotes from rooms
                val globalPacks = session.accountDataService().getUserAccountDataEvent(UserAccountDataTypes.TYPE_EMOTE_ROOMS)
                var packsAdded = 0
                (globalPacks?.content?.get("rooms") as? Map<*, *>)?.forEach { pack ->
                    // If entry is empty, it has been disabled as global pack (after being enabled before).
                    val packsEnabled = pack.value as? Map<*, *>
                    if (packsEnabled.isNullOrEmpty()) {
                        return@forEach
                    }
                    if (packsAdded >= AutocompleteEmojiController.MAX_CUSTOM_OTHER_ROOMS) {
                        return@forEach
                    }
                    val packRoomId = pack.key
                    if (packRoomId is String && packRoomId != roomId) {
                        val packRoom = session.getRoom(packRoomId) ?: return@forEach
                        packsEnabled.forEach roomPack@{ roomPack ->
                            val packId = roomPack.key as? String ?: return@roomPack
                            val emojiItems = packRoom.getEmojiItems(query, QueryStringValue.Equals(packId))
                            val packName = emojiItems.first
                            // Filter out duplicate emotes with the exact same mxc url
                            val allPackImages = emojiItems.second.filter {
                                it.mxcUrl !in emoteUrls
                            }
                            val packImages = allPackImages.maybeLimit(AutocompleteEmojiController.CUSTOM_OTHER_ROOM_MAX, packRoomId, packId)
                            // Add header + emotes
                            if (packImages.isNotEmpty()) {
                                packsAdded++
                                emoteUrls.addAll(packImages.map { it.mxcUrl })
                                val packRoomName = packRoom.roomSummary()?.displayName ?: packRoomId
                                roomPack.value
                                emoteData += listOf(
                                        AutocompleteEmojiDataItem.Header(
                                                packRoomId,
                                                if (packName != null) {
                                                    context.getString(
                                                            CommonStrings.custom_emotes_named_other_room,
                                                            packName,
                                                            packRoomName
                                                    )
                                                } else {
                                                    context.getString(
                                                            CommonStrings.custom_emotes_other_room,
                                                            packRoomName
                                                    )
                                                }
                                        )
                                )
                                emoteData += packImages.toAutocompleteItems()
                                if (allPackImages.size > packImages.size) {
                                    emoteData += listOf(AutocompleteEmojiDataItem.Expand(packRoomId, packId, allPackImages.size - packImages.size))
                                }
                            }
                        }
                    }
                }
            }

            controller.setData(data + emoteData)
        }
    }

    /**
     * Don't limit if only one more would be required, such that showing a "load more" button would be a waste
     */
    private fun <T>List<T>.maybeLimit(limit: Int, loadMoreKey: String, loadMoreKeySecondary: String?): List<T> {
        return if (size > limit + 1 && !expandedSections.contains(Pair(loadMoreKey, loadMoreKeySecondary))) {
            //Timber.d("maybeLimit $loadMoreKey/$loadMoreKeySecondary true ${expandedSections.contains(Pair(loadMoreKey, loadMoreKeySecondary))}")
            limit(limit)
        } else {
            //Timber.d("maybeLimit $loadMoreKey/$loadMoreKeySecondary false")
            this
        }
    }

    private fun List<EmojiItem>.toAutocompleteItems(): List<AutocompleteEmojiDataItem> {
        return map { AutocompleteEmojiDataItem.Emoji(it) }
    }

    private fun Room.getEmojiItems(query: CharSequence?, queryStringValue: QueryStateEventValue): Pair<String?, List<EmojiItem>> {
        val content = getStateEvent(EventType.ROOM_EMOTES, queryStringValue)?.content?.toModel<RoomEmoteContent>()
        val packName = content?.pack?.displayName
        return Pair(packName, content.getEmojiItems(query))
    }

    private fun Room.getAllEmojiItems(query: CharSequence?): List<EmojiItem> {
        // NoCondition isn't allowed by matrix sdk for getStateEvents, so query both empty and non-empty
        val eventTypeList = setOf(EventType.ROOM_EMOTES)
        val emptyItems = stateService().getStateEvents(eventTypeList, QueryStringValue.IsEmpty)
        val keyedItems = stateService().getStateEvents(eventTypeList, QueryStringValue.IsNotEmpty)
        return (emptyItems + keyedItems).flatMap {
            it.content?.toModel<RoomEmoteContent>().getEmojiItems(query)
        }
    }

    private fun RoomEmoteContent?.getEmojiItems(query: CharSequence?): List<EmojiItem> {
        return this?.images.orEmpty()
                .filter {
                    val usages = it.value.usage
                    usages.isNullOrEmpty() || RoomEmoteContent.USAGE_EMOTICON in usages
                }.filter {
                    query == null || it.key.contains(query, true)
                }.map {
                    EmojiItem(it.key, "", emoteImage = it.value)
                }.sortedBy { it.name }.distinctBy { it.mxcUrl }
    }

    private fun <T>List<T>.limit(count: Int): List<T> {
        return subList(0, min(count, size))
    }
}
