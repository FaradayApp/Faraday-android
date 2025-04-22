/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.accounts.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import im.vector.app.features.home.accounts.AccountsUiState
import im.vector.app.features.home.avatar.Avatar
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import org.matrix.android.sdk.api.session.profile.model.toMatrixItem

@Composable
internal fun AccountsScreen(
        uiState: AccountsUiState,
        onAccountSelected: (AccountItem) -> Unit,
) {
    when(uiState) {
        is AccountsUiState.Content -> {
            if (uiState.accounts.isNotEmpty()) {
                AccountsList(
                        accountItems = uiState.accounts,
                        onAccountSelected = onAccountSelected
                )
            }
        }
        AccountsUiState.Loading -> Loading()
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(
            modifier.fillMaxWidth()
                    .fillMaxHeight(0.3f),
            contentAlignment = Alignment.Center
    ) {
        // TODO: Provide themes
        CircularProgressIndicator(color = Color(0xFF0ABAB5))
    }
}

@Composable
private fun AccountsList(
        accountItems: List<AccountItem>,
        onAccountSelected: (AccountItem) -> Unit,
) {
    LazyColumn(
            Modifier.fillMaxWidth()
                    .fillMaxHeight(0.3f)
    ) {
        items(accountItems) { account ->
            AccountItem(
                    account = account,
                    modifier = Modifier
                            .fillMaxSize()
                            .clickable { onAccountSelected(account) }
            )
        }
    }
}

@Composable
private fun AccountItem(
        account: AccountItem,
        modifier: Modifier = Modifier,
) {
    Row(
            modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(30.dp)) {
            Avatar(account.toMatrixItem(), modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.bodyLarge
            )
            Text(
                    text = account.userId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (account.unreadCount > 0) {
            Badge(contentColor = Color.White, containerColor = Color(0xFF0ABAB5)) {
                Text(account.unreadCount.toString())
            }
        }
    }
}
