/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.ui.views

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import im.vector.app.R
import im.vector.app.databinding.ViewJoinConferenceBinding
import im.vector.app.features.themes.ThemeUtils

class JoinConferenceView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var views: ViewJoinConferenceBinding? = null
    var onJoinClicked: (() -> Unit)? = null
    var backgroundAnimator: Animator? = null

    init {
        inflate(context, R.layout.view_join_conference, this)
    }

    @SuppressLint("Recycle")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        views = ViewJoinConferenceBinding.bind(this)
        views?.joinConferenceButton?.setOnClickListener { onJoinClicked?.invoke() }
        val colorFrom = ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.conference_animation_from)
        val colorTo = ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.conference_animation_to)
        // Animate button color to highlight
        backgroundAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            duration = 500
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                views?.joinConferenceButton?.setBackgroundColor(color)
            }
        }
        backgroundAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        views = null
        backgroundAnimator?.cancel()
    }
}
