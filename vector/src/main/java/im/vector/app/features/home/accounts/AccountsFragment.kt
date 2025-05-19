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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.core.dependencies.LocalContentUrlResolver
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.platform.StateView
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.resources.LocalStringProvider
import im.vector.app.core.resources.StringProvider
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.core.utils.LocalDimensionConverter
import im.vector.app.core.utils.toast
import im.vector.app.databinding.FragmentAccountsListBinding
import im.vector.app.features.home.HomeActivity
import im.vector.app.features.home.ShortcutsHandler
import im.vector.app.features.home.accounts.compose.AccountsScreen
import im.vector.app.features.home.room.detail.timeline.helper.LocalMatrixItemColorProvider
import im.vector.app.features.home.room.detail.timeline.helper.MatrixItemColorProvider
import im.vector.app.features.login.ReAuthHelper
import im.vector.app.features.onboarding.AuthenticationDescription
import im.vector.app.features.themes.compose.FaradayTheme
import im.vector.app.features.workers.changeaccount.ChangeAccountErrorUiWorker
import im.vector.app.features.workers.changeaccount.ChangeAccountUiWorker
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import javax.inject.Inject

/**
 * Fragment handles multiple account feature.
 */
@AndroidEntryPoint
class AccountsFragment :
        VectorBaseFragment<FragmentAccountsListBinding>() {

    @Inject lateinit var shortcutsHandler: ShortcutsHandler
    @Inject lateinit var reAuthHelper: ReAuthHelper

    @Inject lateinit var stringProvider: StringProvider
    @Inject lateinit var activeSessionHolder: ActiveSessionHolder
    @Inject lateinit var matrixItemColorProvider: MatrixItemColorProvider
    @Inject lateinit var dimensionConverter: DimensionConverter

    private val viewModel: AccountsViewModel by viewModels()

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAccountsListBinding {
        return FragmentAccountsListBinding.inflate(inflater, container, false).apply {
            groupListView.setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle)
            )
            groupListView.setContent {
                FaradayTheme {
                    CompositionLocalProvider(
                            LocalStringProvider provides stringProvider,
                            LocalContentUrlResolver provides activeSessionHolder.getSafeActiveSession()?.contentUrlResolver(),
                            LocalMatrixItemColorProvider provides matrixItemColorProvider,
                            LocalDimensionConverter provides dimensionConverter,
                    ) {
                        val uiState by viewModel.uiState.collectAsState()
                        val uiEvents = remember { viewModel.uiEvents }

                        LaunchedEffect(uiState) {
                            if (uiState is AccountsUiState.Content) {
                                val isEmpty = (uiState as AccountsUiState.Content).accounts.isEmpty()
                                showAccounts(!isEmpty)
                            }
                        }

                        val lifecycleOwner = LocalLifecycleOwner.current
                        LaunchedEffect(uiEvents) {
                            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.uiEvents.collect { event ->
                                    when (event) {
                                        AccountsUiEvent.RestartApp -> restartApp()
                                        is AccountsUiEvent.Error.ErrorMessage -> onErrorMessage(event.text)
                                        is AccountsUiEvent.Error.FailedChangingAccount -> askToDeleteAccount(
                                                event.account, viewModel::onDeleteAccount
                                        )

                                        AccountsUiEvent.Error.FailedAccountsLoading ->
                                            onErrorMessage(getString(CommonStrings.failed_accounts_loading))
                                    }
                                }
                            }
                        }

                        AccountsScreen(
                                uiState = uiState,
                                onAccountSelected = { account ->
                                    askToChangeAccount(account, viewModel::onChangeAccount)
                                },
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        views.stateView.contentView = views.groupListView
    }

    private fun showAccounts(show: Boolean) {
        views.stateView.state = StateView.State.Content
        views.groupListView.isVisible = show
    }

    private fun onErrorMessage(message: String) {
        activity?.toast(message)
    }

    private fun askToDeleteAccount(account: AccountItem, onDeleteAccount: (AccountItem) -> Unit) {
        ChangeAccountErrorUiWorker(
                requireActivity(),
                accountItem = account,
                onPositiveActionClicked = onDeleteAccount

        ).perform()
    }

    private fun restartApp() {
        val context = requireContext()
        var authDescription: AuthenticationDescription? = null
        if (reAuthHelper.data != null) {
            authDescription = AuthenticationDescription.Register(type = AuthenticationDescription.AuthenticationType.Other)
        }
        val intent = HomeActivity.newIntent(context, firstStartMainActivity = false, authenticationDescription = authDescription)
        startActivity(intent)
        activity?.finish()
    }

    private fun askToChangeAccount(account: AccountItem, onChangeAccount: (AccountItem) -> Unit) {
        ChangeAccountUiWorker(
                requireActivity(),
                accountItem = account,
                onPositiveActionClicked = onChangeAccount
        ).perform()
    }
}
