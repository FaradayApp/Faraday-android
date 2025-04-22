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

package im.vector.app.features.settings.passwordmanagement.enterpassword

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import com.airbnb.mvrx.args
import com.airbnb.mvrx.fragmentViewModel
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.databinding.FragmentPasswordEnterBinding
import im.vector.app.features.MainActivity
import im.vector.app.features.MainActivityArgs
import im.vector.app.features.home.HomeActivity
import im.vector.app.features.settings.VectorSettingsActivity
import im.vector.app.features.settings.passwordmanagement.passwordmanagementmain.VectorSettingsPasswordManagementFragment
import im.vector.app.features.settings.passwordmanagement.setpassword.PasswordErrorLocation
import im.vector.lib.strings.CommonStrings

class EnterPasswordFragment :
        VectorBaseFragment<FragmentPasswordEnterBinding>() {

    private val viewModel: EnterPasswordViewModel by fragmentViewModel()

    private val enterPasswordScreenArgs: EnterPasswordScreenArgs by args()

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPasswordEnterBinding {
        return FragmentPasswordEnterBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpViews()
        observeViewEvents()
        viewModel.handle(EnterPasswordAction.RestoreState)
    }

    private fun setUpViews() {
        views.buttonNext.text = when(enterPasswordScreenArgs.type) {
            EnterPasswordScreenType.HOME -> getString(CommonStrings.enter)
            EnterPasswordScreenType.SETTINGS -> getString(CommonStrings.action_next)
        }
        views.buttonNext.setOnClickListener {
            viewModel.handle(EnterPasswordAction.OnClickNext)
        }
        views.changePasswordOldPwdText.doOnTextChanged { text, _, _, _ ->
            viewModel.handle(EnterPasswordAction.OnChangePassword(password = text.toString()))
            views.changePasswordOldPwdTil.error = null
        }
    }

    private fun observeViewEvents() {
        viewModel.observeViewEvents { event ->
            when(event) {
                is EnterPasswordViewEvents.OnRestoreState -> {
                    views.changePasswordOldPwdText.setText(event.password)
                    event.error.takeIf { it.isNotEmpty() }?.let {
                        views.changePasswordOldPwdText.error = it
                    }
                }
                is EnterPasswordViewEvents.OnNavigateToPasswordManagement -> {
                    when(enterPasswordScreenArgs.type) {
                        EnterPasswordScreenType.HOME -> (activity as? HomeActivity)?.handleApplicationPasswordEntered()
                        EnterPasswordScreenType.SETTINGS -> (activity as? VectorSettingsActivity)?.navigateTo(VectorSettingsPasswordManagementFragment::class.java)
                    }
                }
                is EnterPasswordViewEvents.ShowError -> {
                    when (event.location) {
                        PasswordErrorLocation.PASSWORD -> {
                            views.changePasswordOldPwdTil.error = event.message
                        }
                        PasswordErrorLocation.REPEAT_PASSWORD -> {}
                        PasswordErrorLocation.GENERAL -> {
                            Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is EnterPasswordViewEvents.OnNukePasswordEntered -> {
                    MainActivity.restartApp(requireActivity(), MainActivityArgs(
                            clearCache = true,
                            clearCredentials = true,
                            isUserLoggedOut = true))
                }
            }
        }
    }

    companion object{
        val TAG = EnterPasswordFragment::class.simpleName
    }
}
