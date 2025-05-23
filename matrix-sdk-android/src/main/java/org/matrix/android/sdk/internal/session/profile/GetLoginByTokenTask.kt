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


import org.matrix.android.sdk.api.MatrixPatterns.getServerName
import org.matrix.android.sdk.api.session.profile.model.AccountLoginCredentials
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject

internal interface GetLoginByTokenTask: Task<GetLoginByTokenTask.Params, AccountLoginCredentials> {
    data class Params(
            val token: String
    )
}

internal data class DefaultGetLoginByTokenTask @Inject constructor(
        private val profileAPI: ProfileAPI,
        private val globalErrorReceiver: GlobalErrorReceiver
) : GetLoginByTokenTask {
    override suspend fun execute(params: GetLoginByTokenTask.Params): AccountLoginCredentials {
        val result = executeRequest(globalErrorReceiver) {
            profileAPI.getLoginByToken(
                    GetLoginByTokenBody(
                            type = "m.login.token",
                            token = params.token
                    )
            )
        }
        return AccountLoginCredentials(
                userId = result.userId,
                deviceId = result.deviceId,
                accessToken = result.accessToken,
                homeServer = result.homeServer ?: result.userId.getServerName()
        )
    }
}
