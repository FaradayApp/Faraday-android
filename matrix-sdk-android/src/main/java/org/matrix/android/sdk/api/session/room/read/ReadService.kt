/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.api.session.room.read

import androidx.lifecycle.LiveData
import org.matrix.android.sdk.api.session.room.model.ReadReceipt
import org.matrix.android.sdk.api.util.Optional

/**
 * This interface defines methods to handle read receipts and read marker in a room. It's implemented at the room level.
 */
interface ReadService {

    enum class MarkAsReadParams {
        READ_RECEIPT,
        READ_MARKER,
        BOTH
    }

    /**
     * Force the read marker to be set on the latest event.
     */
    suspend fun markAsRead(params: MarkAsReadParams = MarkAsReadParams.BOTH, mainTimeLineOnly: Boolean = true)

    /**
     * Set the read receipt on the event with provided eventId.
     * @param eventId the id of the event where read receipt will be set
     * @param threadId the id of the thread in which read receipt will be set. For main thread use [ReadService.THREAD_ID_MAIN] constant
     */
    suspend fun setReadReceipt(eventId: String, threadId: String)

    /**
     * Set the read marker on the event with provided eventId.
     */
    suspend fun setReadMarker(fullyReadEventId: String)

    /**
     * Mark a room as unread, or remove an existing unread marker.
     */
    suspend fun setMarkedUnread(markedUnread: Boolean)

    /**
     * Change the explicitly set unread marker flag
     */
    suspend fun setMarkedUnreadFlag(markedUnread: Boolean)

    /**
     * Check if an event is already read, ie. your read receipt is set on a more recent event.
     */
    fun isEventRead(eventId: String, eventTs: Long? = null): Boolean

    /**
     * Returns a live read marker id for the room.
     */
    fun getReadMarkerLive(): LiveData<Optional<String>>

    /**
     * Returns a live read receipt id for the room.
     */
    fun getMyReadReceiptLive(threadId: String?): LiveData<Optional<String>>

    /**
     * Get the eventId from the main timeline where the read receipt for the provided user is.
     * @param userId the id of the user to look for
     *
     * @return the eventId where the read receipt for the provided user is attached, or null if not found
     */
    fun getUserReadReceipt(userId: String): String?

    /**
     * Returns a live list of read receipts for a given event.
     * @param eventId the event
     */
    fun getEventReadReceiptsLive(eventId: String): LiveData<List<ReadReceipt>>

    companion object {
        const val THREAD_ID_MAIN = "main"
        // Artificial read marker, updated whenever someone sets the main or null one
        const val THREAD_ID_MAIN_OR_NULL = "main_or_null"
    }
}
