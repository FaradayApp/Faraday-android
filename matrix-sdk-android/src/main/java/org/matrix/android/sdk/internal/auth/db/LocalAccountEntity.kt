/*
 * Copyright (c) 2024 New Vector Ltd
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

package org.matrix.android.sdk.internal.auth.db

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.matrix.android.sdk.internal.session.profile.LocalAccount

// TODO: make fields non-nullable
internal open class LocalAccountEntity(
        @PrimaryKey var userId: String = "",
        var token: String? = null,
        var username: String? = null,
        var password: String? = null,
        var homeServerUrl: String = "",
        var deviceId: String = "",
        var refreshToken: String? = null,
        var isNew: Boolean = false,
        var unreadCount: Int = 0,
) : RealmObject() {
    companion object
}

internal fun LocalAccountEntity.toLocalAccount() =
        LocalAccount(userId, token, username, password, homeServerUrl, deviceId, refreshToken, isNew, unreadCount)

internal fun LocalAccount.toEntity() =
        LocalAccountEntity(userId, token, username, password, homeServerUrl, deviceId, refreshToken, isNew, unreadCount)
