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

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.preference.Preference
import com.airbnb.mvrx.MavericksView
import de.spiritcroc.preference.ScPreferenceFragment
import im.vector.app.R
import im.vector.app.core.preference.ButtonPreference
import im.vector.app.core.preference.RadioGroupReference
import im.vector.app.core.preference.SpinnerPreference
import im.vector.app.core.preference.TextInputPreference
import im.vector.app.core.preference.VectorPreferenceCategory
import im.vector.app.core.preference.VectorSwitchPreference
import im.vector.app.core.settings.connectionmethods.i2p.I2PEventListener
import im.vector.app.core.settings.connectionmethods.onion.TorEventListener
import im.vector.app.core.settings.connectionmethods.onion.TorService
import im.vector.app.features.settings.VectorPreferences
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.api.util.ConnectionType
import org.matrix.android.sdk.api.util.ProxyType
import javax.inject.Inject


open class ConnectionSettingsBaseFragment : ScPreferenceFragment(), MavericksView {

    protected val preferenceXmlRes = R.xml.vector_settings_connection_methods

    protected var selectedProxyType = ProxyType.NO_PROXY
    protected var proxySetupViews: MutableList<Preference> = mutableListOf()

    @Inject lateinit var lightweightSettingsStorage: LightweightSettingsStorage
    @Inject lateinit var torService: TorService
    @Inject lateinit var torEventListener: TorEventListener
    @Inject lateinit var I2PEventListener: I2PEventListener

    protected val connectionTypePreference by lazy {
        findPreference<RadioGroupReference>(VectorPreferences.SETTINGS_CONNECTION_TYPE_KEY)
    }

    protected val proxyTypePreference by lazy {
        findPreference<SpinnerPreference>(VectorPreferences.SETTINGS_PROXY_TYPE_KEY)
    }

