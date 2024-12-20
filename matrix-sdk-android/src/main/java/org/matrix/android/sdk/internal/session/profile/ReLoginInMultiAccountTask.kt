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

import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.auth.LoginType
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.internal.auth.SessionCreator
import org.matrix.android.sdk.internal.auth.db.LocalAccountStore
import org.matrix.android.sdk.internal.task.Task
import timber.log.Timber
import javax.inject.Inject

internal interface ReLoginInMultiAccountTask : Task<ReLoginInMultiAccountTask.Params, Session> {
    data class Params(
            val userId: String,
            val sessionCreator: SessionCreator,
            val actionForNew: (LocalAccount) -> Unit,
    )
}

internal class DefaultReLoginInMultiAccountTask @Inject constructor(
        authenticationService: AuthenticationService
) : ReLoginInMultiAccountTask {
    private val localAccountStore: LocalAccountStore = authenticationService.getLocalAccountStore()

    override suspend fun execute(params: ReLoginInMultiAccountTask.Params): Session {
        val account = localAccountStore.getAccount(params.userId)
        require(account.homeServerUrl.isNotBlank())

        val homeServerConnectionConfig = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(account.homeServerUrl)
                .build()
        val credentials = Credentials(
                userId = account.userId,
                deviceId = account.deviceId,
                homeServer = account.homeServerUrl,
                accessToken = account.token!!,
                refreshToken = account.refreshToken,
        )

        if (account.isNew) {
            Timber.d("Do action for newly created account")
            params.actionForNew.invoke(account)
            localAccountStore.markAsOld(account.userId)
        }

        Timber.d("Create Session")
        return params.sessionCreator.createSession(credentials, homeServerConnectionConfig, LoginType.DIRECT)
    }
}
