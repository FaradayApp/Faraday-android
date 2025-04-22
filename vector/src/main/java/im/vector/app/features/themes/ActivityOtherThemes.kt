/*
 * Copyright 2018-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.themes

import androidx.annotation.StyleRes
import im.vector.lib.ui.styles.R

/**
 * Class to manage Activity other possible themes.
 * Note that style for light theme is no more default and is no more declared in the Android Manifest
 */
sealed class ActivityOtherThemes(
        @StyleRes val light: Int,
        @StyleRes val dark: Int,
        @StyleRes val black: Int,
        @StyleRes val sc_light: Int,
        @StyleRes val sc: Int,
        @StyleRes val sc_dark: Int,
        @StyleRes val sc_colored: Int,
        @StyleRes val sc_dark_colored: Int
) {

    object Default : ActivityOtherThemes(
            R.style.Theme_Vector_Light,
            R.style.Theme_Vector_Dark,
            R.style.Theme_Vector_Black,
            R.style.AppTheme_SC_Light,
            R.style.AppTheme_SC,
            R.style.AppTheme_SC_Dark,
            R.style.AppTheme_SC_Colored,
            R.style.AppTheme_SC_Dark_Colored
    )

    object Launcher : ActivityOtherThemes(
            R.style.Theme_Vector_Launcher,
            R.style.Theme_Vector_Launcher,
            R.style.Theme_Vector_Launcher,
            R.style.AppTheme_Launcher_SC,
            R.style.AppTheme_Launcher_SC,
            R.style.AppTheme_Launcher_SC,
            R.style.AppTheme_Launcher_SC,
            R.style.AppTheme_Launcher_SC,
    )

    object AttachmentsPreview : ActivityOtherThemes(
            R.style.Theme_Vector_Black_AttachmentsPreview,
            R.style.Theme_Vector_Black_AttachmentsPreview,
            R.style.Theme_Vector_Black_AttachmentsPreview,
            R.style.AppTheme_AttachmentsPreview_SC,
            R.style.AppTheme_AttachmentsPreview_SC,
            R.style.AppTheme_AttachmentsPreview_SC,
            R.style.AppTheme_AttachmentsPreview_SC,
            R.style.AppTheme_AttachmentsPreview_SC
    )

    object VectorAttachmentsPreview : ActivityOtherThemes(
            R.style.Theme_Vector_Black_Transparent,
            R.style.Theme_Vector_Black_Transparent,
            R.style.Theme_Vector_Black_Transparent,
            R.style.AppTheme_Transparent_SC,
            R.style.AppTheme_Transparent_SC,
            R.style.AppTheme_Transparent_SC,
            R.style.AppTheme_Transparent_SC,
            R.style.AppTheme_Transparent_SC
    )
}
