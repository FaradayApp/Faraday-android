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

package im.vector.app.features.login

import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.preference.Preference
import com.airbnb.mvrx.activityViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.extensions.observeEvent
import im.vector.app.core.settings.connectionmethods.onion.TorEvent
import im.vector.app.features.settings.connectionmethod.ConnectionSettingsBaseFragment
import org.matrix.android.sdk.api.util.ConnectionType

@AndroidEntryPoint
class LoginConnectionSettingsFragment : ConnectionSettingsBaseFragment() {

    private val loginViewModel: LoginViewModel by activityViewModel()

    private var torLoggingDialog: AlertDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        initDialog()
    }

    override fun bindPref() {
        super.bindPref()
        savePreference?.let { pref ->
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                when (connectionTypePreference?.type ?: return@OnPreferenceClickListener true) {
                    ConnectionType.MATRIX -> {
                        cancelTorInit()
                        when (switchUseProxyPreference?.isChecked ?: return@OnPreferenceClickListener true) {
                            true -> {
                                if (proxyFieldsAreValid()) {
                                    getStarted(ConnectionType.MATRIX)
                                }
                            }
                            false -> {
                                disableProxy()
                                getStarted(ConnectionType.MATRIX)
                            }
                        }
                    }

                    ConnectionType.ONION -> {
                        torCancelled = false
                        torHidden = false

                        torLoggingDialog?.run {
                            getButton(BUTTON_NEUTRAL)?.isVisible = true
                            getButton(BUTTON_NEGATIVE)?.isVisible = true
                        }

                        when (torService.isProxyRunning) {
                            true -> getStarted(ConnectionType.ONION)
                            false -> {
                                torService.switchTorPrefState(true)
                                observeTorEvents()
                            }
                        }
                    }

                    ConnectionType.I2P -> {
                        getStarted(ConnectionType.I2P)
                    }
                }
                true
            }
        }
    }

    private var torHidden = false

    private fun initDialog() {

        torLoggingDialog = MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .setNeutralButton(getString(R.string.action_hide)) { dialog, _ ->
                    torHidden = true
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                    cancelTorInit()
                }
                .create()
    }

    private var torCancelled = false
    private fun cancelTorInit() {
        torService.switchTorPrefState(false)
        disableProxy()
        torCancelled = true
    }

    override fun observeTorEvents() {
        torEventListener.torEventLiveData.observeEvent(this) { torEvent ->
            if (torCancelled) return@observeEvent

            when (torEvent) {
                is TorEvent.ConnectionEstablished -> {
                    torLoggingDialog?.dismiss()
                    getStarted(ConnectionType.ONION)
                }

                is TorEvent.ConnectionFailed -> {
                    torLoggingDialog?.setMessage(getString(R.string.tor_connection_failed))
                    if (!torHidden && torLoggingDialog?.isShowing == false) torLoggingDialog?.show()
                    torLoggingDialog?.run {
                        getButton(BUTTON_NEUTRAL)?.isVisible = false
                        getButton(BUTTON_NEGATIVE)?.isVisible = false
                    }
                    torLoggingDialog?.setCancelable(true)
                }

                is TorEvent.TorLogEvent -> {
                    torLoggingDialog?.setMessage(torEvent.message)
                    if (!torHidden && torLoggingDialog?.isShowing == false) {
                        torLoggingDialog?.show()
                    }
                }
            }
        }
    }

    private fun getStarted(connectionType: ConnectionType) {
        if (connectionType != ConnectionType.ONION && torService.isProxyRunning) {
            torService.switchTorPrefState(false)
        }
        lightweightSettingsStorage.setConnectionType(connectionType)
        loginViewModel.handle(LoginAction.OnGetStarted(resetLoginConfig = false))
    }
}
