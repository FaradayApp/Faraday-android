/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.settings.connectionmethod.compose

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.mvrx.MavericksView
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.core.utils.toast
import im.vector.app.features.login.LoginActivity
import im.vector.app.features.settings.ComposePreferenceFragment
import im.vector.app.features.themes.compose.FaradayTheme
import im.vector.lib.strings.CommonStrings
import timber.log.Timber

@AndroidEntryPoint
class ConnectionSettingsFragment : ComposePreferenceFragment(), MavericksView {
    override var titleRes: Int = CommonStrings.settings_connection_method

    private val viewModel: ConnectionSettingsViewModel by viewModels()

    @SuppressLint("UnsafeRepeatOnLifecycleDetector")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner.lifecycle))
            setContent {
                FaradayTheme {
                    LaunchedEffect(Unit) {
                        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.uiEvents.collect { event ->
                                when (event) {
                                    is ConnectionSettingsUiEvent.Connected -> onConnected(event.needRestart)
                                    is ConnectionSettingsUiEvent.UnknownError -> onErrorMessage(event.message)
                                }
                            }
                        }
                    }

                    ConnectionChooseScreen(viewModel)
                }
            }
        }
    }

    private fun onConnected(needRestart: Boolean = false) {
        Timber.d("ConnectionSettingsFragment: On connected")
        if (activity is LoginActivity) {
            (activity as? LoginActivity)?.onGetStarted(needRestart)
        } else {
            restartApp()
        }
    }

    private fun restartApp() {
        startActivity(
                Intent.makeRestartActivityTask(
                        requireContext().packageManager.getLaunchIntentForPackage(
                                requireContext().packageName
                        )!!.component
                )!!
        )
        Runtime.getRuntime().exit(0)
    }

    private fun onErrorMessage(message: String) {
        activity?.toast(message)
    }

    override fun invalidate() {}
}
