/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.app.features.home

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.extensions.cannotLogoutSafely
import im.vector.app.core.extensions.hideKeyboard
import im.vector.app.core.extensions.hidePassword
import im.vector.app.core.extensions.observeK
import im.vector.app.core.extensions.replaceChildFragment
import im.vector.app.core.platform.SimpleTextWatcher
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.resources.BuildMeta
import im.vector.app.core.session.ConfigureAndStartSessionUseCase
import im.vector.app.core.utils.ensureProtocol
import im.vector.app.core.utils.startSharePlainTextIntent
import im.vector.app.core.utils.toast
import im.vector.app.databinding.DialogAddAccountBinding
import im.vector.app.databinding.FragmentHomeDrawerBinding
import im.vector.app.features.MainActivity
import im.vector.app.features.MainActivityArgs
import im.vector.app.features.analytics.plan.MobileScreen
import im.vector.app.features.home.accounts.AccountsFragment
import im.vector.app.features.login.HomeServerConnectionConfigFactory
import im.vector.app.features.login.PromptSimplifiedModeActivity
import im.vector.app.features.login.ReAuthHelper
import im.vector.app.features.permalink.PermalinkFactory
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.settings.VectorSettingsActivity
import im.vector.app.features.spaces.SpaceListFragment
import im.vector.app.features.usercode.UserCodeActivity
import im.vector.app.features.workers.signout.SignOutBottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject

