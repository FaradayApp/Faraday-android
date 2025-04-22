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

import dagger.Lazy
import okhttp3.OkHttpClient
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.auth.data.LoginFlowTypes
import org.matrix.android.sdk.api.failure.toRegistrationFlowResponse
import org.matrix.android.sdk.internal.auth.registration.AuthParams
import org.matrix.android.sdk.internal.auth.registration.RegistrationParams
import org.matrix.android.sdk.internal.di.Unauthenticated
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.RetrofitFactory
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.network.httpclient.addSocketFactory
import org.matrix.android.sdk.internal.task.Task
import timber.log.Timber
import javax.inject.Inject

internal interface RegisterNewAccountTask : Task<RegisterNewAccountTask.Params, LocalAccount?> {
    data class Params(
            val registrationParams: RegistrationParams,
            val homeServerConnectionConfig: HomeServerConnectionConfig
    )
}

internal class DefaultRegisterNewAccountTask @Inject constructor(
        private val globalErrorReceiver: GlobalErrorReceiver,
        @Unauthenticated
        private val okHttpClient: Lazy<OkHttpClient>,
        private val retrofitFactory: RetrofitFactory,
) : RegisterNewAccountTask {
    override suspend fun execute(params: RegisterNewAccountTask.Params): LocalAccount? {
        var credentials: Credentials? = null
        val unauthorizedProfileAPI = buildProfileAPI(params.homeServerConnectionConfig)
        try {
            executeRequest(globalErrorReceiver) {
                unauthorizedProfileAPI.register(params.registrationParams)
            }
        } catch (throwable: Throwable) {
            Timber.i("Registration error $throwable")
            throwable.toRegistrationFlowResponse()?.session?.let {
                val newParams = RegistrationParams(auth = AuthParams(type = LoginFlowTypes.DUMMY, session = it))
                credentials = executeRequest(globalErrorReceiver) {
                    unauthorizedProfileAPI.register(newParams)
                }
            } ?: throw throwable
        }

        credentials?.let {
            return LocalAccount(
                    userId = it.userId,
                    homeServerUrl = params.homeServerConnectionConfig.homeServerUri.toString(),
                    username = params.registrationParams.username,
                    password = params.registrationParams.password,
                    token = it.accessToken,
                    deviceId = params.registrationParams.deviceId!!,
                    refreshToken = it.refreshToken,
                    isNew = true
            )
        }

        return null
    }

    private fun buildClient(homeServerConnectionConfig: HomeServerConnectionConfig): OkHttpClient {
        return okHttpClient.get()
                .newBuilder()
                .addSocketFactory(homeServerConnectionConfig)
                .build()
    }

    private fun buildProfileAPI(homeServerConnectionConfig: HomeServerConnectionConfig): ProfileAPI {
        val retrofit = retrofitFactory.create(buildClient(homeServerConnectionConfig), homeServerConnectionConfig.homeServerUriBase.toString())
        return retrofit.create(ProfileAPI::class.java)
    }
}


