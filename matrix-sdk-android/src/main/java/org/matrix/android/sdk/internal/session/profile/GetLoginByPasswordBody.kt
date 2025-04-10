/*
 * Copyright (c) 2023 New Vector Ltd
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

package org.matrix.android.sdk.internal.session.profile

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class GetLoginByPasswordBody(
        @Json(name = "type") val type: String,
        @Json(name = "identifier") val identifier: LoginIdentifier,
        @Json(name = "password") val password: String,
        @Json(name = "device_id") val deviceId: String,
)

@JsonClass(generateAdapter = true)
internal data class LoginIdentifier(
        @Json(name = "type") val type: String,
        @Json(name = "user") val user: String,
)
