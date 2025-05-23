/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.poll.create

import android.view.Gravity
import android.view.inputmethod.EditorInfo
import com.airbnb.epoxy.EpoxyController
import im.vector.app.R
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.resources.StringProvider
import im.vector.app.core.ui.list.ItemStyle
import im.vector.app.core.ui.list.genericButtonItem
import im.vector.app.core.ui.list.genericItem
import im.vector.app.features.form.formEditTextItem
import im.vector.app.features.form.formEditTextWithDeleteItem
import im.vector.lib.core.utils.epoxy.charsequence.toEpoxyCharSequence
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.room.model.message.PollType
import javax.inject.Inject

class CreatePollController @Inject constructor(
        private val stringProvider: StringProvider,
        private val colorProvider: ColorProvider
) : EpoxyController() {

    private var state: CreatePollViewState? = null
    var callback: Callback? = null

    fun setData(state: CreatePollViewState) {
        this.state = state
        requestModelBuild()
    }

    override fun buildModels() {
        val currentState = state ?: return
        val host = this

        genericItem {
            id("poll_type_title")
            style(ItemStyle.BIG_TEXT)
            title(host.stringProvider.getString(CommonStrings.poll_type_title).toEpoxyCharSequence())
        }

        pollTypeSelectionItem {
            id("poll_type_selection")
            pollType(currentState.pollType)
            pollTypeChangedListener { _, id ->
                host.callback?.onPollTypeChanged(
                        if (id == R.id.openPollTypeRadioButton) {
                            PollType.DISCLOSED_UNSTABLE
                        } else {
                            PollType.UNDISCLOSED_UNSTABLE
                        }
                )
            }
        }

        genericItem {
            id("question_title")
            style(ItemStyle.BIG_TEXT)
            title(host.stringProvider.getString(CommonStrings.create_poll_question_title).toEpoxyCharSequence())
        }

        val questionImeAction = if (currentState.options.isEmpty()) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT

        formEditTextItem {
            id("question")
            value(currentState.question)
            hint(host.stringProvider.getString(CommonStrings.create_poll_question_hint))
            singleLine(true)
            imeOptions(questionImeAction)
            maxLength(340)
            onTextChange {
                host.callback?.onQuestionChanged(it)
            }
        }

        genericItem {
            id("options_title")
            style(ItemStyle.BIG_TEXT)
            title(host.stringProvider.getString(CommonStrings.create_poll_options_title).toEpoxyCharSequence())
        }

        currentState.options.forEachIndexed { index, option ->
            val imeOptions = if (index == currentState.options.size - 1) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            formEditTextWithDeleteItem {
                id("option_$index")
                value(option)
                hint(host.stringProvider.getString(CommonStrings.create_poll_options_hint, (index + 1)))
                singleLine(true)
                imeOptions(imeOptions)
                maxLength(340)
                onTextChange {
                    host.callback?.onOptionChanged(index, it)
                }
                onDeleteClicked {
                    host.callback?.onDeleteOption(index)
                }
            }
        }

        if (currentState.canAddMoreOptions) {
            genericButtonItem {
                id("add_option")
                text(host.stringProvider.getString(CommonStrings.create_poll_add_option))
                textColor(host.colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorAccent))
                gravity(Gravity.START)
                bold(true)
                highlight(false)
                buttonClickAction {
                    host.callback?.onAddOption()
                }
            }
        }
    }

    interface Callback {
        fun onQuestionChanged(question: String)
        fun onOptionChanged(index: Int, option: String)
        fun onDeleteOption(index: Int)
        fun onAddOption()
        fun onPollTypeChanged(type: PollType)
    }
}
