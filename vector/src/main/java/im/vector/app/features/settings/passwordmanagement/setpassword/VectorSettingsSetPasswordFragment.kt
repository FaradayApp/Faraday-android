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

package im.vector.app.features.settings.passwordmanagement.setpassword

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.Preference
import com.airbnb.mvrx.fragmentViewModel
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.platform.SimpleTextWatcher
import im.vector.app.core.preference.ButtonPreference
import im.vector.app.core.preference.TextInputPreference
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.settings.VectorSettingsBaseFragment
import im.vector.app.features.settings.passwordmanagement.changepassword.PasswordType
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.failure.MatrixError

@AndroidEntryPoint
class VectorSettingsSetPasswordFragment :
        VectorSettingsBaseFragment()  {

    private val viewModel: VectorSettingsSetPasswordViewModel by fragmentViewModel()

    override val preferenceXmlRes = R.xml.vector_settings_set_password
    override var titleRes = CommonStrings.settings_set_password

    override fun setDivider(divider: Drawable?) {
        super.setDivider(Color.TRANSPARENT.toDrawable())
    }

    private val passwordPreference by lazy {
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
        viewModel.handle(VectorSettingsSetPasswordAction.OnRestoreState)
    }

    override fun bindPref() {
        savePreference?.let { pref ->
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (checkFieldsAreValid()) {
                    viewModel.handle(
                            VectorSettingsSetPasswordAction.OnSavePassword
                    )
                }
                true
            }
        }
        passwordPreference?.let { pref ->
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
                is VectorSettingsSetPasswordViewEvents.OnPasswordSaved -> {
                    Toast.makeText(activity, "Password saved", Toast.LENGTH_SHORT).show()
                   // (vectorActivity as? VectorSettingsActivity)?.onBackPressed()
                }

                is VectorSettingsSetPasswordViewEvents.ShowError -> {
                    when (event.location) {
                        PasswordErrorLocation.PASSWORD -> {
                            passwordPreference?.textInputLayout?.error = when(event.message) {
                                MatrixError.M_PASSWORD_WRONG_LENGTH -> getString(CommonStrings.application_password_wrong_length)
                                MatrixError.M_PASSWORD_NO_DIGIT -> getString(CommonStrings.application_password_no_digit)
                                MatrixError.M_PASSWORD_NO_SYMBOL -> getString(CommonStrings.application_password_no_symbol)
                                MatrixError.M_PASSWORD_NO_LOWERCASE -> getString(CommonStrings.application_password_no_lowercase)
                                MatrixError.M_PASSWORD_NO_UPPERCASE -> getString(CommonStrings.application_password_no_uppercase)
                                else -> event.message
                            }
                        }

                        PasswordErrorLocation.REPEAT_PASSWORD -> {
                            repeatPasswordPreference?.textInputLayout?.error = getString(CommonStrings.settings_fail_to_update_password_invalid_current_password)
                        }

                        PasswordErrorLocation.GENERAL -> {
                            Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is VectorSettingsSetPasswordViewEvents.RestorePasswords -> {
                    passwordPreference?.text = event.password
                    repeatPasswordPreference?.text = event.repeatPassword
                }
            }
        }
    }

    private fun checkFieldsAreValid(): Boolean {
        var allFieldsAreValid = true
        if (passwordPreference?.editTextView?.text?.isEmpty() == true) {
            passwordPreference?.textInputLayout?.error = getString(CommonStrings.error_empty_field_old_password)
            allFieldsAreValid = false
        }
        if (repeatPasswordPreference?.editTextView?.text?.isEmpty() == true) {
            repeatPasswordPreference?.textInputLayout?.error = getString(CommonStrings.error_empty_field_repeat_new_password)
            allFieldsAreValid = false
        }
        if (repeatPasswordPreference?.editTextView?.text.toString() != passwordPreference?.editTextView?.text.toString()) {
            repeatPasswordPreference?.textInputLayout?.error = getString(CommonStrings.error_passwords_do_not_match)
            allFieldsAreValid = false
        }
        return allFieldsAreValid
    }

    private fun TextInputPreference.setTextWatcher(type: PasswordType) {
        textWatcher = object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.handle(
                        VectorSettingsSetPasswordAction.OnSetPassword(
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
