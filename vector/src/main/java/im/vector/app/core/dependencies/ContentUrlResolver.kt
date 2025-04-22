/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.dependencies

import androidx.compose.runtime.staticCompositionLocalOf
import org.matrix.android.sdk.api.session.content.ContentUrlResolver

val LocalContentUrlResolver = staticCompositionLocalOf<ContentUrlResolver?> {
    error("ContentUrlResolver not provided")
}