    protected val proxyHostPreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_PROXY_HOST_KEY)
    }

    protected val proxyPortPreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_PROXY_PORT_KEY)
    }

    protected val proxyAuthRequiredPreference by lazy {
        findPreference<VectorPreferenceCategory>(VectorPreferences.SETTINGS_PROXY_AUTH_REQUIRED_KEY)
    }

    protected val proxyUsernamePreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_PROXY_USERNAME_KEY)
    }

    protected val proxyPasswordPreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_PROXY_PASSWORD_KEY)
    }

    protected val savePreference by lazy {
        findPreference<ButtonPreference>(VectorPreferences.SETTINGS_CONNECTION_SAVE_KEY)
    }

    protected val switchUseProxyPreference by lazy {
        findPreference<VectorSwitchPreference>(VectorPreferences.SETTINGS_USE_PROXY_SERVER_KEY)
    }

    protected val torBridgePreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_TOR_BRIDGE_KEY)
    }

    @CallSuper
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(preferenceXmlRes)
        bindPref()
    }

    override fun setDivider(divider: Drawable?) {
        super.setDivider(ColorDrawable(Color.TRANSPARENT))
    }

    override fun setDividerHeight(height: Int) {
        super.setDividerHeight(0)
    }

    override fun invalidate() {}

    protected open fun bindPref() {
        connectionTypePreference?.let { pref ->
            val initialType = lightweightSettingsStorage.getConnectionType()
            pref.setType(initialType)
            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                newValue as ConnectionType
                when (newValue) {
                    ConnectionType.MATRIX -> {
                        switchUseProxyPreference?.isVisible = true
                    }
                    ConnectionType.ONION, ConnectionType.I2P -> {
                        switchUseProxyPreference?.isChecked = false
                        toggleProxyFieldsVisibility(isVisible = false)
                        torBridgePreference?.isVisible = true
                    }
                }
                if (newValue != ConnectionType.ONION)
                    torBridgePreference?.isVisible = false
                if (newValue != ConnectionType.MATRIX)
                    switchUseProxyPreference?.isVisible = false
                true
            }
        }
        proxyTypePreference?.let { pref ->
            selectedProxyType = lightweightSettingsStorage.getProxyType()
            proxySetupViews.add(pref)
            pref.setValue(ProxyType.toString(selectedProxyType))
            pref.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedProxyType = when (position) {
                        0 -> ProxyType.NO_PROXY
                        1 -> ProxyType.HTTP
                        else -> ProxyType.SOCKS
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })
        }
        torBridgePreference?.let { pref ->
            lightweightSettingsStorage.getTorBridge()?.let { pref.text = it }
            pref.isVisible = connectionTypePreference?.type == ConnectionType.ONION
        }
        proxyHostPreference?.let { pref ->
            proxySetupViews.add(pref)
            pref.text = lightweightSettingsStorage.getProxyHost()
        }
        proxyPortPreference?.let { pref ->
            proxySetupViews.add(pref)
            pref.text = lightweightSettingsStorage.getProxyPort().toString().takeIf { it != "0" }.orEmpty()
        }
        proxyAuthRequiredPreference?.let { pref ->
            proxySetupViews.add(pref)
        }
        proxyUsernamePreference?.let { pref ->
            proxySetupViews.add(pref)
            pref.text = lightweightSettingsStorage.getProxyUsername()
        }
        proxyPasswordPreference?.let { pref ->
            proxySetupViews.add(pref)
            pref.text = lightweightSettingsStorage.getProxyPassword()
        }
        switchUseProxyPreference?.let { pref ->
            pref.isVisible = connectionTypePreference?.type == ConnectionType.MATRIX
            pref.isChecked =
                    lightweightSettingsStorage.getProxyType() != ProxyType.NO_PROXY && lightweightSettingsStorage.getConnectionType() == ConnectionType.MATRIX
            toggleProxyFieldsVisibility(pref.isChecked)

            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (connectionTypePreference?.type != ConnectionType.MATRIX) {
                    Toast.makeText(context, getString(CommonStrings.proxy_only_for_matrix), Toast.LENGTH_SHORT).show()
                    pref.isChecked = false
                    return@OnPreferenceClickListener false
                }
                toggleProxyFieldsVisibility(pref.isChecked)
                true
            }
        }

    }

    protected open fun observeTorEvents() {}

    protected fun toggleProxyFieldsVisibility(isVisible: Boolean) {
        proxySetupViews.forEach {
            it.isVisible = isVisible
        }
    }

    protected fun proxyFieldsAreValid(): Boolean {

        var allFieldsAreValid = true

        if (proxyHostPreference?.editTextView?.text?.matches(Regex(PROXY_HOST_REGEX_PATTERN)) == false) {
            proxyHostPreference?.editTextView?.error = getString(CommonStrings.error_in_host_address)
            allFieldsAreValid = false
        }
        if (proxyPortPreference?.editTextView?.text?.matches(Regex(PROXY_PORT_REGEX_PATTERN)) == false) {
            proxyPortPreference?.editTextView?.error = getString(CommonStrings.error_in_port_number)
            allFieldsAreValid = false
        }
        if (proxyUsernamePreference?.editTextView?.text?.isNotEmpty() == true && proxyPasswordPreference?.editTextView?.text?.isEmpty() == true) {
            proxyPasswordPreference?.editTextView?.error = getString(CommonStrings.error_in_proxy_provide_password)
            allFieldsAreValid = false
        }
        if (proxyUsernamePreference?.editTextView?.text?.isEmpty() == true && proxyPasswordPreference?.editTextView?.text?.isNotEmpty() == true) {
            proxyUsernamePreference?.editTextView?.error = getString(CommonStrings.error_in_proxy_provide_username)
            allFieldsAreValid = false
        }
        if (selectedProxyType == ProxyType.NO_PROXY) {
            Toast.makeText(context, getString(CommonStrings.error_in_proxy_provide_type), Toast.LENGTH_SHORT).show()
            allFieldsAreValid = false
        }

        if (allFieldsAreValid) {
            proxyPortPreference?.editTextView?.text?.toString()?.toInt()?.let {
                lightweightSettingsStorage.setProxyPort(it)
            }
            proxyHostPreference?.editTextView?.text?.toString()?.let {
                lightweightSettingsStorage.setProxyHost(it)
            }
            proxyUsernamePreference?.editTextView?.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
                lightweightSettingsStorage.setProxyUsername(it)
            }
            proxyPasswordPreference?.editTextView?.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
                lightweightSettingsStorage.setProxyPassword(it)
            }
            lightweightSettingsStorage.setProxyType(selectedProxyType)
        }
        return allFieldsAreValid
    }

    protected fun disableProxy() {
        lightweightSettingsStorage.setProxyType(ProxyType.NO_PROXY)
        lightweightSettingsStorage.setProxyHost("")
        lightweightSettingsStorage.setProxyPort(0)
        lightweightSettingsStorage.setProxyUsername("")
        lightweightSettingsStorage.setProxyPassword("")
    }

    companion object {
        protected const val PROXY_HOST_REGEX_PATTERN = "^(?:(\\w+)(?::(\\w+))?@)?((?:\\d{1,3})(?:\\.\\d{1,3}){3})(?::(\\d{1,5}))?\$"
        protected const val PROXY_PORT_REGEX_PATTERN = "^([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])\$"
    }
}
