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

package im.vector.app.core.dialogs

import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import im.vector.app.R
import im.vector.app.databinding.DialogNukePasswordBinding


/**
 * Dialog used to demonstrate nuke password fetched from server.
 */

class NukePasswordDialog {
    fun show(activity: FragmentActivity, nukePassword: String) {
        val dialogLayout = activity.layoutInflater.inflate(R.layout.dialog_nuke_password, null)
        val views = DialogNukePasswordBinding.bind(dialogLayout)
        val builder = MaterialAlertDialogBuilder(activity)
                .setView(dialogLayout)

        val exportDialog = builder.show()

        views.tvNukePassword.text = nukePassword

        views.ivClose.setOnClickListener {
            exportDialog.dismiss()
        }
    }
}
