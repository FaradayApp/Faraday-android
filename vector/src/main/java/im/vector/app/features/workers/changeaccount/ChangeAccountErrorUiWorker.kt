/*
 * Copyright (c) 2024 New Vector Ltd
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

package im.vector.app.features.workers.changeaccount

import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.profile.model.AccountItem

class ChangeAccountErrorUiWorker(
        private val activity: FragmentActivity,
        private val onPositiveActionClicked: (AccountItem) -> Unit,
        private val accountItem: AccountItem
) {

    fun perform() {
        MaterialAlertDialogBuilder(activity)
                .setTitle(CommonStrings.action_сhange_error_title)
                .setMessage(CommonStrings.action_сhange_error_message)
                .setPositiveButton(CommonStrings.action_сhange_error_delete) { _, _ ->
                    onPositiveActionClicked(accountItem)
                }
                .setNegativeButton(CommonStrings.action_cancel, null)
                .show()
    }
}
