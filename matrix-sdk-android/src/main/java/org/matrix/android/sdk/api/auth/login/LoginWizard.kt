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

package org.matrix.android.sdk.api.auth.login

import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.util.JsonDict

/**
 * Set of methods to be able to login to an existing account on a homeserver.
 *
 * More documentation can be found in the file https://github.com/element-hq/element-android/blob/main/docs/signin.md
 */
interface LoginWizard {
    /**
     * Get some information about a matrixId: displayName and avatar url.
     */
    suspend fun getProfileInfo(matrixId: String): LoginProfileInfo

    /**
     * Login to the homeserver.
     *
     * @param login the login field. Can be a user name, or a msisdn (email or phone number) associated to the account
     * @param password the password of the account
     * @param initialDeviceName the initial device name
     * @param deviceId the device id, optional. If not provided or null, the server will generate one.
     * @return a [Session] if the login is successful
     */
    suspend fun login(
            login: String,
            password: String,
            initialDeviceName: String,
            deviceId: String
    ): Session

    /**
     * Exchange a login token to an access token.
     *
     * @param loginToken login token, obtain when login has happen in a WebView, using SSO
     * @return a [Session] if the login is successful
     */
    suspend fun loginWithToken(loginToken: String): Session

    /**
     * Login to the homeserver by sending a custom JsonDict.
     * The data should contain at least one entry "type" with a String value.
     */
    suspend fun loginCustom(data: JsonDict): Session

    /**
     * Ask the homeserver to reset the user password. The password will not be reset until
     * [resetPasswordMailConfirmed] is successfully called.
     *
     * @param email an email previously associated to the account the user wants the password to be reset.
     */
    suspend fun resetPassword(email: String)

    /**
     * Confirm the new password, once the user has checked their email
     * When this method succeed, tha account password will be effectively modified.
     *
     * @param newPassword the desired new password.
     * @param logoutAllDevices defaults to true, all devices will be logged out. False values will only be taken into account
     * if [org.matrix.android.sdk.api.auth.data.LoginFlowResult.isLogoutDevicesSupported] is true.
     */
    suspend fun resetPasswordMailConfirmed(newPassword: String, logoutAllDevices: Boolean = true)
}
