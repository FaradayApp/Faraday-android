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

import android.app.Activity
import android.text.Editable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import im.vector.app.R
import im.vector.app.core.platform.SimpleTextWatcher
import im.vector.app.core.utils.isValidUrl
import im.vector.app.databinding.DialogKanbanBoardBinding
import im.vector.lib.strings.CommonStrings

/**
 * Dialog to prompt user to enter kanban board url.
 */

class KanbanBoardDialog {

    fun show(activity: Activity, kanbanBoardDialogListener: KanbanBoardDialogListener) {
        val dialogLayout = activity.layoutInflater.inflate(R.layout.dialog_kanban_board, null)
        val views = DialogKanbanBoardBinding.bind(dialogLayout)
        val builder = MaterialAlertDialogBuilder(activity)
                .setView(dialogLayout)

        val textWatcher = object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                views.kanbanBoardDialogTil.error = null
                when {
                    views.kanbanBoardDialogEt.text.isNullOrEmpty() -> {
                        views.kanbanBoardDialogSaveButton.isEnabled = false
                    }
                    else -> {
                        views.kanbanBoardDialogSaveButton.isEnabled = true
                    }
                }
            }
        }

        views.kanbanBoardDialogEt.addTextChangedListener(textWatcher)

        val exportDialog = builder.show()

        views.kanbanBoardDialogSaveButton.setOnClickListener {
            if(!views.kanbanBoardDialogEt.text.toString().isValidUrl()) {
                views.kanbanBoardDialogTil.error = activity.getString(CommonStrings.login_error_invalid_home_server)
                return@setOnClickListener
            }
            kanbanBoardDialogListener.onKanbanBoardAdded(views.kanbanBoardDialogEt.text.toString())
            exportDialog.dismiss()
        }
    }

    interface KanbanBoardDialogListener {
        fun onKanbanBoardAdded(url: String)
    }
}
