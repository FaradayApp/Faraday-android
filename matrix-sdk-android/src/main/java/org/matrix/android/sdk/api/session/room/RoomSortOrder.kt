/*
 * Copyright 2021 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.api.session.room

/**
 * Enum to sort room list.
 */
enum class RoomSortOrder {
    /**
     * Sort room list by room ascending name.
     */
    NAME,

    /**
     * Sort room list by room descending last activity.
     */
    ACTIVITY,

    /**
     * Show unread above read
     */
    UNREAD_AND_ACTIVITY,

    /**
     * Sort room list by room priority and last activity: favorite room first, low priority room last,
     * then descending last activity.
     */
    PRIORITY_AND_ACTIVITY,

    /**
     * Do not sort room list. Useful if the order does not matter. Order can be indeterminate.
     */
    NONE
}
