/*
 * Copyright (c) 2022 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.internal.settings

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.matrix.android.sdk.api.MatrixConfiguration
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.api.util.ConnectionType
import org.matrix.android.sdk.api.util.ProxyType
import org.matrix.android.sdk.internal.session.sync.SyncPresence
import timber.log.Timber
import javax.inject.Inject

/**
 * The purpose of this class is to provide an alternative and lightweight way to store settings/data
 * on the sdk without using the database. This should be used just for sdk/user preferences and
 * not for large data sets
 */
class DefaultLightweightSettingsStorage @Inject constructor(
        context: Context,
        private val matrixConfiguration: MatrixConfiguration
) : LightweightSettingsStorage {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sdkDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val sdkEncryptedPrefs = EncryptedSharedPreferences.create(
            ENCRYPTED_PREFS_FILENAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun setThreadMessagesEnabled(enabled: Boolean) {
        sdkDefaultPrefs.edit {
            putBoolean(MATRIX_SDK_SETTINGS_THREAD_MESSAGES_ENABLED, enabled)
        }
    }

    override fun areThreadMessagesEnabled(): Boolean {
        return sdkDefaultPrefs.getBoolean(MATRIX_SDK_SETTINGS_THREAD_MESSAGES_ENABLED, matrixConfiguration.threadMessagesEnabledDefault)
    }

    override fun getProxyHost(): String {
        return sdkDefaultPrefs.getString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_HOST, matrixConfiguration.connectionProxyHostDefault).orEmpty()
    }

    override fun setProxyHost(proxyHost: String) {
        sdkDefaultPrefs.edit {
            putString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_HOST, proxyHost)
        }
    }

    override fun setProxyPort(port: Int) {
        sdkDefaultPrefs.edit {
            putInt(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PORT, port)
        }
    }

    override fun getProxyPort(): Int {
        return sdkDefaultPrefs.getInt(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PORT, matrixConfiguration.connectionProxyPortDefault)
    }

    override fun getConnectionType(): ConnectionType {
        return try {
            val strPref = sdkDefaultPrefs
                    .getString(MATRIX_SDK_SETTINGS_CONNECTION_TYPE, ConnectionType.MATRIX.name)
            ConnectionType.values().firstOrNull { it.name == strPref } ?: ConnectionType.MATRIX
        } catch (e: Throwable) {
            ConnectionType.MATRIX
        }
    }

    override fun setConnectionType(connectionType: ConnectionType) {
        sdkDefaultPrefs.edit(commit = true) {
            putString(MATRIX_SDK_SETTINGS_CONNECTION_TYPE, connectionType.name)
        }
        Timber.d("connection type changed to $connectionType")
    }

    override fun getProxyType(): ProxyType {
        return try {
            val strPref = sdkDefaultPrefs
                    .getString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_TYPE, ProxyType.NO_PROXY.name)
            ProxyType.values().firstOrNull { it.name == strPref } ?: ProxyType.NO_PROXY
        } catch (e: Throwable) {
            ProxyType.NO_PROXY
        }
    }

    override fun setProxyType(proxyType: ProxyType) {
        sdkDefaultPrefs.edit {
            putString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_TYPE, proxyType.name)
        }
    }

    override fun isProxyAuthRequired(): Boolean {
        return sdkDefaultPrefs.getBoolean(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_AUTH_REQUIRED, matrixConfiguration.connectionProxyAuthRequired)
    }

    override fun setProxyAuthRequired(isProxyAuthRequired: Boolean) {
        sdkDefaultPrefs.edit {
            putBoolean(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_AUTH_REQUIRED, isProxyAuthRequired)
        }
    }

    override fun getProxyUsername(): String {
        return sdkDefaultPrefs.getString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_USERNAME, matrixConfiguration.connectionProxyUsernameDefault).orEmpty()
    }

    override fun setProxyUsername(proxyUsername: String) {
        sdkDefaultPrefs.edit {
            putString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_USERNAME, proxyUsername)
        }
    }

    override fun getProxyPassword(): String {
        return sdkEncryptedPrefs.getString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PASSWORD, matrixConfiguration.connectionProxyPasswordDefault).orEmpty()
    }

    override fun setProxyPassword(proxyPassword: String) {
        sdkEncryptedPrefs.edit {
            putString(MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PASSWORD, proxyPassword)
        }
    }

    /**
     * Indicates whether the application password will be shown to user when he launches the app.
     */
    override fun isApplicationPasswordSet(): Boolean {
        return sdkDefaultPrefs.getBoolean(MATRIX_SDK_APPLICATION_PASSWORD_SET, matrixConfiguration.applicationPasswordEnabledDefault)
    }

    override fun setApplicationPasswordEnabled(enabled: Boolean) {
        sdkDefaultPrefs.edit {
            putBoolean(MATRIX_SDK_APPLICATION_PASSWORD_SET, enabled)
        }
    }

    override fun setApplicationPassword(applicationPassword: String?) {
        sdkEncryptedPrefs.edit {
            putString(MATRIX_SDK_APPLICATION_PASSWORD, applicationPassword)
        }
    }

    override fun getApplicationPassword(): String? {
        return sdkEncryptedPrefs.getString(MATRIX_SDK_APPLICATION_PASSWORD, null)
    }

    override fun setNukePassword(nukePassword: String) {
        sdkEncryptedPrefs.edit {
            putString(MATRIX_SDK_NUKE_PASSWORD, nukePassword)
        }
    }

    override fun getNukePassword(): String? {
        return sdkEncryptedPrefs.getString(MATRIX_SDK_NUKE_PASSWORD, null)
    }

    override fun getLastSessionHash(): String? {
        return sdkDefaultPrefs.getString(MATRIX_SDK_LAST_SESSION_HASH, null)
    }

    override fun setLastSessionHash(hash: String?) {
        sdkDefaultPrefs.edit {
            putString(MATRIX_SDK_LAST_SESSION_HASH, hash)
        }
    }

    /**
     * Set the presence status sent on syncs when the application is in foreground.
     *
     * @param presence the presence status that should be sent on sync
     */
    internal fun setSyncPresenceStatus(presence: SyncPresence) {
        sdkDefaultPrefs.edit {
            putString(MATRIX_SDK_SETTINGS_FOREGROUND_PRESENCE_STATUS, presence.value)
        }
    }

    /**
     * Get the presence status that should be sent on syncs when the application is in foreground.
     *
     * @return the presence status that should be sent on sync
     */
    internal fun getSyncPresenceStatus(): SyncPresence {
        val presenceString = sdkDefaultPrefs.getString(MATRIX_SDK_SETTINGS_FOREGROUND_PRESENCE_STATUS, SyncPresence.Online.value)
        return SyncPresence.from(presenceString) ?: SyncPresence.Online
    }

    companion object {
        const val ENCRYPTED_PREFS_FILENAME = "matrix_sdk_global_prefs"

        const val MATRIX_SDK_SETTINGS_THREAD_MESSAGES_ENABLED = "MATRIX_SDK_SETTINGS_THREAD_MESSAGES_ENABLED"
        private const val MATRIX_SDK_SETTINGS_FOREGROUND_PRESENCE_STATUS = "MATRIX_SDK_SETTINGS_FOREGROUND_PRESENCE_STATUS"
        const val MATRIX_SDK_SETTINGS_CONNECTION_TYPE = "MATRIX_SDK_SETTINGS_CONNECTION_TYPE"
        const val MATRIX_SDK_SETTINGS_CONNECTION_PROXY_HOST = "MATRIX_SDK_SETTINGS_CONNECTION_PROXY_HOST"
        const val MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PORT = "MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PORT"
        const val MATRIX_SDK_SETTINGS_CONNECTION_PROXY_TYPE = "MATRIX_SDK_SETTINGS_CONNECTION_PROXY_TYPE"
        const val MATRIX_SDK_SETTINGS_CONNECTION_PROXY_AUTH_REQUIRED = "MATRIX_SDK_SETTINGS_CONNECTION_PROXY_AUTH_REQUIRED"
        const val MATRIX_SDK_SETTINGS_CONNECTION_PROXY_USERNAME = "MATRIX_SDK_SETTINGS_CONNECTION_PROXY_USERNAME"
        const val MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PASSWORD = "MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PASSWORD"
        const val MATRIX_SDK_APPLICATION_PASSWORD_SET = "MATRIX_SDK_APPLICATION_PASSWORD_SET"
        const val MATRIX_SDK_APPLICATION_PASSWORD = "MATRIX_SDK_APPLICATION_PASSWORD"
        const val MATRIX_SDK_NUKE_PASSWORD = "MATRIX_SDK_NUKE_PASSWORD"
        const val MATRIX_SDK_LAST_SESSION_HASH = "MATRIX_SDK_LAST_SESSION_HASH"
    }
}
