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

package im.vector.app.features.settings.passwordmanagement.changepassword


import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import com.airbnb.mvrx.fragmentViewModel
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.platform.SimpleTextWatcher
import im.vector.app.core.preference.ButtonPreference
import im.vector.app.core.preference.TextInputPreference
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.settings.VectorSettingsBaseFragment
import im.vector.lib.strings.CommonStrings

@AndroidEntryPoint
class VectorSettingsChangePasswordFragment :
        VectorSettingsBaseFragment() {

    private val viewModel: VectorSettingsChangePasswordViewModel by fragmentViewModel()

    override val preferenceXmlRes = R.xml.vector_settings_change_password
    override var titleRes = CommonStrings.settings_reset_password

    override fun setDivider(divider: Drawable?) {
        super.setDivider(ColorDrawable(Color.TRANSPARENT))
    }

    private val oldPasswordPreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_CURRENT_PASSWORD_PREFERENCE_KEY)
    }

    private val newPasswordPreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_NEW_PASSWORD_PREFERENCE_KEY)
    }

    private val repeatPasswordPreference by lazy {
        findPreference<TextInputPreference>(VectorPreferences.SETTINGS_REPEAT_PASSWORD_PREFERENCE_KEY)
    }

    private val savePreference by lazy {
        findPreference<ButtonPreference>(SETTINGS_SAVE_KEY)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewEvents()
        viewModel.handle(VectorSettingsChangePasswordAction.OnRestoreState)
    }

    override fun bindPref() {
        savePreference?.let { pref ->
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (checkFieldsAreValid()) {
                    viewModel.handle(
                            VectorSettingsChangePasswordAction.OnSaveNewPassword
                    )
                }
                true
            }
        }
        oldPasswordPreference?.let { pref ->
            pref.passwordMode = true
            pref.setTextWatcher(type = PasswordType.OLD)
        }
        newPasswordPreference?.let { pref ->
            pref.passwordMode = true
            pref.setTextWatcher(type = PasswordType.NEW)
        }
        repeatPasswordPreference?.let { pref ->
            pref.passwordMode = true
            pref.setTextWatcher(type = PasswordType.REPEAT)
        }
    }

    private fun observeViewEvents() {
        viewModel.observeViewEvents { event ->
            when (event) {
                is VectorSettingsChangePasswordViewEvents.OnPasswordReset -> {
                    Toast.makeText(activity, CommonStrings.login_reset_password_success_notice, Toast.LENGTH_SHORT).show()
                }

                is VectorSettingsChangePasswordViewEvents.ShowError -> {
                    when (event.location) {
                        ErrorLocation.OLD_PASSWORD -> {
                            oldPasswordPreference?.textInputLayout?.error = event.message
                        }

                        ErrorLocation.GENERAL -> {
                            Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is VectorSettingsChangePasswordViewEvents.RestorePasswords -> {
                    oldPasswordPreference?.text = event.oldPassword
                    newPasswordPreference?.text = event.newPassword
                    repeatPasswordPreference?.text = event.repeatPassword
                }
            }
        }
    }

    private fun checkFieldsAreValid(): Boolean {
        var allFieldsAreValid = true
        if (oldPasswordPreference?.editTextView?.text?.isEmpty() == true) {
            oldPasswordPreference?.textInputLayout?.error = getString(CommonStrings.error_empty_field_old_password)
            allFieldsAreValid = false
        }
        if (newPasswordPreference?.editTextView?.text?.isEmpty() == true) {
            newPasswordPreference?.textInputLayout?.error = getString(CommonStrings.error_empty_field_new_password)
            allFieldsAreValid = false
        }
        if (repeatPasswordPreference?.editTextView?.text?.isEmpty() == true) {
            repeatPasswordPreference?.textInputLayout?.error = getString(CommonStrings.error_empty_field_repeat_new_password)
            allFieldsAreValid = false
        }
        if (repeatPasswordPreference?.editTextView?.text.toString() != newPasswordPreference?.editTextView?.text.toString()) {
            repeatPasswordPreference?.textInputLayout?.error = getString(CommonStrings.error_passwords_do_not_match)
            allFieldsAreValid = false
        }
        return allFieldsAreValid
    }

    private fun TextInputPreference.setTextWatcher(type: PasswordType) {
        textWatcher = object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.handle(
                        VectorSettingsChangePasswordAction.OnSetPassword(
                                password = s.toString(), type = type
                        )
                )
                textInputLayout?.error = null
            }
        }
    }

    companion object {
        private const val SETTINGS_SAVE_KEY = "SETTINGS_SAVE_KEY"
    }
}