@AndroidEntryPoint
class HomeDrawerFragment :
        VectorBaseFragment<FragmentHomeDrawerBinding>() {

    @Inject lateinit var session: Session
    @Inject lateinit var vectorPreferences: VectorPreferences
    @Inject lateinit var avatarRenderer: AvatarRenderer
    @Inject lateinit var buildMeta: BuildMeta
    @Inject lateinit var permalinkFactory: PermalinkFactory
    @Inject lateinit var lightweightSettingsStorage: LightweightSettingsStorage
    @Inject lateinit var homeServerConnectionConfigFactory: HomeServerConnectionConfigFactory
    @Inject lateinit var authenticationService: AuthenticationService
    @Inject lateinit var activeSessionHolder: ActiveSessionHolder
    @Inject lateinit var configureAndStartSessionUseCase: ConfigureAndStartSessionUseCase
    @Inject lateinit var shortcutsHandler: ShortcutsHandler
    @Inject lateinit var reAuthHelper: ReAuthHelper

    private lateinit var sharedActionViewModel: HomeSharedActionViewModel

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeDrawerBinding {
        return FragmentHomeDrawerBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedActionViewModel = activityViewModelProvider.get(HomeSharedActionViewModel::class.java)

        if (savedInstanceState == null) {
            replaceChildFragment(R.id.homeDrawerGroupListContainer, SpaceListFragment::class.java)
        }
        views.homeDrawerAddAccountButton.isVisible = lightweightSettingsStorage.areCustomSettingsEnabled()
        session.userService().getUserLive(session.myUserId).observeK(viewLifecycleOwner) { optionalUser ->
            val user = optionalUser?.getOrNull()
            if (user != null) {
                avatarRenderer.render(user.toMatrixItem(), views.homeDrawerHeaderAvatarView)
                views.homeDrawerUsernameView.text = user.displayName
                views.homeDrawerUserIdView.text = user.userId
                if (savedInstanceState == null && lightweightSettingsStorage.areCustomSettingsEnabled()) {
                    replaceChildFragment(
                            frameId = R.id.homeDrawerAccountsListContainer,
                            fragmentClass = AccountsFragment::class.java,
                            tag = ACCOUNTS_FRAGMENT_TAG
                    )
                }

                sharedActionViewModel.post(HomeActivitySharedAction.AccountLoaded)
            }
        }
        // Profile
        views.homeDrawerHeader.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
            navigator.openSettings(requireActivity(), directAccess = VectorSettingsActivity.EXTRA_DIRECT_ACCESS_GENERAL)
        }
        // Settings
        views.homeDrawerHeaderSettingsView.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
            navigator.openSettings(requireActivity())
        }
        // Sign out
        views.homeDrawerHeaderSignoutView.debouncedClicks {
            onSignOutClicked()
        }
        // Add account
        views.homeDrawerAddAccountButton.debouncedClicks {
            onAddAccountClicked()
        }

        views.homeDrawerQRCodeButton.debouncedClicks {
            UserCodeActivity.newIntent(requireContext(), sharedActionViewModel.session.myUserId).let {
                val options =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                requireActivity(),
                                views.homeDrawerHeaderAvatarView,
                                ViewCompat.getTransitionName(views.homeDrawerHeaderAvatarView) ?: ""
                        )
                startActivity(it, options.toBundle())
            }
        }

        views.homeDrawerInviteFriendButton.debouncedClicks {
            permalinkFactory.createPermalinkOfCurrentUser()?.let { permalink ->
                analyticsTracker.screen(MobileScreen(screenName = MobileScreen.ScreenName.InviteFriends))
                val text = getString(R.string.invite_friends_text, permalink)

                startSharePlainTextIntent(
                        context = requireContext(),
                        activityResultLauncher = null,
                        chooserTitle = getString(R.string.invite_friends),
                        text = text,
                        extraTitle = getString(R.string.invite_friends_rich_title)
                )
            }
        }

        // Debug menu
        views.homeDrawerHeaderDebugView.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
            navigator.openDebug(requireActivity())
        }
    }

    private fun multiAccountSignOut() {
        sharedActionViewModel.viewModelScope.launch {
            val result = runCatching {
                val profileService = session.profileService()
                val userId = session.myUserId
                authenticationService.getLocalAccountStore().deleteAccount(userId)
                val accounts = profileService.getMultipleAccount(userId)

                var accountChanged = false
                accounts.forEach { account ->
                    try {
                        session.close()
                        reAuthHelper.data = null
                        val result = profileService.reLoginMultiAccount(
                                account.userId, authenticationService.getSessionCreator()
                        ) { reAuthHelper.data = it.password }
                        activeSessionHolder.setActiveSession(result)
                        authenticationService.reset()
                        configureAndStartSessionUseCase.execute(result)

                        accountChanged = true
                        return@forEach
                    } catch (_: Throwable) {
                    }
                }
                accountChanged
            }.getOrDefault(false)

            MainActivity.restartApp(requireActivity(), MainActivityArgs(clearCredentials = !result))
        }
    }

    private fun onSignOutClicked() {
        sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
        if (session.cannotLogoutSafely()) {
            // The backup check on logout flow has to be displayed if there are keys in the store, and the keys backup state is not Ready
            val signOutDialog = SignOutBottomSheetDialogFragment.newInstance()
            signOutDialog.onSignOut = Runnable {
                showLoadingDialog("Try to connect to another account. Please wait...")
                lightweightSettingsStorage.setApplicationPasswordEnabled(false)
                shortcutsHandler.clearShortcuts()
                multiAccountSignOut()
            }
            signOutDialog.show(requireActivity().supportFragmentManager, "SO")
        } else {
            // Display a simple confirmation dialog
            MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.action_sign_out)
                    .setMessage(R.string.action_sign_out_confirmation_simple)
                    .setPositiveButton(R.string.action_sign_out) { _, _ ->
                        showLoadingDialog("Try to connect to another account. Please wait...")
                        lightweightSettingsStorage.setApplicationPasswordEnabled(false)
                        shortcutsHandler.clearShortcuts()
                        multiAccountSignOut()
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SC: settings migration
        vectorPreferences.scPreferenceUpdate()
        // SC-Easy mode prompt
        PromptSimplifiedModeActivity.showIfRequired(requireContext(), vectorPreferences)
    }

    override fun onResume() {
        super.onResume()
        views.homeDrawerHeaderDebugView.isVisible = buildMeta.isDebug && vectorPreferences.developerMode()
    }

    private fun onAddAccountClicked() {
        activity?.let { activity ->
            val view: ViewGroup = activity.layoutInflater.inflate(R.layout.dialog_add_account, null) as ViewGroup
            val views = DialogAddAccountBinding.bind(view)

            val dialog = MaterialAlertDialogBuilder(activity)
                    .setView(view)
                    .setCancelable(true)
                    .setOnDismissListener {
                        view.hideKeyboard()
                    }
                    .create()

            dialog.setOnShowListener {
                val addAccountButton = views.addAccountButton
                val registerButton = views.registerButton
                val header = views.header
                val notice = views.notice
                notice.isVisible = false
                addAccountButton.isEnabled = false
                var isSignUpMode = false

                fun updateUi() {
                    val homeserverUrl = views.accountHomeserverText.text.toString()
                    val username = views.accountUsernameText.text.toString()
                    val password = views.accountPasswordText.text.toString()

                    addAccountButton.isEnabled =
                            homeserverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
                }

                views.accountHomeserverText.addTextChangedListener(object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        views.accountHomeserverTil.error = null
                        updateUi()
                    }
                })

                views.accountUsernameText.addTextChangedListener(object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        views.accountUsernameTil.error = null
                        updateUi()
                    }
                })

                views.accountPasswordText.addTextChangedListener(object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        views.accountPasswordTil.error = null
                        updateUi()
                    }
                })

                fun showPasswordLoadingView(toShow: Boolean) {
                    if (toShow) {
                        views.accountHomeserverText.isEnabled = false
                        views.accountUsernameText.isEnabled = false
                        views.accountPasswordText.isEnabled = false
                        views.changePasswordLoader.isVisible = true
                        addAccountButton.isEnabled = false
                        registerButton.isEnabled = false
                    } else {
                        views.accountHomeserverText.isEnabled = true
                        views.accountUsernameText.isEnabled = true
                        views.accountPasswordText.isEnabled = true
                        views.changePasswordLoader.isVisible = false
                        addAccountButton.isEnabled = true
                        registerButton.isEnabled = true
                    }
                }

                fun updateSignUpMode(isSignUpMode: Boolean) {
                    notice.isVisible = isSignUpMode
                    when (isSignUpMode) {
                        true -> {
                            header.text = getString(R.string.login_signup)
                            registerButton.text = getString(R.string.add_existing_account)
                            addAccountButton.text = getString(R.string.login_signup)
                        }

                        false -> {
                            header.text = getString(R.string.add_account)
                            registerButton.text = getString(R.string.register_new_account)
                            addAccountButton.text = getString(R.string.add_account)
                        }
                    }
                }

                registerButton.debouncedClicks {
                    isSignUpMode = !isSignUpMode
                    updateSignUpMode(isSignUpMode)
                }

                addAccountButton.debouncedClicks {

                    views.accountPasswordText.hidePassword()

                    view.hideKeyboard()

                    val homeserverUrl = views.accountHomeserverText.text.toString().trim().ensureProtocol()
                    val username = views.accountUsernameText.text.toString()
                    val password = views.accountPasswordText.text.toString()

                    views.accountHomeserverText.setText(homeserverUrl)

                    showPasswordLoadingView(true)

                    lifecycleScope.launch {
                        val result = runCatching {
                            when (isSignUpMode) {
                                true -> session.profileService().createAccount(
                                        username, password, getString(R.string.login_mobile_device_sc),
                                        homeServerConnectionConfigFactory.create(homeserverUrl)!!
                                )

                                false -> session.profileService().addNewAccount(
                                        username, password, homeserverUrl
                                )
                            }
                        }
                        if (!isAdded) {
                            return@launch
                        }
                        showPasswordLoadingView(false)
                        result.fold({ success ->
                            when (success) {
                                true -> {
                                    dialog.dismiss()
                                    activity.toast(R.string.account_successfully_added)
                                }

                                false -> activity.toast(R.string.error_adding_account)
                            }
                        }, { failure ->
                            val message = when (failure) {
                                is Failure.ServerError -> failure.error.message
                                else -> getString(R.string.error_adding_account)
                            }
                            activity.toast(message)
                        })
                    }
                }
            }
            dialog.show()
        }
    }

    fun updateAddAccountButtonVisibility(isVisible: Boolean) {
        views.homeDrawerAddAccountButton.isVisible = isVisible && lightweightSettingsStorage.areCustomSettingsEnabled()
    }

    companion object {
        private const val ACCOUNTS_FRAGMENT_TAG = "AccountsFragment"
    }
}
