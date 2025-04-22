/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.accounts

import org.matrix.android.sdk.api.session.profile.model.AccountItem

sealed interface AccountsUiEvent {
    data object RestartApp : AccountsUiEvent
    sealed interface Error : AccountsUiEvent {
        data class ErrorMessage(val text: String) : Error
        data class FailedChangingAccount(val account: AccountItem) : Error
        data object FailedAccountsLoading : Error
    }
}
