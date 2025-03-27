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

package im.vector.app.features.home.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.ui.Modifier
import androidx.core.view.isVisible
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.core.extensions.cleanup
import im.vector.app.core.extensions.configureWith
import im.vector.app.core.platform.StateView
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.utils.toast
import im.vector.app.databinding.FragmentAccountsListBinding
import im.vector.app.features.home.HomeActivity
import im.vector.app.features.home.HomeDrawerFragment
import im.vector.app.features.home.ShortcutsHandler
import im.vector.app.features.login.ReAuthHelper
import im.vector.app.features.onboarding.AuthenticationDescription
import im.vector.app.features.workers.changeaccount.ChangeAccountErrorUiWorker
import im.vector.app.features.workers.changeaccount.ChangeAccountUiWorker
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import javax.inject.Inject

/**
 * Fragment handles multiple account feature.
 */
@AndroidEntryPoint
class AccountsFragment :
        VectorBaseFragment<FragmentAccountsListBinding>(), AccountsController.Callback {

    @Inject lateinit var shortcutsHandler: ShortcutsHandler
    @Inject lateinit var accountsController: AccountsController
    @Inject lateinit var reAuthHelper: ReAuthHelper

    private val viewModel: AccountsViewModel by fragmentViewModel()
    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAccountsListBinding {
        return FragmentAccountsListBinding.inflate(inflater, container, false)//.apply {
//            groupListView.setContent {
//                LazyColumn(Modifier.fillMaxSize()) {
//
//                }
//            }
//        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        views.stateView.contentView = views.groupListView
        setupAccountsController()
        observeViewEvents()
        observeViewState()
    }

    private fun observeViewState() {
        viewModel.onEach { state ->
            if (state.restartApp) {
                viewModel.handle(AccountsAction.SetRestartAppValue(false))

                val context = requireContext()
                var authDescription: AuthenticationDescription? = null
                if (reAuthHelper.data != null) {
                    authDescription = AuthenticationDescription.Register(type = AuthenticationDescription.AuthenticationType.Other)
                }
                val intent = HomeActivity.newIntent(context, firstStartMainActivity = false, authenticationDescription = authDescription)
                startActivity(intent)
                activity?.finish()
            }
            if (state.invalidAccount != null) {
                ChangeAccountErrorUiWorker(
                        requireActivity(),
                        accountItem = state.invalidAccount,
                        onPositiveActionClicked = {
                            viewModel.handle(AccountsAction.DeleteAccount(it))
                            views.stateView.state = StateView.State.Content
                        }
                ).perform()
                viewModel.handle(AccountsAction.SetErrorWhileAccountChange(null))
            }
            state.errorMessage?.let { message ->
                activity?.toast(message)
                viewModel.handle(AccountsAction.SetErrorMessage(null))
            }
            if (state.accountItems != null) {
                views.stateView.state = StateView.State.Content
                views.groupListView.isVisible = state.accountItems.isNotEmpty()
//                (parentFragment as? HomeDrawerFragment)
//                        ?.updateAddAccountButtonVisibility(isVisible = state.accountItems.size < 4)
            } else {
                views.stateView.state = StateView.State.Loading
                return@onEach
            }
            accountsController.update(state)
        }
    }

    private fun setupAccountsController() {
        accountsController.callback = this
        views.groupListView.configureWith(accountsController)
    }

    private fun observeViewEvents() = viewModel.observeViewEvents {
        when (it) {
            is AccountsViewEvents.SelectAccount -> {
                viewModel.handle(AccountsAction.SelectAccount(it.account))
            }
        }
    }

    /**
     * In case a user changes account, app restart is required to update dependencies with new Credentials.
     */
    override fun invalidate() = withState(viewModel) { state ->
        if (state.restartApp) {
            viewModel.handle(AccountsAction.SetRestartAppValue(false))

            val context = requireContext()
            var authDescription: AuthenticationDescription? = null
            if (reAuthHelper.data != null) {
                authDescription = AuthenticationDescription.Register(type = AuthenticationDescription.AuthenticationType.Other)
            }
            val intent = HomeActivity.newIntent(context, firstStartMainActivity = false, authenticationDescription = authDescription)
            startActivity(intent)
            activity?.finish()
        }
        if (state.invalidAccount != null) {
            ChangeAccountErrorUiWorker(
                    requireActivity(),
                    accountItem = state.invalidAccount,
                    onPositiveActionClicked = {
                        viewModel.handle(AccountsAction.DeleteAccount(it))
                        views.stateView.state = StateView.State.Content
                    }
            ).perform()
            viewModel.handle(AccountsAction.SetErrorWhileAccountChange(null))
        }
        state.errorMessage?.let { message ->
            activity?.toast(message)
            viewModel.handle(AccountsAction.SetErrorMessage(null))
        }
        if (state.accountItems != null) {
            views.stateView.state = StateView.State.Content
            views.groupListView.isVisible = state.accountItems.isNotEmpty()
//            (parentFragment as? HomeDrawerFragment)
//                    ?.updateAddAccountButtonVisibility(isVisible = state.accountItems.size < 4)
        } else {
            views.stateView.state = StateView.State.Loading
            return@withState
        }
        accountsController.update(state)
    }

    override fun onAccountSelected(account: AccountItem) {
        ChangeAccountUiWorker(
                requireActivity(),
                accountItem = account,
                onPositiveActionClicked = {
                    viewModel.handle(AccountsAction.SelectAccount(account))
                    views.stateView.state = StateView.State.Loading
                }
        ).perform()
    }

    override fun onDestroyView() {
        accountsController.callback = null
        views.groupListView.cleanup()
        super.onDestroyView()
    }
}
