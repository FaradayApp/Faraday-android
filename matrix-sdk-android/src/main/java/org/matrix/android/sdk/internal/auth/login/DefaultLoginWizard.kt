/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.internal.auth.login

import org.matrix.android.sdk.api.auth.LoginType
import org.matrix.android.sdk.api.auth.login.LoginProfileInfo
import org.matrix.android.sdk.api.auth.login.LoginWizard
import org.matrix.android.sdk.api.auth.registration.RegisterThreePid
import org.matrix.android.sdk.api.extensions.isEmail
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.util.JsonDict
import org.matrix.android.sdk.internal.auth.AuthAPI
import org.matrix.android.sdk.internal.auth.PendingSessionStore
import org.matrix.android.sdk.internal.auth.SessionCreator
import org.matrix.android.sdk.internal.auth.data.PasswordLoginParams
import org.matrix.android.sdk.internal.auth.data.ThreePidMedium
import org.matrix.android.sdk.internal.auth.data.TokenLoginParams
import org.matrix.android.sdk.internal.auth.db.PendingSessionData
import org.matrix.android.sdk.internal.auth.registration.AddThreePidRegistrationParams
import org.matrix.android.sdk.internal.auth.registration.RegisterAddThreePidTask
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.session.content.DefaultContentUrlResolver
import org.matrix.android.sdk.internal.session.contentscanner.DisabledContentScannerService
import org.matrix.android.sdk.internal.session.profile.LocalAccount
import org.matrix.android.sdk.internal.session.media.IsAuthenticatedMediaSupported

internal class DefaultLoginWizard(
        private val authAPI: AuthAPI,
        private val sessionCreator: SessionCreator,
        private val pendingSessionStore: PendingSessionStore
) : LoginWizard {

    private var pendingSessionData: PendingSessionData = pendingSessionStore.getPendingSessionData() ?: error("Pending session data should exist here")

    private val getProfileTask: GetProfileTask = DefaultGetProfileTask(
            authAPI = authAPI,
            contentUrlResolver = DefaultContentUrlResolver(
                    homeServerConnectionConfig = pendingSessionData.homeServerConnectionConfig,
                    scannerService = DisabledContentScannerService(),
                    isAuthenticatedMediaSupported = object : IsAuthenticatedMediaSupported {
                        override fun invoke() = false
                    }
            )
    )

    override suspend fun getProfileInfo(matrixId: String): LoginProfileInfo {
        return getProfileTask.execute(GetProfileTask.Params(matrixId))
    }

    override suspend fun login(
            login: String,
            password: String,
            initialDeviceName: String,
            deviceId: String
    ): Session {
        val loginParams = if (login.isEmail()) {
            PasswordLoginParams.thirdPartyIdentifier(
                    medium = ThreePidMedium.EMAIL,
                    address = login,
                    password = password,
                    deviceDisplayName = initialDeviceName,
                    deviceId = deviceId
            )
        } else {
            PasswordLoginParams.userIdentifier(
                    user = login.trim(),
                    password = password,
                    deviceDisplayName = initialDeviceName,
                    deviceId = deviceId
            )
        }
        val credentials = executeRequest(null) {
            authAPI.login(loginParams)
        }

        val session = sessionCreator.createSession(credentials, pendingSessionData.homeServerConnectionConfig, LoginType.PASSWORD)
        val profileService = session.profileService()
        val user = profileService.getProfileAsUser(session.myUserId)
        session.profileService().storeAccount(LocalAccount(
            userId = user.userId,
            homeServerUrl = session.sessionParams.homeServerUrl,
            token = session.sessionParams.credentials.accessToken,
            username = user.userId,
            password = password,
            deviceId = deviceId,
            refreshToken = session.sessionParams.credentials.refreshToken
        ))

        return session
    }

    /**
     * Ref: https://matrix.org/docs/spec/client_server/latest#handling-the-authentication-endpoint
     */
    override suspend fun loginWithToken(loginToken: String): Session {
        val loginParams = TokenLoginParams(
                token = loginToken
        )
        val credentials = executeRequest(null) {
            authAPI.login(loginParams)
        }

        val session = sessionCreator.createSession(credentials, pendingSessionData.homeServerConnectionConfig, LoginType.SSO)
        session.profileService().storeAccount(LocalAccount(
                userId = session.myUserId,
                homeServerUrl = session.sessionParams.homeServerUrl,
                token = loginToken,
                deviceId = session.sessionParams.deviceId,
                refreshToken = session.sessionParams.credentials.refreshToken,
                username = null,
                password = null
        ))

        return session
    }

    override suspend fun loginCustom(data: JsonDict): Session {
        val credentials = executeRequest(null) {
            authAPI.login(data)
        }

        return sessionCreator.createSession(credentials, pendingSessionData.homeServerConnectionConfig, LoginType.CUSTOM)
    }

    override suspend fun resetPassword(email: String) {
        val param = RegisterAddThreePidTask.Params(
                RegisterThreePid.Email(email),
                pendingSessionData.clientSecret,
                pendingSessionData.sendAttempt
        )

        pendingSessionData = pendingSessionData.copy(sendAttempt = pendingSessionData.sendAttempt + 1)
                .also { pendingSessionStore.savePendingSessionData(it) }

        val result = executeRequest(null) {
            authAPI.resetPassword(AddThreePidRegistrationParams.from(param))
        }

        pendingSessionData = pendingSessionData.copy(resetPasswordData = ResetPasswordData(result))
                .also { pendingSessionStore.savePendingSessionData(it) }
    }

    override suspend fun resetPasswordMailConfirmed(newPassword: String, logoutAllDevices: Boolean) {
        val resetPasswordData = pendingSessionData.resetPasswordData ?: throw IllegalStateException("Developer error - Must call resetPassword first")
        val param = ResetPasswordMailConfirmed.create(
                pendingSessionData.clientSecret,
                resetPasswordData.addThreePidRegistrationResponse.sid,
                newPassword,
                logoutAllDevices
        )

        executeRequest(null) {
            authAPI.resetPasswordMailConfirmed(param)
        }

        // Set to null?
        // resetPasswordData = null
    }
}
