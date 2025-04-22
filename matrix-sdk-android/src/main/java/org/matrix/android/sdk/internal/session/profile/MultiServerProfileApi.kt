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

package org.matrix.android.sdk.internal.session.profile

import org.matrix.android.sdk.api.util.JsonDict
import org.matrix.android.sdk.internal.network.MultiServerCredentials
import org.matrix.android.sdk.internal.network.NetworkConstants
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Tag

internal interface MultiServerProfileApi {
    /**
     * Return user credentials by password.
     */
    @POST(NetworkConstants.URI_API_PREFIX_PATH_V3 + "login")
    suspend fun getLoginByPassword(
            @Tag requestCredentials: MultiServerCredentials,
            @Body body: GetLoginByPasswordBody
    ): GetLoginResponse

    @GET(NetworkConstants.URI_API_PREFIX_PATH_R0 + "profile/{userId}")
    suspend fun getProfile(
            @Tag requestCredentials: MultiServerCredentials,
            @Path("userId") userId: String
    ): JsonDict
}
