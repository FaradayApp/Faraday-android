/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */
package im.vector.app.features.home.room.list

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.textview.MaterialTextView
import dagger.hilt.EntryPoints
import im.vector.app.R
import im.vector.app.core.di.SingletonEntryPoint

class UnreadCounterBadgeView : MaterialTextView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val vectorPreferences = EntryPoints.get(context.applicationContext, SingletonEntryPoint::class.java).vectorPreferences()

    fun render(state: State) {
        when (state) {
            is State.Count -> renderAsCount(state)
            is State.Text -> renderAsText(state)
        }
    }

    private fun renderAsCount(state: State.Count) {
        val view = this

        with(state) {
            if (count == 0 && !markedUnread && (unread == 0 || !vectorPreferences.shouldShowUnimportantCounterBadge())) {
                visibility = View.INVISIBLE
            } else {
                visibility = View.VISIBLE
                val bgRes = if (count > 0 || markedUnread) {
                    if (highlighted) {
                        R.drawable.bg_unread_highlight
                    } else {
                        R.drawable.bg_unread_notification
                    }
                } else {
                    R.drawable.bg_unread_unimportant
                }
                setBackgroundResource(bgRes)
                view.text = if (count == 0 && markedUnread)
                    "!"
                else
                    RoomSummaryFormatter.formatUnreadMessagesCounter(if (count > 0) count else unread)
            }
        }
    }

    private fun renderAsText(state: State.Text) {
        val view = this

        with(state) {
            visibility = View.VISIBLE
            val bgRes = if (highlighted) R.drawable.bg_unread_highlight else R.drawable.bg_unread_notification
            setBackgroundResource(bgRes)
            view.text = text
        }
    }

    sealed class State {
        data class Count(val count: Int, val highlighted: Boolean, val unread: Int, val markedUnread: Boolean) : State()
        data class Text(val text: String, val highlighted: Boolean) : State()
    }
}
