/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import im.vector.app.core.di.UserId
import im.vector.app.features.home.ChangeAccountUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import org.matrix.android.sdk.internal.auth.db.LocalAccountStore
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
        @UserId private val userId: String,
        private val changeAccountUseCase: ChangeAccountUseCase,
        private val localAccountStore: LocalAccountStore,
        private val accountsLoader: AccountsLoader
) : ViewModel() {
    private val _uiState = MutableStateFlow<AccountsUiState>(AccountsUiState.Loading)
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<AccountsUiEvent>(replay = 0)
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            accountsLoader.observeAccounts(userId)
                    .catch {
                        processAccountsLoadingFail()
                    }
                    .collect { result ->
                        result.fold(
                                onSuccess = {
                                    accounts -> _uiState.update { AccountsUiState.Content(accounts) }
                                },
                                onFailure = {
                                    processAccountsLoadingFail()
                                }
                        )
                    }
        }
    }

    private suspend fun processAccountsLoadingFail() {
        _uiEvents.emit(AccountsUiEvent.Error.FailedAccountsLoading)
        _uiState.compareAndSet(AccountsUiState.Loading, AccountsUiState.Content())
    }

    fun onChangeAccount(account: AccountItem) {
        viewModelScope.launch {
            _uiState.update { AccountsUiState.Loading }

            try {
                withContext(Dispatchers.IO) {
                    changeAccountUseCase.execute(account.userId)
                }
                _uiEvents.emit(AccountsUiEvent.RestartApp)
            } catch (throwable: Throwable) {
                Timber.i("Error re-login into app $throwable")
                if (throwable is Failure.ServerError) {
                    _uiEvents.emit(AccountsUiEvent.Error.ErrorMessage(throwable.error.message))
                }
                _uiEvents.emit(AccountsUiEvent.Error.FailedChangingAccount(account))
            }
        }
    }

    fun onDeleteAccount(account: AccountItem) = viewModelScope.launch {
        localAccountStore.deleteAccount(account.userId)
    }
}
