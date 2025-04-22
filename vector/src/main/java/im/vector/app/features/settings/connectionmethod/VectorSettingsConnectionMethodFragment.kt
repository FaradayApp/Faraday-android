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

package im.vector.app.features.settings.connectionmethod

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.error.ErrorFormatter
import im.vector.app.core.extensions.observeEvent
import im.vector.app.core.extensions.singletonEntryPoint
import im.vector.app.core.platform.VectorBaseActivity
import im.vector.app.core.settings.connectionmethods.i2p.I2PService
import im.vector.app.features.analytics.AnalyticsTracker
import im.vector.app.features.analytics.plan.MobileScreen
import im.vector.lib.strings.CommonStrings
import net.i2p.data.PrivateKeyFile
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.util.ConnectionType
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class VectorSettingsConnectionMethodFragment : ConnectionSettingsBaseFragment() {


    private var titleRes = CommonStrings.settings_connection_method

    private var analyticsScreenName: MobileScreen.ScreenName? = null

    private lateinit var analyticsTracker: AnalyticsTracker

    private var mLoadingView: View? = null

    private lateinit var session: Session
    private lateinit var errorFormatter: ErrorFormatter

    private val vectorActivity: VectorBaseActivity<*> by lazy {
        activity as VectorBaseActivity<*>
    }

    override fun onAttach(context: Context) {
        val singletonEntryPoint = context.singletonEntryPoint()
        super.onAttach(context)
        session = singletonEntryPoint.activeSessionHolder().getActiveSession()
        errorFormatter = singletonEntryPoint.errorFormatter()
        analyticsTracker = singletonEntryPoint.analyticsTracker()
    }

    override fun onResume() {
        super.onResume()
        Timber.i("onResume Fragment ${javaClass.simpleName}")
        analyticsScreenName?.let {
            analyticsTracker.screen(MobileScreen(screenName = it))
        }
        vectorActivity.supportActionBar?.setTitle(titleRes)
        mLoadingView = vectorActivity.findViewById(R.id.vector_settings_spinner_views)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        I2PEventListener.I2PEventLiveData.observeEvent(this) { success ->
            when (success) {
                true -> {
                    val currentConnectionType = lightweightSettingsStorage.getConnectionType()
                    if (currentConnectionType == ConnectionType.I2P) return@observeEvent
                    hideLoadingView()
                    restartApp(ConnectionType.I2P)
                    I2PEventListener.resetConnection()
                }

                false -> {
                    hideLoadingView()
                }
            }
        }
    }


    override fun bindPref() {
        super.bindPref()
        savePreference?.let { pref ->
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                when (connectionTypePreference?.type ?: return@OnPreferenceClickListener true) {
                    ConnectionType.MATRIX -> {
                        when (switchUseProxyPreference?.isChecked ?: return@OnPreferenceClickListener true) {
                            true -> {
                                Timber.d("disable tor: Proxy Checked")
                                if (proxyFieldsAreValid()) {
                                    restartApp(ConnectionType.MATRIX)
                                }
                            }
                            false -> {
                                Timber.d("disable tor: Proxy unchecked")
                                disableProxy()
                                restartApp(ConnectionType.MATRIX)
                            }
                        }
                    }

                    ConnectionType.ONION -> {
                        val newBridge = torBridgePreference?.editTextView?.text.toString().takeIf { !it.isNullOrBlank() }
                        val oldBridge = lightweightSettingsStorage.getTorBridge().takeIf { !it.isNullOrBlank() }
                        val hasNewBridge = newBridge != oldBridge

                        if(torService.isProxyRunning && hasNewBridge) {
                            Toast.makeText(context, getString(CommonStrings.tor_connection_is_already_established), Toast.LENGTH_SHORT).show()
                            restartApp(ConnectionType.ONION)
                        } else {
                            if (hasNewBridge) {
                                lightweightSettingsStorage.setTorBridge(newBridge)
                            }
                            restartApp(ConnectionType.ONION)
                        }
                    }

                    ConnectionType.I2P -> {
                        connectToI2P()
                    }
                }
                true
            }
        }
    }

    private fun connectToI2P() {
        displayLoadingView()

        val dir = requireContext().cacheDir
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val senderClientPrivateKeyFileName = File(dir, "sender_client.dat").absolutePath
        val receiverClientPrivateKeyFileName = File(dir, "receiver_client.dat").absolutePath

        Timber.d("I2P connecting 1")

        // Clients private keys generation if not exist
        if (!File(senderClientPrivateKeyFileName).exists())
            PrivateKeyFile.main(arrayOf(senderClientPrivateKeyFileName))
        if (!File(receiverClientPrivateKeyFileName).exists())
            PrivateKeyFile.main(arrayOf(receiverClientPrivateKeyFileName))

        Timber.d("I2P connecting 2")

        val client = I2PService(receiverClientPrivateKeyFileName, senderClientPrivateKeyFileName, I2PEventListener)

        client.connect()
        Timber.d("I2P connecting 3")
    }

    /**
     * Once a user changes connection type, app restart is required so that all network dependencies would be updated
     * with new proxy settings. If connection type was set to ConnectionType.ONION, after app rebirth TorService
     * would be started in MainActivity.
     */
    private fun restartApp(connectionType: ConnectionType) {
        Timber.d("restart app")
        if (connectionType != ConnectionType.ONION && torService.isProxyRunning) {
            torService.switchTorPrefState(false)
        }
        lightweightSettingsStorage.setConnectionType(connectionType)
        Timber.d("switch connection type: ${lightweightSettingsStorage.getConnectionType()}")
        displayLoadingView()

        startActivity(
                Intent.makeRestartActivityTask(
                        requireContext().packageManager.getLaunchIntentForPackage(
                                requireContext().packageName
                        )!!.component
                )!!
        )
        Runtime.getRuntime().exit(0)
    }

    private fun displayLoadingView() {
        if (null == mLoadingView) {
            var parent = view

            while (parent != null && mLoadingView == null) {
                mLoadingView = parent.findViewById(R.id.vector_settings_spinner_views)
                parent = parent.parent as View
            }
        } else {
            mLoadingView?.visibility = View.VISIBLE
        }
    }

    private fun hideLoadingView() {
        mLoadingView?.visibility = View.GONE
    }
}
