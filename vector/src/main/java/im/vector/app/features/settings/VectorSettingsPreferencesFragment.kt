/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.dialogs.PhotoOrVideoDialog
import im.vector.app.core.extensions.restart
import im.vector.app.core.preference.VectorListPreference
import im.vector.app.core.preference.VectorPreference
import im.vector.app.core.preference.VectorSwitchPreference
import im.vector.app.features.MainActivity
import im.vector.app.features.MainActivityArgs
import im.vector.app.features.VectorFeatures
import im.vector.app.features.analytics.plan.MobileScreen
import im.vector.app.features.configuration.VectorConfiguration
import im.vector.app.features.settings.font.FontScaleSettingActivity
import im.vector.app.features.themes.BubbleThemeUtils
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.session.presence.model.PresenceEnum
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class VectorSettingsPreferencesFragment :
        VectorSettingsBaseFragment() {

    @Inject lateinit var vectorPreferences: VectorPreferences
    @Inject lateinit var bubbleThemeUtils: BubbleThemeUtils
    @Inject lateinit var vectorConfiguration: VectorConfiguration
    @Inject lateinit var fontScalePreferences: FontScalePreferences
    @Inject lateinit var vectorFeatures: VectorFeatures
    @Inject lateinit var vectorLocale: VectorLocale

    companion object {
        const val BUBBLE_APPEARANCE_KEY = "BUBBLE_APPEARANCE_KEY"
    }

    override var titleRes = CommonStrings.settings_preferences
    override val preferenceXmlRes = R.xml.vector_settings_preferences

    //private var bubbleTimeLocationPref: VectorListPreference? = null
    private var alwaysShowTimestampsPref: VectorSwitchPreference? = null
    private var bubbleAppearancePref: VectorPreference? = null

    private val selectedLanguagePreference by lazy {
        findPreference<VectorPreference>(VectorPreferences.SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY)!!
    }
    private val textSizePreference by lazy {
        findPreference<VectorPreference>(VectorPreferences.SETTINGS_INTERFACE_TEXT_SIZE_KEY)!!
    }
    private val takePhotoOrVideoPreference by lazy {
        findPreference<VectorPreference>("SETTINGS_INTERFACE_TAKE_PHOTO_VIDEO")!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsScreenName = MobileScreen.ScreenName.SettingsPreferences
    }

    override fun bindPref() {
        // user interface preferences
        setUserInterfacePreferences()

        findPreference<VectorSwitchPreference>(VectorPreferences.SETTINGS_VOICE_MESSAGE)?.isEnabled = Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP

        // Language: follow system
        findPreference<VectorSwitchPreference>(VectorPreferences.SETTINGS_FOLLOW_SYSTEM_LOCALE)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue is Boolean) {
                        vectorLocale.followSystemLocale = newValue
                        vectorLocale.reloadLocale()
                        vectorConfiguration.applyToApplicationContext()
                        // Restart the Activity
                        activity?.restart()
                        true
                    } else {
                        false
                    }
                }

        // Themes
        val lightThemePref = findPreference<VectorListPreference>(ThemeUtils.APPLICATION_THEME_KEY)!!
        val darkThemePref = findPreference<VectorListPreference>(ThemeUtils.APPLICATION_DARK_THEME_KEY)!!
        lightThemePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue is String) {
                ThemeUtils.setApplicationLightTheme(requireContext().applicationContext, newValue)
                if (!ThemeUtils.shouldUseDarkTheme(requireContext())) {
                    // Restart the Activity
                    activity?.restart()
                }
                true
            } else {
                false
            }
        }
        if (ThemeUtils.darkThemePossible(requireContext())) {
            lightThemePref.title = getString(CommonStrings.settings_light_theme)
            darkThemePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    ThemeUtils.setApplicationDarkTheme(requireContext().applicationContext, newValue)
                    if (ThemeUtils.shouldUseDarkTheme(requireContext())) {
                        // Restart the Activity
                        activity?.restart()
                    }
                    true
                } else {
                    false
                }
            }
        } else {
            lightThemePref.title = getString(CommonStrings.settings_theme)
            darkThemePref.parent?.removePreference(darkThemePref)
        }

        val bubbleStylePreference = findPreference<VectorListPreference>(BubbleThemeUtils.BUBBLE_STYLE_KEY)
        bubbleStylePreference!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            updateBubbleDependencies(bubbleStyle = newValue as String)
            true
        }

        alwaysShowTimestampsPref = findPreference<VectorSwitchPreference>(VectorPreferences.SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY)
        bubbleAppearancePref = findPreference(BUBBLE_APPEARANCE_KEY)
        updateBubbleDependencies(bubbleStyle = bubbleStylePreference.value)

        findPreference<VectorSwitchPreference>(VectorPreferences.SETTINGS_PRESENCE_USER_ALWAYS_APPEARS_OFFLINE)!!.let { pref ->
            pref.isChecked = vectorPreferences.userAlwaysAppearsOffline()
            pref.setOnPreferenceChangeListener { _, newValue ->
                val presenceOfflineModeEnabled = newValue as? Boolean ?: false
                lifecycleScope.launch {
                    session.presenceService().setMyPresence(if (presenceOfflineModeEnabled) PresenceEnum.OFFLINE else PresenceEnum.ONLINE)
                }
                true
            }
        }

        findPreference<VectorSwitchPreference>(VectorPreferences.SETTINGS_PREF_SPACE_SHOW_ALL_ROOM_IN_HOME)!!.let { pref ->
            pref.isChecked = vectorPreferences.prefSpacesShowAllRoomInHome()
            pref.setOnPreferenceChangeListener { _, _ ->
                MainActivity.restartApp(requireActivity(), MainActivityArgs(clearCache = false))
                true
            }
        }

        /*
        findPreference<Preference>(VectorPreferences.SETTINGS_PREF_SPACE_CATEGORY)!!.let { pref ->
            pref.isVisible = !vectorPreferences.isNewAppLayoutEnabled()
            pref.isEnabled = !vectorPreferences.isNewAppLayoutEnabled()
        }
         */

        // Url preview
        /*
        TODO Note: we keep the setting client side for now
        findPreference<SwitchPreference>(VectorPreferences.SETTINGS_SHOW_URL_PREVIEW_KEY)!!.let {
            it.isChecked = session.isURLPreviewEnabled

            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                if (null != newValue && newValue as Boolean != session.isURLPreviewEnabled) {
                    displayLoadingView()
                    session.setURLPreviewStatus(newValue, object : MatrixCallback<Unit> {
                        override fun onSuccess(info: Void?) {
                            it.isChecked = session.isURLPreviewEnabled
                            hideLoadingView()
                        }

                        private fun onError(errorMessage: String) {
                            activity?.toast(errorMessage)

                            onSuccess(null)
                        }

                        override fun onNetworkError(e: Exception) {
                            onError(e.localizedMessage)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            onError(e.localizedMessage)
                        }

                        override fun onUnexpectedError(e: Exception) {
                            onError(e.localizedMessage)
                        }
                    })
                }

                false
            }
        }
         */

        // update keep medias period
        findPreference<VectorPreference>(VectorPreferences.SETTINGS_MEDIA_SAVING_PERIOD_KEY)!!.let {
            it.summary = vectorPreferences.getSelectedMediasSavingPeriodString()

            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                context?.let { context: Context ->
                    MaterialAlertDialogBuilder(context)
                            .setSingleChoiceItems(
                                    im.vector.lib.strings.R.array.media_saving_choice,
                                    vectorPreferences.getSelectedMediasSavingPeriod()
                            ) { d, n ->
                                vectorPreferences.setSelectedMediasSavingPeriod(n)
                                d.cancel()

                                it.summary = vectorPreferences.getSelectedMediasSavingPeriodString()
                            }
                            .show()
                }

                false
            }
        }

        // Take photo or video
        updateTakePhotoOrVideoPreferenceSummary()
        takePhotoOrVideoPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            PhotoOrVideoDialog(requireActivity(), vectorPreferences).showForSettings(object : PhotoOrVideoDialog.PhotoOrVideoDialogSettingsListener {
                override fun onUpdated() {
                    updateTakePhotoOrVideoPreferenceSummary()
                }
            })
            true
        }
    }

    private fun updateTakePhotoOrVideoPreferenceSummary() {
        takePhotoOrVideoPreference.summary = getString(
                when (vectorPreferences.getTakePhotoVideoMode()) {
                    VectorPreferences.TAKE_PHOTO_VIDEO_MODE_PHOTO -> CommonStrings.option_take_photo
                    VectorPreferences.TAKE_PHOTO_VIDEO_MODE_VIDEO -> CommonStrings.option_take_video
                    /* VectorPreferences.TAKE_PHOTO_VIDEO_MODE_ALWAYS_ASK */
                    else -> CommonStrings.option_always_ask
                }
        )
    }

    // ==============================================================================================================
    // user interface management
    // ==============================================================================================================

    private fun setUserInterfacePreferences() {
        // Selected language
        selectedLanguagePreference.summary = vectorLocale.localeToLocalisedString(vectorLocale.applicationLocale)

        // Text size
        textSizePreference.summary = getString(fontScalePreferences.getResolvedFontScaleValue().nameResId)

        textSizePreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(activity, FontScaleSettingActivity::class.java))
            true
        }
    }

    private fun updateBubbleDependencies(bubbleStyle: String) {
        //bubbleTimeLocationPref?.setEnabled(BubbleThemeUtils.isBubbleTimeLocationSettingAllowed(bubbleStyle))
        alwaysShowTimestampsPref?.isEnabled = bubbleStyle in listOf(BubbleThemeUtils.BUBBLE_STYLE_NONE, BubbleThemeUtils.BUBBLE_STYLE_START)
        bubbleAppearancePref?.isEnabled = bubbleStyle in listOf(BubbleThemeUtils.BUBBLE_STYLE_START, BubbleThemeUtils.BUBBLE_STYLE_BOTH)
    }
}
