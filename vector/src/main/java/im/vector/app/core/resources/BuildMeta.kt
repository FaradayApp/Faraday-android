/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.resources

import im.vector.app.BuildConfig

data class BuildMeta(
        val isDebug: Boolean,
        val applicationId: String,
        val applicationName: String,
        val lowPrivacyLoggingEnabled: Boolean,
        val versionName: String,
        val gitRevision: String,
        val gitRevisionDate: String,
        val gitBranchName: String,
        val flavorDescription: String,
        val flavorShortDescription: String,
) {
    val isInternalBuild: Boolean = BuildConfig.DEBUG || gitBranchName == "sm_fdroid"
    // Play Store has some annoying forms to fill out if we have all features, like easy-access to registering an account at matrix.org.
    // Accordingly, we want to disable some features for releases that go to the Play Store, while keeping them in all fdroid-based releases.
    val isPlayStoreBuild: Boolean =  "gplay" in gitBranchName
}
