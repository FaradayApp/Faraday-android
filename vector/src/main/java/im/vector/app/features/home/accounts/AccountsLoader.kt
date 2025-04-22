/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.accounts

import androidx.lifecycle.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.session.profile.ProfileService
import org.matrix.android.sdk.api.session.profile.model.AccountItem
import javax.inject.Inject

class AccountsLoader @Inject constructor(
        private val profileService: ProfileService
) {
    fun observeAccounts(userId: String): Flow<Result<List<AccountItem>>> =
            profileService.getAccounts().asFlow()
                    .distinctUntilChanged()
                    .map { localAccounts -> localAccounts.filter { it.userId != userId } }
                    .map { filteredAccounts ->
                        Result.runCatching {
                            filteredAccounts.map { account ->
                                withContext(Dispatchers.IO) {
                                    try {
                                        val data = profileService.getProfile(
                                                account.userId,
                                                account.homeServerUrl,
                                                storeInDatabase = false,
                                                account.token
                                        )
                                        AccountItem(
                                                userId = account.userId,
                                                displayName = data.get(ProfileService.DISPLAY_NAME_KEY) as? String ?: "",
                                                avatarUrl = data.get(ProfileService.AVATAR_URL_KEY) as? String,
                                                unreadCount = account.unreadCount
                                        )
                                    } catch (e: Throwable) {
                                        AccountItem(
                                                userId = account.userId,
                                                displayName = account.userId.removePrefix("@").split(':')[0],
                                                avatarUrl = null,
                                                unreadCount = account.unreadCount
                                        )
                                    }
                                }
                            }
                        }
                    }
}
