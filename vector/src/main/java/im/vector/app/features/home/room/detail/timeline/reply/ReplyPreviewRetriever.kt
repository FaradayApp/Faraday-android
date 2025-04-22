/*
 * Copyright (c) 2020 New Vector Ltd
 * Copyright (c) 2022 Beeper Inc.
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

import im.vector.app.BuildConfig
import im.vector.app.features.home.room.detail.timeline.MessageColorProvider
import im.vector.app.features.home.room.detail.timeline.format.DisplayableEventFormatter
import im.vector.app.features.home.room.detail.timeline.helper.MatrixItemColorProvider
import im.vector.app.features.home.room.detail.timeline.item.MessageInformationData
import im.vector.app.features.home.room.detail.timeline.render.EventTextRenderer
import im.vector.app.features.html.EventHtmlRenderer
import im.vector.app.features.html.PillsPostProcessor
import im.vector.app.features.html.SpanUtils
import im.vector.app.features.html.VectorHtmlCompressor
import im.vector.app.features.media.ImageContentRenderer
import im.vector.app.features.settings.VectorPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.crypto.MXCryptoError
import org.matrix.android.sdk.api.session.crypto.model.OlmDecryptionResult
import org.matrix.android.sdk.api.session.events.model.getRelationContent
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.room.getTimelineEvent
import org.matrix.android.sdk.api.session.room.powerlevels.PowerLevelsHelper
import org.matrix.android.sdk.api.session.room.sender.SenderInfo
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.getLatestEventId
import org.matrix.android.sdk.api.util.toMatrixItem
import timber.log.Timber
import java.util.UUID

class ReplyPreviewRetriever(
        vectorPreferences: VectorPreferences,
        private val roomId: String,
        private val session: Session,
        private val coroutineScope: CoroutineScope,
        private val displayableEventFormatter: DisplayableEventFormatter,
        private val pillsPostProcessorFactory: PillsPostProcessor.Factory,
        private val textRendererFactory: EventTextRenderer.Factory,
        private val callback: PreviewReplyRetrieverCallback,
        private val powerLevelProvider: PowerLevelProvider,
        val messageColorProvider: MessageColorProvider,
        val htmlCompressor: VectorHtmlCompressor,
        val htmlRenderer: EventHtmlRenderer,
        val spanUtils: SpanUtils,
        val imageContentRenderer: ImageContentRenderer,
) {
    private data class ReplyPreviewUiState(
            // Id of the latest event in the case of an edited event, or the eventId for an event which has not been edited
            //val latestEventId: String,
            // Id of the latest replied-to event in the case of an edited event, or the eventId for an replied-to event which has not been edited
            val latestRepliedToEventId: String?,
            val previewReplyUiState: PreviewReplyUiState
    )

    companion object {
        // Delay between attempts to fetch the replied-to event from the server, if it failed.
        private const val RETRY_SERVER_LOOKUP_INTERVAL_MS = 1000 * 30

        private val DEBUG = BuildConfig.DEBUG // TODO: false
    }

    private fun TimelineEvent.getCacheId(): String {
        return if (root.isRedacted()) {
            "REDACTED"
        } else {
            "L:${getLatestEventId()}"
        }
    }

    // Keys are the main eventId
    private val data = mutableMapOf<String, ReplyPreviewUiState>()
    private val listeners = mutableMapOf<String, MutableSet<PreviewReplyRetrieverListener>>()
    // Cache which replied-to events we already looked up successfully: key is main eventId, value is the getCacheId() value, which wraps the latest eventId.
    // To be synchronized with the data field's lock.
    private val lookedUpEvents = mutableMapOf<String, String>()
    // Timestamps of allowed server requests for individual events, to not spam server with the same request
    private val serverRequests = mutableMapOf<String, Long>()
    // eventToRetrieveId-specific locking
    private val retrieveEventLocks = mutableMapOf<String, Any>()

    private val threadsEnabled = vectorPreferences.areThreadMessagesEnabled()

    fun invalidateEventsFromSnapshot(snapshot: List<TimelineEvent>) {
        val snapshotEvents = snapshot.associateBy { it.eventId }
        synchronized(data) {
            // Invalidate all events that have been updated in the snapshot, or are not included in it (in which case we don't know if they updated)
            for (eventId in lookedUpEvents.keys.toList()) {
                val cacheId = snapshotEvents[eventId]?.getCacheId()
                if (lookedUpEvents[eventId] != cacheId) {
                    if (DEBUG) Timber.i("Reply retriever: invalidate $eventId: ${lookedUpEvents[eventId]} -> $cacheId")
                    lookedUpEvents.remove(eventId)
                }
            }
        }
    }

    val pillsPostProcessor by lazy {
        pillsPostProcessorFactory.create(roomId)
    }

    val textRenderer by lazy {
        textRendererFactory.create(roomId)
    }

    fun getReplyTo(event: TimelineEvent) {
        val eventId = event.root.eventId ?: return
        val now = System.currentTimeMillis()

        synchronized(data) {
            val current = data[eventId]

            val relationContent = event.root.getRelationContent()
            val repliedToEventId = relationContent?.inReplyTo?.eventId?.takeIf { relationContent.isFallingBack != true || !threadsEnabled }
            if (current == null || repliedToEventId != current.latestRepliedToEventId) {
                // We have not rendered this yet, or the replied-to event has updated
                if (repliedToEventId?.isNotEmpty().orFalse()) {
                    updateState(eventId, repliedToEventId, PreviewReplyUiState.ReplyLoading(repliedToEventId))
                    repliedToEventId
                } else {
                    updateState(eventId, repliedToEventId, PreviewReplyUiState.NoReply)
                    null
                }
            } else {
                // Nothing changed, we have rendered this before... but the replied-to event might have been edited or decrypted in the meantime
                if (repliedToEventId in lookedUpEvents) {
                    // We have looked this event up before and haven't removed it from lookedUpEvents yet, so no need to re-render
                    null
                } else {
                    repliedToEventId
                }
            }
        }?.let { eventIdToRetrieve ->
            coroutineScope.launch(Dispatchers.IO) {
                val retrieveEventLock = synchronized(retrieveEventLocks) {
                    retrieveEventLocks.getOrPut(eventIdToRetrieve) { eventIdToRetrieve }
                }
                runCatching {
                    // Don't spam the server too often if it doesn't know the event
                    val mayAskServerForEvent = synchronized(serverRequests) {
                        val lastAttempt = serverRequests[eventIdToRetrieve]
                        if (lastAttempt == null || lastAttempt < now - RETRY_SERVER_LOOKUP_INTERVAL_MS) {
                            serverRequests[eventIdToRetrieve] = now
                            true
                        } else {
                            false
                        }
                    }
                    if (DEBUG) Timber.i("REPLY HANDLING AFTER ${System.currentTimeMillis() - now} for $eventId / $eventIdToRetrieve, may ask: $mayAskServerForEvent")// TODO remove
                    // Synchronize, so that we await a pending server fetch if necessary
                    synchronized(retrieveEventLock) {
                        if (mayAskServerForEvent) {
                            session.getRoom(roomId)?.timelineService()?.getOrFetchAndPersistTimelineEventBlocking(eventIdToRetrieve)
                        } else {
                            session.getRoom(roomId)?.getTimelineEvent(eventIdToRetrieve)
                        }
                    }?.apply {
                        // We need to check encryption
                        val repliedToEvent = root // TODO what if rendered event is not root, i.e. root.eventId != getLatestEventId()? (we currently just use the initial event in this case, better than nothing)
                        if (repliedToEvent.isEncrypted() && repliedToEvent.mxDecryptionResult == null) {
                            // for now decrypt sync
                            try {
                                val result = session.cryptoService().decryptEvent(root, root.roomId + UUID.randomUUID().toString())
                                repliedToEvent.mxDecryptionResult = OlmDecryptionResult(
                                        payload = result.clearEvent,
                                        senderKey = result.senderCurve25519Key,
                                        keysClaimed = result.claimedEd25519Key?.let { k -> mapOf("ed25519" to k) },
                                        forwardingCurve25519KeyChain = result.forwardingCurve25519KeyChain,
                                        verificationState = result.messageVerificationState,
                                )
                            } catch (e: MXCryptoError) {
                                Timber.w("Failed to decrypt event in reply")
                            }
                        }
                    }
                }.fold(
                        {
                            // We should render a reply
                            synchronized(data) {
                                updateState(eventId, eventIdToRetrieve,
                                        if (it == null) PreviewReplyUiState.Error(Exception("Event not found"), eventIdToRetrieve) // TODO proper exception or sth.
                                        else {
                                            val senderName = callback.resolveDisplayName(it.senderInfo)
                                            PreviewReplyUiState.InReplyTo(eventIdToRetrieve, it, senderName)
                                        }
                                )
                            }
                        },
                        {
                            synchronized(data) {
                                updateState(eventId, eventIdToRetrieve, PreviewReplyUiState.Error(it, eventIdToRetrieve))
                            }
                        }
                )
            }
        }
    }

    private fun updateState(eventId: String, latestRepliedToEventId: String?, state: PreviewReplyUiState) {
        data[eventId] = ReplyPreviewUiState(latestRepliedToEventId, state)
        if (state is PreviewReplyUiState.InReplyTo) {
            if (state.event.isEncrypted() && state.event.root.mxDecryptionResult == null) {
                if (DEBUG) Timber.i("Reply retriever: not caching $eventId / $latestRepliedToEventId")
                // Do not cache encrypted events, so we try again on next update
                lookedUpEvents.remove(state.repliedToEventId)
            } else {
                if (DEBUG) Timber.i("Reply retriever: caching $eventId / $latestRepliedToEventId")
                lookedUpEvents[state.repliedToEventId] = state.event.getCacheId()
            }
        }
        // Notify the listener
        coroutineScope.launch(Dispatchers.Main) {
            listeners[eventId].orEmpty().forEach {
                it.onStateUpdated(state)
            }
        }
    }

    // Called by the Epoxy item during binding
    fun addListener(key: String, listener: PreviewReplyRetrieverListener) {
        listeners.getOrPut(key) { mutableSetOf() }.add(listener)

        // Give the current state if any
        synchronized(data) {
            listener.onStateUpdated(data[key]?.previewReplyUiState ?: PreviewReplyUiState.NoReply)
        }
    }

    // Called by the Epoxy item during unbinding
    fun removeListener(key: String, listener: PreviewReplyRetrieverListener) {
        listeners[key]?.remove(listener)
    }

    interface PreviewReplyRetrieverListener {
        fun onStateUpdated(state: PreviewReplyUiState)
    }
    interface PowerLevelProvider {
        fun getPowerLevelsHelper(): PowerLevelsHelper?
    }

    fun getMemberNameColor(event: TimelineEvent, roomInformationData: MessageInformationData): Int {
        val matrixItem = event.senderInfo.toMatrixItem()
        return messageColorProvider.getMemberNameTextColor(
                matrixItem,
                MatrixItemColorProvider.UserInRoomInformation(
                        roomInformationData.isDirect,
                        roomInformationData.isPublic,
                        powerLevelProvider.getPowerLevelsHelper()?.getUserPowerLevelValue(event.senderInfo.userId)
                )
        )
    }

    interface PreviewReplyRetrieverCallback {
        fun resolveDisplayName(senderInfo: SenderInfo): String
    }

    fun formatFallbackReply(event: TimelineEvent): CharSequence {
        return displayableEventFormatter.format(event,
                // This is not a preview in the traditional sense, as sender information is rendered outside either way.
                // So we want to omit these information from the text.
                isDm = false,
                appendAuthor = false,
        )
    }
}
