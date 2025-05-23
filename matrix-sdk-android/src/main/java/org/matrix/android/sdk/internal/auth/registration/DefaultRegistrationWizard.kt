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

package org.matrix.android.sdk.internal.auth.registration

import kotlinx.coroutines.delay
import org.matrix.android.sdk.api.auth.LoginType
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.LoginFlowTypes
import org.matrix.android.sdk.api.auth.registration.RegisterThreePid
import org.matrix.android.sdk.api.auth.registration.RegistrationAvailability
import org.matrix.android.sdk.api.auth.registration.RegistrationResult
import org.matrix.android.sdk.api.auth.registration.RegistrationWizard
import org.matrix.android.sdk.api.auth.registration.toFlowResult
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.failure.Failure.RegistrationFlowError
import org.matrix.android.sdk.api.util.JsonDict
import org.matrix.android.sdk.internal.auth.AuthAPI
import org.matrix.android.sdk.internal.auth.PendingSessionStore
import org.matrix.android.sdk.internal.auth.SessionCreator
import org.matrix.android.sdk.internal.auth.db.PendingSessionData
import org.matrix.android.sdk.internal.session.profile.LocalAccount

/**
 * This class execute the registration request and is responsible to keep the session of interactive authentication.
 */
internal class DefaultRegistrationWizard(
        authAPI: AuthAPI,
        private val sessionCreator: SessionCreator,
        private val pendingSessionStore: PendingSessionStore
) : RegistrationWizard {

    private var pendingSessionData: PendingSessionData = pendingSessionStore.getPendingSessionData() ?: error("Pending session data should exist here")

    private val registerTask: RegisterTask = DefaultRegisterTask(authAPI)
    private val registerAvailableTask: RegisterAvailableTask = DefaultRegisterAvailableTask(authAPI)
    private val registerAddThreePidTask: RegisterAddThreePidTask = DefaultRegisterAddThreePidTask(authAPI)
    private val validateCodeTask: ValidateCodeTask = DefaultValidateCodeTask(authAPI)
    private val registerCustomTask: RegisterCustomTask = DefaultRegisterCustomTask(authAPI)

    override fun getCurrentThreePid(): String? {
        return when (val threePid = pendingSessionData.currentThreePidData?.threePid) {
            is RegisterThreePid.Email -> threePid.email
            is RegisterThreePid.Msisdn -> {
                // Take formatted msisdn if provided by the server
                pendingSessionData.currentThreePidData?.addThreePidRegistrationResponse?.formattedMsisdn?.takeIf { it.isNotBlank() } ?: threePid.msisdn
            }
            null -> null
        }
    }

    override fun isRegistrationStarted() = pendingSessionData.isRegistrationStarted

    override suspend fun getRegistrationFlow(): RegistrationResult {
        val params = RegistrationParams()
        return performRegistrationRequest(params, LoginType.PASSWORD)
    }

    override suspend fun createAccount(
            userName: String?,
            password: String?,
            initialDeviceDisplayName: String?,
            deviceId: String
    ): RegistrationResult {
        val params = RegistrationParams(
                username = userName,
                password = password,
                initialDeviceDisplayName = initialDeviceDisplayName,
                deviceId = deviceId
        )
        return performRegistrationRequest(params, LoginType.PASSWORD)
                .also {
                    pendingSessionData = pendingSessionData.copy(isRegistrationStarted = true)
                            .also { pendingSessionStore.savePendingSessionData(it) }
                }.also {
                    if(it is RegistrationResult.Success) {
                        it.session.profileService().storeAccount(LocalAccount(
                            userId = it.session.myUserId,
                            homeServerUrl = it.session.sessionParams.homeServerUrl,
                            token = it.session.sessionParams.credentials.accessToken,
                            username = userName,
                            password = password,
                            deviceId = deviceId,
                            refreshToken = it.session.sessionParams.credentials.refreshToken
                        ))
                    }
                }
    }

    override suspend fun performReCaptcha(response: String): RegistrationResult {
        val safeSession = pendingSessionData.currentSession
                ?: throw IllegalStateException("developer error, call createAccount() method first")

        val params = RegistrationParams(auth = AuthParams.createForCaptcha(safeSession, response))
        return performRegistrationRequest(params, LoginType.PASSWORD)
    }

    override suspend fun acceptTerms(): RegistrationResult {
        val safeSession = pendingSessionData.currentSession
                ?: throw IllegalStateException("developer error, call createAccount() method first")

        val params = RegistrationParams(auth = AuthParams(type = LoginFlowTypes.TERMS, session = safeSession))
        return performRegistrationRequest(params, LoginType.PASSWORD)
    }

    override suspend fun addThreePid(threePid: RegisterThreePid): RegistrationResult {
        pendingSessionData = pendingSessionData.copy(currentThreePidData = null)
                .also { pendingSessionStore.savePendingSessionData(it) }

        return sendThreePid(threePid)
    }

    override suspend fun sendAgainThreePid(): RegistrationResult {
        val safeCurrentThreePid = pendingSessionData.currentThreePidData?.threePid
                ?: throw IllegalStateException("developer error, call createAccount() method first")

        return sendThreePid(safeCurrentThreePid)
    }

    private suspend fun sendThreePid(threePid: RegisterThreePid): RegistrationResult {
        val safeSession = pendingSessionData.currentSession ?: throw IllegalStateException("developer error, call createAccount() method first")
        val response = registerAddThreePidTask.execute(
                RegisterAddThreePidTask.Params(
                        threePid,
                        pendingSessionData.clientSecret,
                        pendingSessionData.sendAttempt
                )
        )

        pendingSessionData = pendingSessionData.copy(sendAttempt = pendingSessionData.sendAttempt + 1)
                .also { pendingSessionStore.savePendingSessionData(it) }

        val params = RegistrationParams(
                auth = if (threePid is RegisterThreePid.Email) {
                    AuthParams.createForEmailIdentity(
                            safeSession,
                            ThreePidCredentials(
                                    clientSecret = pendingSessionData.clientSecret,
                                    sid = response.sid
                            )
                    )
                } else {
                    AuthParams.createForMsisdnIdentity(
                            safeSession,
                            ThreePidCredentials(
                                    clientSecret = pendingSessionData.clientSecret,
                                    sid = response.sid
                            )
                    )
                }
        )
        // Store data
        pendingSessionData = pendingSessionData.copy(currentThreePidData = ThreePidData.from(threePid, response, params))
                .also { pendingSessionStore.savePendingSessionData(it) }

        // and send the sid a first time
        return performRegistrationRequest(params, LoginType.PASSWORD)
    }

    override suspend fun checkIfEmailHasBeenValidated(delayMillis: Long): RegistrationResult {
        val safeParam = pendingSessionData.currentThreePidData?.registrationParams
                ?: throw IllegalStateException("developer error, no pending three pid")

        return performRegistrationRequest(safeParam, LoginType.PASSWORD, delayMillis)
    }

    override suspend fun handleValidateThreePid(code: String): RegistrationResult {
        return validateThreePid(code)
    }

    private suspend fun validateThreePid(code: String): RegistrationResult {
        val registrationParams = pendingSessionData.currentThreePidData?.registrationParams
                ?: throw IllegalStateException("developer error, no pending three pid")
        val safeCurrentData = pendingSessionData.currentThreePidData ?: throw IllegalStateException("developer error, call createAccount() method first")
        val url = safeCurrentData.addThreePidRegistrationResponse.submitUrl ?: throw IllegalStateException("Missing url to send the code")
        val validationBody = ValidationCodeBody(
                clientSecret = pendingSessionData.clientSecret,
                sid = safeCurrentData.addThreePidRegistrationResponse.sid,
                code = code
        )
        val validationResponse = validateCodeTask.execute(ValidateCodeTask.Params(url, validationBody))
        if (validationResponse.isSuccess()) {
            // The entered code is correct
            // Same than validate email
            return performRegistrationRequest(registrationParams, LoginType.PASSWORD, 3_000)
        } else {
            // The code is not correct
            throw Failure.SuccessError
        }
    }

    override suspend fun dummy(): RegistrationResult {
        val safeSession = pendingSessionData.currentSession
                ?: throw IllegalStateException("developer error, call createAccount() method first")

        val params = RegistrationParams(auth = AuthParams(type = LoginFlowTypes.DUMMY, session = safeSession))
        return performRegistrationRequest(params, LoginType.PASSWORD)
    }

    override suspend fun registrationCustom(
            authParams: JsonDict
    ): RegistrationResult {
        val safeSession = pendingSessionData.currentSession
                ?: throw IllegalStateException("developer error, call createAccount() method first")

        val mutableParams = authParams.toMutableMap()
        mutableParams["session"] = safeSession

        val params = RegistrationCustomParams(auth = mutableParams)
        return performRegistrationOtherRequest(LoginType.CUSTOM, params)
    }

    private suspend fun performRegistrationRequest(
            registrationParams: RegistrationParams,
            loginType: LoginType,
            delayMillis: Long = 0
    ): RegistrationResult {
        delay(delayMillis)
        return register(loginType) { registerTask.execute(RegisterTask.Params(registrationParams)) }.also {
            if(it is RegistrationResult.Success) {
                it.session.profileService().storeAccount(LocalAccount(
                    userId = it.session.myUserId,
                    homeServerUrl = it.session.sessionParams.homeServerUrl,
                    token = it.session.sessionParams.credentials.accessToken,
                    username = registrationParams.username,
                    password = registrationParams.password,
                    deviceId = it.session.sessionParams.deviceId,
                    refreshToken = it.session.sessionParams.credentials.refreshToken
                ))
            }
        }
    }

    private suspend fun performRegistrationOtherRequest(
            loginType: LoginType,
            registrationCustomParams: RegistrationCustomParams,
    ): RegistrationResult {
        return register(loginType) { registerCustomTask.execute(RegisterCustomTask.Params(registrationCustomParams)) }
    }

    private suspend fun register(
            loginType: LoginType,
            execute: suspend () -> Credentials,
    ): RegistrationResult {
        val credentials = try {
            execute.invoke()
        } catch (exception: Throwable) {
            if (exception is RegistrationFlowError) {
                pendingSessionData =
                        pendingSessionData.copy(currentSession = exception.registrationFlowResponse.session)
                                .also { pendingSessionStore.savePendingSessionData(it) }
                return RegistrationResult.FlowResponse(exception.registrationFlowResponse.toFlowResult())
            } else {
                throw exception
            }
        }

        val session = sessionCreator.createSession(credentials, pendingSessionData.homeServerConnectionConfig, loginType)
        return RegistrationResult.Success(session)
    }

    override suspend fun registrationAvailable(userName: String): RegistrationAvailability {
        return registerAvailableTask.execute(RegisterAvailableTask.Params(userName))
    }
}
