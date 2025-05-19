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

package im.vector.app.features.settings.passwordmanagement.passwordmanagementmain


import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import com.airbnb.mvrx.fragmentViewModel
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.dialogs.NukePasswordDialog
import im.vector.app.core.platform.OnBackPressed
import im.vector.app.core.preference.VectorPreference
import im.vector.app.core.preference.VectorSwitchPreference
import im.vector.app.features.settings.VectorPreferences.Companion.SETTINGS_NUKE_PASSWORD_PREFERENCE_KEY
import im.vector.app.features.settings.VectorPreferences.Companion.SETTINGS_PASSWORD_CHANGE_PREFERENCE_KEY
import im.vector.app.features.settings.VectorPreferences.Companion.SETTINGS_PASSWORD_PREFERENCE_KEY
import im.vector.app.features.settings.VectorSettingsActivity
import im.vector.app.features.settings.VectorSettingsBaseFragment
import im.vector.app.features.settings.passwordmanagement.changepassword.VectorSettingsChangePasswordFragment
import im.vector.app.features.settings.passwordmanagement.enterpassword.EnterPasswordFragment
import im.vector.app.features.settings.passwordmanagement.setpassword.VectorSettingsSetPasswordFragment
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import javax.inject.Inject

@AndroidEntryPoint
class VectorSettingsPasswordManagementFragment :
        VectorSettingsBaseFragment(), OnBackPressed {

    @Inject lateinit var lightweightSettingsStorage: LightweightSettingsStorage

    private val viewModel: VectorSettingsPasswordManagementViewModel by fragmentViewModel()

    override var titleRes = CommonStrings.settings_password

    override val preferenceXmlRes = R.xml.vector_password_management

    private val passwordPreference by lazy {
        findPreference<VectorSwitchPreference>(SETTINGS_PASSWORD_PREFERENCE_KEY)
    }

    private val changePasswordPreference by lazy {
        findPreference<VectorPreference>(SETTINGS_PASSWORD_CHANGE_PREFERENCE_KEY)
    }

    private val nukePasswordPreference by lazy {
        findPreference<VectorPreference>(SETTINGS_NUKE_PASSWORD_PREFERENCE_KEY)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewEvents()
        bindPref()
    }

    override fun bindPref() {
        passwordPreference?.let { pref ->
            pref.isChecked = lightweightSettingsStorage.isApplicationPasswordSet()
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                when (passwordPreference?.isChecked == true) {
                    true -> goToSetPasswordScreen()
                    false -> viewModel.handle(VectorSettingsPasswordManagementAction.DeletePassword)
                }
                true
            }
        }
        changePasswordPreference?.apply {
            isIconFrameHidden = true
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                goToChangePasswordScreen()
                true
            }
            isVisible = lightweightSettingsStorage.isApplicationPasswordSet()
        }
        nukePasswordPreference?.apply {
            isIconFrameHidden = true
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showNukePassword()
                true
            }
            isVisible = lightweightSettingsStorage.isApplicationPasswordSet()
        }
    }

    private fun observeViewEvents() {
        viewModel.observeViewEvents { event ->
            when (event) {
                is VectorSettingsPasswordManagementViewEvents.ShowPasswordDialog -> {
                    activity?.let {
                        NukePasswordDialog().show(it, event.nukePassword)
                    }
                }

                is VectorSettingsPasswordManagementViewEvents.ShowError -> {
                    Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                    lightweightSettingsStorage.setApplicationPasswordEnabled(true)
                }

                is VectorSettingsPasswordManagementViewEvents.OnPasswordDeleted -> {
                    Toast.makeText(activity, "Application password disabled", Toast.LENGTH_SHORT).show()
                    lightweightSettingsStorage.setApplicationPasswordEnabled(false)
                    changePasswordPreference?.isVisible = false
                    nukePasswordPreference?.isVisible = false
                }
            }
        }
    }

    private fun showNukePassword() {
        viewModel.handle(VectorSettingsPasswordManagementAction.OnClickNukePassword)
    }

    private fun goToChangePasswordScreen() {
        (vectorActivity as? VectorSettingsActivity)?.navigateTo(VectorSettingsChangePasswordFragment::class.java)
    }

    private fun goToSetPasswordScreen() {
        (vectorActivity as? VectorSettingsActivity)?.navigateTo(VectorSettingsSetPasswordFragment::class.java)
    }

    override fun onBackPressed(toolbarButton: Boolean): Boolean {
        try {
            val entry = requireActivity().supportFragmentManager.getBackStackEntryAt(requireActivity().supportFragmentManager.backStackEntryCount - 2)
            if (entry.name == EnterPasswordFragment.TAG) {
                requireActivity().supportFragmentManager.popBackStack(entry.name, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
